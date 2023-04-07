(ns eval.deps-try
  {:clj-kondo/config '{:lint-as {babashka.fs/with-temp-dir clojure.core/let}}}
  (:require
   [babashka.classpath :as cp :refer [get-classpath]]
   [babashka.deps :as deps]
   [babashka.process :as p]
   [clojure.string :as str]
   [clojure.java.io :as io]))

(def init-cp (get-classpath))

(deps/add-deps '{:deps {org.clojure/tools.gitlibs {:mvn/version "2.4.181"}}})

(require '[eval.deps-try.deps :as try-deps]
         '[babashka.fs :as fs] :reload
         '[babashka.http-client] :reload) ;; reload so we use the dep, not the built-in

(defn parse-cp-file [s]
  (some #(when (str/includes? % "cp_file")
           (str/trim (second (str/split % #"=")))) (str/split-lines s)))

(defn deps->cp [tmp deps]
  (str/trim (with-out-str (deps/clojure {:dir (str tmp)} "-Spath" "-Sdeps" (str {:deps deps})))))

(defn print-usage []
  (println "Usage:
  deps-try [dep-name [dep-version] [dep2-name ...] ...]

Example:
# A REPL using the latest Clojure version
$ deps-try

# A REPL with specific dependencies (latest version implied)
$ deps-try metosin/malli criterium/criterium

# ...specific version
$ deps-try metosin/malli 0.9.2

# Dependency from GitHub/GitLab/SourceHut (gets you the latest SHA from the default branch)
$ deps-try https://github.com/metosin/malli

# ...a specific branch/SHA
$ deps-try https://github.com/metosin/malli some-branch-sha-or-tag

# ...using the 'infer' notation, e.g.
# com.github.<user>/<project>, com.gitlab.<user>/<project>, ht.sr.<user>/<project>
$ deps-try com.github.metosin/malli

During a REPL-session:
# see help for all options
user=> :repl/help

# add dependencies
user=> :deps/try dev.weavejester/medley
"))

(defn print-version []
  (println (str "deps-try " (str/trim (slurp (io/resource "VERSION"))))))

(defn- print-usage? [args]
  (contains? #{"-h" "--help" "help"} (first args)))

(defn- print-version? [args]
  (contains? #{"-v" "--version" "version"} (first args)))

(defn- invalid-args? [args]
  (when-let [[some-dep] (not-empty args)]
    (not (re-seq #"/" some-dep))))

(defn -main [& args]
  (if (print-version? args)
    (print-version)
    (if (or (print-usage? args) (invalid-args? args))
      (print-usage)
      (fs/with-temp-dir [tmp {}]
        (let [verbose-output (with-out-str (deps/clojure {:dir (str tmp)} "-Sverbose" "-Spath"))
              cp-file        (parse-cp-file verbose-output)
              basis-file     (str/replace cp-file #".cp$" ".basis")
              requested-deps (try-deps/parse-dep-args args)
              default-cp     (deps->cp tmp '{org.clojure/clojure {:mvn/version "RELEASE"}})
              requested-cp   (deps->cp tmp requested-deps)
              classpath      (str default-cp fs/path-separator init-cp fs/path-separator requested-cp)]
          (p/exec "java" "-classpath" classpath
                  (str "-Dclojure.basis=" basis-file)
                  "clojure.main" "-m" "eval.deps-try.try"))))))
