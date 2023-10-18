(ns eval.deps-try.recipe
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [edamame.core :as e]))

(defn -parse [s]
  (let [[ns-step & steps] (string/split s #"(\r?\n){3,}")
        ns-meta           (some-> ns-step (e/parse-string) (e/parse-ns-form) :meta)]
    (assoc (select-keys ns-meta [:deps-try/deps])
           :steps (into [ns-step] steps))))

(defn parse [path]
  (let [contents (-> path slurp string/trim)]
    (merge {:location path}
     (-parse contents))))

(defn- parse-recipe-string [s]
  (with-open [r (clojure.java.io/reader (char-array s))]
    (parse r)))

(defn parse-arg
  "Parse the value for recipe that was provided by the user."
  [recipe-arg]
  ;; concerned with
  ;; - existance of recipe
  ;; - does it look like a recipe?
  ;; - parsing
  ;; - yielding {:error ,,,} or {:deps-try/deps [] :steps [,,,]}
  ;; possibly {:deps ,,,}
  #_{:error "That's not a recipe!"}
  (let [{:deps-try/keys [deps] :as parsed-recipe} (parse recipe-arg)]
    (cond-> parsed-recipe
      deps (assoc :deps deps))))

(comment
  (parse "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try.clj")

  (parse "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try/util.clj")

  (parse-arg "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc_postgresql.clj")
  (slurp "README.md")
  (slurp "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc_postgresql.clj")
  (parse-recipe-string
   ";; some recipe introduction\n (ns foo {:deps-try2/deps [\"foo/bar\"]})


 ;; some introduction here
(def a 1)")

  (parse-arg "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc_postgresql.clj")
  (string/split "" #"")

  (parse (io/reader (char-array "(ns foo {:deps.try/deps [\"foo/bar\"]})")))

  #_:end)
