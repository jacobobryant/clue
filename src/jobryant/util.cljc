(ns jobryant.util
  (:require [clojure.walk :refer [postwalk]])
  #?(:cljs (:require-macros jobryant.util)))

#?(:clj (do

(defmacro capture [& xs]
  `(do ~@(for [x xs] `(def ~x ~x))))

(defmacro forv [& body]
  `(vec (for ~@body)))

(defmacro condas->
  "Combination of as-> and cond->."
  [expr name & clauses]
  (assert (even? (count clauses)))
  `(as-> ~expr ~name
     ~@(map (fn [[test step]] `(if ~test ~step ~name))
            (partition 2 clauses))))

(defn pred-> [x f g]
  (if (f x) (g x) x))

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

(defn assoc-some [m k v]
  (cond-> m (some? v) (assoc k v)))

(defn split-by [f coll]
  (reduce
    #(update %1 (if (f %2) 0 1) conj %2)
    [nil nil]
    coll))

(defn deep-merge [& ms]
  (apply
    merge-with
    (fn [x y]
      (cond (map? y) (deep-merge x y)
            :else y))
    ms))

(defn remove-nil-empty [m]
  (into {} (remove (fn [[k v]]
                     (or (nil? v)
                         (and (coll? v) (empty? v)))) m)))

(defn remove-nils [m]
  (into {} (remove (comp nil? second) m)))

(defn deep-merge-some [& ms]
  (postwalk (fn [x]
              (if (map? x)
                (remove-nil-empty x)
                x))
            (apply deep-merge ms)))

(defn merge-some [& ms]
  (reduce
    (fn [m m']
      (let [[some-keys nil-keys] (split-by (comp some? m') (keys m'))]
        (as-> m x
          (merge x (select-keys m' some-keys))
          (apply dissoc x nil-keys))))
    ms))

#?(:cljs
(def char->int (into {} (map #(vector (char %) %) (range 256))))
)

(defn ord [c]
  (#?(:clj int :cljs char->int) c))

(defn parse-int [s]
  (#?(:clj Integer/parseInt :cljs js/parseInt) s))

; Do this with algo.generic
(defn cop [op & cs]
  (char (apply op (map ord cs))))

(defn c+ [& cs]
  (apply cop + cs))

(defn c- [& cs]
  (apply cop - cs))

(defn zip [xs]
  (apply map vector xs))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))
