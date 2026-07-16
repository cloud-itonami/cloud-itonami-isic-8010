# Governance

`cloud-itonami-8010` is an OSS open-business blueprint for community private
security operations. The implemented `guardops` actor (`src/guardops/`) is a
**coordination-only** actor: it schedules, logs, and reports around an
already human-directed security operation. It NEVER dispatches a guard to
an incident and NEVER authorizes any physical intervention (use of force,
detention, arrest) — see the Scope section of [README.md](README.md).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- the `GuardOpsGovernor` remains independent of the `GuardOpsAdvisor` and
  can reject any proposal.
- a proposal claiming a guard dispatch-to-respond action, a use-of-force
  authorization, or a detention/arrest authorization is a HARD, permanent,
  un-overridable block — never gated to "requires human sign-off," never
  eligible for auto-commit at any rollout phase.
- `:flag-incident-concern` always escalates to a human and is never a
  member of any rollout phase's auto-commit set.
- every proposal's `:effect` must be `:propose`; any other value is a HARD
  block (a claim to directly actuate outside governance).
- the closed four-op proposal allowlist (`log-patrol-record`,
  `schedule-guard-shift`, `coordinate-equipment-supply`,
  `flag-incident-concern`) is not silently widened — an op outside it is a
  HARD scope violation.
- a post-assignment (site contract) must be independently registered and
  verified in the store before any proposal targeting it may commit or
  escalate.
- every commit, hold, and escalation is auditable via the append-only
  ledger.
- sensitive personnel and incident data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/` in this repo, and in the
`com-junkawasaki/root` superproject's `90-docs/adr/` — see
ADR-2630008010 (`cloud-itonami-isic-8010` — Private Security Operations
Coordination), the ADR that filled in this actor's implementation and
established the coordination-only scope framing. Changes to the trust
model, storage contract, public business model, operator certification,
or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, and data-flow
review.

Certified operators can lose certification for:
- widening the closed op-allowlist or weakening the scope-exclusion check
  without a governance review
- mishandling personnel or incident data
- misrepresenting certification status
- bypassing the governor or the append-only audit ledger
