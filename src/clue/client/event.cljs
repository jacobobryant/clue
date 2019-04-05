(ns clue.client.event
  (:require [clue.client.ws :refer [send!]]
            [clue.client.db :as db :refer [db]]))

(defn new-game! []
  (swap! db assoc :new-game :pending)
  (send! [:clue/new-game nil]))

(defn leave-game! []
  (let [game-id @db/game-id]
    (swap! db dissoc :game)
    (send! [:clue/leave-game game-id])))
