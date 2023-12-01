(ns recipes.next-jdbc.intro-sqlite
  "A next-jdbc introduction using SQLite."
  {:deps-try.recipe/deps ["org.xerial/sqlite-jdbc"
                          "com.github.seancorfield/next.jdbc"]}
  (:require [next.jdbc :as jdbc]))

;; Datasource
;;
;; The db-file will be placed in the current working directory.
;; Change the path if needed.
(def ds "jdbc:sqlite:./test.sqlite3")


;; Ensure no address-table exists
(jdbc/execute! ds ["drop table if exists address"])


;; Let's create a table...
(jdbc/execute! ds ["
  create table address (
    name  TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE
  )
"])


;; ...and add some data.
;; NOTE replace the `?placeholders` and evaluate.
(jdbc/execute! ds ["
  insert into address(name, email) values (?, ?)
" '?name '?email])


;; The result of `execute!` indicates how many rows were affected.
;;
;; If instead we want the data just inserted, we could for most other databases
;; pass it the option-map `{:return-keys true}`, but alas, not for sqlite!
;;
;; Suffixing the query with "returning *" has the same effect though (don't forget the placeholders):
(jdbc/execute! ds ["
  insert into address(name, email) values (?, ?) returning *
" '?name '?email])


;; Notice how we got a vector of hash maps with namespace-qualified keys,
;; representing the result set from the operation.
;;
;; Let's insert multiple rows in one go:
(jdbc/execute! ds ["
  insert into address(name, email) values (?, ?), (?, ?) returning *
" '?name '?email '?name '?email])


;; `execute!` & `execute-one!`
;;
;; If you only expect 1 result, then you can also use `execute-one!`:
(jdbc/execute-one! ds ["select count(*) as address_count from address"])


;; Let's see what data we got.
;;
;; NOTE Check what difference it makes to use `execute-one!` for the following query.
;; NOTE To quickly try different variations of an expression, use eval-at-point:
;; place the cursor behind the last paren and press Control-x Control-e (i.e. ^X^E).
;; Then change the function-name, move the cursor and eval-at-point again.
;; Finally submit the step to continue to the next step.
(jdbc/execute! ds ["select * from address"])


;; When we created our address-table we marked no column as primary key.
;; But SQLite added a mistery column...
;;
;; Let's refresh our memory to see what the actual structure of the address-table is:
(jdbc/execute-one! ds ["select sql from sqlite_schema where name = 'address'"])


;; By default SQLite adds a 64-bit signed integer ROWID to every row in the table.
;; We can refer to this column as `rowid`, `_rowid_` or `oid`.
;; It doesn't show up unless we explicitly query for it.
;;
;; Complete the query:
(jdbc/execute! ds ["select *,??? from address"])


;; We may provide a value for the ROWID (first expression).
;; Any subsequent auto-generated ROWID will not be lower than any existing ROWID. Test that with the second expression.
;;
;; Edit the `?placeholders` and use eval-at-point for both expressions to see what happens.
(jdbc/execute! ds ["insert into address(oid, name, email) values(?,?,?) returning *,oid" '?id '?name '?email])
(jdbc/execute! ds ["insert into address(name, email) values(?,?) returning *,oid" '?name '?email])


;; While ROWIDs are unique among all _existing_ rows in the table, if data is deleted a ROWID can be reused.
;;
;; Complete the following queries to show that ROWIDs are reused.
;; HINT delete the highest existing rowid.
;; NOTE Use eval-at-point here as well to quickly (re-)try these expressions.
(jdbc/execute! ds ["delete from address where rowid = ? returning *,oid" '?rowid])
(jdbc/execute! ds ["insert into address(name, email) values(?,?) returning *,oid" '?name '?email])


;; We can make the ROWID explicit by adding a column of type INTEGER PRIMARY KEY.
;; Any such column will then be an alias for the ROWID.
;; There's then 4 names we can use to refer to the ROWID column.
;;
;; Let's re-create the address-table and add our own id-column.
;; Complete the table definition.
(jdbc/execute! ds ["drop table address"])
(jdbc/execute! ds ["
  create table address (
    ?column     ?type,
    name        TEXT NOT NULL,
    email       TEXT NOT NULL UNIQUE
  )
"])


;; Assuming the column is named `id` we can insert some data with a specific id:
(jdbc/execute! ds ["insert into address(id, name, email) values(?,?,?) returning *" 5 '?name '?email])


;; As our new column is practically the ROWID, we don't need to provide a value in for it to get a value:
(jdbc/execute! ds ["insert into address(name, email) values(?,?) returning *" "???" "???"])


;; Finally we can list all addresses. By just using `*` we get all columns including `id`:
(jdbc/execute! ds ["select * from address"])


;; Fin!
;; For any recipe additions/corrections, visit https://github.com/eval/deps-try

(comment
  ;; more
  (sql/find-by-keys ds :address {:name "Gert"} {:limit 10})
  #_:end)
