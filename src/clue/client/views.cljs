(ns clue.client.views
  (:require [reagent.core :as r]
            [reagent.ratom :refer-macros [reaction]]
            [jobryant.re-com.core :as rc]
            [jobryant.util :as u]
            [clue.client.color :as color]
            [clue.client.db :as db :refer [db]]
            [clue.client.event :as event]
            [clue.human :as human]
            [clue.info :as info]
            [clue.core :as core]
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

(defn select-cards [{:keys [all?]} model]
  (let [info (cond-> [[info/sorted-characters "Person"]
                      [info/weapons-vec "Weapon"]]
               all? (conj [info/rooms-vec "Room"]))
        _ (reset! model (vec (repeat (count info) nil)))
        dropdowns (u/forv [[i [choices placeholder]] (u/zip [(range) info])]
                    [rc/single-dropdown
                     :model (reaction (nth @model i))
                     :choices (u/forv [c choices]
                                {:id c :label (info/card-names c)})
                     :placeholder placeholder
                     :on-change #(swap! model assoc i %)
                     :width "150px"
                     :max-height "300px"])]
    (fn [] (into [rc/h-box] dropdowns))))

(defn suggest []
  (let [cards (r/atom nil)]
    (fn []
      [rc/h-box
       [select-cards {:all? false} cards]
       [rc/button
        :label "Make suggestion"
        :disabled? (some nil? @cards)
        :on-click #(apply event/suggest! @cards)]])))

(defn select-card []
  (let [choices @db/possible-responses
        model (r/atom (if (= 1 (count choices))
                        (first choices)
                        nil))]
    (fn []
      (vec (concat
             [rc/h-box]
             (for [c choices]
               [rc/radio-button
                :model model
                :value c
                :label (info/card-names c)
                :on-change #(reset! model %)])
             [[rc/button
               :label "Show card"
               :disabled? (nil? @model)
               :on-click #(event/show-card! @model)]])))))

(defn show-card []
  (if (= @db/responder @db/username)
    [select-card]
    [rc/p "Waiting for " @db/responder " to show "
     (if (= @db/username @db/current-player) "you" @db/current-player)
     " a card."]))

(defn accuse []
  (let [accusing? (r/atom false)
        cards (r/atom nil)]
    (fn []
      (if @accusing?
        [rc/h-box
         [select-cards {:all? true} cards]
         [rc/button
          :label "Make accusation"
          :on-click #(event/accuse! @cards)
          :disabled? (some nil? @cards)]
         [rc/button
          :label "Cancel"
          :on-click #(reset! accusing? false)]]
        [rc/h-box
         [rc/button
          :label "End turn"
          :on-click event/end-turn!]
         [rc/button
          :label "Make accusation"
          :on-click #(reset! accusing? true)]]))))

(defn turn-controls []
  [:div {:style {:height "40px"}}
   (cond
     @db/game-done? [rc/p (if (= @db/username @db/winner)
                            "You are"
                            (str @db/winner " is"))
                     " the winner."]
     (some? @db/responder) [show-card]
     @db/your-turn? (case @db/game-state
                      :game.state/start-turn [rc/button
                                              :label "Roll dice"
                                              :on-click event/roll!]
                      :game.state/post-roll [rc/label :label "Choose a destination."]
                      :game.state/make-suggestion [suggest]
                      :game.state/accuse [accuse])
     :default [rc/p "It's " @db/current-player "'s turn."])])

(defn static-info []
  [rc/v-box
   [:p "Your hand: " (join ", " (map info/card-names @db/hand))]
   [:p "Players: " (join ", "
                         (for [[player character] @db/player-characters]
                           (str player " (" (info/card-names character) ")")))]])

(defn event-log []
  (let [username @db/username]
    [rc/scroller
     :v-scroll :auto
     :h-scroll :spill
     :height "500px"
     :max-width "315px"
     :child [rc/v-box
             (for [[i e] (u/indexed @db/events)]
               ^{:key [(= i 0) (:turn e) (:event e)]}
               [rc/p {:style (cond-> {:font-size "14px"
                                      :width "300px"
                                      :min-width "300px"}
                               (= i 0) (assoc :font-weight "bold"))}
                (info/event-text e username)])]]))

(def square-size 22)

(defn board-element [row col width height z]
  {:position "absolute"
   :top (int (* square-size row))
   :left (int (* square-size col))
   :width (int (* square-size width))
   :height (int (* square-size height))
   :z-index z})

(defn board []
  (let [available-locations @db/available-locations
        moving? (and @db/your-turn? @db/post-roll?)]
    [rc/h-box
     [:div {:style {:position "relative"
                    :width (* square-size core/board-width)
                    :height (* square-size 25)}}

      ; Rooms
      (for [[room [row col width height]] info/room-tiles
            :let [available? (contains? available-locations room)
                  can-move? (and available? moving?)]]
        ^{:key room}
        [:div {:class (when can-move? "available-room")
               :style (merge (board-element row col width height 1)
                             {:text-align "center"})
               :on-click (when can-move? #(event/move! (info/rooms-map-invert room)))}
         [rc/label
          :label (info/card-names room)
          :style (cond-> {}
                   (= :conservatory room) (assoc :margin-left "-20px")
                   available? (assoc :font-weight "bold"))]])

      ; Squares
      (for [[row col :as loc] (keys core/empty-board)
            :let [available? (contains? available-locations loc)
                  can-move? (and available? moving?)
                  [bottom-border right-border]
                  (for [i [0 1]
                        :let [next-loc (update loc i inc)
                              next-available? (contains? available-locations next-loc)]]
                    (str "1px solid " (if (or available? next-available?) "black" "#bc9971")))
                  [top-border left-border]
                  (for [i [0 1]
                        :let [prev-loc (update loc i dec)]]
                    (when (and available? (not (contains? core/empty-board prev-loc)))
                      "1px solid black"))]]
        ^{:key loc}
        [:div {:class (when can-move? :available-square)
               :style (merge (board-element row col 1 1 2)
                             {:border-top top-border
                              :border-left left-border
                              :border-right right-border
                              :border-bottom bottom-border
                              :background-color "#eac8a3"})
               :on-click (when can-move? #(event/move! loc))}])

      ; Doors
      (for [[[row col] orientation] info/door-directions]
        (let [horizontal? (= orientation :horizontal)
              [width height] (cond-> [0.1 1] horizontal? reverse)
              style (merge (board-element row col width height 3)
                           {:background-color "red"})
              style (update style (if horizontal? :top :left) dec)]
          ^{:key [row col]}
          [:div {:style style}]))

      ; Players
      (for [[player [row col]] (human/player-coordinates @db/player-locations)]
        (let [style (merge (board-element row col 0.7 0.7 4)
                           {:margin (int (* square-size 0.15))
                            :background-color (info/player-colors player)})]
          ^{:key player}
          [:div {:style style}]))]

     [event-log]]))

(defn ongoing-game []
  [rc/v-box
   [rc/title
    :label (str "Game ID: " @db/game-id ". You are " @db/character ".")
    :level :level2]
   [turn-controls]
   [board]
   [static-info]
   [rc/button
    :label "Quit"
    :on-click event/quit!]])

(defn main [nav-state]
  [:div
   [header]
   [rc/v-box {:width "900px" :margin "0 auto"}
    [rc/gap :size "10px"]
    (cond
      (not @db/loaded?) [rc/throbber
                         :style {:align-self "center"}
                         :size :large
                         :color color/primary]
      @db/in-new-game? [new-game]
      @db/in-ongoing-game? [ongoing-game]
      :default [lobby])]])
