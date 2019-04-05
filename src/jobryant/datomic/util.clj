(ns jobryant.datomic.util
  (:require [clojure.string :as str]
            [datomic.api :as d]
            [clojure.pprint :refer [pprint]]))

(defn- expand-flags [xs]
  (->> xs
       (map (fn [x]
              (condp #(str/starts-with? %2 %1) (str x)
                ":db.type/"        [:db/valueType x]
                ":db.cardinality/" [:db/cardinality x]
                ":db.unique/"      [:db/unique x]
                ":db/isComponent"  [:db/isComponent true]
                [:db/doc x])))
       (into {})))

(defn expand-schema [schema]
  (->> schema
       (map (fn [[k v]] [k (expand-flags v)]))
       (map (fn [[k v]]
              (cond
                (empty? v) {:db/ident k}
                :else      (merge {:db/ident k
                                   :db/cardinality :db.cardinality/one}
                                  v))))))

(defn exists?
  ([db eid]
   (boolean (d/q '[:find ?e . :in $ ?e :where [?e]] db eid)))
  ([db attr value]
   (boolean (d/q '[:find ?e . :in $ ?a ?v :where [?e ?a ?v]] db attr value))))

(defn ref? [db attr]
  (= :db.type/ref (:value-type (d/attribute db attr))))

(defn expand-tx-fns [ns-sym]
  (for [[fnsym fnvar] (ns-publics ns-sym)
        :let [params (-> fnvar meta :arglists first)]]
    {:db/ident (-> fnvar symbol keyword)
     :db/fn (d/function
              {:lang "clojure"
               :params params
               :requires ['[datomic.api :as d] [ns-sym]]
               :code `(~(symbol fnvar) ~@params)})}))
