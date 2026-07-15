;; (Re)vendors alexander-yakushev/compliment into vendor/deps-try.compliment,
;; renaming namespaces `compliment.*` -> `deps-try.compliment.*` so completions
;; keep working when a user tries compliment (or tooling depending on it) in
;; the REPL. The `:compliment.lite/*` markers (only used by upstream's lite
;; build) are left as-is, mirroring the original rename in the (now retired)
;; eval/compliment fork.
;;
;; Usage: bb vendor:compliment [git-rev]   ;; git-rev defaults to the pin below
;;
;; Run from the repo root. To upgrade: bump the pin (or pass a rev), rerun,
;; check `git diff vendor/deps-try.compliment`, smoke test completions.
(ns vendor-compliment
  (:require [babashka.fs :as fs]
            [clojure.string :as string]
            [clojure.tools.gitlibs :as gitlibs]))

(def ^:private pinned-rev "0.8.1")
(def ^:private git-url "https://github.com/alexander-yakushev/compliment.git")
(def ^:private lib 'io.github.alexander-yakushev/compliment)
(def ^:private target (fs/path "vendor" "deps-try.compliment"))

(defn- rename [content]
  (string/replace content #"compliment\.(?!lite)" "deps-try.compliment."))

(let [rev  (or (first *command-line-args*) pinned-rev)
      sha  (or (gitlibs/resolve git-url rev)
               (throw (ex-info (str "Cannot resolve rev " rev " of " git-url) {:rev rev})))
      root (or (gitlibs/procure git-url lib sha)
               (throw (ex-info (str "Cannot procure " git-url " @ " sha) {:sha sha})))
      src  (fs/path root "src" "compliment")
      dest (fs/path target "src" "deps_try" "compliment")]
  (fs/delete-tree (fs/path target "src"))
  (doseq [f     (fs/glob src "**")
          :when (fs/regular-file? f)
          :let  [out (fs/path dest (fs/relativize src f))]]
    (fs/create-dirs (fs/parent out))
    (spit (fs/file out) (rename (slurp (fs/file f)))))
  (fs/copy (fs/path root "LICENSE") (fs/path target "LICENSE") {:replace-existing true})
  (spit (fs/file (fs/path target "deps.edn")) "{:paths [\"src\"]}\n")
  (spit (fs/file (fs/path target "UPSTREAM.edn"))
        (str {:git/url git-url :git/rev rev :git/sha sha} \newline))
  (println "Vendored compliment" rev (str "(" sha ")") "into" (str target)))
