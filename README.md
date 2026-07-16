# cloud-itonami-8010

Open Business Blueprint for **ISIC Rev.5 8010**: private security activities
(guarding, patrol, escort and monitoring services provided under contract).

This repository designs a forkable OSS business for community private
security operations: guard/patrol scheduling, patrol/checkpoint/incident
data logging, incident-concern flagging, and equipment coordination — run
by a qualified operator so a security firm keeps its own scheduling and
incident records instead of renting a closed guarding platform.

## Scope — coordination only, never dispatch/force/detention

**This actor NEVER dispatches a guard to an incident and NEVER authorizes
any physical intervention.** It is a purely administrative/coordination
layer around an ALREADY human-directed private security operation:
scheduling, logging, and reporting only. Concretely, the `guardops`
actor implemented in `src/guardops/` (see [Modules](#modules) below):

- drafts and proposes exactly four kinds of back-office record from a
  closed allowlist — patrol/checkpoint/incident data logging, guard-shift
  scheduling proposals, incident-concern flagging (always escalated to a
  human), and equipment/uniform supply coordination;
- never proposes, authorizes, or gates a guard dispatch-to-respond
  decision, a use-of-force decision, or a detention/arrest decision — any
  proposal that so much as claims one of those actions is a HARD,
  permanent, un-overridable block by the independent `GuardOpsGovernor`,
  not merely a milestone still to be enabled by rollout phase;
- treats "flag an incident concern" as always requiring human sign-off —
  it is never a member of any rollout phase's auto-commit set, so a human
  always decides how (or whether) to respond to a flagged concern.

Any live incident response, use-of-force decision, or detention/arrest
decision is always made by a human already directing the security
operation on the ground. This repository's earlier draft framing (a
"robotics-premised" actor that gates guard/robot dispatch itself) has
been superseded by this narrower, stricter design — see
[GOVERNANCE.md](GOVERNANCE.md) and the design ADR referenced there.

## Core Contract

```text
intake + verified post-assignment (site contract)
        |
        v
GuardOpsAdvisor -> GuardOpsGovernor -> commit | hold | human approval
        |
        v
patrol/checkpoint records, shift proposals, equipment requests,
incident-concern flags (always human-reviewed) + audit ledger
```

No automated advice can commit a proposal the governor refuses, and no
proposal claiming a dispatch/use-of-force/detention/arrest action can ever
commit or even reach human approval — it is rejected outright.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `8010`). This is a coordination-only actor (no robotics/physical
capability dependency): identity, forms, DMN/BPMN, and the audit-ledger
substrate cover its scheduling/logging/reporting proposal set.

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Features

- **Closed proposal-op allowlist**: `log-patrol-record`, `schedule-guard-shift`, `coordinate-equipment-supply`, `flag-incident-concern` (all `:effect :propose`).
- **Three HARD governor checks** (permanent, un-overridable):
  1. **Post-assignment verified** — target site contract must exist AND be registered/verified in the store.
  2. **Effect is `:propose`** — any other `:effect` value is rejected.
  3. **Scope exclusion** — guard dispatch-to-respond, use-of-force authorization, detention, and arrest are permanently blocked, phrased as the finalization/execution ACTION (not a bare noun) to avoid the advisor's own default disclaimer text ever self-tripping the check.
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: patrol-record logging only (approval-gated)
  - Phase 2: + guard-shift scheduling, equipment-supply coordination (approval-gated)
  - Phase 3: auto-commits clean, high-confidence proposals (incident concerns always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:dev:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

## Test suite

- `test/guardops/governor_test.clj` — unit tests of governor hard checks, scope exclusion, and a dedicated regression asserting the default mock-advisor proposals for every allowed op never self-trip the scope-exclusion check
- `test/guardops/advisor_test.clj` — advisor proposal shape and consistency
- `test/guardops/phase_test.clj` — rollout phase logic
- `test/guardops/governor_contract_test.clj` — full graph integration, audit trail
- `test/guardops/store_contract_test.clj` — Store protocol and MemStore implementation

## Modules

- `guardops.store` — SSoT (MemStore, String-keyed post-assignment directory, append-only ledger)
- `guardops.advisor` — contained intelligence node (mock + real-LLM seam)
- `guardops.governor` — independent compliance layer
- `guardops.phase` — staged rollout (0→3)
- `guardops.operation` — langgraph-clj StateGraph
- `guardops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 4 fleet. See ADR-2607121000
(Wave definition) and ADR-2630008010 (`cloud-itonami-isic-8010` —
Private Security Operations Coordination, this implementation) in the
`com-junkawasaki/root` superproject for design decisions.
