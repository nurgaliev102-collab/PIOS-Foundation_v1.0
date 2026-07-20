# PIOS MVP v0.1 — Candidate Direction

Read `AI_HANDOFF.md` first. **This document is not an implementation authorization.** It captures the current candidate MVP direction — the Product Owner's own stated intent to move quickly toward an installable, mobile-first PIOS experience and test it with real drivers — without authorizing this consolidation task, or any AI reading it, to build any of it. Every slice below requires its own explicit task commission before any code is written, per `.ai/EXECUTION_PROTOCOL.md`.

---

## Primary Objective

**`[CURRENT DIRECTION]`.** Get the smallest installable, mobile-first PIOS experience onto real phones quickly, and learn from real driver/passenger behavior rather than building the entire theoretical PIOS model first. Development philosophy: build the smallest usable vertical slice → put it on phones → test with real drivers/passengers → observe behavior → add functions based on real evidence, not upfront speculation.

**Grounding in already-`[RATIFIED]` material:** ADR-024 already selects the frontend technology (TypeScript, React for web, React Native for mobile) this direction would build with. No PWA-specific packaging or installability decision has been made; "PWA" here is Product Owner intent, not yet an ADR.

---

## Candidate Slice 1 — Invitation Identity and Attribution

**`[CURRENT DIRECTION]` / `[HYPOTHESIS]`. Do not implement.**

```
Driver opens PIOS
   -> has a personal invitation identity
   -> can display a QR code / share an invitation (link, native Share action, or short code)
   -> passenger opens the invitation on another phone
   -> sees who invited them
   -> understands the basic service proposition
   -> begins minimal onboarding/registration
   -> system preserves invitation attribution
```

**Explicit dependencies, none resolved:**

- No invitation, attribution, or identity concept exists anywhere in `docs/DOMAIN_MODEL.md` or `backend/` today (`PIOS_CURRENT_STATE.md`). This slice would require new domain modeling — a Domain-layer document — before any code, per Documentation First (Constitution Section 4).
- Authentication mechanism is `[OPEN]` (no ratified document defines one; `PIOS_CURRENT_STATE.md` confirms zero authentication code exists).
- "Invited by" vs. "relationship ownership" distinction (`PIOS_MASTER_CONTEXT.md` Section 7) must be preserved in any design — do not conflate them.
- Frontend surfaces to build this on do not exist yet at all — this would be the first frontend code in the repository.

## Candidate Slice 2 — Passenger Creates Demand/Order

**`[CURRENT DIRECTION]`. Do not implement.**

A passenger, having completed Slice 1's onboarding, creates a transportation demand/order. **What already exists to build on:** the real `POST /v1/orders` REST endpoint (Order Management, Tranche 2) and `OrderSubmissionRequestHandler` already accept a `passengerReference` and create a real `Order`. **What does not exist:** any connection between an invited/onboarded passenger identity (Slice 1, unbuilt) and the `passengerReference` this endpoint already accepts; any frontend to actually place the request.

## Candidate Slice 3 — Driver Online/Offline and Opportunity Handling

**`[CURRENT DIRECTION]`. Do not implement.**

A driver toggles online/offline; while online, they may receive and respond to an opportunity. **What already exists:** `Driver`/`Availability`/`DeclareAvailabilityCommand` (Driver Management), fully working, with a real event pipeline into Dispatch. **What does not exist:** any frontend; any concept of "opportunity" being *offered* to a driver for a decision (as opposed to Dispatch's current `Assignment.create()`, which requires the driver already decided/be decided for it by an external caller — there is no "driver receives and can decline an opportunity before assignment" flow in current code at all).

## Candidate Slice 4 — Minimal Electronic Dispatcher, Controlled Pilot

**`[CURRENT DIRECTION]`, touches material that is explicitly `[DEFERRED]`/`[OPEN]` elsewhere. Do not implement without a further Product Decision.**

```
driver goes online
   -> a real order enters the system
   -> the electronic dispatcher determines who receives an opportunity
   -> driver accepts/declines
   -> fallback to the next candidate if needed
   -> ride occurs
   -> ride is completed
```

**This flow's shape is fine to describe; its mechanism is not fine to build yet.** The step "determines who receives an opportunity" is exactly Opportunity + Eligibility + Fair Opportunity Policy + Assignment Policy territory — every one of which is explicitly `[OPEN]`/`[DEFERRED]` by name, in its own ratified Product Decision (`PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md`, `PRODUCT_DECISION_ELIGIBILITY_MINIMUM_TRUST_BOUNDARY.md`, `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`, ADR-034). **Do not silently implement any of them merely because this flow is desired.** A "controlled pilot" framing does not itself authorize skipping that decision — the existing, running Network Pilot already tests a narrower, fully manual version of "who receives an opportunity" (a human Coordinator), precisely because these are unresolved (see `docs/NETWORK_PILOT_LAUNCH_KIT_V1.md`).

## Candidate Slice 5 — Operational/Admin Visibility

**`[CURRENT DIRECTION]`. Do not implement.**

A minimal surface for whoever runs the pilot to see what is happening — cases, orders, assignments. No Administration module is scaffolded; ADR-024 assigns any operational surface to the Administrator interface (React/TypeScript web), not a separately justified application, but nothing has been built.

---

## Explicit Non-Resolutions (Restated, Not Silently Filled)

Building any of Slices 1–5 does **not** resolve, and must not be treated as resolving:

- **Opportunity** semantics beyond what's already ratified (`Assignment`'s CREATED/ACCEPTED split already covers Commitment; a true multi-candidate Opportunity concept remains architecturally absent).
- **Eligibility** — no formal criterion beyond manual admission exists or is authorized.
- **Fair Opportunity Policy mechanism** — 7 candidates named, none chosen; do not pick one implicitly by building Slice 4's "determines who receives" step.
- **Team/network economics** — entirely unaddressed by any of these slices; if a future slice touches invitation-driven team formation, it inherits every open question in `PIOS_MASTER_CONTEXT.md` Sections 9–10.
- **Legal structure** — building a prototype does not answer, and must not be described as answering, which of the three candidate legal structures (`PIOS_MASTER_CONTEXT.md` Section 12) PIOS operates under.
- **Payments** — no slice above includes PIOS taking custody of funds; if a future slice needs real payment flow, it is a distinct, unaddressed dependency, not something these slices quietly assume a shape for.
- **Pricing** — not addressed, not assumed, by any slice above.

---

## Dependency Summary

| Slice | Depends on already-built code | Depends on new domain modeling | Depends on an open Product Decision |
| --- | --- | --- | --- |
| 1. Invitation identity | none | yes — invitation/attribution concept | authentication mechanism |
| 2. Passenger creates order | Submit Order REST endpoint | yes — link identity to `passengerReference` | none beyond Slice 1's own |
| 3. Driver online + opportunity | `Availability`, event pipeline | yes — "offered opportunity" concept | none beyond Slice 4's own, if opportunity display implies selection logic |
| 4. Minimal electronic dispatcher | `Assignment.create()`, outbox/RabbitMQ pipeline | yes — Opportunity aggregate | **yes — Eligibility, Fair Opportunity Policy, Assignment Policy, all currently unresolved** |
| 5. Operational visibility | none (Administration unscaffolded) | yes — whatever is displayed | none directly, but exposes whatever Slices 1–4 leave open |

---

Nothing in this document is a commitment to build any slice in this order, on this timeline, or at all. It exists so a future AI or human can see the candidate shape of "what's next" without mistaking it for a decision already made.
