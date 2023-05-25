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
  [v pred]
  (when (pred v) v))
