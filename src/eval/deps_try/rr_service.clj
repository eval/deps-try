(ns eval.deps-try.rr-service
  (:require
   [clojure.java.basis]
   [clojure.string :as str]
   [deps-try.compliment.core]
   [deps-try.compliment.utils]
   [eval.deps-try.fs :as fs]
   [eval.deps-try.util :as util]
   [rebel-readline.clojure.line-reader :as clj-reader]
   [rebel-readline.clojure.service.local :as local-service]
   [rebel-readline.tools :as tools]
   [rebel-readline.utils :refer [strip-literals]]))


(derive ::service ::local-service/service)

(defn- word->doc-searchable
  "Turn word (string) into a [ns sym] tuple"
  [s]
  (when-let [sym (some-> s not-empty strip-literals symbol)]
    (if (special-symbol? sym)
      (list (find-ns 'clojure.core) sym)
      (let [var->ns&name #(-> % meta ((juxt :ns :name)))]
        (some-> (local-service/safe-resolve sym)
                var->ns&name)))))

(defn- set-examples-file-name! [path]
  (alter-var-root (requiring-resolve 'orchard.clojuredocs/cache-file-name) (constantly (str path))))

(defn- ensure-fresh-examples-cache!
  "Best effort to refresh clojuredocs example cache when it does not exist or is older than `max-age`.
  Silenty fails when no connection."
  [path {:keys [max-age]}]
  (let [needs-refresh?  (or (not (fs/exists? path))
                            (util/file-last-modified-before? path (- (util/duration->millis max-age))))]
    (when needs-refresh?
      (try ((requiring-resolve 'orchard.clojuredocs/update-cache!)) (catch Exception _e)))))

(defmethod clj-reader/-examples ::service [self word]
  (let [data-path (:data-path self)]
    (when-let [[wns wname] (word->doc-searchable word)]
      (let [examples-file-name (fs/file data-path "clojuredocs-export.edn")]
        (set-examples-file-name! examples-file-name)
        (ensure-fresh-examples-cache! examples-file-name {:max-age {:weeks 2}})
        ;; NOTE in case no local cache, orchard uses (old) export
        ((requiring-resolve 'orchard.clojuredocs/resolve-and-find-doc) wns wname)))))

(defn- classpath-for-completions
  "This 'fixes' two things with compliment.utils/classpath:
  - it removes cwd from the classpath which can simply yield too many options (e.g. ~)
  - it picksup added libraries"
  []
  (let [java-cp-sans-cwd (rest (str/split (System/getProperty "java.class.path") #":"))
        basis-cp         (->> (clojure.java.basis/current-basis)
                              :libs
                              vals
                              (mapcat :paths))]
    (into java-cp-sans-cwd basis-cp)))

(defmethod clj-reader/-complete ::service [_self word options]
  (let [options (merge {:ns *ns*} options)
        options (if (:extra-metadata options)
                  options
                  (assoc options :extra-metadata #{:private :deprecated}))]
    (with-redefs [deps-try.compliment.utils/classpath classpath-for-completions]
      #_(prn ::-complete :word word :options options)
      (doall (cond-> (deps-try.compliment.core/completions word options)
               :namespace/other? (->> (map (fn [{:keys [ns] :as cand}]
                                             (if-not (seq ns)
                                               cand
                                               (assoc cand :namespace/other? (not= ns (:ns options)))))))
               :namespace/found? (->> (map (fn [{:keys [type candidate] :as cand}]
                                             (if (not= type :namespace)
                                               cand
                                               (let [ns-found? (boolean (find-ns (symbol (strip-literals candidate))))]
                                                 (assoc cand :namespace/found? ns-found?)))))))))))

(defn create
  ([] (create nil))
  ([options]
   (merge clj-reader/default-config
          (tools/user-config)
          options
          {:rebel-readline.service/type ::service})))
