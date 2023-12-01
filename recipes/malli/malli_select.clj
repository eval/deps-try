;; Use this file with deps-try:
;; $ deps-try --recipe malli/malli-select
(ns recipes.malli.malli-select
  "Introduction to malli-select, a library for spec2-inspired selection of Malli-schemas."
  {:deps-try.recipe/deps ["dk.thinkcreate/malli-select"  "metosin/malli"]}
  (:require [malli-select.core :as ms]
            [malli.core :as m :refer [form] :rename {form p}]
            [malli.generator :as mg]))

;; Introduction
;;
;; malli-select (https://github.com/eval/malli-select) is a library that allows you
;; to create Malli-schemas from existing schemas by selecting which map-attributes
;; are required and which are optional.
;;
;; It's based on what Rich Hickey presented in his talk "Maybe Not" (link in last step)
;; about how a future version of Clojure's spec-library might allow for schema reuse via selections.
;;
;; A big motivation to make it easy to extract schemas from other schemas is to prevent
;; a proliferation of (incompatible) schemas: a person-schema in one part of the application
;; requires a map with a :name, and another schema calls it :first-name; they could have
;; been describing the same 'thing' but that data is now labelled differently.
;;
;; So instead of defining different schemas for different contexts (e.g. a person entering
;; a chat vs someone ordering a product), we take one schema describing the full shape
;; of an entity and derive subschemas from it for validation of data in specific contexts.
;;
;; This recipe will walk you through creating subschemas via selections.
;;


;; Let's say a person in it's totality looks like this:
(def Person
  [:map
   [:name string?]
   [:email string?]
   [:addresses [:vector
                [:map
                 [:street string?]
                 [:zip string?]
                 [:country
                  [:map
                   [:name string?]
                   [:iso string?]]]]]]])


;; To create a schema that requires both name and email, we'll use the following selection:
(def s (ms/select Person [:name :email]))
(p s)


;; As you can see from the prettified form: all attributes except :name and :email are optional.
;; Try some of the following expressions (using 'eval-at-point') to see what
;; maps are considered valid using schema s.
;;
;; NOTE to eval-at-point: place the cursor behind an expression and press
;; Control-x Control-e to evaluate it.
;; Press Control-x Control-m to submit all the expressions and proceed to the next step.
(m/validate s {:name "Gert" :email "foo@bar.org"})
(m/validate s {:name nil :email "foo@bar.org"})
(m/explain s {:name nil :email "foo@bar.org"})
(m/validate s {:name "Gert" :email false})
(m/explain s {:name "Gert" :email false})


;; To require attributes from aggregates (e.g. addresses or country), we'd use the following notation:
(def s (ms/select Person [{:addresses [:street]}]))
(p s)


;; The resulting schema will validate a map in the following way:
;; Any address in the addresses-collection must contain a :street.
;; The addresses-key may be absent though.
;;
;; That the shape of the addresses-value is a collection of maps (and not just a map)
;; does not matter for our selection:
;; attributes in selections always apply to attributes of map-schemas.
;;
;; Test it yourself (again using eval-at-point) with some data:
(m/validate s {:name "Foo"})
(m/validate s {:name "Foo" :addresses []})
(m/validate s {:name "Foo" :addresses [{}]})
(m/explain s {:name "Foo" :addresses [{}]})


;; Of course when we require persons to have addresses, we can combine requiredness and optionality.
;;
;; Given the person-schema try to come up with a selection that validates a map like so:
;; - it must contain an addresses-key
;; - an address must contain a country-key
;; - a country must contain an iso-key
;;
;; NOTE don't worry about mistyping attributes in a selection: it will show you a
;; nice error message.
(def s (ms/select Person ['???]))

(and (not (m/validate s {}))
     (not (m/validate s {:addresses [{}]}))
     (not (m/validate s {:addresses [{:country {}}]}))
     (m/validate s {:addresses [{:country {:iso "DK"}}]}))


;; Star-selections
;;
;; In order to mark all attributes of a map-schema as required, we can use '* like so:
(ms/select Person ['*])

;; It's not recursive though, so requiring all address-attributes has no impact on
;; the requiredness of an address' country-attributes.
;;
;; NOTE A risk with star-selections is that when we extend the Person-schema
;; (e.g. add an :age-attribute), existing code might start to fail as it's now stricter
;; in what data it accepts.
;; Explicit selections may be better than implicit ones.
;;
;; Evaluate the following def-expression and see what a star-selection means for
;; aggregates: what is the 'minimal' country that would be considered valid in an address.
(def s (ms/select Person [{:addresses ['*]}]))
(p s)
(m/explain s {:addresses [{:street "foo" :zip "1234" :country "change-me"}]})


;; Data generation
;;
;; Malli allows us to generate data from a schema.
;; This is ideal in test-scenarios when we need person-like data.
;;
;; Eval the following expressions a couple of times to see the differences in
;; data being generated:
(mg/generate [:map [:name string?]])
(mg/generate (ms/select Person [:name]))
(mg/generate (ms/select Person))          ;; the empty-selection


;; Pruning optionals
;;
;; While optional attributes are helpful when validating data (i.e. any 'extra'
;; person-data is automatically validated), it can become quite noisy
;; when generating data.
;;
;; Use the option `prune-optionals` to prevent this:
(def s (ms/select Person [:name] {:prune-optionals true}))
(p s)


;; More descriptive is to add it as metadata to the selection:
(def s (ms/select Person ^:only [:name]))
(p s)


;; The generated data is now more succinct:
(mg/generate (ms/select Person ^:only [:name]))


;; Selectors
;;
;; Finally, if you plan on doing multiple selections from one schema and speed is a concern,
;; then using a selector is more performant:
(def selector (ms/selector Person))

(p (selector [:name]))
(p (selector ^:only [{:addresses [:zip]}]))


;; Fin!
;;
;; Check out the talk by Rich Hickey "Maybe Not" (https://youtu.be/YR5WdGrpoug?feature=shared&t=1965)
;; in which he explains the motivation for select-ing subschemas.
;;
;; Missing anything from this recipe or got corrections?
;; Open a PR at https://github.com/eval/deps-try
