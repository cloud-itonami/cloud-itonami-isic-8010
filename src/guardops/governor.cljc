(ns guardops.governor
  "GuardOpsGovernor -- the independent compliance layer that earns the
  GuardOpsAdvisor the right to commit. The advisor has no notion of
  whether a post-assignment (site contract) is actually registered and
  verified, whether its own proposed `:effect` secretly claims a
  direct actuation instead of a mere proposal, or whether it has
  silently drifted into a permanently out-of-scope decision area, so
  this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  This actor's scope is deliberately narrow -- COORDINATION ONLY
  (patrol/checkpoint/incident-report data logging, guard-shift/post
  scheduling proposals, incident-concern flagging, uniform/equipment
  supply coordination) around an ALREADY human-directed private
  security operation. It NEVER performs or authorizes:
    - dispatching a guard to respond to an incident
    - any use-of-force decision or authorization
    - detention of any person
    - arrest or arrest-adjacent action

  Any decision in that territory is always either a permanent HARD
  block (this governor) or an always-escalate-to-human op -- NEVER an
  op eligible for auto-commit at any rollout phase. This actor never
  dispatches a guard/robot to an incident and never authorizes any
  physical intervention; it only coordinates the scheduling, logging,
  and reporting layer around a security operation a human is already
  directing.

  Three HARD checks, ALL permanent, un-overridable by any human
  approval:

    1. Assignment unverified     -- the target post-assignment
                                     (site-contract) record must exist
                                     AND be independently confirmed
                                     `:registered?`/`:verified?` in the
                                     store before ANY proposal for it
                                     may commit or even escalate. Never
                                     trusts a proposal's own claim
                                     about the assignment -- re-derived
                                     from the assignment's own store
                                     record, the same 'ground truth,
                                     not self-report' discipline every
                                     sibling actor's governor uses.
    2. Effect not :propose       -- every proposal's `:effect` MUST
                                     be `:propose`. Any other effect
                                     value is, by construction, a
                                     claim to directly actuate/commit
                                     outside governance -- HARD block,
                                     not merely low-confidence.
    3. Scope exclusion           -- ANY proposal (regardless of op)
                                     whose op, rationale, summary,
                                     citations or draft value touches
                                     guard-dispatch-to-respond/
                                     use-of-force-authorization/
                                     detention/arrest territory is a
                                     HARD, PERMANENT block -- this
                                     actor's charter excludes that
                                     territory structurally, not as a
                                     rollout milestone. Evaluated
                                     UNCONDITIONALLY on every
                                     proposal. An op outside the
                                     closed four-op allowlist is the
                                     SAME failure mode (an advisor
                                     proposing something it was never
                                     authorized to propose) and is
                                     folded into this same check.

  One ESCALATE (SOFT) gate: LLM confidence below the floor, OR the op
  is `:flag-incident-concern` -- ALWAYS escalates to a human, regardless
  of confidence, regardless of how clean the proposal otherwise is.
  `guardops.phase` independently agrees: `:flag-incident-concern` is
  never a member of any phase's `:auto` set either -- two layers, not
  one.

  IMPORTANT (self-trip discipline): every string in
  `scope-excluded-terms` below is phrased as the finalization/execution
  ACTION ('authorize the use of force', 'dispatch a guard to respond',
  '実力行使を許可'), never as a bare noun ('force', '拘束'). A sibling
  actor in this fleet previously self-tripped its own scope-exclusion
  check because a bare-noun term matched inside its own default
  advisor's disclaimer text on a legitimate, allowed proposal. Phrasing
  every term as the action alone (not the topic) keeps a legitimate
  proposal that merely mentions the topic in passing -- or, as here,
  a default advisor that never mentions the topic at all -- from
  colliding with this list. `guardops.advisor`'s default proposals are
  additionally written to never restate these concepts at all (belt
  and suspenders); see `guardops.governor-test` for the regression
  test asserting this holds for every default proposal."
  (:require [clojure.string :as str]
            [guardops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`)."
  #{:log-patrol-record :schedule-guard-shift
    :coordinate-equipment-supply :flag-incident-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-incident-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as claiming a
  permanently out-of-scope EXECUTION action -- guard dispatch-to-
  respond, use-of-force authorization, detention, or arrest. Phrased
  as the action itself (verb + object), never a bare topic noun --
  see the self-trip discipline note in the namespace docstring.
  Scanned across the proposal's op/summary/rationale/cites/value,
  never trusting the advisor's own framing of its intent."
  ["dispatch a guard to respond" "dispatch the guard to respond"
   "dispatch guard to respond" "dispatch to respond"
   "direct the guard to respond" "send the guard to respond"
   "order the guard to respond" "guard to respond to the incident"
   "authorize the use of force" "authorize use of force"
   "authorize physical force" "authorization to use force"
   "use-of-force authorization" "use of force authorization"
   "authorize detention" "authorize the detention"
   "authorize an arrest" "authorize the arrest" "authorize arrest"
   "place under arrest" "place the individual under arrest"
   "make an arrest" "detain the individual" "order the detention"
   "direct the guard to detain"
   "現場へ急行させる" "現場に急行させる" "現場への出動を指示"
   "臨場を指示" "警備員に対応を指示" "警備員を現場に向かわせる"
   "実力行使を許可" "武力行使を許可" "強制力の行使を許可"
   "身柄を拘束することを許可" "拘束を許可" "逮捕を許可"
   "現行犯逮捕を指示"])

;; ----------------------------- checks -----------------------------

(defn- assignment-unverified-violations
  "The target post-assignment must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the
  proposal's own `:assignment-id` claim without a store lookup."
  [{:keys [assignment-id]} st]
  (let [a (store/assignment st assignment-id)]
    (when-not (and a (:registered? a) (:verified? a))
      [{:rule :assignment-unverified
        :detail (str assignment-id " は未登録または未検証の配置契約 -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content claims a dispatch-to-respond/use-of-force/
  detention/arrest EXECUTION action, regardless of confidence or how
  clean every other check is. Evaluated UNCONDITIONALLY on every
  proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "現場対応の指示/実力行使の許可/身柄拘束や逮捕の許可に触れる提案は永久に禁止"}])))

(defn check
  "Censors a GuardOpsAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [assignment-id (or (:assignment-id proposal) (:assignment-id request))
        hard (into []
                   (concat (assignment-unverified-violations {:assignment-id assignment-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (always-escalate-ops (:op proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :assignment-id (:assignment-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
