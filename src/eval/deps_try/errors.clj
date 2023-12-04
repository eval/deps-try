(ns eval.deps-try.errors)

(def errors-by-id
  {:resolve.local/path-not-found          ["Could not find path '" :full-path "'."]
   :resolve.local/not-a-deps-folder       ["Folder '" :full-path "' does not contain a file 'deps.edn'."]
   :resolve.git/ref-not-found             ["Could not find branch or tag '" :ref "' in repository '" :url "'."]
   :resolve.git/sha-not-found             ["Could not find SHA, branch or tag '" :sha "' in repository '" :url "'."]
   :resolve.git/sha-not-found-offline     ["Could not find SHA, branch or tag '" :sha "' in repository '" :url "' while offline."]
   :resolve.git/repos-not-found           ["Could not find repository '" :url "'."]
   :resolve.git/repos-not-found-offline   ["Could not find repository '" :url "' while offline."]
   :resolve.git/caret-version-unsupported ["For this git-hosting it's not possible to point to PRs. Use regular refs/SHAs instead."]
   :resolve.mvn/library-not-found         ["Could not find library '" :lib "' on Maven Central or Clojars."]
   :resolve.mvn/library-not-found-offline ["Could not find library '" :lib "' while offline."]
   :resolve.mvn/version-not-found         ["Could not find version '" :version "' of library '" :lib "' on Maven Central or Clojars."]
   :resolve.mvn/version-not-found-offline ["Could not find version '" :version "' of library '" :lib "' while offline."]
   :parse.recipe/path-not-found           ["Recipe not found: file '" :path "' does not exist."]
   :parse.recipe/url-not-found            ["Recipe not found: url '" :path "' not found."]
   :parse.recipe/offline                  ["Recipe '" :path "' can't be loaded while offline."]})

(defn format-error [{:error/keys [id] :as error}]
  (let [extract-placeholders (fn [err]
                               (filter keyword? err))]
    (if-let [error-tpl (errors-by-id id)]
      (let [placeholders (extract-placeholders error-tpl)
            replacements (zipmap placeholders (map #(error % %) placeholders))]
        (apply str (replace replacements error-tpl)))
      (if (string? error) error (pr-str error)))))
