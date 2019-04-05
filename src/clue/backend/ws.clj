(ns clue.backend.ws
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer (get-sch-adapter)]
            [mount.core :refer [defstate]]
            [clojure.pprint :refer [pprint]]
            [jobryant.util :as u]
            [jobryant.datomic.api :as d]
            [clue.db :as db :refer [conn]]))

(defn username [event]
  (or (get-in event [:ring-req :session :uid])
      (get-in event [:session :uid])))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket!
        (get-sch-adapter) {:user-id-fn :client-id
                           #_(fn [event]
                               (u/capture event)
                               {:client-id (:client-id event)
                                :username (username event)})})]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def *connected-uids               connected-uids))

(defmulti handler :id)
(defmethod handler :default [event]
  nil #_(println "unhandled event:" (:id event)))

(defn event-msg-handler [{:keys [?reply-fn] :as event}]
  (let [result (try (handler event)
                    (catch AssertionError e
                      (u/capture event e)
                      (pprint e)
                      {:result :error})
                    (catch Exception e
                      (u/capture event e)
                      (pprint e)
                      {:result :error}))]
    (when (some? ?reply-fn)
      (?reply-fn (select-keys result [:result :message])))))

(defstate ws-router-stop-fn
  :start (sente/start-server-chsk-router!
           ch-chsk event-msg-handler)
  :stop (ws-router-stop-fn))

(defmethod handler :chsk/uidport-open [event]
  (let [_username (username event)]
    (chsk-send! (:uid event) [:db/merge {:username _username
                                         :new-games (db/new-games)}])))

(defn broadcast-new-games! []
  ; exclude people in started games
  (let [games (db/new-games)]
    (doseq [uid (:any @*connected-uids)]
      (chsk-send! uid [:db/merge {:new-games games}]))))

(defmethod handler :clue/new-game [event]
  ; Make sure this fails if game id is taken.
  (let [_username (username event)
        game-id (u/rand-str 4)]
    @(d/transact conn [{:db/id "new-game"
                        :game/id game-id
                        :game/players [_username]
                        :game/status :game.status/new}])
    (broadcast-new-games!)))

(defmethod handler :clue/leave-game [{game-id :?data :as event}]
  @(d/transact conn [[:clue.backend.tx/leave-game game-id (username event)]])
  (broadcast-new-games!))

(defmethod handler :clue/join-game [{game-id :?data :as event}]
  @(d/transact conn [{:game/id game-id
                      :game/players [(username event)]}])
  (broadcast-new-games!))

(defmethod handler :clue/start-game [event]
  @(d/transact conn [[:clue.backend.tx/start-game (username event)]])
  (broadcast-new-games!))
