(ns guardops.store
  "SSoT for the ISIC-8010 private-security-guard-services COORDINATION
  actor, behind a `Store` protocol so the backend is a swap, not a
  rewrite -- the same seam every `cloud-itonami-isic-*` actor in this
  fleet uses.

  This actor coordinates the BACK-OFFICE administration of an already
  human-directed private security operation (guard/patrol services
  provided under contract to a client site): patrol/checkpoint/
  incident-report data logging, guard-shift and post-assignment
  scheduling proposals, incident/suspicious-activity concern flagging,
  and uniform/equipment supply coordination.

  It NEVER dispatches a guard to respond to an incident, authorizes
  any use of force, or authorizes detention/arrest of any person --
  see `guardops.governor`'s `scope-exclusion-violations`, a HARD,
  permanent, un-overridable block. Any physical response, use-of-force
  decision, or detention decision is always made by a human directing
  the security operation, never by this actor or its advisor.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/
  demo (no deps). An `assignments` directory keyed by `:assignment-id`
  STRING (never a keyword -- consistent keying from the start, avoiding
  the silent-miss bug that plagued an earlier shepherd attempt).

  A registered/verified post-assignment (site-contract) record must
  exist before ANY proposal for that assignment may ever commit or
  escalate -- `guardops.governor`'s `assignment-unverified-violations`
  re-derives this from the assignment's own `:registered?`/
  `:verified?` fields, never from proposal self-report, the SAME
  'ground truth, not self-report' discipline every sibling actor's own
  governor uses.

  The ledger stays append-only: which assignment a proposal targeted,
  which operation, on what basis, committed/held/escalated and
  approved by whom is always a query over an immutable log.")

(defprotocol Store
  (assignment [s assignment-id] "Registered post-assignment (site-contract)
    record, or nil. Assignment map: {:assignment-id .. :site ..
    :registered? bool :verified? bool}.")
  (all-assignments [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-assignments [s assignments] "replace/seed the assignment directory (map assignment-id->assignment)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained post-assignment directory covering both the
  happy path and the governor's own hard checks, so the actor + tests
  run offline."
  []
  {:assignments
   {"assignment-1" {:assignment-id "assignment-1" :site "Riverside Logistics Yard"
                     :registered? true :verified? true}
    "assignment-2" {:assignment-id "assignment-2" :site "Harbor Gate Retail Plaza"
                     :registered? true :verified? true}
    "assignment-3" {:assignment-id "assignment-3" :site "Northgate Business Park (in intake)"
                     :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (assignment [_ assignment-id] (get-in @a [:assignments assignment-id]))
  (all-assignments [_] (sort-by :assignment-id (vals (:assignments @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-assignments [s assignments] (when (seq assignments) (swap! a assoc :assignments assignments)) s))

(defn seed-db
  "A MemStore seeded with the demo assignment directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `assignments` map (assignment-id
  string -> assignment map) -- the primary test/dev entry point.
  `assignments` may be empty (an unregistered-everywhere store)."
  [assignments]
  (->MemStore (atom {:assignments (or assignments {}) :ledger [] :coordination-log []})))
