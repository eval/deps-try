(ns eval.deps-try
  {:clj-kondo/config '{:lint-as {eval.deps-try.fs/with-temp-dir clojure.core/let
                                 eval.deps-try.util/pred-> clojure.core/->}}}
  (:require
   [babashka.classpath :as cp :refer [get-classpath]]
   [babashka.process :as p]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [deps-try.cli :as cli]
   [deps-try.http-client]
   [eval.deps-try.ansi-escape :as ansi]
   [eval.deps-try.deps :as try-deps]
   [eval.deps-try.errors :as errors]
   [eval.deps-try.fs :as fs]
   [eval.deps-try.recipe :as recipe]
   [eval.deps-try.util :as util]))

(def init-cp (get-classpath))

(defn- run-repl [& args]
  #_(prn ::run-clojure :opts opts :args args)
  (fs/with-temp-dir [tmp {}]
    (apply p/exec {:dir (str tmp)}
           "clojure" args)))

(def ^:private dev? (nil? (io/resource "VERSION")))

(def ^:private version
  (string/trim
   (if dev?
     (let [git-dir (fs/file (io/resource ".git"))]
       (:out (p/sh {} "git" "--git-dir" (str git-dir) "describe" "--tags")))
     (slurp (io/resource "VERSION")))))

(defn- print-usage []
  (let [usage [(str "A CLI to quickly try Clojure (libraries) on rebel-readline.")
               (str ansi/bold "VERSION" ansi/reset \newline
                    "  " version)
               (str ansi/bold "USAGE" ansi/reset \newline
                    "  $ deps-try [dep-name [dep-version] [dep2-name ...] ...] [--recipe[-ns] recipe]")
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
                    "  --recipe, --recipe-ns" \newline
                    "    Name of recipe (see recipes command) or a path or url to a Clojure file." \newline
                    "    The REPL-history will be seeded with the (ns-)steps from the recipe.")
               (str ansi/bold "EXAMPLES" ansi/reset \newline
                    "  ;; The latest version of malli from maven, and git-tag v1.3.894 of the next-jdbc repository" \newline
                    "  $ deps-try metosin/malli io.github.seancorfield/next-jdbc v1.3.894")
               (str ansi/bold "COMMANDS" ansi/reset \newline
                    "  recipes    list built-in recipes (`recipes --refresh` to update)")
               nil]]
    (print (string/join \newline (interpose nil usage)))))

(defn- full-version []
  (let [bin (if dev? "deps-try-dev" "deps-try")]
    (str bin " " version)))

(defn- print-version []
  (println (full-version)))

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

(defn- print-error-and-exit! [m]
  (print-error m)
  (System/exit 1))

