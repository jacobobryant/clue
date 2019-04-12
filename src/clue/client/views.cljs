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
      [rc/h-box
       [rc/label :label "Coordinates:"]
       [rc/input-text
        :model coordinates
        :transform lower-case]
       [rc/button
        :label "Move"
        :on-click #(event/move! (info/parse-coordinates @coordinates))]])))

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
    [rc/v-box
     [rc/p @db/suggester " suggested "
      (join ", " (map info/card-names @db/suggested-cards)) "."]
     [select-card]]
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
                     :game.state/post-roll [move]
                     :game.state/make-suggestion [suggest]
                     :game.state/accuse [accuse])
    :default [rc/p "It's " @db/current-player "'s turn."]))

(defn static-info []
  [rc/v-box
   [:p "Players: " (join ", "
                         (for [[player character] @db/player-characters]
                           (str player " (" (info/card-names character) ")")))]
   [:p (with-out-str (human/print-rooms))]
   [:p "Your hand: " (join ", " (map info/card-names @db/hand))]])

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

(defn board []
  (let [username @db/username]
    [rc/h-box
     [:pre {:style {:background-color "black"
                    :color "white"
                    :min-width "53ch"
                    :max-width "53ch"
                    :width "53ch"
                    }}
      (with-out-str
        (human/print-game-board (human/current-board @db/player-locations)))]
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
  [:div {:style {:height "100%"
                 :background-color color/site-background}}
   [header]
   [rc/v-box {:width "820px" :margin "0 auto"}
    [rc/gap :size "10px"]
    (cond
      (not @db/loaded?) [rc/throbber
                         :style {:align-self "center"}
                         :size :large
                         :color color/primary]
      @db/in-new-game? [new-game]
      @db/in-ongoing-game? [ongoing-game]
      :default [lobby])]])
