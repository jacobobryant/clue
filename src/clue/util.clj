(ns clue.util
  (:require [clojure.spec.alpha :as s]))

(defmacro sdefn [fname arglist post-spec & body]
  (assert even? (count arglist))
  (let [arglist# (partition 2 arglist)]
    (list*
      'defn fname
      (vec (map first arglist#))
      {:pre (vec (map (fn [[argname argspec]]
                        (list `s/valid? argspec argname)) arglist#))
       :post [(list `s/valid? post-spec '%)]}
      body)))

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

(defn cop [op & cs]
  (char (apply op (map int cs))))

(defn c+ [& cs]
  (apply cop + cs))

(defn c- [& cs]
  (apply cop - cs))

(defn parse-int [s]
  (when (re-matches #"\d+" s)
    (Integer/parseInt s)))
