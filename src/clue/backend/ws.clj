(ns clue.backend.ws
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer (get-sch-adapter)]
            [mount.core :refer [defstate]]
            [clojure.pprint :refer [pprint]]
            [jobryant.util :as u]
            [jobryant.datomic.api :as d]
            [clue.backend.query :as q]
            [clue.db :as db :refer [conn]]))

(defn username [event]
  (or (get-in event [:ring-req :session :uid])
      (get-in event [:session :uid])))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket!
        (get-sch-adapter) {:user-id-fn 
                           (fn [event]
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
  (let [_username (username event)
        db (d/db conn)]
    (chsk-send! (:uid event) [:db/merge {:username _username
                                         :new-games (q/new-games db)
                                         :game (q/game db _username)}])))

(defn broadcast-state! []
  (let [db (d/db conn)
        games (q/new-games db)]
    (doseq [uid (:any @*connected-uids)]
      (chsk-send! uid [:db/merge {:new-games games
                                  :game (q/game db (:username uid))}]))))

; move this to tx fn
; then make it so you can specify if the fn should be atomic or not/what parts of the fn should be atomic
(defmethod handler :clue/new-game [event]
  ; Make sure this fails if game id is taken.
  @(d/transact conn [{:db/id "new-game"
                      :game/id (u/rand-str 4)
                      :game/players [(username event)]
                      :game/status :game.status/new}])
  (broadcast-state!))

(defmethod handler :clue/tx [{[fn-keyword & args] :?data :as event}]
  @(d/transact conn [(into [fn-keyword (username event)] args)])
  (broadcast-state!))
