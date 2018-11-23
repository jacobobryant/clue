(ns clue.util)

(defn map-from
  [f xs]
  (into {} (for [x xs] [x (f x)])))

(defn dissoc-by [m f]
  (into {} (remove (comp f second) m)))

(defn map-inverse [m]
  (reduce
    (fn [inverse [k v]]
      (update inverse v
              #(if (nil? %)
                 #{k}
                 (conj % k))))
    {}
    m))

(defn conj-some [coll x]
  (cond-> coll
    x (conj x)))

(defn split-by [f coll]
  (reduce
    #(update %1 (if (f %2) 0 1) conj %2)
    [nil nil]
    coll))

; Do this with algo.generic
(defn cop [op & cs]
  (char (apply op (map int cs))))

(defn c+ [& cs]
  (apply cop + cs))

(defn c- [& cs]
  (apply cop - cs))

(defn parse-int [s]
  (when (re-matches #"\d+" s)
    (Integer/parseInt s)))
