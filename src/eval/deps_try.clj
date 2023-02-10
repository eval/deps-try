(ns eval.deps-try
  {:clj-kondo/config '{:lint-as {babashka.fs/with-temp-dir clojure.core/let}}}
  (:require
   [babashka.classpath :as cp :refer [get-classpath]]
   [babashka.deps :as deps]
   [babashka.process :as p]
   [clojure.string :as str]))

(def init-cp (get-classpath))

(deps/add-deps '{:deps {org.clojure/tools.gitlibs {:mvn/version "2.4.181"}}})

(require '[eval.deps-try.deps :as try-deps]
         '[babashka.fs :as fs] :reload
         '[babashka.http-client] :reload) ;; reload so we use the dep, not the built-in

(defn parse-cp-file [s]
  (some #(when (str/includes? % "cp_file")
           (str/trim (second (str/split % #"=")))) (str/split-lines s)))

(defn deps->cp [tmp deps]
  (str/trim (with-out-str (deps/clojure {:dir (str tmp)} "-Spath" "-Sdeps" (str {:deps deps})))))

(defn -main [& args]
  (fs/with-temp-dir [tmp {}]
    (let [verbose-output (with-out-str (deps/clojure {:dir (str tmp)} "-Sverbose" "-Spath"))
          cp-file (parse-cp-file verbose-output)
          basis-file (str/replace cp-file #".cp$" ".basis")
          requested-deps (try-deps/parse-dep-args args)
          default-cp (deps->cp tmp '{org.clojure/clojure {:mvn/version "RELEASE"}})
          requested-cp (deps->cp tmp requested-deps)
          classpath (str default-cp fs/path-separator init-cp fs/path-separator requested-cp)]
      (p/exec "java" "-classpath" classpath
              (str "-Dclojure.basis=" basis-file)
              "clojure.main" "-m" "eval.deps-try.try"))))
