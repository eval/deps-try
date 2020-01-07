(ns deps-try.main
  (:require [clojure.tools.deps.alpha.repl :as deps-repl]
            [rebel-readline.clojure.main :as rebel-main]
            [rebel-readline.commands :as rebel-readline]
            [rebel-readline.core :as rebel-core]))

;; SOURCE https://github.com/avescodes/lein-try/blob/master/src/leiningen/try.clj
(defn- version-string?
  "Check if a given String represents a version number."
  [^String s]
  (or (contains? #{"RELEASE" "LATEST"} s)
      (Character/isDigit (first s))))


;; SOURCE https://github.com/avescodes/lein-try/blob/master/src/leiningen/try.clj
(def ->dep-pairs
  "From a sequence of command-line args describing dependency-version pairs,
  return a list of vector pairs. If no version is given, 'RELEASE' will be
  used.
  Example:
  (->dep-pairs [\"clj-time\" \"\\\"0.5.1\\\"]\"])
  ; -> ([clj-time \"0.5.1\"])
  (->dep-pairs [\"clj-time\" \"\\\"0.5.1\\\"\"])
  ; -> ([clj-time \"0.5.1\"])
  (->dep-pairs [\"clj-time\" \"conformity\"])
  ; -> ([clj-time \"RELEASE\"] [conformity \"RELEASE\"])"
  (letfn [(lazy-convert [args]
            (lazy-seq
              (when (seq args)
                (let [[^String artifact-str & rst] args
                      artifact                     (symbol artifact-str)]
                  (if-let [[^String v & nxt] (seq rst)]
                    (if (version-string? v)
                      (cons [artifact v] (lazy-convert nxt))
                      (cons [artifact "RELEASE"] (lazy-convert rst)))
                    (vector [artifact "RELEASE"]))))))]
    (fn [args]
      (lazy-convert args))))


(defn add-libs [libs]
  (doseq [[lib version] libs]
    (println "Loading dependency" lib version)
    (deps-repl/add-lib (symbol lib) {:mvn/version version}))
  (println "[deps-try] Dependencies loaded. Require via e.g. (require '[some-lib.core :as sl])."))


(defmethod rebel-readline/command-doc :repl/try [_]
  (str "Add more dependencies (e.g. `:repl/try clj-time`)"))


(defmethod rebel-readline/command :repl/try [[_ & args]]
  (if (seq args)
    (add-libs (->dep-pairs args))
    (println "Usage: `:repl/try clj-time`")))


(defn print-usage []
  (println "Usage:
  clojure -A:deps-try dep-name [dep-version] [dep2-name ...]

Example:
$ clojure -A:deps-try clj-time

# specific version
$ clojure -A:deps-try clj-time \"0.14.2\"

# multiple deps
$ clojure -A:deps-try clj-time org.clojure/core.logic
"))


(defn print-usage? [args]
  (contains? #{"-h" "--help"} (first args)))


(defn -main [& args]
  (if (print-usage? args)
    (print-usage)
    (rebel-core/ensure-terminal
      (rebel-main/repl
        :init (fn []
                (add-libs (->dep-pairs args)))))))
