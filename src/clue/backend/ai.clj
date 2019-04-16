(ns clue.backend.ai
  (:require [clue.backend.query :as q]
            [clue.core :as core]
            [clue.info :as info]
            [clue.db :refer [conn]]
            [jobryant.datomic.api :as d]
            [jobryant.util :as u]
            [clojure.set :refer [difference union]]))

(defn init-data [hand players player]
  (let [all-cards (set (keys info/card-names))
        other-positions (disj (set (conj players :solution)) player)
        get-positions #(if (contains? hand %) #{player} other-positions)
        card->pos (u/map-from get-positions all-cards)]
    {:positions card->pos
     :processed-suggestions #{}}))

(defn process-suggestion [db game-id ai-name ai-data suggestion]
  (let [{cards :suggestion/cards
         suggester :suggestion/suggester
         responder :suggestion/responder
         response :suggestion/response} suggestion
        next-players (map (partial q/player-from-character db game-id)
                          (info/rotate-characters
                            (set (q/sorted-characters db game-id))
                            (q/character-from-player db game-id suggester)))
        non-responders (take-while (complement #{responder}) next-players)]
    (as-> ai-data x
      (reduce
        (fn [data [non-responder card]]
          (update-in data [:positions card] disj non-responder))
        x
        (for [r non-responders
              c cards]
          [r c]))
      (if (and (some? response) (= suggester ai-name))
        (assoc-in x [:positions response] #{responder})
        x))))

(defn fixed-positions [positions hand-size]
  (let [size #(if (= % :solution) 3 hand-size)
        values (vals positions)]
    (set (filter (fn [pos]
                   (= (count (filter #(= #{pos} %) values)) (size pos)))
                 (apply union values)))))

(defn simplify [positions hand-size]
  (let [fixed (fixed-positions positions hand-size)
        cards (set (keys positions))
        rm #(if (> (count %) 1) (difference % fixed) %)
        simplified (reduce #(update %1 %2 rm) positions cards)]
    (if (= positions simplified)
      simplified
      (recur simplified hand-size))))

(defn think! [db game-id ai-name]
  (let [ai-data (q/ai-data db game-id ai-name)
        _ (u/capture db game-id ai-name ai-data)
        suggestions (->> (q/suggestions db game-id)
                         (remove (comp (:processed-suggestions ai-data) :db/id)))
        hand-size (q/hand-size db game-id)
        ai-data (as-> ai-data x
                  (reduce (partial process-suggestion db game-id ai-name) x suggestions)
                  (update x :processed-suggestions into (map :db/id suggestions))
                  (update x :positions simplify hand-size))]
    @(d/transact conn [{:player/name ai-name :player/ai-data (pr-str ai-data)}])))

(defn move [db game-id ai-name]
  (let [source (q/location db ai-name)
        roll (q/roll db game-id)
        available (core/available-locations source roll)]
    (first (sort-by vector? (shuffle available)))))

(defn suggest [db game-id ai-name]
  (map rand-nth [info/sorted-characters info/weapons-vec]))

(defn show-card [db game-id]
  (let [options (q/response-options db game-id)]
    (rand-nth options)))

(defn solution-possibilities [positions]
  (->> (keys positions)
       (filter #(contains? (positions %) ::solution))
       set))

(defn make-accusation? [db game-id ai-name]
  (let [ai-data (q/ai-data db game-id ai-name)]
    (= 3 (count (solution-possibilities (:positions ai-data))))))

(defn accuse [db game-id ai-name]
  [:mustard :wrench :ballroom])

(defn do-something-maybe [db prev-user handler]
  (let [game-id (q/game-id db prev-user)]
    (when-some [ai-name (q/current-ai db game-id)]
      (future
        (Thread/sleep 1000)
        (handler
          (case (q/game-state db game-id)
            :game.state/start-turn (do (think! db game-id ai-name)
                                       {:id :clue/roll
                                        :session {:uid ai-name}})
            :game.state/post-roll {:id :clue/move
                                   :session {:uid ai-name}
                                   :?data (move db game-id ai-name)}
            :game.state/accuse (if (make-accusation? db game-id ai-name)
                                 {:id :clue/accuse
                                  :?data (accuse db game-id ai-name)
                                  :session {:uid ai-name}}
                                 {:id :clue/end-turn
                                  :session {:uid ai-name}})
            :game.state/make-suggestion {:id :clue/suggest
                                         :session {:uid ai-name}
                                         :?data (suggest db game-id ai-name)}
            :game.state/show-card {:id :clue/show-card
                                   :session {:uid ai-name}
                                   :?data (show-card db game-id)}))))))
