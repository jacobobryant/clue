(ns clue.client.ws
  (:require [taoensso.sente :as sente]
            [clue.client.db :refer [db]]
            [jobryant.util :as u]))

(defmulti handler :id)
(defmethod handler :default [event]
  nil #_(println "unhandled event:" event))

(defmulti recv-handler :id)
(defmethod recv-handler :default [event]
  nil #_(println "unhandled recv event:" event))

(defmethod handler :chsk/recv [event]
  (recv-handler (into {} (map vector [:id :data] (:?data event)))))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def *chsk-state state)
  (def send! send-fn)
  (sente/start-client-chsk-router! ch-chsk handler))

(defmethod recv-handler :db/merge [{:keys [data]}]
  (swap! db u/deep-merge-some data))

(defmethod recv-handler :db/reset [{:keys [data]}]
  (reset! db data))

(defmethod recv-handler :clue/new-game [{{:keys [username game-id]} :data}]
  (let [game #:game{:id game-id
                    :players #{username}
                    :game/state :game.state/new}]
    (swap! db #(if (= username (:username %))
                 (assoc % :game game)
                 (assoc-in % [:new-games game-id] game)))))

(defmethod recv-handler :self/leave-game [{new-games :data}]
  (swap! db #(-> %
                 (dissoc :game)
                 (assoc :new-games new-games))))

(defmethod recv-handler :game/leave-game [{username :data}]
  (swap! db update-in [:game :game/players] disj username))

(defmethod recv-handler :lobby/leave-game [{[game-id username] :data}]
  (swap! db update-in [:new-games game-id :game/players] disj username))

(defmethod recv-handler :lobby/remove-game [{game-id :data}]
  (swap! db update :new-games dissoc game-id))

(defmethod recv-handler :self/join-game [{game :data}]
  (swap! db assoc :game game))

(defmethod recv-handler :lobby/join-game [{[game-id username] :data}]
  (swap! db update-in [:new-games game-id :game/players] conj username))

(defmethod recv-handler :game/join-game [{username :data}]
  (swap! db update-in [:game :game/players] conj username))

(defmethod recv-handler :game/start [{game :data}]
  (swap! db assoc :game game))

(defmethod recv-handler :game/quit [{new-games :data}]
  (swap! db #(-> %
                 (dissoc :game)
                 (assoc :new-games new-games))))
