(ns clue.client.event
  (:require [clue.client.ws :refer [send!]]
            [clue.client.db :as db :refer [db]]))

; try to shorted tx fn keywords

(defn new-game! []
  (send! [:clue/new-game nil]))

(defn leave-game! []
  (let [game-id @db/game-id]
    (swap! db dissoc :game)
    (send! [:clue/tx [:clue.backend.tx/leave-game game-id]])))

(defn join-game! [game-id]
  (send! [:clue/tx [:clue.backend.tx/join-game game-id]]))

(defn start-game! []
  (send! [:clue/tx [:clue.backend.tx/start-game]]))

(defn quit! []
  (send! [:clue/tx [:clue.backend.tx/quit-game]]))
