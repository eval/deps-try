(ns eval.deps-try.update-check
  "Version freshness without boot-time network: startup only *reads* the
  cache files that `refresh!` — running in the background of a REPL session
  — keeps up to date for the next boot."
  (:require
   [clojure.string :as string]
   [deps-try.http-client :as http]
   [eval.deps-try.deps :as try-deps]
   [eval.deps-try.fs :as fs]))

(defn- cache-file [fname]
  (fs/file (fs/xdg-data-home "deps-try") fname))

(defn- read-cache [fname]
  (let [f (cache-file fname)]
    (when (fs/exists? f)
      (not-empty (string/trim (slurp f))))))

(defn- write-cache! [fname value]
  (when value
    (fs/create-dirs (fs/xdg-data-home "deps-try"))
    (spit (cache-file fname) value)))

(defn cached-latest-released-version
  "Latest stable deps-try release (as of the last `refresh!`), nil when (so
  far) unknown."
  []
  (read-cache "latest-version"))

(defn cached-latest-clojure-version
  "Latest stable Clojure version (as of the last `refresh!`), nil when (so
  far) unknown."
  []
  (read-cache "clojure-latest-version"))

(defn- fetch-latest-released-version
  "Tag of the latest stable release per GitHub (which excludes pre-releases
  and drafts, i.e. the 'unstable' releases that master pushes yield).
  Nil when it can't be determined."
  []
  (let [resp     (http/head "https://github.com/eval/deps-try/releases/latest"
                            {:client  (http/client {:follow-redirects :never})
                             :timeout 5000
                             :throw   false})
        location (get-in resp [:headers "location"] "")
        tag      (last (string/split location #"/"))]
    (when (some->> tag (re-matches #"v\d+\.\d+\.\d+"))
      tag)))

(defn refresh!
  "Fetch the latest released deps-try version and the latest stable Clojure
  version and cache them (informing the next boot). Best-effort and silent;
  a no-op with env DEPS_TRY_NO_UPDATE_CHECK."
  []
  (when-not (System/getenv "DEPS_TRY_NO_UPDATE_CHECK")
    (try (write-cache! "latest-version" (fetch-latest-released-version))
         (catch Exception _))
    (try (write-cache! "clojure-latest-version"
                       (get-in (try-deps/resolve-version
                                [:dep/mvn "org.clojure/clojure" :latest])
                               [:mvn/version :mvn/version]))
         (catch Exception _))))