(defn- start-repl! [{requested-deps              :deps
                     prepare                     :prepare
                     {recipe-deps     :deps
                      ns-only         :ns-only
                      recipe-location :location} :recipe :as _args}]
  ;; TODO re-enable?
  ;; see https://github.com/clojure/brew-install/blob/1.11.3/CHANGELOG.md
  #_(warn-unless-minimum-clojure-cli-version "1.11.1.1273" tdeps-version)
  (let [paths     (into ["."] ;; needed for clojure.java.io/resource
                        (string/split init-cp #":"))
        deps      (merge
                   {'org.clojure/clojure {:mvn/version "1.12.0"}}
                   recipe-deps
                   requested-deps)
        main-args (cond-> ["--version" version]
                    recipe-location (into (if ns-only
                                            ["--recipe-ns" recipe-location]
                                            ["--recipe" recipe-location]))
                    prepare         (conj "-P"))]
    (apply run-repl "-Sdeps" (str {:paths paths
                                   :deps  deps})
           "-M" "-m" "eval.deps-try.try" main-args)))

(defn- recipe-manifest-contents [{:keys [refresh] :as _cli-opts}]
  (let [remote-manifest-file "https://raw.githubusercontent.com/eval/deps-try/master/recipes/manifest.edn"
        default-recipes-path (doto (fs/path (fs/xdg-data-home "deps-try") "recipes" "default")
                               (fs/create-dirs))
        manifest-file        (fs/file default-recipes-path "manifest.edn")]
    (when (or
           refresh
           (not (fs/exists? manifest-file))
           (util/file-last-modified-before? manifest-file {:weeks -1}))
      (try (spit manifest-file (slurp remote-manifest-file)) (catch Exception)))
    ;; TODO edn read error at this point: file not present, corrupt contents(???)
    ;; currently: "/home/user/.local/share/deps-try/recipes/default/manifest.edn (No such file or directory)"
    (edn/read-string (slurp manifest-file))))

(defn recipes [cli-opts]
  (:deps-try.manifest/recipes (recipe-manifest-contents cli-opts)))

(def ^:private cli-opts {:exec-args {:deps []}
                         :alias     {:h :help, :v :version},
                         :coerce    {:recipe :string :deps [:string]},
                         :restrict  [:recipe :deps :help :version], :args->opts (repeat :deps)})

(defn- print-recipes [recipes cli-opts]
  (let [no-color?      (util/no-color? cli-opts)
        plain-mode?    (util/plain-mode? cli-opts)
        skip-header?   plain-mode?
        column-atts    {:deps-try.recipe/name "name" :deps-try.recipe/title "title"}
        max-width      (when-not plain-mode?
                         (:cols (util/terminal-dimensions)))
        title-truncate #(util/truncate %1 {:truncate-to %2
                                           :omission    "…"})]
    (util/print-table column-atts recipes {:skip-header         skip-header?
                                           :max-width           max-width
                                           :width-reduce-column :deps-try.recipe/title
                                           :width-reduce-fn     title-truncate
                                           :no-color            no-color?})))

(defn- handle-recipes-cmd [{cli-opts :opts}]
  ;; TODO print recipes (after refresh)
  ;; TODO handle option help
  ;; TODO err on more args
  (print-recipes (sort-by :deps-try.recipe/name (recipes cli-opts)) cli-opts))


(defn- handle-repl-start [{{:keys [recipe recipe-ns] :as parsed-opts} :opts}]
  (let [recipes-by-name       #(update-vals (group-by :deps-try.recipe/name (recipes {})) first)
        known-recipe-url      (some->> (or recipe recipe-ns)
                                       (get (recipes-by-name))
                                       :deps-try.recipe/url)
        parsed-recipe         (some-> (or known-recipe-url recipe recipe-ns)
                                      (recipe/parse-arg)
                                      (assoc :ns-only (boolean recipe-ns)))
        assoc-possible-recipe (fn [acc {:keys [error] :deps-try.recipe/keys [deps] :as recipe}]
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
                                           (->> (merge parsed-opts))
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
    :restrict [:refresh :help :plain :color]}
   {:cmds      []
    :fn        #'handle-fallback-cmd
    :restrict  [:version :deps :help :recipe :recipe-ns :print-deps :prepare]
    :coerce    {:deps [:string] :prepare boolean}
    :alias     {:h :help
                :v :version
                :P :prepare}
    :exec-args {:deps []} :args->opts (repeat :deps)}])

(defn -main [& args]
  #_(prn ::args args)
  (try
    (cli/dispatch dispatch-table args {})
    (catch Exception e
      (-> e
          (Throwable->map)
          :cause
          errors/format-error
          print-error-and-exit!))))

(comment

  ;; manifest
  {:deps-try.manifest/recipes [{:deps-try.recipe/name      "clojure/namespaces"
                                :deps-try.recipe/title     "Learn all about Clojure namespaces"
                                :deps-try.recipe/desc "Some introduction to namespaces."}]}

  (try-deps/parse-dep-args {:deps ["metosin/malli"]})

  (try
    (cli/parse-opts '("other/bar" "--recipe" "-")
                    {:exec-args {:deps []}
                     :alias     {:h :help, :v :version},
                     :coerce    {:recipe :string :deps [:string]},
                     :restrict  [:recipe :deps :help :version], :args->opts (repeat :deps)})
    (catch Exception e
      (ex-data e)))


  #_:end)
