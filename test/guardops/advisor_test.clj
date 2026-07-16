(ns guardops.advisor-test
  "Unit tests of `guardops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [guardops.advisor :as adv]
            [guardops.store :as store]))

(def db (store/seed-db))

(deftest propose-patrol-record-shape
  (testing "patrol-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-patrol-record
                           :assignment-id "assignment-1"
                           :patch {:checkpoint "north-gate" :status "clear"}})]
      (is (= :log-patrol-record (:op p)))
      (is (= "assignment-1" (:assignment-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :assignment-id)))))

(deftest propose-guard-shift-shape
  (testing "guard-shift proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-guard-shift
                           :assignment-id "assignment-2"
                           :patch {:guard "officer Alvarez" :shift "night"}})]
      (is (= :schedule-guard-shift (:op p)))
      (is (= "assignment-2" (:assignment-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-equipment-supply-shape
  (testing "equipment-supply proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-equipment-supply
                           :assignment-id "assignment-1"
                           :patch {:item "radio battery pack" :quantity 4}})]
      (is (= :coordinate-equipment-supply (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-incident-concern-shape
  (testing "incident-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-incident-concern
                           :assignment-id "assignment-1"
                           :patch {:concern "unfamiliar vehicle at perimeter"}})]
      (is (= :flag-incident-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-patrol-record :schedule-guard-shift :coordinate-equipment-supply
                :flag-incident-concern]]
      (let [p (adv/infer db {:op op :assignment-id "assignment-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-patrol-record :schedule-guard-shift :coordinate-equipment-supply
                :flag-incident-concern]]
      (let [p (adv/infer db {:op op :assignment-id "assignment-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))

(deftest out-of-scope-hook-injects-excluded-content
  (testing "the :out-of-scope? test hook actually produces content the governor's scope-exclusion scan will catch"
    (let [p (adv/infer db {:op :log-patrol-record :assignment-id "assignment-1"
                           :out-of-scope? true :patch {}})]
      (is (re-find #"dispatch the guard to respond" (:rationale p)))
      (is (re-find #"authorize the use of force" (:rationale p))))))
