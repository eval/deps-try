(ns deps-try.core
  (:require [rebel-readline.clojure.main :as rebel-main]
            [user]))

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


(defn -main [& args]
  (alter-var-root #'user/args (partial apply conj (->dep-pairs args)))
  (rebel-main/-main))
