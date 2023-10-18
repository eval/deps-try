(ns eval.deps-try.util)

(defmacro  pred->
  "When expr satisfies pred, threads it into the first form (via ->),
  and when that result satisfies pred, through the next etc.
  Example:
  (let [{err :error res :result} (pred-> :error args
                                (parse-args))]
    ,,,)"
  ^{:author "Sean Corfield"
    :source "https://ask.clojure.org/index.php/12272/would-a-generalized-some-macro-be-useful?show=12272#q12272"}
  [pred expr & forms]
  (let [g (gensym)
        p pred
        steps (map (fn [step] `(if (~p ~g) (-> ~g ~step) ~g))
                   forms)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))


(defn when-pred
  ^{:author "Sergey Trofimov"
    :source "https://ask.clojure.org/index.php/8945/something-like-when-pred-in-the-core"}
  [pred v]
  (when (pred v) v))


(require '[babashka.http-client :as http] :reload)

(defn url-test [url {:keys [timeout include-body] :or {timeout 1000 include-body false}}]
  (try
    (let [http-fn                     (if include-body #'http/get #'http/head)
          {status :status body :body} (http-fn url {:throw false :timeout timeout})
          status                      (cond
                                        (< 199 status 400) :found
                                        (< 399 status 500) :not-found
                                        :else              :unavailable)]
      (cond-> {:status status}
        include-body (assoc :body body)))
    (catch java.io.IOException _
      {:status :offline})
    (catch java.net.SocketException _
      {:status :offline})
    #_(catch java.nio.channels.UnresolvedAddressException _
      {:status :unknown})
    (catch java.net.ConnectException _
      {:status :offline})
    (catch java.net.http.HttpTimeoutException _
      {:status :unavailable})
    ;; TODO needed? not available in bb
    #_(catch java.net.http.HttpConnectTimeoutException _
      {:status :unavailable})))


(defn multi-url-test
  "Yields result of `url-test` for first found url. Shortcuts when offline."
  [urls options]
  (let [result (atom nil)
        stop?  #(let [{:keys [status]} @result]
                  (#{:offline :found} status))]
    (doseq [url    urls
            :while (not (stop?))]
      (reset! result (url-test url options)))
    @result))
