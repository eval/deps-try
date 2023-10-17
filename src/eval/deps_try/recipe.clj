(ns eval.deps-try.recipe
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [edamame.core :as e :refer [parse-string-all]]))

(defn -parse [s]
  (let [[ns-step & steps] (string/split s #"(\r?\n){3,}")
        ns-meta           (some-> ns-step (e/parse-string) (e/parse-ns-form) :meta)]
    (assoc (select-keys ns-meta [:mvn/repos :jvm-opts :deps-try/deps])
           :steps (into [ns-step] steps))))

(defn parse [path]
  (let [contents (-> path slurp string/trim)]
    (-parse contents)))

(defn- parse-recipe-string [s]
  (with-open [r (clojure.java.io/reader (char-array s))]
    (parse r)))


(comment
  (parse "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try.clj")

  (parse "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try/util.clj")

  (parse-recipe-string
   ";; some recipe introduction\n (ns foo {:deps-try/deps [\"foo/bar\"]})


 ;; some introduction here
(def a 1)")


  (string/split "" #"")

  (parse (io/reader (char-array "(ns foo {:deps.try/deps [\"foo/bar\"]})")))

  #_:end)
