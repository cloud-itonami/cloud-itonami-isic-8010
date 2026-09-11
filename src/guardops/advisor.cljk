(ns guardops.advisor
  "GuardOpsAdvisor -- the *contained intelligence node* for the
  ISIC-8010 private-security-guard-services operations-coordination
  actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: patrol/checkpoint/incident-report data logging,
  guard-shift/post-assignment scheduling, incident/suspicious-activity
  concern flagging, and uniform/equipment supply coordination.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record and NEVER a direct actuation -- every proposal's
  `:effect` is always `:propose`. Every output is censored downstream
  by `guardops.governor` before anything touches the SSoT.

  This advisor NEVER drafts a guard dispatch-to-respond decision, a
  use-of-force authorization, or a detention/arrest decision -- those
  are permanently out of scope for this actor, not merely
  un-implemented. This is a purely administrative/coordination layer
  around an already human-directed security operation: it never
  decides how a live incident is physically handled.
  `guardops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised
  or confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Note on the default proposal text below: none of the four default
  rationale/summary strings name a dispatch/use-of-force/detention/
  arrest concept at all (not even to disclaim it) -- this is
  deliberate. A prior sibling actor in this fleet self-tripped its own
  governor's scope-exclusion check because its DEFAULT disclaimer text
  ('...no medication administered') itself contained the excluded bare
  noun. Rather than rely solely on phrasing exclusion terms as
  multi-word actions (which `guardops.governor` also does), the
  cleanest fix is for the happy-path proposal text to simply never
  restate the excluded concepts. See `guardops.governor-test` for a
  dedicated regression test asserting these defaults never self-trip.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :assignment-id str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-patrol-record
  "Draft a patrol/checkpoint/incident-report DATA LOGGING entry. Pure
  recording of an already-observed patrol pass, checkpoint transit, or
  handover confirmation -- never a field-response decision."
  [_db {:keys [assignment-id patch]}]
  {:op         :log-patrol-record
   :assignment-id assignment-id
   :summary    (str assignment-id " の巡回・チェックポイント記録を入力: " (pr-str (keys patch)))
   :rationale  "巡回経路・チェックポイント通過・受渡し確認のデータ記録のみ。現場対応の実行判断は含まない。"
   :cites      [assignment-id]
   :effect     :propose
   :value      (merge {:assignment-id assignment-id} patch)
   :confidence 0.93})

(defn- propose-guard-shift
  "Draft a guard-shift/post-assignment scheduling PROPOSAL only (never
  a binding assignment). Actual shift finalization is always done by
  a human shift supervisor."
  [_db {:keys [assignment-id patch]}]
  {:op         :schedule-guard-shift
   :assignment-id assignment-id
   :summary    (str assignment-id " の警備シフト・配置ポストを提案: " (pr-str (keys patch)))
   :rationale  "警備員のシフト・配置ポストの割当て提案のみ。確定は人間のシフト管理者が判断する。"
   :cites      [assignment-id]
   :effect     :propose
   :value      (merge {:assignment-id assignment-id} patch)
   :confidence 0.88})

(defn- propose-incident-concern
  "Surface an observed incident/suspicious-activity concern for HUMAN
  triage. This op ALWAYS escalates in `guardops.governor` -- never
  auto-committed at any phase -- regardless of how confident the
  advisor is that the concern is real. This op NEVER decides or
  suggests how the concern should be physically handled."
  [_db {:keys [assignment-id patch]}]
  {:op         :flag-incident-concern
   :assignment-id assignment-id
   :summary    (str assignment-id " のインシデント懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "現場で観察された不審行動・インシデントに関する懸念の報告のみ。対応の実行判断は常に人間が行う。"
   :cites      [assignment-id]
   :effect     :propose
   :value      (merge {:assignment-id assignment-id} patch)
   :confidence (or (:confidence patch) 0.85)})

(defn- propose-equipment-supply
  "Draft a uniform/equipment consumable supply coordination (radios,
  flashlights, hi-vis vests, patrol-log tablets -- ordinary
  operational consumables, never weapons or restraint equipment)."
  [_db {:keys [assignment-id patch]}]
  {:op         :coordinate-equipment-supply
   :assignment-id assignment-id
   :summary    (str assignment-id " に関連する装備品リクエスト: " (pr-str (keys patch)))
   :rationale  "制服・無線機・懐中電灯・防犯ベストなど消耗品の調達手配のみ。"
   :cites      [assignment-id]
   :effect     :propose
   :value      (merge {:assignment-id assignment-id} patch)
   :confidence 0.90})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-patrol-record (propose-patrol-record _db request)
                   :schedule-guard-shift (propose-guard-shift _db request)
                   :flag-incident-concern (propose-incident-concern _db request)
                   :coordinate-equipment-supply (propose-equipment-supply _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually dispatch the guard to respond and authorize the use of force")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :assignment-id (:assignment-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
