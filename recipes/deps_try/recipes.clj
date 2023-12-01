;; Use this file with deps-try:
;; $ deps-try --recipe deps-try/recipes
(ns recipes.deps-try.recipes
  "Introducing recipes."
  {:deps-try.recipe/deps []})

;; A recipe is a normal clj-file that is loaded from disk or via http, e.g.:
;;
;; $ deps-try --recipe ./some/local/path/to/foo.clj
;; $ deps-try --recipe https://gist.github.com/eval/ee80ebddaa120a7732396cea8cfc96da/raw
;;
;; There's also built-in recipes.
;; To show a listing:
;; $ deps-try recipes
;;
;; To load a built-in recipe:
;; $ deps-try --recipe malli/malli-select


;; The contents of the recipe-file is broken up in steps that are added to
;; the front of the REPL's history.
;;
;; A step is any section in the file that is pre- and suffixed by 2 empty lines.
;;
;; By repeatingly choosing a recipe step from the history and
;; submitting it, a user progresses through the recipe (until the
;; regular history items remain).
;;
;; You don't need to submit *every* single step in order to progress:
;; a submitted step will ensure that any step up to that step is
;; removed from the front of the history.


;; Like any Clojure file, a recipe starts with a namespace declaration where
;; the docstring gives a short description of the recipe.
;;
;; The attr-map of the namespace may contain dependencies needed for
;; the recipe.
;; These dependencies will be put on the classpath when the user loads the recipe.
;;
;; An example recipe-namespace:
;; (ns foo
;;   "Some description"
;;   {:deps-try/deps ["metosin/malli" "0.13.0" "foo/bar"]}
;;   (:require [malli.core :as m]
;;             [bar.core :as b]))
;;


;; Instead of loading the full recipe into the REPL's history, a
;; user can also only load the namespace-step of a recipe:
;;
;; $ deps-try --recipe-ns https://gist.github.com/eval/ee80ebddaa120a7732396cea8cfc96da/raw
;;
;; This has as benefit that the history is only slightly adjusted,
;; while still getting the benefit of having the needed dependencies and requires.
