(ns rebel-readline.clojure.sexp
  (:require
   [borkdude.deflet :refer [deflet]]
   [clojure.string :as string]
   [rebel-readline.clojure.tokenizer :as tokenize]
   [rebel-readline.utils :as utils :refer [log]])
  (:import
   [java.util.regex Pattern]))

#_(defn position-in-range? [s pos]
  (<= 0 pos (dec (count s))))

#_(defn blank-at-position? [s pos]
  (or (not (position-in-range? s pos))
      (Character/isWhitespace (.charAt s pos))))

#_(defn non-interp-bounds [code-str]
  (map rest
       (tokenize/tag-non-interp code-str)))

#_(defn in-non-interp-bounds? [code-str pos] ;; position of insertion not before
  (or (some #(and (< (first %) pos (second %)) %)
            (non-interp-bounds code-str))
      (and (<= 0 pos (dec (count code-str)))
           (= (.charAt code-str pos) \\)
           [pos (inc pos) :character])))

(def delims #{:deref :literal-list :literal-set :bracket :brace :paren :quote})
(def openers (set (map #(->> % name (str "open-") keyword) delims)))
(def closers (set (map #(->> % name (str "close-") keyword) delims)))

(def flip-it
  (->> openers
       (map
        (juxt identity #(as-> % x
                          (name x)
                          (string/split x #"-" 2)
                          (str "close-" (second x))
                          (keyword x))))
       ((juxt identity (partial map (comp vec reverse))))
       (apply concat)
       (into {})))

(def delim-key->delim
  {:open-literal-set "#{"
   :close-literal-set \}
   :open-deref "@("
   :close-deref \)
   :open-literal-list "'("
   :close-literal-list \)
   :open-paren \(
   :close-paren \)
   :open-brace \{
   :close-brace \}
   :open-bracket \[
   :close-bracket \]
   :open-quote  \"
   :close-quote \"})

(defn scan-builder [open-test close-test]
  (fn [specific-test stack x]
    (cond
      (open-test x)
      (cons x stack)
      (close-test x)
      (cond
        (and (empty? stack) (specific-test x))
        (reduced [:finished x])
        (empty? stack) (reduced [:finished nil]) ;; found closing bracket of wrong type
        (= (-> stack first last) (flip-it (last x)))
        (rest stack)
        ;; unbalanced
        :else (reduced [:finished nil]))
      :else stack)))

(def end-scan   (scan-builder (comp openers last) (comp closers last)))
(def start-scan (scan-builder (comp closers last) (comp openers last)))

(declare in-quote?)

(defn find-open-sexp-end
  ([tokens pos]
   (find-open-sexp-end tokens pos nil))
  ([tokens pos final-delim-pred]
   (let [res (reduce
              (partial end-scan (or final-delim-pred identity))
              nil
              (drop-while
               #(<= (nth % 2) pos)
               tokens))]
     (when (= :finished (first res))
       (second res)))))

(defn find-open-sexp-ends [tokens pos]
  (when-let [[_ _ end _ :as res] (find-open-sexp-end tokens pos)]
    (cons res
          (lazy-seq
           (find-open-sexp-ends tokens end)))))

(defn find-open-sexp-start
  ([tokens pos]
   (find-open-sexp-start tokens pos nil))
  ([tokens pos final-delim-pred]
   (let [res (reduce
              (partial start-scan (or final-delim-pred identity))
              nil
              (reverse (take-while
                        #(<= (nth % 2) pos)
                        tokens)))]
     (when (= :finished (first res))
       (second res)))))

(defn find-open-sexp-starts [tokens pos]
  (when-let [[_ start _ :as res] (find-open-sexp-start tokens pos)]
    (cons res
          (lazy-seq
           (find-open-sexp-starts tokens start)))))

;; TODO :character should not be in in-quote?
(defn in-quote? [tokens pos]
  (->> tokens
       (filter #(#{:string-literal-body
                   :unterm-string-literal-body
                   :character} (last %)))
       (filter (fn [[_ start end typ]]
                 (if (= :character typ)
                   (< start pos (inc end))
                   (<= start pos end))))
       first))

#_(defn in-line-comment? [tokens pos]
  (->> tokens
       (filter #(#{:end-line-comment} (last %)))
       (filter (fn [[_ start end _]]
                 (< start pos (inc end))))
       first))

(defn search-for-line-start [s pos]
  (loop [p pos]
    (cond
      (<= p 0) 0
      (= (.charAt ^String s p) \newline)
      (inc p)
      :else (recur (dec p)))))

(defn count-leading-white-space [s] (count (re-find #"^[^\S\n]+" s)))

(defn delims-outward-from-pos [tokens pos]
  (map vector
       (find-open-sexp-starts tokens pos)
       (concat (find-open-sexp-ends tokens pos)
               (repeat nil))))

(defn resolve-alias-delim-keys [tokens]
  (deflet
    (def close-delim? #(->> % name (re-find #"^close")))
    (def open-delim? #(->> % name (re-find #"^open")))

    (:result (reduce (fn [{:keys [result opens]} [_ _ _ delim-keys :as token]]
                       (let [opens     (cond-> opens
                                         (and (keyword? delim-keys) (open-delim? delim-keys)) (conj delim-keys))
                             delim-key (if (keyword? delim-keys)
                                         delim-keys
                                         (get (set delim-keys) (flip-it (peek opens)) (first delim-keys)))
                             token     (update token 3 (constantly delim-key))
                             opens     (if (close-delim? delim-key) (some-> opens not-empty pop) opens)]
                         {:opens opens :result (conj result token)}))
                     {:result [] :opens '()} tokens))))

(comment
  (find-open-sexp-starts '(["(" 0 1 :open-paren]
                       ["[" 5 6 :open-bracket]
                       ["#{" 8 10 :open-literal-set]
                       ["\\c" 10 12 :character]
                       ["}" 12 13 :close-literal-set #_#{:close-set :close-brace}]
                       ["}" 12 13 :close-brace #_#{:close-set :close-brace}]
                       ["]" 13 14 :close-bracket]
                       [")" 14 15 :close-paren #_#{:close-paren :close-literal-list}]
                       #_[")" 14 15 :close-literal-list #_#{:close-paren :close-literal-list}]) 12)

  (find-open-sexp-starts '(["(" 0 1 :open-paren]
                           ["[" 5 6 :open-bracket]
                           ["#{" 8 10 :open-literal-set]
                           ["\\c" 10 12 :character]
                           ["}" 12 13 :close-literal-set]
                           ["}" 12 13 :close-brace]
                           ["'(" 16 18 :open-literal-list]
                           [")" 21 22 :close-literal-list]
                           [")" 21 22 :close-deref]
                           [")" 21 22 :close-paren]
                           ["]" 22 23 :close-bracket]
                           [")" 23 24 :close-literal-list]
                           [")" 23 24 :close-deref]
                           [")" 23 24 :close-paren]) 12)

(delims-outward-from-pos '(["(" 0 1 :open-paren]
                          ["[" 5 6 :open-bracket]
                          ["{" 8 10 :open-brace]
                          ["\\c" 10 12 :character]
                          ["}" 12 13 :close-brace #_#{:close-set :close-brace}]
                          ["]" 13 14 :close-bracket]
                           [")" 14 15 :close-paren #_#{:close-paren :close-literal-list}]) 12)
#_(delims-outward-from-pos
 (bar '(["(" 0 1 :open-paren]
        ["[" 5 6 :open-bracket]
        ["#{" 8 10 :open-literal-set]
        ["\\c" 10 12 :character]
        ["}" 12 13 #{:close-brace :close-literal-set}]
        ["'(" 16 18 :open-literal-list]
        [")" 21 22 #{:close-paren :close-deref :close-literal-list}]
        ["]" 22 23 :close-bracket]
        [")" 23 24 #{:close-paren :close-deref :close-literal-list}])
      ) 11)

  (resolve-alias-delim-keys
   ;; sample result of sexp/tag-sexp-traversal
   '(["(" 0 1 :open-paren]
     ["[" 5 6 :open-bracket]
     ["#{" 8 10 :open-literal-set]
     ["\\c" 10 12 :character]
     ["}" 12 13 #{:close-brace :close-literal-set}]
     ["'(" 16 18 :open-literal-list]
     [")" 21 22 #{:close-paren :close-deref :close-literal-list}]
     ["]" 22 23 :close-bracket]
     [")" 23 24 #{:close-paren :close-deref :close-literal-list}]))
  #_:end)

(defn valid-sexp-from-point [s pos]
  (let [tokens (resolve-alias-delim-keys (tokenize/tag-sexp-traversal s))
        delims (take-while
                (fn [[a b]]
                  (or (= (last a) (flip-it (last b)))
                      (nil? (last b))))
                (delims-outward-from-pos tokens pos))
        max-exist     (last (take-while some? (map second delims)))
        end           (max (nth max-exist 2 0) pos)
        need-repairs  (filter (complement second) delims)
        [_ start _ _] (first (last delims))]
    (when (not-empty delims)
      (->> need-repairs
           (map (comp delim-key->delim flip-it last first))
           (apply str (subs s start end))))))

(defn word-at-position [s pos]
  (->> (tokenize/tag-words s)
       (filter #(= :word (last %)))
       (filter #(<= (second %) pos (nth % 2)))
       first))

(defn whitespace? [c]
  (re-matches #"[\s,]+" (str c)))

(defn scan-back-from [pred s pos]
  (first (filter #(pred (.charAt s %))
                 (range (min (dec (count s)) pos) -1 -1))))

(defn first-non-whitespace-char-backwards-from [s pos]
  (scan-back-from (complement whitespace?) s pos))

(defn sexp-ending-at-position [s pos]
  (let [c (try (.charAt s pos) (catch Exception e nil))]
    (when (#{\" \) \} \]} c)
      (let [sexp-tokens (resolve-alias-delim-keys (tokenize/tag-sexp-traversal s))]
        (log ::sexp-ending-at-position :sexp-tokens sexp-tokens)
        (when-let [[_ start] (find-open-sexp-start sexp-tokens pos)]
          [(subs s start (inc pos)) start (inc pos) :sexp])))))

;; TODO support characters
(defn sexp-or-word-ending-at-position [s pos]
  (or (sexp-ending-at-position s pos)
      (word-at-position s (inc pos))))

(defn funcall-word
  "Given a string with sexps an a position into that string that
  points to an open paren, return the first token that is the function
  call word"
  [code-str open-paren-pos]
  (log ::funcall-word :code-str code-str :open-paren-pos open-paren-pos)
  (some->>
   (tokenize/tag-matches (subs code-str open-paren-pos)
                         ;; matches first word after paren
                         (Pattern/compile (str "(\\()\\s*(" tokenize/not-delimiter-exp "+)"))
                         :open-paren
                         :word)
   not-empty
   (take 2)
   ((fn [[a b]]
      (when (= a ["(" 0 1 :open-paren])
        b)))))
