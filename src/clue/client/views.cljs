(ns clue.client.views
  (:require [jobryant.re-com.core :as rc]
            [clue.client.color :as color]
            [clue.client.db :as db :refer [db]]
            [clue.client.event :as event]))

(defn header-title [label level]
  [rc/title
   :style {:color color/primary-text
           :margin 0}
   :label label
   :level level])

(defn header []
  [rc/h-box {:style {:padding "15px 20px"
                     :background-color color/primary}
             :align :center}
   [header-title "Clue" :level1]
   [rc/grow]
   (when @db/loaded?
     [header-title (str "Welcome, " (:username @db)) :level2])
   [rc/line :color color/primary-text :style {:align-self "stretch"}]
   [rc/hyperlink-href
    :style {:font-size "1.2em" :color color/primary-text}
    :label "Log out"
    :href "/logout"]])

(defn mapview [m]
  (into [rc/v-box]
        (for [[k v] m]
          [rc/h-box
           [rc/label
            :label (str k ":")
            :style {:font-weight "bold"}
            :width "100px"]
           [rc/label :label v]])))

(defn new-game []
  [rc/v-box
   [rc/title
    :label (str "Game ID: " @db/game-id)
    :level :level2]
   [rc/p [:strong "Players: "] (clojure.string/join ", " @db/players)]
   [rc/h-box
    [rc/button
     :label "Start game"
     :disabled? (not @db/can-start-game?)]
    [rc/button
     :label "Leave game"
     :on-click event/leave-game!]]])

(defn lobby []
  [rc/v-box
   [rc/h-box
    [rc/button
     :label "New game"
     :on-click event/new-game!
     :disabled? db/new-game-pending?]
    (when @db/new-game-pending?
      [rc/throbber :size :small :color color/primary])]])

(defn main [nav-state]
  [:div {:style {:height "100%"
                 :background-color color/site-background}}
   [header]
   [rc/v-box {:width "700px" :margin "0 auto"}
    [rc/gap :size "10px"]
    (cond
      (not @db/loaded?) [rc/throbber
                         :style {:align-self "center"}
                         :size :large
                         :color color/primary]
      @db/in-new-game? [new-game]
      :default [lobby])]])
