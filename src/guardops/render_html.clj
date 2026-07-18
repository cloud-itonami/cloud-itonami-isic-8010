(ns guardops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 iteration 11): this repo previously had no demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`guardops.operation` -> `guardops.governor` -> `guardops.store`)
  through a scenario adapted from this repo's own `guardops.sim` demo
  driver (`clojure -M:run`, confirmed by actually running it before this
  file was written -- unlike `cloud-itonami-isic-851`'s `schoolops.sim`,
  this repo's own sim driver uses ids (`assignment-1`/`assignment-2`/
  `assignment-3`) that DO match `guardops.store/demo-data`'s seeded
  post-assignments exactly, and every disposition it produced when run
  for real -- three phase-3 auto-commits, one always-escalate op
  approved by a human, and four HARD holds -- matched
  `guardops.governor`'s own documented checks and rule keywords
  precisely, so it was safe to reuse rather than author from scratch),
  trimmed to a representative subset (three distinct clean phase-3
  auto-commits on one post-assignment, the one op that ALWAYS escalates
  regardless of phase or confidence -- approved by a human security
  supervisor -- and three DISTINCT HARD-hold reasons that never reach a
  human) and rendered deterministically -- no invented numbers, no
  timestamps in the page content, byte-identical across reruns against
  the same seed (verified by diffing two consecutive runs before
  shipping).

  This actor coordinates only the BACK-OFFICE administration of an
  already human-directed private security operation (patrol/checkpoint
  logging, guard-shift/post-assignment scheduling proposals, incident-
  concern flagging, uniform/equipment supply coordination). It never
  dispatches a guard to respond to an incident, authorizes use of
  force, or authorizes detention/arrest -- see `guardops.governor`'s
  permanent, un-overridable scope-exclusion block.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [guardops.store :as store]
            [guardops.advisor :as advisor]
            [guardops.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :security-supervisor :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, using ONLY real assignment ids from
  `guardops.store/demo-data` (`assignment-1`/`assignment-2` registered
  AND verified, `assignment-3` registered but NOT YET verified):

  assignment-1 walks three distinct clean phase-3 auto-commits -- a
  `:log-patrol-record` checkpoint entry, a `:schedule-guard-shift`
  proposal, and a `:coordinate-equipment-supply` request -- each a
  member of phase 3's `:auto` set with a governor-clean, high-confidence
  advisor proposal.

  assignment-2 gets a `:flag-incident-concern` report (an unfamiliar
  vehicle circling the perimeter twice). This op is a member of
  `guardops.governor/always-escalate-ops` AND is permanently absent
  from every phase's `:auto` set in `guardops.phase` -- two independent
  layers agree it ALWAYS needs a human -- so it escalates regardless of
  the clean phase-3 context, and is approved by a human security
  supervisor.

  Then three DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation):
    - assignment-3 (registered but NOT independently `:verified?` in
      the store): a `:log-patrol-record` proposal HARD-holds on
      `:assignment-unverified` -- the governor never trusts a
      proposal's own claim about assignment status, only the store's
      own record.
    - assignment-1, advisor attempts a direct actuation (`:effect
      :commit` instead of `:propose`, via a swapped-in advisor): a
      `:schedule-guard-shift` proposal HARD-holds on
      `:effect-not-propose` -- any effect other than `:propose` is, by
      construction, a claim to actuate outside governance.
    - assignment-1, advisor drifts into the permanently-excluded guard-
      dispatch/use-of-force scope (the `:out-of-scope?` request hook
      `guardops.advisor/infer` itself exercises for exactly this
      regression path): a `:log-patrol-record` proposal HARD-holds on
      `:scope-excluded` -- this actor's charter structurally excludes
      dispatch-to-respond/use-of-force/detention/arrest territory,
      independent of confidence or how clean every other check is.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; assignment-1: clean patrol/checkpoint record -- phase-3 auto-commit.
    (exec! actor "a1-patrol" {:op :log-patrol-record :assignment-id "assignment-1"
                               :patch {:checkpoint "north-gate" :time "22:00" :status "clear"}})

    ;; assignment-1: clean guard-shift scheduling proposal -- phase-3 auto-commit.
    (exec! actor "a1-shift" {:op :schedule-guard-shift :assignment-id "assignment-1"
                              :patch {:guard "officer Alvarez" :shift "night" :date "2026-07-20"}})

    ;; assignment-1: clean equipment-supply coordination -- phase-3 auto-commit.
    (exec! actor "a1-supply" {:op :coordinate-equipment-supply :assignment-id "assignment-1"
                               :patch {:item "radio battery pack" :quantity 4 :urgency "routine"}})

    ;; assignment-2: incident-concern flag -- ALWAYS escalates, approved
    ;; by a human security supervisor.
    (exec! actor "a2-concern" {:op :flag-incident-concern :assignment-id "assignment-2"
                                :patch {:concern "unfamiliar vehicle circling the perimeter twice" :confidence 0.9}})
    (approve! actor "a2-concern")

    ;; assignment-3 is registered but NOT YET independently verified --
    ;; HARD hold on :assignment-unverified, never reaches a human.
    (exec! actor "a3-patrol" {:op :log-patrol-record :assignment-id "assignment-3"
                               :patch {:checkpoint "front-lobby" :time "06:00" :status "clear"}})

    ;; assignment-1: advisor attempts a direct actuation (`:effect
    ;; :commit`) -- HARD hold on :effect-not-propose, never reaches a
    ;; human.
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (exec! actor-direct "a1-direct" {:op :schedule-guard-shift :assignment-id "assignment-1"
                                        :patch {:guard "officer Nakamura" :shift "day" :date "2026-07-22"}}))

    ;; assignment-1: advisor drifts into the permanently-excluded guard-
    ;; dispatch/use-of-force scope -- HARD hold on :scope-excluded, never
    ;; reaches a human, permanent regardless of confidence.
    (exec! actor "a1-scope" {:op :log-patrol-record :assignment-id "assignment-1"
                              :out-of-scope? true :patch {}})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for
  "The key name on commit-fact/hold-fact records that carries the
  subject in this repo is `:assignment-id` (confirmed by reading
  `guardops.operation`'s `commit-fact` and `guardops.governor`'s
  `hold-fact`)."
  [ledger assignment-id]
  (last (filter #(= (:assignment-id %) assignment-id) ledger)))

(defn- status-cell [ledger assignment-id]
  (let [f (last-fact-for ledger assignment-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- assignment-row [ledger {:keys [assignment-id site registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc assignment-id) (esc site)
          (if registered? "<span class=\"ok\">registered</span>" "<span class=\"err\">unregistered</span>")
          (if verified? "<span class=\"ok\">verified</span>" "<span class=\"warn\">unverified</span>")
          (status-cell ledger assignment-id)))

(defn- coordination-row [{:keys [op assignment-id payload]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (name op)) (esc assignment-id)
          (esc (dissoc payload :assignment-id :approved-by))
          (if-let [by (:approved-by payload)]
            (esc by)
            "<span class=\"muted\">auto-commit (no human approval needed)</span>")))

(defn- ledger-row [{:keys [t op assignment-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc assignment-id)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                    (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`guardops.governor`/`guardops.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately hand-
  ;; described rather than derived from a live run.
  ["        <tr><td><code>:log-patrol-record</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; patrol/checkpoint/handover DATA LOGGING only, never a field-response decision</span></td></tr>"
   "        <tr><td><code>:schedule-guard-shift</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; a scheduling PROPOSAL only, never a binding assignment -- finalization is always a human shift supervisor's call</span></td></tr>"
   "        <tr><td><code>:coordinate-equipment-supply</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; ordinary operational consumables (radios, flashlights, hi-vis vests) &middot; never weapons or restraint equipment</span></td></tr>"
   "        <tr><td><code>:flag-incident-concern</code></td><td><span class=\"warn\">ALWAYS human approval, at any phase &middot; never a member of any phase's <code>:auto</code> set &middot; this op itself never decides how a concern is physically handled</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        assignments (store/all-assignments db)
        assignment-rows (str/join "\n" (map (partial assignment-row ledger) assignments))
        coordination-rows (str/join "\n" (map coordination-row (store/coordination-log db)))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-8010 &middot; private security activities</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Private security activities (ISIC 8010) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · guard dispatch/use-of-force/detention permanently out of scope</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Post-assignments (site contracts)</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>guardops.store</code> via <code>guardops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Assignment</th><th>Site</th><th>Registered</th><th>Verified</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     assignment-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Committed coordination records</h2>\n"
     "    <p class=\"muted\">Back-office scheduling/logging/reporting records only — never a directive to act; any physical response is always a human security supervisor's call.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Assignment</th><th>Value</th><th>Approved by</th></tr></thead>\n"
     "      <tbody>\n"
     coordination-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (GuardOpsGovernor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Post-assignment verification, proposal effect, and scope exclusion (guard dispatch-to-respond / use-of-force / detention / arrest) are independently checked, never trusted from the advisor's own proposal.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Assignment</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
