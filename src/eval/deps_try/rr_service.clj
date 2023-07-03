(ns eval.deps-try.rr-service
  (:require
   [babashka.fs :as fs]
   [clojure.java.basis]
   [clojure.string :as str]
   [compliment.core]
   [compliment.utils]
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

(defn- duration->millis [{:keys [seconds minutes hours days weeks]
                          :or   {seconds 0 minutes 0 hours 0 days 0 weeks 0}}]
  (let [days    (+ days (* weeks 7))
        hours   (+ hours (* days 24))
        minutes (+ minutes (* hours 60))
        seconds (+ seconds (* minutes 60))]
    (* 1000 seconds)))

(defn- set-examples-file-name! [path]
  (alter-var-root (requiring-resolve 'orchard.clojuredocs/cache-file-name) (constantly (str path))))

(defn- ensure-fresh-examples-cache! [path {:keys [max-age]}]
  (let [modified-time (if (fs/exists? path)
                        (-> path
                            fs/last-modified-time
                            fs/file-time->millis)
                        0)
        age           (- (System/currentTimeMillis) modified-time)]
    (when (< max-age age)
      ((requiring-resolve 'orchard.clojuredocs/update-cache!)))))

(defmethod clj-reader/-examples ::service [self word]
  (let [data-path (:data-path self)]
    (when-let [[wns wname] (word->doc-searchable word)]
      (let [examples-file-name (fs/path data-path "clojuredocs-export.edn")]
        (set-examples-file-name! examples-file-name)
        ;; TODO are we online?
        (ensure-fresh-examples-cache! examples-file-name {:max-age (duration->millis {:weeks 2})})
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
  (let [options (if (:extra-metadata options) options (assoc options :extra-metadata #{:private :deprecated}))]
    (with-redefs [compliment.utils/classpath classpath-for-completions]
      #_(prn ::-complete :word word :options options)
      (doall (cond-> (compliment.core/completions word options)
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
