# Operator Guide

## First Deployment
1. Register operator, sites/contracts (post-assignments), and personnel.
2. Import existing patrol-record and incident-reporting history.
3. Run read-only patrol-record and shift-scheduling dry-runs (Phase 0).
4. Configure the rollout phase (0→3) and human approval paths.
5. Publish a dry-run coordination log and audit export.

## Minimum Production Controls
- post-assignment (site contract) verification before any proposal
- governor gate on every proposal before commit
- a proposal claiming a guard dispatch-to-respond, use-of-force, or
  detention/arrest action is always a HARD, permanent block — never
  eligible for human sign-off as a substitute for that block
- `flag-incident-concern` always requires human sign-off, at every phase
- audit export for every commit, hold, and escalation
- backup manual scheduling/logging process for outages

## Certification
Certified operators must prove scope-exclusion discipline (dispatch/
use-of-force/detention/arrest content is always blocked, never merely
gated), post-assignment verification discipline, and evidence-backed
incident-concern reporting.
