(ns eval.deps-try.recipe
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [edamame.core :as e]
            [eval.deps-try.util :as util]))

(defn -parse [s]
  (let [[ns-form {:keys [end-row]}] ((juxt identity meta) (e/parse-string s {:quote true}))
        ns-step                     (->> s string/split-lines (take end-row) (string/join \newline))
        s-sans-ns                   (->> s
                                         string/split-lines
                                         (drop end-row)
                                         (string/join \newline)
                                         string/triml)
        steps                       (string/split s-sans-ns #"(\r?\n){3,}")
        ns-meta                     (:meta (e/parse-ns-form ns-form))]
    (assoc (select-keys ns-meta [:deps-try/deps])
           :steps (into [ns-step] steps))))

(defn parse
  [slurpable]
  (let [contents (-> slurpable slurp string/trim)]
    (merge {:location (str slurpable)}
           (-parse contents))))

(defn parse-arg [recipe-arg]
  (let [url?                 #(re-find #"^http" (str %))
        path?                #(and (not (url? %))
                                   (re-find #"\.clj" (str %)))
        slurpable            (cond
                               (url? recipe-arg)  recipe-arg
                               (path? recipe-arg) (-> recipe-arg fs/expand-home fs/normalize fs/absolutize)
                               :else              (str "https://raw.githubusercontent.com/eval/deps-try/master/recipes/"
                                                       recipe-arg ".clj"))
        {url-status :status} (when (url? slurpable) (util/url-test slurpable {}))
        error                (cond
                               (and url-status (= url-status :offline))   :parse.recipe/offline
                               (and url-status (not= url-status :found))  :parse.recipe/url-not-found
                               (and
                                (path? slurpable)
                                (not (and (fs/exists? slurpable)
                                          (fs/regular-file? slurpable)))) :parse.recipe/path-not-found)]
    (if error
      {:error {:error/id error :path recipe-arg}}
      (let [{:deps-try/keys [deps] :as parsed-recipe} (parse (str slurpable))]
        (cond-> parsed-recipe
          deps (assoc :deps deps))))))

(defn- parse-recipe-string [s]
  (with-open [r (clojure.java.io/reader (char-array s))]
    (parse r)))

(comment

  (parse "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try.clj")

  (parse-arg "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try/util.clj2")

  (util/url-test "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try/util.clj2" {})
  (parse-arg "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc_postgresql.clj")

  (slurp "README.md")
  (slurp "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc_postgresql.clj")
  (parse-recipe-string
   "(ns my.namespace
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}})")

  (parse-recipe-string
   ";; some recipe introduction\n (ns foo {:deps-try2/deps [\"foo/bar\"]})


 ;; some introduction here
(def a 1)")

  (parse-arg "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc_postgresql.clj")
  (string/split "" #"")

  (parse (io/reader (char-array "(ns foo {:deps.try/deps [\"foo/bar\"]})")))

  #_:end)
