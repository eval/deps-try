(ns deps-try.core
  (:require [clojure.tools.deps.alpha.repl :as deps-repl]
            [rebel-readline.clojure.main :as rebel-main]
            [rebel-readline.commands :as rebel-readline]))

(def deps [])

;; taken from lein-try
;; https://github.com/rkneufeld/lein-try/blob/master/src/leiningen/try.clj
(defn- version-string?
  "Check if a given String represents a version number."
  [^String s]
  (or (contains? #{"RELEASE" "LATEST"} s)
      (Character/isDigit (first s))))


;; taken lein-try
;; https://github.com/rkneufeld/lein-try/blob/master/src/leiningen/try.clj
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


(defmethod rebel-readline/command-doc :repl/try [_]
  (str "Load the dependencies to try, ie " (prn-str deps)))


(defmethod rebel-readline/command :repl/try [[_ & args]]
  (alter-var-root #'deps-try.core/deps (partial apply conj (->dep-pairs (map str args))))
  (doseq [[dep version] deps]
    (println "Adding lib" dep version)
    (deps-repl/add-lib (symbol dep) {:mvn/version version}))
  (println "Done! Deps can now be required, e.g: (require '[some-lib.core :as sl])"))


(defn -main [& args]
  (alter-var-root #'deps-try.core/deps (partial apply conj (->dep-pairs args)))
  (rebel-main/-main))
