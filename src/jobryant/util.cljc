(ns jobryant.util
  #?(:cljs (:require-macros jobryant.util)))

#?(:clj (do

(defmacro capture [& xs]
  `(do ~@(for [x xs] `(def ~x ~x))))

(defmacro condas->
  "Combination of as-> and cond->."
  [expr name & clauses]
  (assert (even? (count clauses)))
  `(as-> ~expr ~name
     ~@(map (fn [[test step]] `(if ~test ~step ~name))
            (partition 2 clauses))))

(defmacro pullall [ns]
  `(do ~@(for [[sym var] (ns-publics ns)]
           `(def ~sym ~var))))

(defmacro cljs-pullall [ns & syms]
  `(do ~@(for [s syms]
           `(def ~s ~(symbol (str ns) (str s))))))

(defmacro foo [ns]
  (pr-str (ns-publics ns)))

; todo move instead of copy
(defn move [src dest]
  (spit dest (slurp src)))

(defn manhattand [a b]
  (->> (map - a b)
       (map #(Math/abs %))
       (apply +)))

(defn parse-int [s]
  (when (re-matches #"\d+" s)
    (Integer/parseInt s)))

))

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

(defn merge-some [& ms]
  (reduce
    (fn [m m']
      (let [[some-keys nil-keys] (split-by (comp some? m') (keys m'))]
        (as-> m x
          (merge x (select-keys m' some-keys))
          (apply dissoc x nil-keys))))
    ms))

; Do this with algo.generic
(defn cop [op & cs]
  (char (apply op (map int cs))))

(defn c+ [& cs]
  (apply cop + cs))

(defn c- [& cs]
  (apply cop - cs))

(defn zip [xs]
  (apply map vector xs))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))
