(ns eval.deps-try.recipe
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [edamame.core :as e]
            [eval.deps-try.util :as util]))

(defn -parse [s]
  (let [[ns-step & steps] (string/split s #"(\r?\n){3,}")
        ns-meta           (some-> ns-step (e/parse-string) (e/parse-ns-form) :meta)]
    (assoc (select-keys ns-meta [:deps-try/deps])
           :steps (into [ns-step] steps))))

(defn parse [path]
  (let [contents (-> path str slurp string/trim)]
    (merge {:location (str path)}
           (-parse contents))))

(defn parse-arg
  "Parse the value for recipe that was provided by the user."
  [recipe-arg]
  (let [url?                 (re-find #"^http" recipe-arg)
        expanded-path        (when-not url?
                               (cond-> recipe-arg
                                 (not (fs/extension recipe-arg)) (str ".clj")
                                 :finally                        (-> fs/expand-home
                                                                     fs/normalize
                                                                     fs/absolutize)))
        #_#__                    (prn :path expanded-path)
        {url-status :status} (when url? (util/url-test recipe-arg {}))
        error                (cond
                               (and url-status (= url-status :offline))           :parse.recipe/offline
                               (and url-status (not= url-status :found))          :parse.recipe/url-not-found
                               (and expanded-path
                                    (not (and (fs/exists? expanded-path)
                                              (fs/regular-file? expanded-path)))) :parse.recipe/path-not-found)
        path-to-parse        (or expanded-path recipe-arg)]
    (if error
      {:error {:error/id error :path path-to-parse}}
      (let [{:deps-try/keys [deps] :as parsed-recipe} (parse path-to-parse)]
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
   ";; some recipe introduction\n (ns foo {:deps-try2/deps [\"foo/bar\"]})


 ;; some introduction here
(def a 1)")

  (parse-arg "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc_postgresql.clj")
  (string/split "" #"")

  (parse (io/reader (char-array "(ns foo {:deps.try/deps [\"foo/bar\"]})")))

  #_:end)
