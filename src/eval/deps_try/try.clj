(ns eval.deps-try.try
  (:require [clojure.main]
            [clojure.pprint :as pp]
            [clojure.repl :as clj-repl]
            [deps-try.cli :as cli]
            [eval.deps-try.ansi-escape :as ansi]
            [eval.deps-try.deps :as try-deps]
            [eval.deps-try.fs :as fs]
            [eval.deps-try.history :as history]
            [eval.deps-try.recipe :as recipe]
            [eval.deps-try.rr-service :as rebel-service]
            [eval.deps-try.util :as util]
            [rebel-readline.clojure.line-reader :as clj-line-reader]
            [rebel-readline.clojure.main :as rebel-main]
            [rebel-readline.commands :as rebel-readline]
            [rebel-readline.core :as rebel-core]
            [rebel-readline.jline-api :as api]
            [rebel-readline.tools :as rebel-tools]
            [rebel-readline.utils :refer [*debug-log*]])
  (:import [org.jline.reader LineReader]
           [org.jline.reader.impl.history DefaultHistory]))

(defn- ensure-path-exists! [path]
  (fs/create-dirs path))

(defn- ensure-file-exists! [path]
  (when-not (fs/exists? path)
    (fs/create-file path)))

(defn- warm-up-completion-cache! []
  (clj-line-reader/-complete {:rebel-readline.service/type ::rebel-service/service} "nil" {}))


(defmethod rebel-readline/command-doc :deps/ls [_]
  (str "Show available deps"))

(defmethod rebel-readline/command :deps/ls [_]
  (println (try-deps/fmt-deps-available)))

(defmethod rebel-readline/command-doc :deps/try [_]
  (str "Add dependencies (e.g. `:deps/try metosin/malli`)"))

