(ns eval.deps-try
  {:clj-kondo/config '{:lint-as {babashka.fs/with-temp-dir clojure.core/let
                                 eval.deps-try.util/pred-> clojure.core/->}}}
  (:require
   [babashka.classpath :as cp :refer [get-classpath]]
   [babashka.cli :as cli]
   [babashka.process :as p]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [eval.deps-try.errors :as errors]
   [eval.deps-try.recipe :as recipe]
   [eval.deps-try.util :as util]
   [strojure.ansi-escape.core :as ansi]))

(def init-cp (get-classpath))

(require '[eval.deps-try.deps :as try-deps]
         '[babashka.fs :as fs] :reload
         '[babashka.http-client] :reload) ;; reload so we use the dep, not the built-in

(defn- run-clojure [opts & args]
  (let [[opts args] (if (map? opts) [opts args] [nil (cons opts args)])]
    (fs/with-temp-dir [tmp {}]
      (apply p/sh (merge {:dir (str tmp)} opts)
             "clojure" args))))

(defn- deps->cp [deps]
  (when (seq deps)
    (str/trim (:out (run-clojure  "-Spath" "-Sdeps" (str {:paths [] :deps deps}))))))

(def ^:private dev? (nil? (io/resource "VERSION")))

(def ^:private version
  (str/trim
   (if dev?
     (let [git-dir (fs/file (io/resource ".git"))]
       (:out (p/sh {} "git" "--git-dir" (str git-dir) "describe" "--tags")))
     (slurp (io/resource "VERSION")))))

(defn- print-usage []
  (let [usage [(str "A CLI to quickly try Clojure (libraries) on rebel-readline.")
               (str ansi/bold "VERSION" ansi/reset \newline
                    "  " version)
               (str ansi/bold "USAGE" ansi/reset \newline
                    "  $ deps-try [dep-name [dep-version] [dep2-name ...] ...] [--recipe recipe]")
               (str ansi/bold "OPTIONS" ansi/reset \newline
                    "  dep-name\n"
                    "    dependency from maven (e.g. `metosin/malli`, `org.clojure/cache`),\n"
                    "    git (e.g. `com.github.user/project`, `ht.sr.user/project`," \newline
                    "    `https://github.com/user/project`, `https://anything.org/user/project.git`)," \newline
                    "    or a local folder containing a file `deps.edn` (e.g. `.`," \newline
                    "    `~/projects/my-project`, `./path/to/project`)." \newline
                    \newline
                    "  dep-version (optional)\n"
                    "    A maven version (e.g. `1.2.3`, `LATEST`) or git ref (e.g. `some-branch`,"  \newline
                    "    `v1.2.3`)." \newline
                    "    The id of a PR or MR is also an acceptable version for git deps (e.g. `^123`)." \newline
                    "    When not provided, `LATEST` is implied for maven deps and the latest SHA" \newline
                    "    of the default-branch for git deps." \newline
                    \newline
                    "  --recipe" \newline
                    "    Name of recipe (see recipes command) or a path or url to a Clojure file." \newline
                    "    The REPL-history will be seeded with the expressions from the recipe.")
               (str ansi/bold "EXAMPLES" ansi/reset \newline
                    "  ;; The latest version of malli from maven, and git-tag v1.3.894 of the next-jdbc repository" \newline
                    "  $ deps-try metosin/malli io.github.seancorfield/next-jdbc v1.3.894")
               (str ansi/bold "COMMANDS" ansi/reset \newline
                    "  recipes  show list of recipes"
                    #_#_#_"  ;; The latest version of malli from maven, and git-tag v1.3.894 of the next-jdbc repository" \newline
                      "  $ deps-try metosin/malli io.github.seancorfield/next-jdbc v1.3.894")
               nil]]
    (print (str/join \newline (interpose nil usage)))))

(defn- print-version []
  (let [bin (if dev? "deps-try-dev" "deps-try")]
    (println (str bin " " version))))

(def ^:private clojure-cli-version-re #"^(\d+)\.(\d+)\.(\d+)\.(\d+)")

(defn- parse-clojure-cli-version [s]
  (map parse-long (rest (re-find clojure-cli-version-re s))))

(defn- at-least-version? [version-or-above version]
  (let [[major1 minor1 patch1 build1] (parse-clojure-cli-version version-or-above)
        [major2 minor2 patch2 build2] (parse-clojure-cli-version version)]
    (or (< major1 major2)
        (and (= major1 major2) (< minor1 minor2))
        (and (= major1 major2) (= minor1 minor2) (< patch1 patch2))
        (and (= major1 major2) (= minor1 minor2) (= patch1 patch2) (or (= build1 build2)
                                                                       (< build1 build2))))))

(defn- print-message [msg {:keys [msg-type]}]
  (let [no-color?        (or (System/getenv "NO_COLOR") (= "dumb" (System/getenv "TERM")))
        color-by-type    {:warning {"WARNING" ["\033[1m" "\033[33m" :msg "\033[0m"]}
                          :error   {"ERROR" ["\033[1m" "\033[31m" :msg "\033[0m"]}}
        [no-color color] (first (color-by-type msg-type))
        maybe-color-wrap #(if no-color?
                            (str no-color %)
                            (apply str (replace {:msg %} color)))]
    (println (maybe-color-wrap msg))))

(defn- print-warning [m]
  (print-message m {:msg-type :warning}))

(defn- print-error [m]
  (print-message m {:msg-type :error}))

(defn- warn-unless-minimum-clojure-cli-version [minimum version]
  (when-not (at-least-version? minimum version)
    (print-warning (str "Adding (additional) libraries to this REPL-session via ':deps/try some/lib' won't work as it requires Clojure CLI version >= " minimum " (current: " version ")."))))

(defn- print-error-and-exit! [m]
  (print-error m)
  (System/exit 1))

(defn- tdeps-verbose->map [s]
  (let [[cp & pairs] (reverse (str/split-lines s))
        keywordize   (comp keyword #(str/replace % \_ \-) name)] ;; `name` makes it also usable for keywords
    (update-keys
     (into {:cp cp}
           (map #(str/split % #" += +") (filter seq pairs))) keywordize)))


(defn- start-repl! [{requested-deps              :deps
                     {recipe-deps     :deps
                      recipe-location :location} :recipe :as _args}]
  #_(prn ::args args)
  (let [default-deps                 {'org.clojure/clojure {:mvn/version "1.12.0-alpha5"}}
        {:keys         [cp-file]
         default-cp    :cp
         tdeps-version :version
         :as           _tdeps-paths} (-> (run-clojure "-Sverbose" "-Spath"
                                                      "-Sdeps" (str {:paths [] :deps default-deps}))
                                         :out
                                         str/trim
                                         tdeps-verbose->map)

        basis-file   (str/replace cp-file #".cp$" ".basis")
        requested-cp (deps->cp requested-deps)
        recipe-cp    (deps->cp recipe-deps)
        classpath    (cond-> (str (fs/cwd) fs/path-separator
                                  default-cp fs/path-separator
                                  init-cp fs/path-separator
                                  requested-cp)
                       recipe-cp (str fs/path-separator recipe-cp))
        jvm-opts     [(str "-Dclojure.basis=" basis-file)]]
    (warn-unless-minimum-clojure-cli-version "1.11.1.1273" tdeps-version)
    (let [cmd (cond-> ["java" "-classpath" classpath]
                (seq jvm-opts)  (into jvm-opts)
                :always         (into ["clojure.main" "-m" "eval.deps-try.try"])
                recipe-location (into ["--recipe" recipe-location]))]
      (apply p/exec cmd))))

(def ^:private cli-opts {:exec-args {:deps []}
                         :alias     {:h :help, :v :version},
                         :coerce    {:recipe :string :deps [:string]},
                         :restrict  [:recipe :deps :help :version], :args->opts (repeat :deps)})

(defn- handle-recipes-cmd [{{:keys [refresh]} :opts}]
  ;; TODO print recipes (after refresh)
  ;; TODO handle option help
  ;; TODO err on more args
  (println (str "Showing all the recipes " (when refresh "after refresh!"))))

(defn- handle-repl-start [{{:keys [recipe] :as parsed-opts} :opts}]
  (let [parsed-recipe         (some-> recipe (recipe/parse-arg))
        assoc-possible-recipe (fn [acc {:keys [error] :deps-try/keys [deps] :as recipe}]
                                #_(prn ::recipe recipe)
                                (if-not (seq recipe)
                                  acc
                                  (let [{parse-dep-error :error
                                         parsed-deps     :deps} (when (seq deps)
                                                                  (try-deps/parse-dep-args {:deps deps}))
                                        error                   (or error parse-dep-error)]
                                    (cond-> (assoc acc :recipe recipe)
                                      error       (assoc :error error)
                                      parsed-deps (update :recipe assoc :deps parsed-deps)))))
        {error :error}        (util/pred-> (complement :error) parsed-opts
                                           (try-deps/parse-dep-args)
                                           (assoc-possible-recipe parsed-recipe)
                                           (start-repl!))]
    (when error
      (print-error-and-exit! (errors/format-error error)))))

(defn- handle-fallback-cmd [{{:keys [help version]} :opts :as cli-args}]
  (cond
    version (print-version)
    help    (print-usage)
    :else   (handle-repl-start cli-args)))


(comment
  (cli/parse-opts '("foo/bar") cli-opts #_{:alias {:h :help :v :version}})

  (cli/dispatch dispatch-table
   #_[{:cmds ["recipe:ls"], :fn identity #_#'eval.deps-try/handle-cmd-recipe-ls, :restrict [:refresh :help], :exec-args {}}
    {:cmds [], :fn identity, :restrict [:version :deps :help :recipe], :coerce {:deps [:string]} :alias {:h :help, :v :version} :exec-args {:deps []} :args->opts (repeat :deps)}]
                '("foo/bar") {} #_cli-opts)
  #_:end)

(def ^:private dispatch-table
  [{:cmds     ["recipes"]
    :fn       #'handle-recipes-cmd
    :restrict [:refresh :help]}
   {:cmds      []
    :fn        #'handle-fallback-cmd
    :restrict  [:version :deps :help :recipe]
    :coerce    {:deps [:string]}
    :alias     {:h :help
                :v :version}
    :exec-args {:deps []} :args->opts (repeat :deps)}])

(defn -main [& args]
  (try
    (cli/dispatch dispatch-table args {})
    (catch Exception e
      (-> e
          ex-data
          :msg
          errors/format-error
          print-error-and-exit!))))

(comment

  (try-deps/parse-dep-args {:deps ["metosin/malli"]})

  (try
    (cli/parse-opts '("other/bar" "--recipe" "-")
                   {:exec-args {:deps []}
                    :alias {:h :help, :v :version},
                    :coerce {:recipe :string :deps [:string]},
                    :restrict [:recipe :deps :help :version], :args->opts (repeat :deps)})
    (catch Exception e
      (ex-data e)))

  #_:end)
