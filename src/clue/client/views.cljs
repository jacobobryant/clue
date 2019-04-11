(ns clue.client.views
  (:require [reagent.core :as r]
            [jobryant.re-com.core :as rc]
            [jobryant.util :as u]
            [clue.client.color :as color]
            [clue.client.db :as db :refer [db]]
            [clue.client.event :as event]
            [clue.human :as human]
            [clue.info :as info]
            [clojure.string :refer [join lower-case]]))

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
  [rc/v-box
   (rc/for [[k v] m]
     [rc/h-box
      [rc/label
       :label (str k ":")
       :style {:font-weight "bold"}
       :width "100px"]
      [rc/label :label v]])])

(defn new-game []
  [rc/v-box
   [rc/title
    :label (str "Game ID: " @db/game-id)
    :level :level2]
   [rc/p [:strong "Players: "] (clojure.string/join ", " @db/players)]
   [rc/h-box
    [rc/button
     :label "Start game"
     :on-click event/start-game!
     :disabled? (not @db/can-start-game?)]
    [rc/button
     :label "Leave game"
     :on-click event/leave-game!]]])

(def cell-style {:style {:padding-right 15}})

(defn table [header-row rows]
  [:table.table {:style {:width "initial"
                         :min-width "50%"}}
   [:thead
    [:tr (rc/for [th header-row] [:th cell-style th])]]
   [:tbody
    (rc/for [r rows]
      [:tr (rc/for [td r] [:td cell-style td])])]])

(defn lobby []
  [rc/v-box
   [rc/title
    :label "Games"
    :level :level2]
   (if @db/have-new-games?
     [table
      ["ID" "Players" ""]
      (for [game @db/new-games]
        [(:game/id game)
         (join ", " (:game/players game))
         [rc/button
          :label "Join"
          :on-click #(event/join-game! (:game/id game))]])]
     [rc/p "No games yet."])
   [rc/h-box
    [rc/button
     :label "Create game"
     :on-click event/new-game!]]])

(defn move []
  (let [coordinates (reagent.core/atom "")]
    (fn []
      [rc/v-box
       [rc/p "You rolled a " @db/roll]
       [rc/h-box
        [rc/label :label "Coordinates:"]
        [rc/input-text
         :model coordinates
         :transform lower-case]]
       [rc/button
        :label "Move"
        :on-click #(event/move! (info/parse-coordinates @coordinates))]])))

(defn suggest []
  (let [[person-props weapon-props room-props :as props]
        (for [[choices placeholder] [[info/sorted-characters "Person"]
                                     [info/weapons-vec "Weapon"]]
              :let [model (r/atom nil)]]
          {:model model
           :choices (u/forv [c choices]
                            {:id c :label (info/card-names c)})
           :placeholder placeholder
           :on-change #(reset! model %)
           :width "150px"
           :max-height "300px"})]
    (u/capture props)
    (fn []
      [rc/v-box
       [rc/p "Make a suggestion:"]
       [rc/h-box
        (into [rc/single-dropdown] (apply concat person-props))
        (into [rc/single-dropdown] (apply concat weapon-props))
        [rc/button
         :label "Make suggestion"
         :disabled? (some (comp nil? deref :model) props)
         :on-click #(apply event/suggest! (map (comp deref :model) props))]]])))

(defn turn-controls []
  (if @db/your-turn?
    (case @db/game-state
      :game.state/start-turn [rc/button
                              :label "Roll dice"
                              :on-click event/roll!]
      :game.state/post-roll [move]
      :game.state/make-suggestion [suggest])
    [rc/p "It's " @db/current-player "'s turn."]))

(defn static-info []
  [rc/v-box
   [:p "Players: " (join ", "
                         (for [[player character] @db/player-characters]
                           (str player " (" (info/card-names character) ")")))]
   [:p (with-out-str (human/print-rooms))]
   [:p "Your hand: " (join ", " (map info/card-names @db/hand))]])

(defn board []
  [:pre {:style {:background-color "black"
                 :color "white"}}
   (with-out-str
     (human/print-game-board (human/current-board @db/player-locations)))])

(defn ongoing-game []
  [rc/v-box
   [rc/title
    :label (str "Game ID: " @db/game-id)
    :level :level2]
   [turn-controls]
   [board]
   [static-info]
   [rc/button
    :label "Quit"
    :on-click event/quit!]])

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
      @db/in-ongoing-game? [ongoing-game]
      :default [lobby])]])
