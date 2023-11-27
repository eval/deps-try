(ns eval.deps-try.try
  (:require [clojure.main]
            [clojure.pprint :as pp]
            [clojure.repl :as clj-repl]
            [eval.deps-try.deps :as try-deps]
            [eval.deps-try.history :as history]
            [eval.deps-try.recipe :as recipe]
            [eval.deps-try.rr-service :as rebel-service]
            [rebel-readline.clojure.line-reader :as clj-line-reader]
            [rebel-readline.clojure.main :as rebel-main]
            [rebel-readline.commands :as rebel-readline]
            [rebel-readline.core :as rebel-core]
            [rebel-readline.jline-api :as api]
            [rebel-readline.tools :as rebel-tools]
            [rebel-readline.utils :refer [*debug-log*]])
  (:import [org.jline.reader LineReader]
           [org.jline.reader.impl.history DefaultHistory]))

(require '[babashka.fs :as fs] :reload)

(defn- ensure-path-exists! [path]
  (fs/create-dirs path))

(defn- ensure-file-exists! [path]
  (when-not (fs/exists? path)
    (fs/create-file path)))

(defn- warm-up-completion-cache! []
  (clj-line-reader/-complete {:rebel-readline.service/type ::rebel-service/service} "nil" {}))

(defmethod rebel-readline/command-doc :deps/try [_]
  (str "Add dependencies (e.g. `:deps/try metosin/malli`)"))


(defmethod rebel-readline/command :deps/try [[_ & args]]
  (if (seq args)
    (let [{:keys [deps error]} (try-deps/parse-dep-args {:deps (map str args)})]
      (if-not error
        (do ((requiring-resolve 'clojure.repl.deps/add-libs) deps)
            (warm-up-completion-cache!))
        (rebel-tools/display-error error)))
    (rebel-tools/display-warning "Usage: :deps/try metosin/malli \"0.9.2\" https://github.com/user/project some-ref \"~/some/project\"")))


(defmethod rebel-readline/command-doc :recipe/help [_]
  "Print documentation about the current recipe and how to use them.")

(defmethod rebel-readline/command :recipe/help [_]
  (println "Helping"))

(defmethod rebel-readline/command-doc :recipe/quit [_]
  "Remove any remaining recipe steps from the REPL-history.")

(defn- quit-recipe! []
  (let [history-file (.getVariable api/*line-reader* LineReader/HISTORY_FILE)]
    (.setHistory api/*line-reader*
                 (history/make-history {:history-file     history-file
                                        :writable-history (DefaultHistory. api/*line-reader*)}))
    (swap! api/*line-reader* dissoc :deps-try/recipe)
    (rebel-tools/display-warning "Recipe quit.")))

(defmethod rebel-readline/command :recipe/quit [_]
  (if (:deps-try/recipe @api/*line-reader*)
    (quit-recipe!)
    (rebel-tools/display-warning "No active recipe.")))


(defmethod rebel-readline/command-doc :clojure/toggle-print-meta [_]
  (let [current (if clojure.core/*print-meta* "on" "off")]
    (str "Toggle clojure.core/*print-meta* on and off (" current ")")))

(defmethod rebel-readline/command :clojure/toggle-print-meta [[_]]
  (set! clojure.core/*print-meta* (not clojure.core/*print-meta*)))


(defmethod rebel-readline/command-doc :clojure/toggle-print-namespace-maps [_]
  (let [current (if clojure.core/*print-namespace-maps* "on" "off")]
    (str "Toggle clojure.core/*print-namespace-maps* on and off (" current ")")))

(defmethod rebel-readline/command :clojure/toggle-print-namespace-maps [[_]]
  (set! clojure.core/*print-namespace-maps* (not clojure.core/*print-namespace-maps*)))


;; SOURCE https://github.com/bhauman/rebel-readline/issues/151#issuecomment-457631846
(defn- ^{:author "Dominic Monroe (SevereOverfl0w)"}
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

(defn- recipe-instructions [recipe]
  "Recipe successfully loaded in the REPL-history. Type :recipe/help for help.")

;; terminel
;;  line-reader
;;    service

(defn repl [{:deps-try/keys [data-path recipe] :as opts}]
  (rebel-core/with-line-reader
    (let [history-file (doto (fs/path data-path "history")
                         (ensure-file-exists!))]
      (doto (clj-line-reader/create
             (rebel-service/create {:data-path data-path}))
        (.setVariable LineReader/SECONDARY_PROMPT_PATTERN "%P ")
        (.setVariable LineReader/HISTORY_SIZE "10000")
        (.setVariable LineReader/HISTORY_FILE (str history-file))
        (#(.setHistory % (history/make-history {:history-file     history-file
                                                :seed-items       (:steps recipe)
                                                :writable-history (DefaultHistory. %)})))))
    ;; repl time:
    (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
      (when-let [prompt-fn (:prompt opts)]
        (swap! api/*line-reader* assoc :prompt prompt-fn))
      (when recipe
        (swap! api/*line-reader* assoc :deps-try/recipe recipe)
        (rebel-tools/display-warning (recipe-instructions recipe)))
      (println (rebel-core/help-message))
      (apply
       clojure.main/repl
       (-> {:print rebel-main/syntax-highlight-prn
            :read  (rebel-main/create-repl-read)}
           (merge opts {:prompt (fn [])})
           seq
           flatten)))))

(defn- load-slow-deps! []
  (require 'cljfmt.core)
  (warm-up-completion-cache!))

(defn- persist-just-caught
  "This is needed for tap-at-point to tap an exception that just occurred."
  [ex]
  (swap! api/*line-reader* assoc :repl/just-caught ex))

(defn- reset-just-caught []
  `(swap! api/*line-reader* dissoc :repl/just-caught))

(defn -main [& args]
  ;; via --debug flag?
  #_(prn ::args args)
  (binding [*debug-log* false]
    (let [data-path (fs/xdg-data-home "deps-try")]
      (ensure-path-exists! data-path)
      (rebel-core/ensure-terminal
       (let [repl-opts (cond-> {:deps-try/data-path data-path
                                #_#_:prompt (fn [] (println (str *ns* "=>"))) ;; when prompt is too deep
                                :caught             (fn [ex]
                                                      (persist-just-caught ex)
                                                      (clojure.main/repl-caught ex))
                                :init               (fn []
                                                      (load-slow-deps!)
                                                      (apply require clojure.main/repl-requires)
                                                      (set! clojure.core/*print-namespace-maps* false))
                                :eval               (fn [form]
                                                      (eval `(do ~(handle-sigint-form)
                                                                 ~(reset-just-caught)
                                                                 ~form)))
                                :print              syntax-highlight-pprint}
                         (second args) (assoc :deps-try/recipe (recipe/parse (second args))))]
         (repl repl-opts)))
      (System/exit 0))))
