# cloud-itonami-8010

Open Business Blueprint for **ISIC Rev.5 8010**: private security activities
(guarding, patrol, escort and monitoring services provided under contract).

This repository designs a forkable OSS business for community private
security operations: guard/patrol registration, credential and licensing
management, robotics-assisted site patrol and monitoring, and incident
reporting — run by a qualified operator so a security firm keeps its own
dispatch and incident records instead of renting a closed guarding
platform.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (patrol, perimeter
monitoring, access-point observation) operate under an actor that proposes
actions and an independent **Private Security Governor** that gates them.
The governor never dispatches hardware itself; `:high`/`:safety-critical`
actions (any action involving use of force, entering a private residence,
handling a detained person) require human sign-off.

## Core Contract

```text
intake + identity + guard licensing + patrol assignment
        |
        v
Security Advisor -> Private Security Governor -> license, dispatch, incident report, or human approval
        |
        v
robot/guard actions (gated) + dispatch record + incident report + audit ledger
```

No automated advice can dispatch a guard/robot action the governor refuses,
license a guard outside their verified scope, or publish an incident report
without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `8010`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/labor`](https://github.com/kotoba-lang/labor) — guard contracts, shift timesheets, wages

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
