(ns user
  (:require [clojure.tools.deps.alpha.repl :as deps-repl]))

(def ^:dynamic args [])

(defn try! []
  (doseq [[dep version] args]
    (println "Adding lib" dep version)
    (deps-repl/add-lib (symbol dep) {:mvn/version version})))
