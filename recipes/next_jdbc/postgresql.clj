(ns recipes.next-jdbc.postgresql
  "Some recipe description"
  {:deps-try.recipe/status :draft
   :deps-try.recipe/deps   ["org.postgresql/postgresql"
                            "com.github.seancorfield/next.jdbc" "1.3.894"]}
  (:require [next.jdbc :as jdbc]))


;; Check&complete the jdbc-url and eval
(def ds "jdbc:postgres://localhost:5432/,,,")


;; Test the connection
(jdbc/execute! ds ["select NOW()"])
