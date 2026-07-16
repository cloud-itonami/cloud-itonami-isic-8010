# Business Model: Community Private Security Operations

## Classification
- Repository: `cloud-itonami-8010`
- ISIC Rev.5: `8010` — private security activities
- Social impact: public safety, crime deterrence, accountability

## Customer
- commercial property owners and facility managers needing guarding/patrol
- event organizers needing crowd/access-point security
- residential communities and gated developments
- programs that cannot accept closed, unauditable guarding platforms

## Offer
- guard-shift and post-assignment scheduling proposals
- patrol/checkpoint/incident data logging
- incident/suspicious-activity concern flagging (always human-reviewed)
- uniform/equipment supply coordination
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per site/contract
- support retainer with SLA

## Trust Controls
- this actor NEVER dispatches a guard to an incident and NEVER authorizes
  any physical intervention (use of force, detention, arrest) — those
  decisions are always made by a human already directing the security
  operation, structurally out of scope for this actor, not merely gated
- a proposal the independent governor refuses is never committed
- `flag-incident-concern` always requires human review before any action
  is taken on it
- a post-assignment cannot be proposed against outside its verified scope
- sensitive personnel and incident data stays outside Git
