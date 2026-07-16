(ns eval.deps-try.deps
  {:clj-kondo/config '{:lint-as {eval.deps-try.util/pred-> clojure.core/->}}}
  (:require
   #?@(:bb [] :clj [clojure.java.basis])
   [clojure.string :as str]
   [clojure.tools.gitlibs :as gitlib]
   [eval.deps-try.ansi-escape :as ansi]
   [eval.deps-try.fs :as fs]
   [eval.deps-try.process :refer [process]]
   [eval.deps-try.util :as util]))

(def ^:private git-services
  [[:github    {:dep-url-re    #"^(?:io|com)\.github\.([^/]+)\/(.+)"
                :dep-url-tpl   ["io.github." 'org "/" 'project]
                :git-url-re    #"^https://github\.com\/([^/]+)\/(.+?(?=.git)?)(?:.git)?$"
                :git-url-tpl   ["https://github.com/" 'org "/" 'project]
                :caret-ref-tpl ["refs/pull/" 'id "/head"]}]
   [:sourcehut {:dep-url-re  #"^ht\.sr\.([^/]+)\/(.+)"
                :dep-url-tpl ["ht.sr." 'org "/" 'project]
                :git-url-re  #"^https://git\.sr\.ht/~([^/]+)\/(.+)"
                :git-url-tpl ["https://git.sr.ht/~" 'org "/" 'project]}]
   [:gitlab    {:dep-url-re    #"^(?:io|com)\.gitlab\.([^/]+)\/(.+)"
                :dep-url-tpl   ["io.gitlab." 'org "/" 'project]
                :git-url-re    #"^https://gitlab\.com\/([^/]+)\/(.+?(?=.git)?)(?:.git)?$"
                :git-url-tpl   ["https://gitlab.com/" 'org "/" 'project]
                :caret-ref-tpl ["refs/merge-requests/" 'id "/head"]}]
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

(defn- git-url->dep-name [url]
  (let [[_service {:keys [git-url-re dep-url-tpl]}] (git-url->git-service url)
        [_ org project]                             (re-find git-url-re url)]
    (apply str (replace {'org org 'project project} dep-url-tpl))))

(defn- dep-url->git-url [url]
  (when-let [[_service {:keys [dep-url-re git-url-tpl]}] (dep-url->git-service url)]
    (let [[_ org project] (re-find dep-url-re url)]
      (apply str (replace {'org org 'project project} git-url-tpl)))))

(defn- path? [s]
  (re-find #"^[./~]?[A-Za-z0-9./]+" s))

(defn- url? [s]
  (re-find #"^https?://" s))

(defn- git-dep? [s]
  (or (dep-url->git-url s)
      (url? s)))

(defn- local-dep? [s]
  (and (path? s)
       (not (url? s))))

(defn- mvn-dep? [s]
  (re-find #"^(?![.~])[^/]+/[^/]+$" s))

(def ^:private dep-types
  {:dep/mvn #'eval.deps-try.deps/mvn-dep?
   :dep/git #'eval.deps-try.deps/git-dep?
   :dep/local #'eval.deps-try.deps/local-dep?})

(defn- mvn-version? [v]
  (let [mvn-version-re #"^\d+\.\d+\.\d+"]
    (or (re-find mvn-version-re v)
        ;; maven-native tokens, passed through as-is (escape hatch)
        (#{"RELEASE" "LATEST"} v))))

(defn- version-token
  "Recognize deps-try's version tokens (case-insensitive):
  `latest` — newest stable version, `head` — newest incl. pre-releases."
  [s]
  (when (string? s)
    ({"latest" :latest "head" :head} (str/lower-case s))))

(defn- git-version? [s]
  ;; exclude anything that
  ;; - looks like a mvn- or git-dep
  ;; - looks like a forced local-dep (e.g. "~/project", "./project")
  (and (not (git-dep? s))
       (not (mvn-dep? s))
       (not (re-find #"^[.~]" s))))

(def ^:private version-types
  {:version/mvn #'mvn-version?
   :version/git #'git-version?})

;; TODO handle :latest?
(defn- arg->dep-types [arg]
  (not-empty
   (map first (filter (fn [[_type pred]]
                        (pred arg)) dep-types))))

(defn- arg->version-types [arg]
  (not-empty
   (map first (filter (fn [[_type pred]]
                        (pred arg)) version-types))))

(def ^:private version-key->dep-key
  {:version/git :dep/git
   :version/mvn :dep/mvn})

(defn parse-args [{:keys [args]}]
  ;; tuples: '("a" "b" "c") => '(("a" "b") ("b" "c") ("c" :latest))
  (let [dep-version-tuples (partition 2 1 (list :latest) args)]
    (loop [[[dep maybe-version] :as rem-args] dep-version-tuples
           parsed                             []
           error                              nil]
      (if dep
        (if-let [dep-type-candidates (arg->dep-types dep)]
          (let [latest?                     #(= % :latest)
                ;; explicit `latest`/`head` argument (:latest/:head or nil)
                explicit-token              (version-token maybe-version)
                maybe-version-is-dep?       (and (not explicit-token)
                                                 (not (latest? maybe-version))
                                                 (arg->dep-types maybe-version))
                maybe-version-version-types (and (not explicit-token)
                                                 (not (latest? maybe-version))
                                                 (arg->version-types maybe-version))
                ;; do maybe-version-version-types and dep-type-candidates overlap?
                dep-version-type-overlap    (when maybe-version-version-types
                                              (filter (set dep-type-candidates)
                                                      (map version-key->dep-key maybe-version-version-types)))

                version (cond
                          explicit-token                 explicit-token
                          (seq dep-version-type-overlap) maybe-version
                          maybe-version-is-dep?          :latest
                          (latest? maybe-version)        :latest
                          :else                          :wrong)

                ;;  all dep-type-candidates that match the maybe-version-types
                version-matching-dep-types (cond
                                             (= version :wrong)  '()
                                             ;; :latest (explicit or implied) or :head
                                             (keyword? version)  dep-type-candidates
                                             :else               dep-version-type-overlap)
                resolve-steps              (into [:or] (map (fn [dt]
                                                              [[dt dep version]])
                                                            version-matching-dep-types))
                ;; unwrap from [:or ,,,] if just 1 item wrapped
                resolve-steps              (cond-> resolve-steps
                                             (= 2 (count resolve-steps)) ((comp first last)))
                remaining-args             (cond
                                             (= version :wrong) nil
                                                 ;; consume explicitly passed token
                                             explicit-token     (rest (rest rem-args))
                                                 ;; NOT consume version (implied :latest)
                                             (latest? version)  (rest rem-args)
                                                 ;; consume version
                                             :else              (rest (rest rem-args)))]
            (cond
              (seq version-matching-dep-types)
              (recur remaining-args (conj parsed resolve-steps) nil)
              :else (recur remaining-args parsed {:arg      maybe-version
                                                  :type     :unknown-version-or-dep
                                                  :dep-type dep-type-candidates
                                                  :dep      dep})))
          (recur nil parsed {:arg dep :type :unknown-dep}))
        (cond-> {:recipe parsed}
          error (assoc :error error))))))

(comment
  (parse-args {:args '("v1.2.3")})

  #_:end)


(defn resolve-git-sha
  "Resolve (partial) SHA `sha` for git-repository `git-url`.
  Returns a string representing a full sha or a map containing an `:error`.

  Possible :error ids:
  - :resolve.git/sha-not-found
  - :resolve.git/sha-not-found-offline
  - :resolve.git/repos-not-found
  - :resolve.git/connection-needed
    Either repository or provided sha of repository is not present locally."
  [git-url sha]
  (let [full-sha?                 #(and (seq %) (= 40 (count %)))
        {{:keys [err]} :data
         cause         :cause :as result} (try (gitlib/resolve git-url sha)
                                               (catch Exception e
                                                 (Throwable->map e)))]
    (cond
      (full-sha? result) {:git/version {:git/sha result}}
      (nil? result)      {:error {:error/id :resolve.git/sha-not-found :url git-url :sha sha}}
      (and (string? err) (re-find #"could not read Username" err))
      {:error {:error/id :resolve.git/repos-not-found :url git-url}}
      (and (string? err) (re-find #"Could not resolve host" err))
      (if (re-find #"Unable to fetch" cause)
        {:error {:error/verified-dep-type true
                 :error/id                :resolve.git/sha-not-found-offline :sha sha :url git-url}}
        {:error {:error/id :resolve.git/repos-not-found-offline :url git-url}}))))

(defn resolve-git-ref [url ref-or-sha]
  (let [no-sha?                   (not (every? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \a \b \c \d \e \f}
                                               (seq ref-or-sha)))
        ;; NOTE including empty creds in the url prevents prompting for creds when group or project does not exist
        non-prompting-url      (let [[_ proto-part rem-url] (re-find #"^(https?://)(.+)$" url)]
                                 (str proto-part ":@" rem-url))
        ;; NOTE we can't really verify if `ref-or-sha` is a (partial) SHA (and *not* a ref).
        ;; So we treat it as a ref and pass it to ls-remote - paying a possible penalty.
        ;; NOTE passing branch to gitlib/resolve yields latest *local* SHA of repos, not perse the latest SHA,
        ;; therefor try `git ls-remote` first, and gitlib/resolve only when offline.
        cmd                    ["git" "ls-remote" "--symref" non-prompting-url ref-or-sha]
        {:keys [exit out err]} @(process cmd {:out :string :err :string})
        line->map              (fn [sym->ref line]
                                 (let [[sha r]         (str/split line #"\t")
                                       git-ref         (sym->ref r)
                                       default-branch? (boolean git-ref)
                                       git-ref         (if git-ref git-ref r)]
                                   (cond-> {:git/sha sha :git/ref git-ref}
                                     default-branch? (assoc :git/default-branch? true))))
        extract-sym->ref       (fn [out]
                                 (let [[ref-line & _] (str/split-lines out)]
                                   (apply hash-map (reverse (rest (str/split ref-line #"[\t ]"))))))]
    (cond
      (re-find #"Could not resolve host" (str err)) (resolve-git-sha url ref-or-sha)
      (not (zero? exit)) {:error {:error/id :resolve.git/repos-not-found :url url}}
      (empty? out)       (if no-sha?
                           {:error {:error/id :resolve.git/ref-not-found :ref ref-or-sha :url url}}
                           (resolve-git-sha url ref-or-sha))
      :else              (let [ref-line? (re-find #"^ref:" out)
                               sym->ref  (if ref-line? (extract-sym->ref out) {})
                               lines     (str/split-lines out)
                               lines     (if ref-line? (rest lines) lines)
                               git-refs (map (partial line->map sym->ref) lines)]
                           {:git/version (first git-refs)}))))

(defmulti resolve-version (fn [[type]] type))

(defn- expand-caret-version [git-url version]
  (let [[_ caret-id]                                 (re-find #"^\^(\d+)" version)
        [git-service {caret-ref-tpl :caret-ref-tpl}] (when caret-id (git-url->git-service git-url))]
    (cond
      (and caret-id caret-ref-tpl)       (apply str (replace {'id caret-id} caret-ref-tpl))
      (and caret-id (not caret-ref-tpl)) {:error {:error/id :resolve.git/caret-version-unsupported :git-service git-service}}
      :else                              version)))

(defmethod resolve-version :dep/git [[_type git-url version]]
  ;; TODO :latest could mean 'newest version tag' for git deps
  (let [{err :error :as version} (if (#{:latest :head} version)
                                   "HEAD"
                                   (expand-caret-version git-url version))]
    (if-not err
      (resolve-git-ref git-url version)
      {:error err})))

(defn ^:private local-repo-path []
  (fs/path (fs/home) ".m2" "repository"))

(defn- version-sort-key
  "Sortable key such that the newest version sorts last. Version-ordered (not
  release-date-ordered like maven's LATEST/RELEASE): pre-releases sort before
  the stable release of the same base version (1.2.0-rc1 < 1.2.0), qualifiers
  compare alphabetically, then numerically (alpha2 < alpha11 < beta1)."
  [v]
  (let [[_ nums qual] (re-matches #"(\d+(?:\.\d+)*)[.\-]?(.*)" (str v))
        nums          (if nums
                        (vec (take 6 (concat (map parse-long (str/split nums #"\."))
                                             (repeat 0))))
                        [-1 -1 -1 -1 -1 -1])
        stable?       (str/blank? qual)
        qual-alpha    (str/lower-case (or (some->> qual (re-find #"[a-zA-Z]+")) ""))
        qual-num      (or (some->> qual (re-find #"\d+") parse-long) -1)]
    [nums (if stable? 1 0) qual-alpha qual-num]))

(defn- stable-version? [v]
  (re-matches #"\d+(\.\d+)*" (str v)))

(defn- pick-version
  "Newest of `versions` given `token`: :latest yields the newest stable
  (falling back to newest overall when a library has no stable release),
  :head the newest incl. pre-releases. Nil when `versions` is empty."
  [token versions]
  (let [newest #(last (sort-by version-sort-key %))]
    (case token
      :head   (newest versions)
      :latest (or (newest (filter stable-version? versions))
                  (newest versions)))))

(defn resolve-mvn-local [lib version]
  (let [token        (#{:latest :head} version)
        lib-path     (let [[group artifact] (str/split lib #"/")]
                       (str (apply str (replace {\. \/} group)) "/" artifact))
        lib-fullpath (fs/path (local-repo-path) lib-path)]
    (if token
      (let [local-versions (when (fs/exists? lib-fullpath)
                             ;; NB fs/glob wouldn't yield the version-dirs
                             (->> (fs/match lib-fullpath "glob:*" {:max-depth 1})
                                  (filter fs/directory?)
                                  (filter #(seq (fs/glob % "*.jar")))
                                  (map fs/file-name)))]
        (if-let [picked (pick-version token local-versions)]
          {:mvn/version {:mvn/version picked}}
          {:error {:error/id :resolve.mvn/library-not-found-offline :lib lib :version version}}))
      (cond
        (seq (fs/glob (fs/path lib-fullpath version) "**.jar"))
        {:mvn/version {:mvn/version version}}

        (seq (fs/glob lib-fullpath "**.jar"))
        {:error {:error/id :resolve.mvn/version-not-found-offline :lib lib :version version}}

        :else {:error {:error/id :resolve.mvn/library-not-found-offline :lib lib :version version}}))))

(def standard-repos
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://repo.clojars.org/"}})

(defn- metadata-versions [metadata-body]
  (map second (re-seq #"<version>([^<]+)</version>" metadata-body)))

(defmethod resolve-version :dep/mvn [[_type lib version]]
  (let [token                 (#{:latest :head} version)
        check-version         #(re-find (re-pattern (str ">" %2 "<")) %1)
        lib-path              (let [[group artifact] (str/split lib #"/")]
                                (str (apply str (replace {\. \/} group)) "/" artifact))
        libify                #(str % lib-path "/maven-metadata.xml")
        urls                  (map (comp libify :url) (vals standard-repos))
        {:keys [status body]} (util/multi-url-test urls {:include-body true})]
    (cond
      (= status :not-found) {:error {:error/id :resolve.mvn/library-not-found :lib lib :version version}}
      (empty? body)         (resolve-mvn-local lib version)
      (= status :found)
      (cond
        token
        (if-let [picked (pick-version token (metadata-versions body))]
          {:mvn/version {:mvn/version picked}}
          {:error {:error/id :resolve.mvn/version-not-found :lib lib :version version}})

        ;; maven-native tokens: resolved by maven itself, no validation
        (#{"RELEASE" "LATEST"} version)
        {:mvn/version {:mvn/version version}}

        (check-version body version)
        {:mvn/version {:mvn/version version}}

        :else {:error {:error/id :resolve.mvn/version-not-found :lib lib :version version}}))))

(comment
  (resolve-version [:dep/mvn "org.clojure/clojure" "1.12.0-alpha3"])
  (resolve-version [:dep/mvn "com.github.seancorfield/next.jdbc" :latest])

  #_:end)

(defmulti resolve-dep (fn [[type]] type))

(defmethod resolve-dep :dep/local [[_ arg _version]]
  (let [full (fs/canonicalize (fs/expand-home arg))]
    (cond
      (not (and (fs/exists? full)
                (fs/directory? full))) {:error {:error/id :resolve.local/path-not-found :arg arg :full-path full}}
      (not (fs/exists? (fs/path full "deps.edn")))
      {:error {:error/id :resolve.local/not-a-deps-folder :arg arg :full-path full}}
      :else                            (let [file-name (fs/file-name full)
                                             dep-name  (str file-name "/" file-name)]
                                         {:deps [[(symbol dep-name) {:local/root (str full)}]]}))))


(defmethod resolve-dep :dep/git [[_ arg version]]
  (let [[dep-name git-url]          (cond
                                      (re-find #"^https?" arg) [(symbol (git-url->dep-name arg)) arg]
                                      :else                    [(symbol arg) (dep-url->git-url arg)])
        {:keys [error git/version]} (resolve-version [:dep/git git-url version])]
    (if-not error
      (let [{git-sha :git/sha} version]
        {:deps [[dep-name {:git/sha git-sha}]]})
      {:error error})))


(defmethod resolve-dep :dep/mvn [[_ arg version]]
  (let [{version :mvn/version error :error} (resolve-version [:dep/mvn arg version])]
    (if-not error
      {:deps [[(symbol arg) version]]}
      {:error error})))

(declare resolve-deps*)

(defmethod resolve-dep :or [[_or & recipes]]
  (let [results (atom [])
        store!  #(peek (swap! results conj %))]
    ;; TODO ensure order in recipes: :dep/mvn, :dep/git, :dep/local
    (doseq [recipe recipes
            :let   [last-result (store! (resolve-deps* recipe))]
            :while (and (:error last-result)
                        (not (:error/verified-dep-type (:error last-result))))])
    (let [{:keys [deps error]} (peek @results)
          ;; NOTE error to user would be about the first error
          ;; unless an error with `:error/verified-dep-type`
          error                (when error
                                 (or (and (:error/verified-dep-type error) error)
                                     (-> results deref first :error)))]
      (cond-> {}
        (seq deps) (assoc :deps deps)
        error      (assoc :error error)))))

(defn resolve-deps* [recipe]
  (let [result (atom {:deps []})
        stop?  #(:error @result)]
    (doseq [step   recipe
            :while (not (stop?))]
      (let [{:keys [error deps]} (resolve-dep step)]
        (if error
          (swap! result assoc :error error)
          (swap! result update :deps into deps))))
    @result))

(defn resolve-deps [{:keys [recipe]}]
  (let [{:keys [error deps]} (resolve-deps* recipe)]
    (if-not error
      {:deps (into {} deps)}
      {:error error})))

(defn parse-dep-args [{:keys [deps]}]
  (util/pred-> (complement :error) {:args deps}
                     ;; e.g. [[:dep/local "foo" :latest] [:dep/mvn "bar/baz" :latest]]
               (parse-args)
               #_(-> (doto prn))
               (resolve-deps)))

#?(:bb nil
   :clj
   (defn deps-available []
     (->> (clojure.java.basis/current-basis)
          :libs
          ;; synthetic deps used to put the packaged uberjar on the classpath
          ;; (see eval.deps-try/start-repl!) shouldn't show as user libraries
          (remove (comp #{"deps-try.self"} namespace key))
          (filter (comp #(every? empty? %) :parents val)))))

#?(:bb nil
   :clj
   (defn fmt-deps-available []
     (let [fmt-dep (fn [[dname {sha :git/sha version :mvn/version}]]
                     (str " ⚡️ " (ansi/wrap ansi/bold dname)
                          " " (ansi/wrap ansi/fg-cyan
                                         (or (some-> sha (subs 0 7))
                                             version))))]
       (str/join \newline (map fmt-dep (deps-available))))))

(comment
  (set! clojure.core/*print-namespace-maps* false)

  #_:end)
