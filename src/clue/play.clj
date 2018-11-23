(ns clue.play
  (:gen-class)
  (:require [orchestra.spec.test :as st]
            [clue.core :refer [initial-state] :as core]
            [clue.human :refer [take-turn]]))

(defn -main
  [& args]
  (assert (>= (count args) 2))
  (st/instrument)
  (loop [state (initial-state (map first args))]
    (when (some? (::core/turn state))
      (recur (take-turn state)))))
