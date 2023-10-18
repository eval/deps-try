(ns eval.deps-try
  {:clj-kondo/config '{:lint-as {babashka.fs/with-temp-dir clojure.core/let
                                 eval.deps-try.util/pred-> clojure.core/->}}}
  (:require
   [babashka.classpath :as cp :refer [get-classpath]]
   [babashka.cli :as cli]
   [babashka.process :as p]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [eval.deps-try.recipe :as recipe]
   [eval.deps-try.util :as util]
   [eval.deps-try.errors :as errors]))

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

(defn- print-usage []
  (println "Usage:
  deps-try [dep-name [dep-version] [dep2-name ...] ...] [--recipe recipe]

Supported `dep-name` types:
- maven
  e.g. `metosin/malli`, `org.clojure/cache`.
- git
  - infer-notation, e.g. `com.github.user/project`, `ht.sr.~user/project`.
  - url, e.g. `https://github.com/user/project`, `https://anything.org/user/project.git`.
- local
  - path to project containing `deps.edn`, e.g. `.`, `~/projects/my-project`, `./path/to/project`.

Possible `recipe` values:
- url, e.g. \"https://github.com/eval/deps-try/blob/master/recipes/namespaces.clj\"
- paths, e.g. \"~/recipes/foo.clj\"

Examples:
# A REPL using the latest Clojure version
$ deps-try

# A REPL with specific dependencies (latest version implied)
$ deps-try metosin/malli criterium/criterium

# ...specific version
$ deps-try metosin/malli 0.9.2

# Dependency from GitHub/GitLab/SourceHut (gets you the latest SHA from the default branch)
$ deps-try https://github.com/metosin/malli

# ...a specific branch/tag/SHA
$ deps-try https://github.com/metosin/malli some-branch-tag-or-sha

# ...using the 'infer' notation, e.g.
# com.github.<user>/<project>, com.gitlab.<user>/<project>, ht.sr.~<user>/<project>
$ deps-try com.github.metosin/malli

# A local project
$ deps-try . ~/some/project ../some/other/project

During a REPL-session:
# add additional dependencies
user=> :deps/try dev.weavejester/medley \"~/some/project\"

# see help for all options
user=> :repl/help
"))

(defn- print-version []
  (let [dev?    (nil? (io/resource "VERSION"))
        bin     (if dev? "deps-try-dev" "deps-try")
        version (str/trim
                 (if dev?
                   (let [git-dir (fs/file (io/resource ".git"))]
                     (:out (p/sh {} "git" "--git-dir" (str git-dir) "describe" "--tags")))
                   (slurp (io/resource "VERSION"))))]
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

(defn- warn [m]
  (print-message m {:msg-type :warning}))

(defn- error [m]
  (print-message m {:msg-type :error}))

(defn- warn-unless-minimum-clojure-cli-version [minimum version]
  (when-not (at-least-version? minimum version)
    (warn (str "Adding (additional) libraries to this REPL-session via ':deps/try some/lib' won't work as it requires Clojure CLI version >= " minimum " (current: " version ")."))))

(defn- print-error-and-exit! [m]
  (print-message m {:msg-type :error})
  (System/exit 1))

(defn- tdeps-verbose->map [s]
  (let [[cp & pairs] (reverse (str/split-lines s))
        keywordize   (comp keyword #(str/replace % \_ \-) name)] ;; `name` makes it also usable for keywords
    (update-keys
     (into {:cp cp}
           (map #(str/split % #" += +") (filter seq pairs))) keywordize)))


(defn- start-repl! [{requested-deps              :deps
                     {recipe-deps     :deps
                      recipe-location :location} :recipe :as args}]
  #_(prn :args args)
  (let [default-deps                 {'org.clojure/clojure {:mvn/version "1.12.0-alpha4"}}
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

(defn -main [& args]
  (let [parsed-opts (try
                      (cli/parse-opts args cli-opts)
                      (catch Exception e
                        {:error (:msg (ex-data e))}))]
    (cond
      (:version parsed-opts) (print-version)
      (:help parsed-opts)    (print-usage)

      :else (let [parsed-recipe         (some-> parsed-opts :recipe (recipe/parse-arg))
                  assoc-possible-recipe (fn [acc {:keys [error] :deps-try/keys [deps] :as recipe}]
                                          (if-not (seq recipe)
                                            acc
                                            (let [{parse-dep-error :error
                                                   parsed-deps     :deps} (when (seq deps)
                                                                            (try-deps/parse-dep-args {:deps deps}))
                                                  error                   (or error parse-dep-error)]
                                              (cond-> acc
                                                error       (assoc :error error)
                                                parsed-deps (assoc :recipe (assoc recipe
                                                                                  :deps parsed-deps))))))
                  {error :error}        (util/pred-> (complement :error) parsed-opts
                                                     (try-deps/parse-dep-args)
                                                     (assoc-possible-recipe parsed-recipe)
                                                     (start-repl!))]
              (when error
                (print-error-and-exit! (errors/format-error error)))))))


(comment
  
  (try-deps/parse-dep-args {:deps ["metosin/malli"]})
 
  (try
    (cli/parse-opts '("other/bar" "--recipe" "rec1" "some/bar")
                   {:exec-args {:deps []}
                    :alias {:h :help, :v :version},
                    :coerce {:recipe :string :deps [:string]},
                    :restrict [:recipe :deps :help :version], :args->opts (repeat :deps)})
    (catch Exception e
      (ex-data e)))

  #_:end)
