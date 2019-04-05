(ns clue.repl
  (:require [clojure.spec.alpha :as s]
            [clojure.set :refer [union intersection difference]]
            [orchestra.spec.test :as st]
            [orchestra.core :refer [defn-spec]]
            [clojure.tools.namespace.repl :as tn]
            [clojure.core.logic :as logic]
            [clojure.core.logic.pldb :as pldb]
            [clojure.core.logic.fd :as fd]
            [jobryant.util :as u]
            [clue.core :as c]
            [clue.human :as h]
            [clue.ai :as ai]
            [clue.play :as p]
            [clue.core-test :as t]
            [clue.db]
            [clue.backend.ws]
            [clue.backend.http]
            [mount.core :as mount]
            [nrepl.server :refer [start-server]]))

(st/instrument)

(comment
  (nrepl.server/start-server :port 7888)

  ; for un-botching the repl
  (require '[clojure.tools.namespace.repl :as tn])
  (tn/set-refresh-dirs "src" "test")
  (tn/refresh)

  (require '[clojure.core.async :refer [<! go-loop]])

)

(tn/set-refresh-dirs "src" "test")

(defn nrepl []
  (start-server :port 7888))

(defn go []
  (mount/start)
  :ready)

(defmacro reset []
  `(do (mount/stop)
       (tn/refresh :after 'clue.repl/go)
       (use 'clojure.repl)))

(def test-state
  (assoc (t/mock-initial-state [\r \g \y] (repeat 3 (:ai p/config)))
         ::c/suggestions
         [{::c/suggester \g ::c/solution #{\b \5 "Candlestick"}}]))

;(def state (t/mock-initial-state [\y \g \r]
;                                 (repeat 3 (p/config :human))))

;(pldb/db-rel player x)
;(pldb/db-rel room x)
;(pldb/db-rel weapon x)
;
;(defmacro defdb
;  [name relation members]
;  `(def ~name (apply pldb/db
;                     (map #(vector ~relation %) ~members))))

;(defdb players player c/player-chars)
;(defdb rooms room c/room-chars)
;(defdb weapons weapon c/weapons)

;(def all-cards (concat c/player-chars c/room-chars c/weapons))
;
;(def n->card (into {} (map-indexed vector all-cards)))
;(def card->n (into {} (map (fn [[k v]] [v k]) n->card)))

(def players #{:green :scarlet :peacock :mustard :white :plum})
(def rooms #{:study :hall :lounge :dining-room :kitchen :ballroom
            :conservatory :billiard-room :library})
(def weapons #{:candlestick :lead-pipe :revolver :wrench :rope :knife})
(def all-cards (union players rooms weapons))

(def positions #{:solution :mine :scarlet :green})

(def my-cards #{:rope :study :scarlet :conservatory :mustard :wrench})
(def not-my-cards (difference all-cards my-cards))

; starting
(def pos->card
  {:solution not-my-cards
   :mine my-cards
   :scarlet not-my-cards
   :green not-my-cards
   })

(def not-my-hand #{:solution :scarlet :green})


;(comment
;
;  player doesn't answer suggestion:
;    remove that player from each suggestion card in card->pos
;  player shows you a card:
;    set that card's position
;  ;player shows someone else a card:
;  ;  add an entry to possibilities
;
;  while changes:
;    if all cards in a player's hand are known:
;      remove them from any other sets in card->pos
;    ;for each possibility [player cards]:
;    ;  update cards to (intersection (pos->cards player) cards)
;    ;  if (count cards) = 1:
;    ;    delete possibility
;    ;    set card position
;
;  )

(def possibilities
  #{[:green #{:candlestick :lead-pipe :rope}]})

(def card->pos
  {:hall not-my-hand
   :billiard-room not-my-hand
   :study #{:mine}
   :white not-my-hand
   :kitchen not-my-hand
   :rope #{:mine}
   :candlestick not-my-hand
   :green not-my-hand
   :wrench #{:mine}
   :library not-my-hand
   :peacock not-my-hand
   :lounge not-my-hand
   :knife not-my-hand
   :conservatory #{:mine}
   :plum not-my-hand
   :revolver not-my-hand
   :scarlet #{:mine}
   :mustard #{:mine}
   :ballroom not-my-hand
   :dining-room not-my-hand
   :lead-pipe not-my-hand
   })


;(def card->pos
;  {
;   }

;(defn do-query []
;  (let [card-positions (into {} (map vector all-cards (repeatedly logic/lvar)))
;        ]
;        ;cards (repeatedly 21 logic/lvar)
;        ;[my-cards remaining] (split-at 6 cards)
;        ;[green-cards remaining] (split-at 6 remaining)
;        ;[scarlet-cards solution] (split-at 6 remaining)]
;    (logic/run 1 [x y z]
;      (logic/== (:green card-positions) :green)
;      (logic/== (:lead-pipe card-positions) :green)
;      (logic/== (:knife card-positions) :green)
;      (logic/== (:dining-room card-positions) :green)
;      (logic/== (:billiard-room card-positions) :green)
;      (logic/== (:library card-positions) :green)
;      (logic/== (:revolver card-positions) :scarlet)
;      (logic/== (:plum card-positions) :scarlet)
;      (logic/== (:hall card-positions) :scarlet)
;      (logic/== (:lounge card-positions) :scarlet)
;      (logic/== (:kitchen card-positions) :scarlet)
;      (logic/== (:white card-positions) :scarlet)
;      (logic/== (:rope card-positions) :mine)
;      (logic/== (:study card-positions) :mine)
;      (logic/== (:scarlet card-positions) :mine)
;      (logic/== (:conservatory card-positions) :mine)
;      (logic/== (:mustard card-positions) :mine)
;      (logic/== (:wrench card-positions) :mine)
;      ;(logic/== (:peacock card-positions) :solution)
;      ;(logic/== (:candlestick card-positions) :solution)
;      ;(logic/== (:ballroom card-positions) :solution)
;      (logic/== {card-positions x} :solution)
;      (logic/== {card-positions y} :solution)
;      (logic/== {card-positions z} :solution)
;      ;(logic/== solution card-positions)
;      )))


      ;(logic/== my-cards [:rope :study :scarlet
      ;                    :conservatory :mustard :wrench])
      ;(logic/== green-cards [:green :lead-pipe :knife :dining-room
      ;                       :billiard-room :library])
      ;(logic/== scarlet-cards [:revolver :plum :hall
      ;                         :lounge :kitchen :white])
      ;(logic/membero p players)
      ;(logic/membero r rooms)
      ;(logic/membero w weapons)
      ;(logic/permuteo all-cards cards)
      ;)))
