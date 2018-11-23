(ns clue.core-test
  (:require [clojure.test :refer :all]
            [clue.core :refer :all]
            [clojure.java.io :as io]))

;(deftest tests
;  (testing "get-move"
;    (with-open [r (io/reader (.getBytes "q1\na1\nh1\n"))]
;      (with-redefs [*in* r]
;        (is (= (get-move test-state 12) [0 7])))))
;  (testing "current-player"
;    (is (= (current-player test-state) "green")))
;  (testing "current-location"
;    (is (= (current-location test-state) [24 9]))))
