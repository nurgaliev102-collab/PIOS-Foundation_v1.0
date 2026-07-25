# PIOS Product Decision: Personal Network MVP Transition v1.0

Status: Decided — Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7). This document explicitly and knowingly supersedes, for a narrow and named scope only, [PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md](PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md) Section 9 ("What Must NOT Be Built Before Pilot Validation") and the "manual, zero-software" operating premise of [NETWORK_PILOT_LAUNCH_KIT_V1.md](NETWORK_PILOT_LAUNCH_KIT_V1.md) and [PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md). It is not an ADR and does not itself select any aggregate boundary, module placement, database schema, or API shape — those remain separate architecture questions, addressed by the Sprint 7 technical plan and, where they touch aggregate/module boundaries, by a future ADR.

Derived from PROJECT_CONSTITUTION.md Section 7 (Product Owner authority), and from a direct conflict identified between the founder's own Sprint 7 proposal ("PIOS Personal Network MVP") and the previously-decided pilot boundary, surfaced during implementation-planning review of this repository's current code and documentation state.

---

## Conflict, Stated Explicitly

`PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 9 explicitly lists what must **not** be built before the fully-manual Network Pilot (`NETWORK_PILOT_LAUNCH_KIT_V1.md`) has been run and its results interpreted (Part 10 of that kit): no Opportunity aggregate/event/domain concept, no Eligibility check beyond manual admission, no Assignment Policy or algorithm, no Fulfillment Authority code or automated authorization mechanism, no new database migration, API, or event contract. That document's own "Recommended Next Step" was to stand up the manual pilot process, not to build software.

The founder's Sprint 7 proposal — a `Person`/`Role` core, a `Connection`/`Relationship`/`Invitation` concept, a personal profile link, and order routing that checks for a personal relationship before falling back to the general queue — is, architecturally, the software form of exactly the concepts Section 9 named. It also removes the Coordinator from the primary scenario; `NETWORK_PILOT_LAUNCH_KIT_V1.md` Part 3–4 deliberately kept the Coordinator human and outside PIOS software specifically so no dispatch mechanism gets built before pilot evidence exists.

This is a real conflict, not a misunderstanding on either side: the prior decision chain optimized for zero-code validation risk; this decision consciously accepts build risk instead, in exchange for testing the deeper hypothesis (PRODUCT_FOUNDATION.md's network vision, not only "can PIOS relay an order honestly") directly, with software, during the same real-world meeting/pilot already being prepared with Artur and Regina in Agidel.

## Decision

The Product Owner, acting also as product architect for this decision, chooses to move directly to a minimal software Personal Network MVP ("Sprint 7") rather than first completing the fully-manual Network Pilot described in `NETWORK_PILOT_LAUNCH_KIT_V1.md`. The manual pilot's own preparation (roles, scripts, case log, stop conditions) is not discarded — it remains valid reference material for how to run the Agidel meeting with real people — but its "zero new software" constraint no longer governs this specific scenario.

This decision is scoped narrowly. It authorizes moving forward on:

- A minimal identity concept distinguishing a person from their roles (driver/passenger/other), to the extent Sprint 7's technical plan requires it.
- A minimal relationship/invitation concept recording that one person invited or is connected to another, to the extent Sprint 7's technical plan requires it.
- Order routing that checks for an existing personal relationship before offering the order to the general queue — a single, explicit, transparent rule, not a ranking, score, or learned/automated policy.
- Removing the Coordinator from this specific scenario's primary path, replacing manual candidate selection with the explicit rule above.

It does **not** authorize, and every other exclusion in `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 3 and `NETWORK_PILOT_LAUNCH_KIT_V1.md` Part 4 remains exactly as excluded as before:

- No ranking, scoring, or AI/ML-based candidate selection.
- No reputation or reciprocity engine.
- No PIOS payment custody or commission engine.
- No city-scale or multi-city graph.
- No Fair Opportunity Policy for the *general-queue* fallback path beyond what already exists today (unchanged Coordinator-or-existing-flow behavior when no personal relationship exists).

## Rationale

**Why override now.** The founder judges that the network hypothesis (PRODUCT_FOUNDATION.md's core vision — not the narrower "can a fallback order be relayed honestly" hypothesis the manual pilot was built to test) is best tested by giving Artur and Regina an actual working circle of trust to use, in the same meeting where the vision itself is being presented, rather than by first running a separate, purely manual fallback-only experiment. This is a Product Owner judgment call about sequencing risk, not a claim that the prior decision was wrong when it was made.

**What this does not change.** The Fundamental Laws (PROJECT_CONSTITUTION.md Section 18) — Driver Independence, Trust Preservation, Fair Shared Dispatch, Transparency, Relationship Protection, No Hidden Algorithmic Decisions — are unaffected and remain binding on however Sprint 7 is actually implemented. In particular, "relationship first, else general queue" is chosen specifically because it is a single, explainable, non-hidden rule, not because Section 9's broader exclusions (ranking, scoring, AI selection) are being quietly re-opened — they are not.

## Files Changed

`docs/PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` (new). `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set for every preceding Product Decision. No ADR, code, database, event, or API is created or modified by this document itself — the accompanying Sprint 7 technical plan and any resulting ADR are separate artifacts.

## Recommended Next Step

Not an implementation task by itself. The Sprint 7 technical plan should identify, as an explicit open architecture question requiring its own decision before code is written, where the `Person`/`Connection` concepts are owned (a new module, or an extension of `driver-management`/`passenger-experience`) — consistent with this repository's own rule that architecture changes are recorded via ADR before or alongside the change (CLAUDE.md), not invented inline in a technical plan.

## Unresolved Decisions

1. Whether, and how, the fully-manual Network Pilot (`NETWORK_PILOT_LAUNCH_KIT_V1.md`) is still run in parallel for cohort members who are not part of the Artur/Regina software scenario — not decided here.
2. Module/bounded-context placement for `Person`/`Connection` — deferred to the Sprint 7 technical plan's own open-questions section and, if it changes aggregate or module boundaries, to a future ADR.
3. Every item in `PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md` Section 3 and Section 12 ("Unresolved Decisions") not explicitly re-opened above remains exactly as open as that document left it.

## References

- [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), Section 7 (Product Owner authority), Section 18 (Fundamental Laws)
- [PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md](PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md), Section 3, Section 9, Recommended Next Step
- [NETWORK_PILOT_LAUNCH_KIT_V1.md](NETWORK_PILOT_LAUNCH_KIT_V1.md), Part 3, Part 4, Part 9
- [PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md](PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md), Section 8
- [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md), Section 2 (network vision)
