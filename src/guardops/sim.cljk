(ns guardops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean patrol-record logging
  request through intake -> advise -> govern -> decide -> approval ->
  commit at phase 1 (assisted-logging, always approval), then re-runs
  the same op at phase 3 (supervised-auto, clean + high confidence ->
  auto-commit), then a guard-shift-scheduling request and an
  equipment-supply coordination request (both auto-commit clean at
  phase 3), then an incident-concern flag (ALWAYS escalates, at any
  phase -- approve, then commit), then HARD-hold scenarios: an
  unregistered post-assignment, an assignment registered but not yet
  verified, a proposal whose own `:effect` is not `:propose`, and a
  proposal that has drifted into the permanently-excluded guard-
  dispatch/use-of-force scope."
  (:require [langgraph.graph :as g]
            [guardops.advisor :as advisor]
            [guardops.store :as store]
            [guardops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "security-supervisor-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        supervisor-phase-1 {:actor-id "sup-1" :actor-role :security-supervisor :phase 1}
        supervisor-phase-3 {:actor-id "sup-1" :actor-role :security-supervisor :phase 3}
        actor (op/build db)]

    (println "== log-patrol-record assignment-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-patrol-record :assignment-id "assignment-1"
                                  :patch {:checkpoint "north-gate" :time "22:00" :status "clear"}} supervisor-phase-1)]
      (println r)
      (println "-- human security supervisor approves --")
      (println (approve! actor "t1")))

    (println "\n== log-patrol-record assignment-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-patrol-record :assignment-id "assignment-1"
                                  :patch {:checkpoint "loading-dock" :time "23:00" :status "clear"}} supervisor-phase-3))

    (println "\n== schedule-guard-shift assignment-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-guard-shift :assignment-id "assignment-1"
                                  :patch {:guard "officer Alvarez" :shift "night" :date "2026-07-20"}} supervisor-phase-3))

    (println "\n== coordinate-equipment-supply assignment-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-equipment-supply :assignment-id "assignment-1"
                                  :patch {:item "radio battery pack" :quantity 4 :urgency "routine"}} supervisor-phase-3))

    (println "\n== flag-incident-concern assignment-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :flag-incident-concern :assignment-id "assignment-1"
                                 :patch {:concern "unfamiliar vehicle circling the perimeter twice" :confidence 0.9}} supervisor-phase-3)]
      (println r)
      (println "-- human security supervisor reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== log-patrol-record assignment-99 (unregistered assignment -> HARD hold) ==")
    (println (exec-op actor "t6" {:op :log-patrol-record :assignment-id "assignment-99"
                                  :patch {:checkpoint "front-lobby" :time "06:00" :status "clear"}} supervisor-phase-3))

    (println "\n== log-patrol-record assignment-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-patrol-record :assignment-id "assignment-3"
                                  :patch {:checkpoint "front-lobby" :time "06:00" :status "clear"}} supervisor-phase-3))

    (println "\n== schedule-guard-shift assignment-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t8" {:op :schedule-guard-shift :assignment-id "assignment-1"
                                           :patch {:guard "officer Nakamura" :shift "day" :date "2026-07-22"}} supervisor-phase-3)))

    (println "\n== log-patrol-record assignment-1, advisor drifts into dispatch/force scope -> HARD hold, permanent ==")
    (println (exec-op actor "t9" {:op :log-patrol-record :assignment-id "assignment-1"
                                   :out-of-scope? true
                                   :patch {}} supervisor-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
