(ns guardops.governor-test
  "Pure unit tests of `guardops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [guardops.governor :as gov]
            [guardops.advisor :as adv]
            [guardops.store :as store]))

(def assignment-1 {:assignment-id "assignment-1" :site "Riverside Logistics Yard" :registered? true :verified? true})
(def assignment-3 {:assignment-id "assignment-3" :site "Northgate Business Park" :registered? true :verified? false})

(defn- clean-proposal [op assignment-id]
  {:op op :assignment-id assignment-id :summary "s" :rationale "routine security coordination"
   :cites [assignment-id] :effect :propose :value {} :confidence 0.85})

(deftest assignment-unregistered-is-hard
  (testing "no post-assignment record at all -> HARD hold"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          verdict (gov/check {} nil (clean-proposal :log-patrol-record "unknown-assignment") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:assignment-unverified} (map :rule (:violations verdict)))))))

(deftest assignment-unverified-is-hard
  (testing "assignment registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"assignment-3" assignment-3})
          verdict (gov/check {} nil (clean-proposal :log-patrol-record "assignment-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:assignment-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-guard-shift "assignment-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          verdict (gov/check {} nil (clean-proposal :dispatch-guard-to-incident "assignment-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest dispatch-to-respond-content-is-hard-and-permanent
  (testing "a proposal whose rationale claims a dispatch-to-respond action is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          poisoned (assoc (clean-proposal :log-patrol-record "assignment-1")
                          :rationale "decided to dispatch a guard to respond to the alarm immediately"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest use-of-force-authorization-content-is-hard
  (testing "a proposal touching use-of-force authorization is HARD-blocked, same as dispatch"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          poisoned (assoc (clean-proposal :schedule-guard-shift "assignment-1")
                          :rationale "recommend to authorize the use of force against the trespasser"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest detention-content-is-hard
  (testing "a proposal touching detention authorization is HARD-blocked"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          poisoned (assoc (clean-proposal :coordinate-equipment-supply "assignment-1")
                          :summary "plan to authorize detention of the individual at the loading dock")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest arrest-content-is-hard
  (testing "a proposal touching an arrest decision is HARD-blocked"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          poisoned (assoc (clean-proposal :log-patrol-record "assignment-1")
                          :value {:decision "authorize an arrest of the suspect on-site"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest japanese-dispatch-content-is-hard
  (testing "a Japanese-language dispatch-to-respond instruction is HARD-blocked"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          poisoned (assoc (clean-proposal :log-patrol-record "assignment-1")
                          :rationale "警備員に対応を指示し現場へ急行させることを提案する")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-incident-concern-is-not-scope-excluded
  (testing "flagging observed suspicious-activity/incidents as an INCIDENT CONCERN (not a response decision) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          concern (assoc (clean-proposal :flag-incident-concern "assignment-1")
                         :value {:concern "unfamiliar vehicle circling the perimeter twice near the loading dock"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (suspicious activity) is exactly what this op exists to surface"))))

;; -------- dedicated regression: default mock-advisor proposals must never self-trip --------
;;
;; A prior sibling actor in this fleet self-tripped its own governor's
;; scope-exclusion check because a bare-noun exclusion term matched
;; inside its own default advisor's disclaimer text on an otherwise
;; legitimate, allowed proposal. This test asserts every default
;; proposal from `guardops.advisor`'s mock advisor -- for every op in
;; the closed allowlist -- clears `scope-exclusion-violations` (and is
;; not otherwise HARD-blocked) when targeting a fully verified
;; assignment. This must hold BEFORE considering the build done.

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "every default proposal for every allowed op is governor-clean against a verified assignment"
    (let [s (store/mem-store {"assignment-1" assignment-1})
          patches {:log-patrol-record {:checkpoint "north-gate" :time "22:00" :status "clear"}
                   :schedule-guard-shift {:guard "officer Alvarez" :shift "night" :date "2026-07-20"}
                   :coordinate-equipment-supply {:item "radio battery pack" :quantity 4 :urgency "routine"}
                   :flag-incident-concern {:concern "unfamiliar vehicle circling the perimeter" :confidence 0.9}}]
      (doseq [op gov/allowed-ops]
        (let [proposal (adv/infer nil {:op op :assignment-id "assignment-1" :patch (get patches op)})
              verdict (gov/check {} nil proposal s)]
          (is (false? (:hard? verdict))
              (str "op " op " default proposal must not be HARD-blocked -- verdict: " (pr-str verdict)))
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "op " op " default proposal must never self-trip scope-exclusion -- rationale/summary: "
                   (pr-str (select-keys proposal [:rationale :summary :value])))))))))
