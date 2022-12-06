(ns eval.deps-try
  (:require [babashka.classpath :refer [get-classpath]]
            [babashka.deps :as deps]
            [babashka.fs :as fs]
            [babashka.process :as p :refer [sh]]
            [clojure.string :as str]))

(def init-classpath (get-classpath))

(deps/add-deps '{:deps {org.clojure/tools.gitlibs {:mvn/version "2.4.181"}}})

(require '[eval.deps-try.deps :as try-deps])

(defn -main [& args]
  (let [requested-deps (try-deps/parse-dep-args args)
        default-deps   '{org.clojure/clojure {:mvn/version "RELEASE"}}
        deps           {:deps (merge default-deps requested-deps)}
        default-cp     (fs/with-temp-dir [tmp {}]
                         (str/trim (:out (sh ["clojure" "-Spath" "-Sdeps" (str deps)] {:dir (str tmp)}))))]
    @(deps/clojure ["-Scp" (str default-cp ":" init-classpath) "-Sdeps-file" "-" "-M" "-m" "eval.deps-try.try"]
                   {:inherit  true
                    :shutdown p/destroy-tree}))
  nil)
