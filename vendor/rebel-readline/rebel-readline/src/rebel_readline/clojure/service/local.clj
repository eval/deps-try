(ns rebel-readline.clojure.service.local
  (:require
   [clojure.repl]
   [rebel-readline.clojure.line-reader :as clj-reader]
   [rebel-readline.clojure.utils :as clj-utils :refer [when-pred]]
   [rebel-readline.tools :as tools]
   [rebel-readline.utils :as utils :refer [strip-literals log]]
   [clojure.string :as string]))

;; taken from replicant
;; https://github.com/puredanger/replicant/blobcl/master/src/replicant/util.clj
(defn data-eval
  [form]
  (let [out-writer (java.io.StringWriter.)
        err-writer (java.io.StringWriter.)
        capture-streams (fn []
                          (.flush *out*)
                          (.flush *err*)
                          {:out (.toString out-writer)
                           :err (.toString err-writer)})]
    (binding [*out* (java.io.BufferedWriter. out-writer)
              *err* (java.io.BufferedWriter. err-writer)]
      (try
        (let [result (eval form)]
          ;; important to note that there could be lazy errors in this result

          ;; the strategy embraced by prepl is passing an out-fn
          ;; callback that handles formatting and message sending in
          ;; the scope of the try catch
          (merge (capture-streams) {:result result}))
        (catch Throwable t
          (merge (capture-streams)
                 (with-meta
                   {:exception (Throwable->map t)}
                   {:ex t})))))))

(defn call-with-timeout [thunk timeout-ms]
  (let [prom (promise)
        thread (Thread. (bound-fn [] (deliver prom (thunk))))
        timed-out (Object.)]
    (.start thread)
    (let [res (deref prom timeout-ms timed-out)]
      (if (= res timed-out)
        (do
          (.join thread 100)
          (if (.isAlive thread)
            (.stop thread))
          {:exception (Throwable->map (Exception. "Eval timed out!"))})
        res))))

(defn safe-resolve [s]
  (some-> s
          symbol
          (-> resolve (try (catch Throwable e nil)))))

(def safe-meta (comp meta safe-resolve))

(defn- var-str->ns [var-str]
  (or (some-> (strip-literals var-str) symbol find-ns)
      (some->> (strip-literals var-str) symbol (get (ns-aliases *ns*)))))

(defn resolve-meta [var-str]
  (or (safe-meta (strip-literals var-str))
      (when-let [ns' (var-str->ns var-str)]
        (assoc (meta ns')
               :ns (str ns')))))

(derive ::service ::clj-reader/clojure)

(defmethod clj-reader/-resolve-meta ::service [_ var-str]
  (resolve-meta var-str))

(defmethod clj-reader/-complete ::service [_ word options]
  ;; lazy-load for faster startup
  (when-let [completions (requiring-resolve 'compliment.core/completions)]
    (if options
      (completions word options)
      (completions word))))

(defmethod clj-reader/-current-ns ::service [_]
  (some-> *ns* str))

(defmethod clj-reader/-source ::service [_ var-str]
  (some->> (clojure.repl/source-fn (symbol (strip-literals var-str)))
           (hash-map :source)))

(defmethod clj-reader/-apropos ::service [_ var-str]
  (clojure.repl/apropos var-str))

(defn- javadoc-url [klass-dot-method]
  (when-let [{:keys [nses-and-klass klass method]}
             (not-empty (clj-utils/re-named-captures
                         #"(?x)  # matches e.g. \"java.util.Date.new\", \"Integer\", \"Integer.parse\"
                           (?<nsesAndKlass>.*?                # non-greedy capture all
                             (?:\.?(?<klass>[A-Z][A-Za-z]+))) # match \".Class\", capture \"Class\"
                           (?:\.(?<method>[^\s]+))?           # optional method or field
                          " klass-dot-method {:keywordize true}))]
    (let [java-version-19+  (some->> (clj-utils/java-version)
                                     (re-find #"^(\d*)\.")
                                     last
                                     parse-long
                                     (when-pred #(> % 18)))
          base-url          (str "https://docs.oracle.com/en/java/javase/"
                                 (or java-version-19+ 19)
                                 "/docs/api/search.html?q=")
          constructor-query (when (= method "new")
                              (str nses-and-klass "+" klass "("))
          q                 (or constructor-query
                                (cond-> nses-and-klass
                                  method (str "+" method)))]
      (str base-url q))))

(defmethod clj-reader/-doc ::service [self var-str]
    ;; lazy-load for faster startup
  (when-let [doc ((requiring-resolve 'compliment.core/documentation) var-str)]
    (let [{:keys [ns name private]
           :as   _meta} (if (special-symbol? (symbol var-str))
                          {:ns (find-ns 'clojure.core) :name (symbol var-str)}
                          (clj-reader/-resolve-meta self var-str))
          url           (when (and (not private) ns) (clj-utils/url-for (str ns) (str name)))
          url           (or url (javadoc-url (first (string/split-lines doc))))]
      (cond-> {:doc doc}
        url (assoc :url url)))))

(defmethod clj-reader/-eval ::service [self form]
  (let [res (call-with-timeout
             #(data-eval form)
             (get self :eval-timeout 3000))]
    ;; set! *e outside of the thread
    (when-let [ex (some-> res :exception meta :ex)]
      (set! *e ex))
    res))

(defmethod clj-reader/-read-string ::service [self form-str]
  (log ::read-string :form-str form-str)
  (when (string? form-str)
    (try
      {:form (with-in-str form-str
               (read {:read-cond :allow} *in*))}
      (catch Throwable e
        {:exception (Throwable->map e)}))))

(defn create
  ([] (create nil))
  ([options]
   (merge clj-reader/default-config
          (tools/user-config)
          options
          {:rebel-readline.service/type ::service})))

(comment
  (clj-reader/-resolve-meta {:rebel-readline.service/type ::service} "map")
  (clj-reader/-doc {:rebel-readline.service/type ::service} "if")

  #_:end)
