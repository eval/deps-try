(ns deps-try.repl
  (:require [rebel-readline.clojure.main :as rebel-main]
            [rebel-readline.jline-api :as api]
            [rebel-readline.clojure.line-reader :as clj-line-reader]
            [clojure.pprint :as pp]
            [rebel-readline.core :as rebel-core]))


;; SOURCE https://github.com/bhauman/rebel-readline/issues/151#issuecomment-457631846
(defn ^{:author "Dominic Monroe (SevereOverfl0w)"}
  syntax-highlight-pprint
  "Print a syntax highlighted clojure value.

  This printer respects the current color settings set in the
  service.

  The `rebel-readline.jline-api/*line-reader*` and
  `rebel-readline.jline-api/*service*` dynamic vars have to be set for
  this to work."
  [x]
  (binding [*out* (.. api/*line-reader* getTerminal writer)]
    (try
      (print (api/->ansi (clj-line-reader/highlight-clj-str (with-out-str (pp/pprint x)))))
      (catch java.lang.StackOverflowError e
        (pp/pprint x)))))
