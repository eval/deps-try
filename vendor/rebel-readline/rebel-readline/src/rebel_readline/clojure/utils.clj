(ns rebel-readline.clojure.utils
  (:require
   [clojure.string :as string]))

(defn java-version []
  (System/getProperty "java.version"))

(defn whenp
  "Yields `v` when it's truthy and all `preds` pass.

  Examples:
  (whenp 1 odd? pos?) ;; => 1
  (whenp 2 odd? pos?) ;; => nil

  ;; when `v` is false-ish you get nil
  (whenp nil odd? pos?) ;; => nil

  (whenp coll seq) ;; =>  nil or a collection with at least 1 item
  (whenp nil odd)"
  ([v] v)
  ([v & preds]
   (when (and v ((apply every-pred preds) v))
     v)))

;; Simple/naive transformation that works for re group-names
(letfn [(kebab->camel [s]
          (string/replace s  #"-([a-z])" #(.toUpperCase (%1 1))))]
  (defn re-named-captures
    "Yields captures by group name.

    Examples:
    (re-named-captures #\"(?<foo>Foo) (?<barAndBaz>\\w+)\" \"Foo BarBaz\" [:foo :bar-and-baz])
    ;; => {:foo \"Foo\" :bar-and-baz \"BarBaz\"}
  "
    [re s ks]
    (when-let [^java.util.regex.Matcher matching (whenp (re-matcher re s) re-find)]
      (let [group->key (reduce (fn [acc k]
                                 (let [group-name (if (keyword? k) (kebab->camel (name k)) k)]
                                   (assoc acc group-name k))) {} ks)]
        (reduce (fn [acc [gn k]]
                  (assoc acc k (.group matching gn))) {} group->key)))))

;; taken from cljs-api-gen.encode
(def cljs-api-encoding
  {"."  "DOT"
   ">"  "GT"
   "<"  "LT"
   "!"  "BANG"
   "?"  "QMARK"
   "*"  "STAR"
   "+"  "PLUS"
   "="  "EQ"
   "/"  "SLASH"})

;; taken from cljs-api-gen.encode
(defn cljs-api-encode-name [name-]
  (reduce (fn [s [a b]] (string/replace s a b))
    (name name-) cljs-api-encoding))

(defn url-for [ns name]
  (cond
    (.startsWith (str ns) "clojure.")
    (cond-> "https://clojuredocs.org/"
      ns (str ns)
      (seq name) (str "/" (string/replace name #"\?$" "_q")))
    (.startsWith (str ns) "cljs.")
    (cond-> "http://cljs.github.io/api/"
      ns   (str ns)
      (seq name) (str "/" (cljs-api-encode-name name)))
    :else nil))
