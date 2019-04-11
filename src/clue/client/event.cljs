(ns clue.client.event
  (:require [clue.client.ws :refer [send!]]
            [clue.client.db :as db :refer [db]]))

; try to shorted tx fn keywords

(defn init! []
  (send! [:clue/init nil]))

(defn new-game! []
  (send! [:clue/new-game nil]))

(defn leave-game! []
  (send! [:clue/leave-game nil]))

(defn join-game! [game-id]
  (send! [:clue/join-game game-id]))

(defn start-game! []
  (send! [:clue/start-game nil]))

(defn quit! []
  (send! [:clue/quit-game nil]))

(defn roll! []
  (send! [:clue/roll nil]))

(defn move! [coordinates]
  (send! [:clue/move coordinates]))

(defn suggest! [person weapon]
  (send! [:clue/suggest [person weapon]]))
