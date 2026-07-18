# Governance

`cloud-itonami-isco-6129` is an OSS open-occupation blueprint. Governance covers
both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Advisor cannot directly dispatch robot actions, handle the animals, or
  disclose records.
- HusbandryGovernor remains independent of the advisor.
- hard policy violations cannot be overridden by human approval.
- the closed op allowlist never gains an op that finalizes an animal-
  treatment/welfare/breeding decision, or overrides a farm safety officer's
  judgment.
- every commit, hold and approval path is auditable.
- real worker/animal/farm/operator data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, support and data-flow
review.

Certified operators can lose certification for:

- bypassing policy checks
- mishandling worker/animal/farm/operator data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
- attempting to route an animal-treatment/welfare/breeding decision, or a
  farm-safety-officer-judgment override, through this actor by any means
