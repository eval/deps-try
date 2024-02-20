(ns rebel-readline.clojure.utils
  (:require
   [clojure.string :as string]))

(defn java-version []
  (System/getProperty "java.version"))

(defn when-pred [pred v]
  (when (pred v) v))

(defn re-named-groups [re]
  (let [matcher        (re-matcher re "")
        named-group-re #"\(\?<([a-zA-Z][a-zA-Z0-9]*)>"]
    ;; .groupNames since Java 20
    (try (keys (.groupNames ^java.util.regex.Matcher matcher))
         (catch IllegalArgumentException _
           (map last (re-seq named-group-re (str re)))))))

(letfn [(camel->kebab
          "Simple/naive transformation that works for re group-names"
          [s]
          (-> s
              (string/replace #"[A-Z]" (comp #(str "-" %) string/lower-case))
              (string/replace #"^-|-$" "")))]
  (defn re-named-captures
    ([re s] (re-named-captures re s nil))
    ([re s {:keys [keywordize] :or {keywordize identity}}]
     (let [keywordize-fn (if (true? keywordize) (comp keyword camel->kebab) keywordize)]
       (if-let [matcher (let [m (re-matcher re s)]
                          (when (re-find m) m))]
         (reduce (fn [acc group]
                   (if-let [captured (.group matcher group)]
                     (assoc acc (keywordize-fn group) captured)
                     acc)) {} (re-named-groups re))
         {})))))


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
