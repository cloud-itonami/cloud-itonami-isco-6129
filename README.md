# cloud-itonami-isco-6129

Open Occupation Blueprint for **ISCO-08 6129**: Animal Producers Not
Elsewhere Classified.

This repository designs a forkable OSS business for an animal-
producer farm scheduling and logistics coordination practice: a farm-
scheduling robot manages feeding-schedule/animal-condition-check-in
records, crew/task scheduling, and feed/supplies procurement
coordination under a governor-gated actor — and structurally **never**
finalizes an animal-treatment/welfare/breeding decision, or overrides
a farm safety officer's judgment.

## This actor has no animal-treatment, welfare, breeding, or safety-officer-override authority

Animal Producers NEC (livestock/farm-animal breeders not covered by a
more specific ISCO-08 class) work directly with animals — this
carries BOTH a physical-safety dimension (animal-handling injury) AND
an animal-welfare dimension. **This actor is a farm scheduling/
logistics coordination robot ONLY.** It never handles the animals and
never directly makes a treatment/welfare/breeding decision. It has NO
op, anywhere in its allowlist, that resembles finalizing an animal-
treatment/welfare/breeding decision, or overriding a farm safety
officer's judgment. These are **structurally absent from the closed
op-allowlist entirely**, not merely gated behind escalation — under
any circumstance, at any confidence level, in any phase. Any
observation the robot logs that suggests an animal-welfare or injury-
risk concern is surfaced ONLY via an always-escalating
`:flag-welfare-concern` op that a human reviews and acts on entirely
themselves. This mirrors the Wave4 person-facing-service safety
guardrail (ADR-2607152500): decisions directly touching an animal's
welfare or a worker's safety always exclude the closed op allowlist
and always escalate. This actor's role ends at "here is the feeding/
roster/condition-check-in status" — it has zero authority over
treatment, welfare, or breeding decisions, which remain entirely with
the human farm safety officer/operator, at all times, with zero
exception.

**Maturity: `:implemented`.** `src/husbandry/` implements the
`HusbandryActor` as a `langgraph.graph/state-graph` (`husbandry.actor`)
wired to a `Farm Scheduling Advisor` (`husbandry.advisor`) and an
independent `HusbandryGovernor` (`husbandry.governor`), following the
itonami actor pattern (ADR-2607121000): `:intake -> :advise -> :govern
-> :decide -+-> :commit (:ok?) +-> :request-approval (:escalate?,
human-in-the-loop interrupt) +-> :hold (:hard?)`. Run `clojure -M:test`
for the current test count.

HARD invariants (always hold, never overridable): worker provenance (a
proposal must resolve to an independently registered AND verified
worker/farm-hand record), a closed four-op proposal allowlist (any op
outside it — including anything that would finalize an animal-
treatment/welfare/breeding decision, or override a farm safety
officer's judgment — is a permanent HARD block, because no such op
exists in the allowlist to begin with), no-actuation (`:effect` must
be `:propose`), a registered-and-verified farm basis (for the three
ops that reference one), a work-record-decision-forbidden check
(`:log-work-record` may only carry feeding-schedule/animal-condition-
check-in metadata, never a treatment or breeding decision), a crew-
schedule-override-forbidden check (`:schedule-crew-operation` may
only carry crew/task scheduling logistics, never a farm-safety-
officer-judgment override or a worker animal-handling-judgment
override), and a content-based scope-exclusion check: any proposal
whose free text names a finalization/execution action for an animal-
treatment/welfare/breeding decision, or an override of a farm safety
officer's or worker's safety judgment is a permanent HARD block,
independent of and in addition to the op-allowlist check. This actor
**never** exercises, simulates exercising, or proposes exercising any
animal-treatment/welfare/breeding decision, or any override of a farm
safety officer's judgment — it only documents feeding/roster records
and coordinates farm logistics.

Always-escalate (human sign-off regardless of confidence, mapping this
repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-welfare-concern` (surfacing an animal-welfare or injury-risk
concern that needs human review — always requires human review; never
auto-resolved, never in any phase's auto-commit set — this is the ONLY
channel by which such a concern may be surfaced) and any
`:coordinate-supply-order` above the registered per-farm cost
threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical/administrative domain work**. Here a farm-
scheduling robot performs feeding-schedule/animal-condition-check-in
data entry, crew/task scheduling, and feed/supplies procurement
coordination under an actor that proposes actions and an independent
**HusbandryGovernor** that gates them. The governor never dispatches
hardware itself; `:high`/`:safety-critical` actions (such as flagging
a welfare concern, or an above-threshold supply order) require human
sign-off — and no action in this actor's closed op allowlist can ever
finalize an animal-treatment/welfare/breeding decision, or override a
farm safety officer's judgment. This actor coordinates FARM
SCHEDULING/LOGISTICS ONLY — it never handles the animals and never
directly makes a treatment/welfare/breeding decision.

## Core Contract

```text
worker intake queue + farm roster directory + supply policy
        |
        v
Farm Scheduling Advisor -> HusbandryGovernor -> log record/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize an animal-treatment/welfare/breeding decision, override a
farm safety officer's or worker's safety judgment, suppress an
operating record, or disclose sensitive data without governor approval
and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `6129`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
