(ns clue.client.views
  (:require [jobryant.re-com.core :as rc]
            [clue.client.color :as color]
            [clue.client.db :as db :refer [db]]))

(defn header-title [label level]
  [rc/title
   :style {:color color/primary-text
           :margin 0}
   :label label
   :level level])

(defn header []
  [rc/h-box {:style {:padding "15px 20px"
                     :background-color color/primary}
             :align :center
             :gap "10px"}
   [header-title "Clue" :level1]
   [rc/grow]
   (when @db/loaded?
     [header-title (str "Welcome, " (:username @db)) :level2])
   [rc/line :color color/primary-text :style {:align-self "stretch"}]
   [rc/hyperlink-href
    :style {:font-size "1.2em" :color color/primary-text}
    :label "Log out"
    :href "/logout"]])

(defn main [nav-state]
  [:div {:style {:height "100%"
                 :background-color color/site-background}}
   [header]
   [rc/v-box {:width "700px" :margin "0 auto" :gap "10px"}
    [rc/gap :size "10px"]
    (when (not @db/loaded?)
      [rc/throbber
       :style {:align-self "center"}
       :size :large])
    #_[rc/button :label "New game"]]])
