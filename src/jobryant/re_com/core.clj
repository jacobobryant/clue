(ns jobryant.re-com.core
  (:refer-clojure :exclude [for]))

(defmacro for [bindings body]
  `(clojure.core/for ~bindings
     (with-meta ~body {:key ~(first bindings)})))