(defmethod rebel-readline/command :deps/try [[_ & args]]
  (if (seq args)
    (let [{:keys [deps error]} (try-deps/parse-dep-args {:deps (map str args)})]
      (if-not error
        (do ((requiring-resolve 'clojure.repl.deps/add-libs) deps)
            (warm-up-completion-cache!))
        (rebel-tools/display-error error)))
    (rebel-tools/display-warning
     "Usage: :deps/try metosin/malli \"0.9.2\" https://github.com/user/project some-ref \"~/some/project\"")))


(defmethod rebel-readline/command-doc :recipe/help [_]
  "Print documentation about the current recipe and how to use them.")

(defmethod rebel-readline/command :recipe/help [_]
  (println "Helping hand"))

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


(def ^:private jvmti-stop-thread
  ;; Resolved eagerly (see `:init` in `-main`): `requiring-resolve` takes
  ;; Clojure's require-lock, which the eval being interrupted may itself hold
  ;; (e.g. Ctrl-C during a hung require), deadlocking the break handler.
  (delay (requiring-resolve 'eval.deps-try.util.jvmti/stop-thread)))

(defn break-thread!
  "Forcibly stop `thread`. Called from the SIGINT-handler thread; public as
  it's referenced from an eval'ed form (see `handle-sigint-form`)."
  [^Thread thread]
  (try
    (if (<= util/java-version 19)
      (.stop thread)
      (@jvmti-stop-thread thread))
    (catch Throwable e
      (println (str "Break failed: " (ex-message e))))))

;; SOURCE https://github.com/bhauman/rebel-readline/pull/199
(defn- handle-sigint-form
  []
  `(let [thread# (Thread/currentThread)]
     (clj-repl/set-break-handler!
      (fn [_signal#]
        (break-thread! thread#)))))

(defn- recipe-instructions [{:keys [ns-only]}]
  (str "Recipe" (when ns-only " namespace") " successfully loaded in the REPL-history. Press arrow-up to start with the first step."))


;; terminel
;;  line-reader
;;    service

(defn- repl-help-message [{:keys [version]}]
  (str (ansi/wrap ansi/bold "🩴 Version: ") version \newline
       (ansi/wrap ansi/bold "🏡 Home: ") "https://github.com/eval/deps-try" \newline
       (ansi/wrap ansi/bold "🐻‍❄️ Perks / sponsor: ") "https://polar.sh/eval/deps-try" \newline
       (ansi/wrap ansi/bold "🆘 Help: ")
       "Type " (ansi/wrap ansi/fg-cyan-b ":repl/help") \newline

       \newline
       (ansi/wrap ansi/bold "🧺 Available libraries: ") \newline
       (try-deps/fmt-deps-available)))

(defn repl [{:deps-try/keys [data-path recipe version] :as opts}]
  (rebel-core/with-line-reader
    (let [history-file (doto (fs/path data-path "history")
                         (ensure-file-exists!))]
      (doto (clj-line-reader/create
             (rebel-service/create {:data-path data-path}))
        (.setVariable LineReader/SECONDARY_PROMPT_PATTERN "%P ")
        (.setVariable LineReader/FEATURES_MAX_BUFFER_SIZE 2000)
        (.setVariable LineReader/HISTORY_SIZE "10000")
        (.setVariable LineReader/HISTORY_FILE (str history-file))
        (#(.setHistory % (history/make-history {:history-file     history-file
                                                :seed-items       (cond->> (:steps recipe)
                                                                    (:ns-only recipe) (take 1))
                                                :writable-history (DefaultHistory. %)})))))
    ;; repl time:
    (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
      (when-let [prompt-fn (:prompt opts)]
        (swap! api/*line-reader* assoc :prompt prompt-fn))
      (when recipe
        (swap! api/*line-reader* assoc :deps-try/recipe recipe)
        (rebel-tools/display-warning (recipe-instructions recipe)))
      (println (repl-help-message {:version version}))
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

(defn- interrupted?
  "Returns true if the given throwable was ultimately caused by the break
  handler stopping the thread (see `break-thread!`).
  The ThreadDeath arrives bare when the eval was interrupted at runtime, or
  wrapped in a CompilerException (where `clojure.main/root-cause` stops
  unwrapping) when interrupted during compilation.
  SOURCE `nrepl.middleware.interruptible-eval/interrupted?`"
  [^Throwable ex]
  (or (instance? ThreadDeath (clojure.main/root-cause ex))
      (and (instance? clojure.lang.Compiler$CompilerException ex)
           (instance? ThreadDeath (.getCause ex)))))

(defn- reset-just-caught []
  `(swap! api/*line-reader* dissoc :repl/just-caught))

(defn -main [& args]
  #_(prn ::args args)
  (let [opts      (cli/parse-opts args {:restrict [:recipe :recipe-ns :prepare :version]
                                        :spec     {:version {}
                                                   :recipe  {}
                                                   :prepare {:alias :P}}})
        data-path (fs/xdg-data-home "deps-try")]
    (ensure-path-exists! data-path)
    (if (:prepare opts)
      (System/exit 0)
      (binding [*debug-log* false] ;; via --debug flag?
        (rebel-core/ensure-terminal
         (let [recipe-path ((some-fn :recipe :recipe-ns) opts)
               repl-opts   (cond-> {:deps-try/version (:version opts)
                                    :deps-try/data-path data-path
                                    :caught             (fn [ex]
                                                          (when-not (interrupted? ex)
                                                            (persist-just-caught ex)
                                                            (clojure.main/repl-caught ex)))
                                    :init               (fn []
                                                          (load-slow-deps!)
                                                          (when (>= util/java-version 20)
                                                            ;; see comment at jvmti-stop-thread
                                                            (try @jvmti-stop-thread (catch Throwable _)))
                                                          (apply require clojure.main/repl-requires)
                                                          (set! clojure.core/*print-namespace-maps* false))
                                    :eval               (fn [form]
                                                          (eval `(do ~(handle-sigint-form)
                                                                     ~(reset-just-caught)
                                                                     ~form)))
                                    :print              syntax-highlight-pprint}
                             recipe-path (assoc :deps-try/recipe
                                                (let [recipe (recipe/parse recipe-path)]
                                                  (assoc recipe :ns-only (:recipe-ns opts)))))]
           (repl repl-opts)))
        (System/exit 0)))))

(comment

  (recipe/parse "/Users/gert/projects/deps-try/deps-try/recipes/next_jdbc/postgresql.clj")

  #_:end)
