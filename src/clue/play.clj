(ns clue.play
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [clue.core :refer :all :as core]
            [clue.human :refer :all]
            [clue.util :refer :all]))

(defn play! [players]
  (loop [state (initial-state players)]
    (when (some? (::core/turn state))
      (recur (take-turn state)))))

(defn -main
  [& args]
  (assert (>= (count args) 2))
  (play! (map first args)))
