(ns eval.deps-try
  (:require [babashka.classpath :refer [get-classpath]]
            [babashka.deps :as deps]
            [babashka.process :as p :refer [sh]]
            [clojure.string :as str]))

(def init-cp (get-classpath))


(deps/add-deps '{:deps {org.clojure/tools.gitlibs {:mvn/version "2.4.181"}}})

(require '[eval.deps-try.deps :as try-deps]
         '[babashka.fs :as fs] :reload
         '[babashka.http-client] :reload) ;; reload so we use the dep, not the built-in


(defn -main [& args]
  (let [cache-dir      (fs/with-temp-dir [tmp {}]
                         (:cache-dir (read-string (:out (sh ["clojure" "-Sdescribe"] {:dir (str tmp)})))))
        requested-deps (try-deps/parse-dep-args args)
        deps->cp       #(fs/with-temp-dir [tmp {}]
                          (str/trim (:out (sh ["clojure" "-Spath" "-Sdeps" (str {:deps %})] {:dir (str tmp)}))))
        default-cp     (deps->cp '{org.clojure/clojure {:mvn/version "RELEASE"}})
        requested-cp   (deps->cp requested-deps)]
    (p/exec ["java" "-classpath" (str default-cp fs/path-separator init-cp fs/path-separator requested-cp)
             (str "-Dclojure.basis=" (first (fs/glob cache-dir "*.basis")))
             "clojure.main" "-m" "eval.deps-try.try"])))
