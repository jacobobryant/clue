(ns clue.play
  (:gen-class)
  (:require [clue.core :refer [starting-locations] :as core]
            [clue.human :refer [take-turn]]))

(defn play! [players]
  (loop [state {::core/player-locations (starting-locations players)
                ::core/turn 0}]
    (when (some? (::core/turn state))
      (recur (take-turn state)))))

(def test-state {::core/player-locations (starting-locations [\r \g])
                 ::core/turn 0})

(defn -main
  [& args]
  (play! (map first args)))
