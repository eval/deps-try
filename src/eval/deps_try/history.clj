(ns eval.deps-try.history
  "Responsible for creating a history object."
  (:require [babashka.fs :as fs]
            [clojure.string :as string])
  (:import [java.time Instant]
           [org.jline.reader History History$Entry]))

(defn- create-entry ^History$Entry [index line]
  (let [time (.toEpochMilli (Instant/now))]
    (reify History$Entry
      (index [_this] index)
      (time [_this] time)
      (line [_this] line)
      (toString [_this]
        (format "%d: %s" index line)))))

(defn- history
  "Options:
  - `items` - history items, ordered as in a history file.
  - `seed-items` - ordered as in a file. Will be consumed upon matching eval-ed lines."
  [{:keys [items seed-items writable-history]}]
  (let [!history-items     (atom (vec items))
        !seed-items        (atom (vec seed-items))
        !index             (atom 0)
        !next-ix           (atom 0)
        next-ix            #(dec (swap! !next-ix inc))
        !items             (atom [])
        generate-items!    (fn []
                             (reset! !next-ix 0)
                             (reset! !items
                                     (-> []
                                         (into (map #(create-entry (next-ix) %) @!history-items))
                                         (into (map #(create-entry (next-ix) %) (reverse @!seed-items)))))
                             (reset! !index (count @!items)))
        line-matches-item? (fn [line item]
                             (let [first-line #(-> % string/split-lines first)]
                               (= (first-line line) (first-line item))))
        comment-only-line? (fn [line]
                             (->> line
                                  string/split-lines
                                  (remove #(re-find #"^;+" %))
                                  empty?))

        ;; consumes all seed-items up til the one matching `line`
        possibly-consume-seed-items!
        (fn [line]
          (when-let [line-ix (-> @!seed-items
                                 #_(doto prn)
                                 (->> (map-indexed (fn [ix item]
                                                     (and (line-matches-item? line item) ix))))
                                 #_(doto prn)
                                 (->> (remove false?))
                                 first)]
            (swap! !seed-items subvec (inc line-ix))))]
    (generate-items!)
    (reify History
      (first [_this])
      (attach [_this _reader])
      (load [_this])
      (save [_this])
      (purge [_this])
      (size [_this]
        (count @!items))
      (index [_this]
        @!index)
      (last [this]
        (dec (.size this)))
      (get [_this index]
        (.line (get @!items index)))
      (add [_this time line]
        (when-not (comment-only-line? line)
          (swap! !history-items conj line)
          (.add writable-history time line))
        (possibly-consume-seed-items! line)
        (generate-items!))
      (iterator [_this index]
        (.listIterator @!items index))
      (current [this]
        (if (>= @!index (.size this))
          ""
          (.get this @!index)))
      (previous [this]
        (.moveTo this (dec (.index this))))
      (next [this]
        (.moveTo this (inc (.index this))))
      (moveToFirst [this]
        (.moveTo this 0))
      (moveToLast [this]
        (.moveTo this (dec (.size this))))
      (moveTo [this index]
        (if (and (< index (.size this))
                 (<= 0 index)
                 (not= index (.index this)))
          (do (reset! !index index)
              true)
          false))
      (moveToEnd [this]
        (reset! !index (.size this))))))

(defn- history-items
  "Extract items from a history file."
  [history-file]
  (let [unescape-cmd  #(-> %
                           (string/replace #"\\n" "\n")
                           (string/replace #"\\r" "\r"))]
    (map #(unescape-cmd (second (string/split % #":" 2))) (fs/read-all-lines history-file))))

(defn make-history
  "Extracts all commands from `history-file` and yields a seeded History instance."
  [{:keys [history-file seed-items writable-history]}]
  (let [hist-items (history-items history-file)]
    (history {:items            hist-items
              :seed-items       seed-items
              :writable-history writable-history})))
