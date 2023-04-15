(ns eval.deps-try
  {:clj-kondo/config '{:lint-as {babashka.fs/with-temp-dir clojure.core/let}}}
  (:require
   [babashka.classpath :as cp :refer [get-classpath]]
   [babashka.deps :as deps]
   [babashka.process :as p]
   [clojure.string :as str]
   [clojure.java.io :as io]))

(def init-cp (get-classpath))

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
  (let [dev?    (nil? (io/resource "VERSION"))
        bin     (if dev? "deps-try-dev" "deps-try")
        version (str/trim
                 (if dev?
                   (:out (p/sh {} "git" "describe" "--tags"))
                   (slurp (io/resource "VERSION"))))]
    (println (str bin " " version))))

(defn- print-usage? [args]
  (contains? #{"-h" "--help" "help"} (first args)))

(defn- print-version? [args]
  (contains? #{"-v" "--version" "version"} (first args)))

(defn- invalid-args? [args]
  (when-let [[some-dep] (not-empty args)]
    (not (re-seq #"/" some-dep))))

(def ^:private clojure-cli-version-re #"^(\d+)\.(\d+)\.(\d+)\.(\d+)$")

(defn- clojure-cli-version []
  (peek (str/split (str/trimr (:out (p/sh "clojure" "--version"))) #"\s+")))

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
(defn- warn [m]
  (let [no-color?        (or (System/getenv "NO_COLOR") (= "dumb" (System/getenv "TERM")))
        maybe-color-wrap #(if-not no-color?
                            (str "\033[1m" "\033[33m" % "\033[0m")
                            (str "WARNING " %))]
    (println (maybe-color-wrap m))))

(defn- warn-unless-minimum-clojure-cli-version [minimum version]
  (when-not (at-least-version? minimum version)
    (warn (str "Adding libraries to this REPL-session via ':deps/try some/lib' won't work as it requires Clojure CLI version >= " minimum " (current: " version ")."))))

(defn -main [& args]
  (warn-unless-minimum-clojure-cli-version "1.11.1.1273" (clojure-cli-version))
  (if (print-version? args)
    (print-version)
    (if (or (print-usage? args) (invalid-args? args))
      (print-usage)
      (fs/with-temp-dir [tmp {}]
        (let [verbose-output (with-out-str (deps/clojure {:dir (str tmp)} "-Sverbose" "-Spath"))
              cp-file        (parse-cp-file verbose-output)
              basis-file     (str/replace cp-file #".cp$" ".basis")
              requested-deps (try-deps/parse-dep-args args)
              default-cp     (deps->cp tmp '{org.clojure/clojure {:mvn/version "1.12.0-alpha2"}})
              requested-cp   (deps->cp tmp requested-deps)
              classpath      (str default-cp fs/path-separator init-cp fs/path-separator requested-cp)]
          (p/exec "java" "-classpath" classpath
                  (str "-Dclojure.basis=" basis-file)
                  "clojure.main" "-m" "eval.deps-try.try"))))))
