(ns clue.client.ws
  (:require [taoensso.sente :as sente]
            [clue.client.db :as db :refer [db]]
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

(defmethod recv-handler :db/reset [{:keys [data]}]
  (reset! db data))
