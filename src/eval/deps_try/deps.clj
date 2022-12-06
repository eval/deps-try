(ns eval.deps-try.deps
  (:require [clojure.tools.gitlibs :as gitlib]))

(defn- requested-dep? [rd]
  (some #{\/} (str rd)))

(def ^:private requested-version? (complement #'requested-dep?))

(def ^:private git-services
  [[:github    {:dep-url-re  #"^(?:io|com)\.github\.([^/]+)\/(.+)"
                :dep-url-tpl ["io.github." 'org "/" 'project]
                :git-url-re  #"^https://github\.com\/([^/]+)\/(.+?(?=.git)?)(?:.git)?$"
                :git-url-tpl ["https://github.com/" 'org "/" 'project]}]
   [:sourcehut {:dep-url-re  #"^ht\.sr\.([^/]+)\/(.+)"
                :dep-url-tpl ["ht.sr." 'org "/" 'project]
                :git-url-re  #"^https://git\.sr\.ht/~([^/]+)\/(.+)"
                :git-url-tpl ["https://git.sr.ht/~" 'org "/" 'project]}]
   [:gitlab    {:dep-url-re  #"^(?:io|com)\.gitlab\.([^/]+)\/(.+)"
                :dep-url-tpl ["io.gitlab." 'org "/" 'project]
                :git-url-re  #"^https://gitlab\.com\/([^/]+)\/(.+?(?=.git)?)(?:.git)?$"
                :git-url-tpl ["https://gitlab.com/" 'org "/" 'project]}]
   [:generic   {:dep-url-tpl ['org "/" 'project]
                :git-url-re  #"^https://[^/]+\/([^/]+)\/(.+?(?=.git)?)(?:.git)?$"}]])

(defn- git-url->git-service
  [url]
  (first
   (filter (fn [[_service {:keys [git-url-re]}]]
             (re-seq git-url-re url)) (var-get #'git-services))))

(defn- dep-url->git-service
  [url]
  (first
   (filter (fn [[_service {:keys [dep-url-re]}]]
             (and dep-url-re (re-seq dep-url-re url))) (var-get #'git-services))))

(defn- find-git-service-by-url [url]
  (or (git-url->git-service url)
      (dep-url->git-service url)))


(defn- git-url->dep-name [url]
  (let [[_service {:keys [git-url-re dep-url-tpl]}] (git-url->git-service url)
        [_ org project]                             (re-find git-url-re url)]
    (apply str (replace {'org org 'project project} dep-url-tpl))))

(defn- dep-url->git-url [url]
  (let [[_service {:keys [dep-url-re git-url-tpl]}] (dep-url->git-service url)
        [_ org project]                             (re-find dep-url-re url)]
    (apply str (replace {'org org 'project project} git-url-tpl))))


(defn- dep-arg-version-tuple->dep
  "Yields MapEntry e.g. [tick/tick {:mvn/version \"RELEASE\"}]"
  [[dep-arg version]]
  (let [version->git-version #(if (= % :latest) "HEAD" %)
        dep-type             (cond
                               (git-url->git-service dep-arg) :git-url
                               (dep-url->git-service dep-arg) :dep-url
                               :else                          :maven)
        dep                  (case dep-type
                               :git-url (symbol (git-url->dep-name dep-arg))
                               (symbol dep-arg))

        dep-version (case dep-type
                      :git-url (as-> dep-arg $
                                 (gitlib/resolve $ (version->git-version version))
                                 (assoc {} :sha $))
                      :dep-url (as-> dep-arg $
                                 (dep-url->git-url $)
                                 (gitlib/resolve $ (version->git-version version))
                                 (assoc {} :sha $))
                      :maven   (if (= version :latest)
                                 {:mvn/version "RELEASE"}
                                 {:mvn/version version}))]
    (first {dep dep-version})))



(defn- args->dep-arg-version-tuples [args]
  (let [default-version :latest
        version-or      (fn [fallback]
                          #(if (requested-version? %) % fallback))]
    (->> args
         (partition 2 1 (list default-version))
         (remove (comp requested-version? first))
         (map vec)
         (map #(update % 1 (version-or default-version))))))

(defn parse-dep-args [args]
  (->> args
       args->dep-arg-version-tuples
       (map dep-arg-version-tuple->dep)
       (apply conj {})))

(comment
  (var git-services)
  (var-get #'eval.deps-try.deps/git-services)

  (conj {} (clojure.lang.MapEntry. :a 1) (clojure.lang.MapEntry. :b 2))
  (clojure.lang.MapEntry/create :a 1)
  (first (args->dep-arg-version-tuples '("tick/tick" "https://github.com/eval/deps-try" "master")))

  (parse-dep-args '("tick/tick" "https://github.com/eval/deps-try" "test"))
  (dep-arg-version-tuple->dep ["http://github.com/eval/deps-try" :latest])


  (git-url->git-service "https://github.com/eval/deps-try.va")
  )
