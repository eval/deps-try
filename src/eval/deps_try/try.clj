(ns eval.deps-try.try
  (:require #_[clojure.tools.deps.alpha.repl :as deps-repl]
            [clojure.java.io :as jio]
            [clojure.main]
            [clojure.pprint :as pp]
            [clojure.repl :as clj-repl]
            [eval.deps-try.deps :as try-deps]
            [rebel-readline.clojure.line-reader :as clj-line-reader]
            [rebel-readline.clojure.main :as rebel-main]
            [rebel-readline.clojure.service.local :as rebel-service]
            [rebel-readline.commands :as rebel-readline]
            [rebel-readline.core :as rebel-core]
            [rebel-readline.jline-api :as api]
            [rebel-readline.utils :refer [*debug-log*]])
  (:import [org.jline.reader LineReader]))


(defmethod rebel-readline/command-doc :deps/try [_]
  (str "Add dependencies (e.g. `:deps/try tick/tick`)"))


(defmethod rebel-readline/command :deps/try [[_ & args]]
  (if (seq args)
    ((requiring-resolve 'clojure.tools.deps.alpha.repl/add-libs)
     (try-deps/parse-dep-args (map str args)))
    (println "Usage: `:deps/try tick/tick 0.5.0 https://github.com/user/project`")))

(defmethod rebel-readline/command-doc :clojure/toggle-print-meta [_]
  (str "Toggle clojure.core/*print-meta* on and off ("
       (if clojure.core/*print-meta* "on" "off") ")"))

(defmethod rebel-readline/command :clojure/toggle-print-meta [[_]]
  (set! clojure.core/*print-meta* (not clojure.core/*print-meta*)))


(defmethod rebel-readline/command-doc :clojure/toggle-print-namespace-maps [_]
  (str "Toggle clojure.core/*print-namespace-maps* on and off ("
       (if clojure.core/*print-namespace-maps* "on" "off") ")"))

(defmethod rebel-readline/command :clojure/toggle-print-namespace-maps [[_]]
  (set! clojure.core/*print-namespace-maps* (not clojure.core/*print-namespace-maps*)))


(comment
  ((requiring-resolve 'clojure.tools.deps.alpha.repl/add-libs) (try-deps/parse-dep-args '("tick/tick"))))


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
      (catch java.lang.StackOverflowError _
        (pp/pprint x)))))


;; SOURCE https://github.com/bhauman/rebel-readline/pull/199
(defn- handle-sigint-form
  []
  `(let [thread# (Thread/currentThread)]
     (clj-repl/set-break-handler! (fn [_signal#] (.stop thread#)))))


(defn repl [opts]
  (rebel-core/with-line-reader
    ;; TODO respect $XDG_HOME
    (let [history-file (str (jio/file (System/getProperty "user.home") ".deps-try" "history"))]
      (doto (clj-line-reader/create
             (rebel-service/create
              (when api/*line-reader* @api/*line-reader*)))
        (.setVariable LineReader/HISTORY_FILE history-file)))
    ;; repl time:
    (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
      (when-let [prompt-fn (:prompt opts)]
        (swap! api/*line-reader* assoc :prompt prompt-fn))
      (println (rebel-core/help-message))
      (apply
       clojure.main/repl
       (-> {:print rebel-main/syntax-highlight-prn
            :read (rebel-main/create-repl-read)}
           (merge opts {:prompt (fn [])})
           seq
           flatten)))))

;; terminel
;;  line-reader
;;   service

(defn -main []
  ;; via flag?
  #_(binding [*debug-log* true])
  (rebel-core/ensure-terminal (repl {:init (fn []
                                             (apply require clojure.main/repl-requires))
                                     :eval (fn [form]
                                             (eval `(do ~(handle-sigint-form) ~form)))
                                     :print syntax-highlight-pprint})))