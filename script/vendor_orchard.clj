;; (Re)vendors the (few) orchard namespaces that deps-try uses into
;; vendor/deps-try.orchard, renaming `orchard.*` -> `deps-try.orchard.*`.
;; This keeps orchard itself off the REPL classpath: users (and tooling like
;; cider-jack-in when developing deps-try) are free to bring any version.
;; NB the bundled 1.7MB clojuredocs fallback-export is deliberately NOT
;; vendored: deps-try downloads a fresh export into its data-dir on first use
;; (see eval.deps-try.rr-service).
;;
;; Usage: bb vendor:orchard [git-rev]   ;; git-rev defaults to the pin below
;;
;; Run from the repo root. To upgrade: bump the pin (or pass a rev), rerun,
;; check `git diff vendor/deps-try.orchard`, smoke test the examples widget
;; (Ctrl-X Ctrl-X).
(ns vendor-orchard
  (:require [babashka.fs :as fs]
            [clojure.string :as string]
            [clojure.tools.gitlibs :as gitlibs]))

(def ^:private pinned-rev "v0.44.0")
(def ^:private git-url "https://github.com/clojure-emacs/orchard.git")
(def ^:private lib 'io.github.clojure-emacs/orchard)
(def ^:private target (fs/path "vendor" "deps-try.orchard"))

;; orchard.clojuredocs and its (transitive) orchard requires
(def ^:private files-to-vendor
  ["orchard/clojuredocs.clj"
   "orchard/misc.clj"
   "orchard/util/os.clj"
   "orchard/util/io.clj"])

(defn- rename [content]
  (string/replace content #"orchard\." "deps-try.orchard."))

(let [rev  (or (first *command-line-args*) pinned-rev)
      sha  (or (gitlibs/resolve git-url rev)
               (throw (ex-info (str "Cannot resolve rev " rev " of " git-url) {:rev rev})))
      root (or (gitlibs/procure git-url lib sha)
               (throw (ex-info (str "Cannot procure " git-url " @ " sha) {:sha sha})))]
  (fs/delete-tree (fs/path target "src"))
  (doseq [f files-to-vendor
          :let [in  (fs/path root "src" f)
                out (fs/path target "src" "deps_try" f)]]
    (fs/create-dirs (fs/parent out))
    (spit (fs/file out) (rename (slurp (fs/file in)))))
  (fs/copy (fs/path root "LICENSE") (fs/path target "LICENSE") {:replace-existing true})
  (spit (fs/file (fs/path target "deps.edn")) "{:paths [\"src\"]}\n")
  (spit (fs/file (fs/path target "UPSTREAM.edn"))
        (str {:git/url git-url :git/rev rev :git/sha sha
              :note "partial vendoring, see files-to-vendor in script/vendor_orchard.clj"} \newline))
  (println "Vendored orchard" rev (str "(" sha ")") "into" (str target)))
