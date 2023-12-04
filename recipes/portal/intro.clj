;; Use this file with deps-try:
;; $ deps-try --recipe portal/intro
(ns recipes.portal.intro
  "Introduction to portal, a Clojure tool to navigate data."
  {:deps-try.recipe/status :published
   :deps-try.recipe/deps   ["djblue/portal"]}
  (:require [portal.api :as p]))

;; Portal is a tool to inspect data via a handy web UI.
;; It's a great alternative for `prn` or logging when you're debugging.
;;
;; Without further ado, let's see it in action.
;;


(require '[portal.api :as p])

; Open a new inspector in default browser
(def p (p/open {:app false}))

; Add portal as a tap> target
(add-tap #'p/submit)


;; The step you just evaluated is super helpful to have handy in REPL-history;
;; In case you find yourself in a future REPL-session needing to inspect data,
;; just do a history search (Control-r) and look e.g. for 'add-tap' to find it.
;;
;; Don't forget to first add the library (`djblue/portal`) when starting deps-try or
;; evaluate `:deps/try djblue/portal` in an existing REPL-session.
;;


;; Ok, let's send Portal some data.
;;
;; There's more than one way to do this.
;;
;; First, as Portal uses taps, there's clojure.core's `tap>`
;; function (first example below).
;;
;; Then there's also a special keybinding similar to eval-at-point that
;; will eval the expression before the cursor and tap> the result.
;;
;; Try both below: instead of submitting both expressions by
;; pressing Enter, put the cursor at the comma and
;; (for the first expression) press Control-x Control-e (eval-at-point).
;; A map should now be visible in the Portal UI.
;;
;; To use the special keybinding: put the cursor at the second comma
;; and press Control-x Control-t (eval&tap-at-point) to send its
;; result to Portal.
;;
;; Submit this step when done.
;;
(tap> {:hello "tap>"}),
{:hello "eval&tap-at-point"},


;; eval&tap-at-point is flexible in that when there's no expression in
;; front of the cursor, it will tap the result of the last evaluation.
;; Ideal for when you eval-ed something and there's an unexpected
;; amount of data generated.
;;
;; Try it out:
;; Keep the cursor at the last line of this step and press C-x C-t again.
;;


;; Tapping the last result also works for exceptions.
;; And exceptions look nice in Portal!
;;
;; E.g. eval&tap-at-point the following expression:
(slurp "not-an-existing-file")


;; To be extended...
;;
;; This recipe only scratched the surface of what Portal is capable
;; of. But I hope it gave you insight in how Portal can help you debugging
;; and how to use it from deps-try's REPL.
;;
;; Missing anything from this recipe or got corrections?
;; Open a PR at https://github.com/eval/deps-try
