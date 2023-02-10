(ns eval.deps-try
  (:require [babashka.deps :as deps]
            [babashka.tasks :refer [clojure]]))

(deps/add-deps '{:deps {org.clojure/tools.gitlibs {:mvn/version "2.4.181"}}})

(require '[eval.deps-try.deps :as try-deps]
         '[babashka.fs] :reload
         '[babashka.http-client] :reload) ;; reload so we use the dep, not the built-in

(defn -main [& args]
  (let [requested-deps (try-deps/parse-dep-args args)]
    (clojure "-Sdeps" (str {:deps requested-deps}) "-M" "-m" "eval.deps-try.try")))
