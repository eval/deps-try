(ns eval.deps-try.recipe
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [edamame.core :as e]
            [eval.deps-try.fs :as fs]
            [eval.deps-try.util :as util :refer [whenp]]))

(defn- strip-leading-comments
  "(strip-leading-comments \";; Use this recipe like so,,,\n(ns recipe.hello)\")
  ;; => \"(ns recipe.hello)\"
  "
  [s]
  (let [[comments _] (string/split s #"\(ns" 2)]
    (subs s (count (str comments)))))

(defn -parse [s]
  (let [[ns-form {:keys [end-row]}] ((juxt identity meta) (e/parse-string s {:quote true}))
        docstring                   (some-> ns-form
                                            (whenp (comp #(< 2 %) count))
                                            (nth 2)
                                            (whenp string?))
        ns-step                     (->> s string/split-lines (take end-row) (string/join \newline))
        s-sans-ns                   (->> s
                                         string/split-lines
                                         (drop end-row)
                                         (string/join \newline)
                                         string/triml)
        steps                       (string/split s-sans-ns #"(\r?\n){3,}")
        ns-meta                     (:meta (e/parse-ns-form ns-form))]
    (-> ns-meta
        (select-keys [:deps-try.recipe/deps :deps-try.recipe/status])
        (assoc :steps (into [ns-step] steps))
        (cond->
         docstring (assoc :docstring docstring)))))

(defn parse
  [slurpable]
  (let [contents (-> slurpable slurp string/trim)]
    (merge {:location (str slurpable)}
           (-parse (strip-leading-comments contents)))))


(defn- recipe-arg-dispatch [arg]
  (if (re-find #"^http" (str arg))
    :url
    :path))

(defmulti recipe-arg->slurpable #'recipe-arg-dispatch)
(defmethod recipe-arg->slurpable :url [arg]
  (let [gist?             (re-find #"gist\.github\.com" arg)
        ensure-raw-suffix #(cond-> %
                             (not (re-find #"\/raw$" %)) (str "/raw"))]
    (cond-> arg
      gist? ensure-raw-suffix)))

(defmethod recipe-arg->slurpable :path [arg]
  (-> arg fs/expand-home fs/normalize fs/absolutize))


(defmulti validate-slurpable #'recipe-arg-dispatch)

(defmethod validate-slurpable :url [slurpable]
  (let [{url-status :status} (util/url-test slurpable {})]
    (cond
      (= url-status :offline)  :parse.recipe/offline
      (not= url-status :found) :parse.recipe/url-not-found
      :else nil)))

(defmethod validate-slurpable :path [slurpable]
  (when-not (and (fs/exists? slurpable)
                 (fs/regular-file? slurpable))
    :parse.recipe/path-not-found))

(defn parse-arg [recipe-arg]
  (let [slurpable (recipe-arg->slurpable recipe-arg)]
    (if-let [error (validate-slurpable slurpable)]
      {:error {:error/id error :path recipe-arg}}
      (let [{:deps-try/keys [deps] :as parsed-recipe} (parse (str slurpable))]
        (cond-> parsed-recipe
          deps (assoc :deps deps))))))

(defn- parse-recipe-string [s]
  (with-open [r (clojure.java.io/reader (char-array s))]
    (parse r)))

(defn generate&print-manifest [{:keys [exclude-status folder base-url]}]
  (let [base-url         (str base-url
                              (when-not (re-find #"\/$" base-url) "/"))
        path->name       (fn [p]
                           (-> (str p)
                               (string/replace-first #"\.clj$" "")
                               (string/replace #"_" "-")))
        docstring->title #(some-> % (string/split-lines) first (string/replace #"\. *$" ""))
        recipes          (filterv some?
                                  (map (fn [p]
                                         (let [rel-path                         (fs/relativize folder p)
                                               {:keys                 [docstring]
                                                :deps-try.recipe/keys [status]} (parse (str p))]
                                           (when-not ((set exclude-status) status)
                                             (cond-> {:deps-try.recipe/url (str base-url rel-path)
                                                      :deps-try.recipe/name (path->name rel-path)}
                                               docstring (assoc :deps-try.recipe/title (docstring->title docstring))))))
                                       (fs/glob folder "**/*.clj")))]
    (binding [clojure.core/*print-namespace-maps* false]
      ((requiring-resolve 'clojure.pprint/pprint) {:deps-try.manifest/recipes recipes}))))

(comment

  (def recipes (fs/path (fs/cwd) "recipes"))

  (fs/relativize (str recipes) (first (fs/glob recipes "**/*.clj")))

  (fs/glob "/Users/gert/projects/deps-try/deps-try-recipes/recipes" "**/*.clj")
  (parse "/Users/gert/projects/deps-try/deps-try-recipes/recipes/next_jdbc/postgresql.clj")

  (parse "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try.clj")

  (parse-arg "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try/util.clj2")

  (util/url-test "https://raw.githubusercontent.com/eval/deps-try/master/src/eval/deps_try/util.clj2" {})
  (count (:steps (parse-arg "/Users/gert/projects/deps-try/deps-try-recipes/recipes/next-jdbc/intro-sqlite.clj")))

  (slurp "README.md")
  (slurp "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc_postgresql.clj")
  (parse-recipe-string
   ";; some comment
(ns my.namespace
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}})")

  (parse-recipe-string
   ";; some recipe introduction\n (ns foo {:deps-try2/deps [\"foo/bar\"]})


 ;; some introduction here
(def a 1)")

  (parse-arg "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc_postgresql.clj")
  (string/split "" #"")

  (parse (io/reader (char-array "(ns foo {:deps.try/deps [\"foo/bar\"]})")))

  #_:end)
