(ns clue.util)

(defn map-from
  [f xs]
  (into {} (for [x xs] [x (f x)])))

(defn dissoc-by [m f]
  (into {} (remove f m)))

(defn map-inverse [m]
  (reduce
    (fn [inverse [k v]]
      (update inverse v
              #(if (nil? %)
                 #{k}
                 (conj % k))))
    {}
    m))

(defn times [n f]
  (doall (for [_ (range n)] (f))))

(defn conj-some [coll x]
  (cond-> coll
    x (conj x)))

(defn split-by [f coll]
  (reduce
    #(update %1 (if (f %2) 0 1) conj %2)
    [nil nil]
    coll))

; TODO define + and - for characters if possible
(defn cop [op & cs]
  (char (apply op (map int cs))))

(defn cadd [& cs]
  (apply cop + cs))

(defn cminus [& cs]
  (apply cop - cs))
