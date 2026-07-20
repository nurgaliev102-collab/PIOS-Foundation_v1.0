# PIOS Product Model

Read `AI_HANDOFF.md` and `PIOS_MASTER_CONTEXT.md` first. This file describes the current conceptual product model — actors and flows — distinguishing what exists **NOW**, what is **NEXT** (planned, not yet built), and what is **LATER / HYPOTHESIS** (genuinely uncertain, untested product direction). This is a conceptual model only; it contains no implementation design, no schema, no API shape.

---

## Actors

| Actor | Status | Description |
| --- | --- | --- |
| **Passenger** | `[RATIFIED]` | A person who requests or receives transportation (PRODUCT_FOUNDATION.md Section 6). Represented by Passenger Experience. |
| **Corporate Customer** | `[RATIFIED]` | An organization originating aggregated demand on behalf of its own members (PRODUCT_FOUNDATION.md Section 6). |
| **Independent Driver** | `[RATIFIED]` | The platform's primary stakeholder (PRODUCT_FOUNDATION.md Section 6); independent, self-determined availability, never an employee (Constitution Section 18). Represented by Driver Management. |
| **Dispatcher (historical)** | `[RATIFIED, superseded role]` | The historical human allocation role; PIOS's own Dispatch capability now performs this, though "operational participants may still interact with that capability on behalf of a fleet" (PRODUCT_FOUNDATION.md Section 6). |
| **Fleet** | `[RATIFIED as participant]`, `[OPEN as domain owner]` | Named in PRODUCT_FOUNDATION.md Sections 6–7; DOMAIN_MODEL.md Section 5 marks it "Domain Decision Required" — no ratified domain or ownership assignment exists. |
| **Platform Administrator** | `[RATIFIED]` | Oversees platform operation and governance (PRODUCT_FOUNDATION.md Section 6). Administration module not scaffolded. |
| **Network / Team Originator** | `[CURRENT DIRECTION]` / `[HYPOTHESIS]` | A driver who invites passengers and other drivers, growing a local network/team over time. No ratified document names this actor at all. Legal ownership and economic rights relative to this role are `[OPEN]` (see `PIOS_MASTER_CONTEXT.md` Section 9). |
| **Coordinator** | `[RATIFIED, pilot-only, not a production actor]` | A human process role introduced specifically for the Network Pilot (`PRODUCT_EXPERIMENT_NETWORK_PILOT_V1.md` Section 3) — explicitly not Dispatch, not a Dispatch override, and not a ratified PIOS actor beyond the pilot's own scope. Who fills it is `[OPEN]`. |
| **PIOS Platform** | `[RATIFIED]` | The eight-capability system itself (MODULE_STRUCTURE.md), providing infrastructure, never itself the transportation provider (PRODUCT_FOUNDATION.md Section 8). |

---

## Core Flows

### General (Platform) Demand — **NOW**

`[RATIFIED, CURRENT CODE]`. A passenger submits an order (Submit Order, real REST endpoint on Order Management as of Tranche 2); Order Management creates the Order; Dispatch is invoked directly via `AssignOrderCommand` with an externally-supplied driver reference (no automated trigger from Order Management to Dispatch exists or is ratified — this is the same "general Platform-Order-to-any-available-driver path" `PIOS_CONSOLIDATION_IMPLEMENTATION_READINESS_REVIEW.md` Part 5 confirms is the platform's oldest, most basic ratified mechanism, unaffected by any later Product Decision); Dispatch creates an Assignment; the driver accepts; Order Management is notified via the real, automatic outbox/RabbitMQ pipeline (Tranche 1 + the outbox relay trigger).

### Personal-Client-Directed Demand — **NOW** (as a relationship concept), **OPEN** (as a routing mechanism)

`[RATIFIED]` as a concept: a passenger with a recognized, recurring relationship with a specific driver requests transportation intending that driver to fulfill it (DOMAIN_MODEL.md Section 6, "Order Origin"). `[OPEN]`: whether such an order is even routed through Dispatch's assignment decision at all, or reaches the named driver through some other mechanism entirely — `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 4 states this directly, unresolved.

### Relationship-Preserving Fallback — **NOW** (manual, pilot-only)

`[RATIFIED, HYPOTHESIS-graded by design]`. The Network Pilot's own scenario, entirely manual, zero software: Driver A cannot fulfill → passenger authorizes fallback → a human Coordinator manually identifies Driver B → Driver B accepts → trip completes → payment stays direct. See `PIOS_MASTER_CONTEXT.md` Section 1: this is **one mechanism**, not the whole product, correctly scoped as the current, real, running pilot — see `docs/NETWORK_PILOT_LAUNCH_KIT_V1.md` for full operational detail.

### Electronic Dispatch (General, Automated) — **NEXT**, largely **OPEN**

`[RATIFIED as capability]`, `[OPEN as mechanism]`. Dispatch already decides assignments today, but with no candidate-pool, ranking, eligibility check, or fairness mechanism of any kind — `Assignment.create()` accepts any single, externally-supplied driver, always. A genuinely automated "opportunity reaches multiple eligible drivers, one accepts" flow requires Opportunity (still architecturally absent — `[OPEN]`), Eligibility (still `[OPEN]`), and a Fair Opportunity Policy mechanism (7 candidates classified, `[HYPOTHESIS]`, none chosen) — see `PIOS_OPEN_QUESTIONS.md`. **Do not build any of this without a further Product Decision selecting a mechanism.**

### Invitation — **LATER / HYPOTHESIS**

`[CURRENT DIRECTION]` / `[HYPOTHESIS]`, entirely unratified and unbuilt. A driver generates a personal invitation identity (QR/link/share/code); a passenger or another driver opens it; the system preserves who invited whom, through which channel, and whether the invitee became active. See `PIOS_MASTER_CONTEXT.md` Section 7.

### Driver Onboarding — **NOW** (backend concept only), **LATER** (any real invitation-driven onboarding UX)

`[RATIFIED, CURRENT CODE — narrow]`: `Driver` exists as a domain aggregate with an availability lifecycle (`DeclareAvailabilityCommand`); no frontend or invitation mechanism exists to actually onboard one. `[CURRENT DIRECTION]`: an invitation-driven, mobile-first onboarding experience is the desired direction, not yet designed at any implementation level.

### Passenger Onboarding — **NOW** (backend concept only), **LATER** (any real invitation-driven onboarding UX)

Same status split as Driver Onboarding above. Passenger Experience exists at the application layer only; no persistence, no frontend, no invitation mechanism.

---

## The Conceptual Domino Growth Loop

**`[HYPOTHESIS]` in its entirety — a candidate growth mechanism to observe, not a validated fact, exactly as the Product Owner's own framing insists:**

```
initial driver
   |
   v
brings existing passengers (their own pre-existing relationships)
   |
   v
demand grows
   |
   v
additional drivers are needed to serve that demand
   |
   v
new drivers join
   |
   v
new drivers bring their own existing passengers
   |
   v
demand grows further
   |
   v
more drivers join
   |
   v
network expands
```

**What is untested, stated precisely:** whether a small initial seed can create genuinely self-propagating local network growth at all; whether each new driver actually contributes both supply and pre-existing demand in practice (as opposed to supply alone); whether growth compounds or plateaus; what conditions (trust, visible value, low friction) are actually necessary for a driver to invite another. None of this is measured or claimed anywhere in `docs/`.

**Relationship to the Network Pilot:** the currently-running pilot (relationship-preserving fallback, above) tests a narrower, adjacent question (H1: will drivers share unmet demand; H2: will they pay for coordination) — it does not itself test the domino loop's own growth-compounding claim. A future, distinct pilot or instrumented rollout would be needed to observe the loop directly; this Project Brain does not design one.

---

## Status Summary Table

| Flow | NOW | NEXT | LATER / HYPOTHESIS |
| --- | --- | --- | --- |
| General demand → Dispatch (manual driver reference) | ✅ real code | — | — |
| Submit Order via real REST | ✅ real code | — | — |
| Assignment creation/acceptance, real outbox + RabbitMQ | ✅ real code | — | — |
| Personal-client-directed order routing | concept ratified | — | mechanism `[OPEN]` |
| Relationship-preserving fallback | ✅ running, manual pilot | — | production mechanism `[OPEN]`, gated on pilot evidence |
| Automated Electronic Dispatch (Opportunity/Eligibility/Fair Opportunity Policy) | — | candidate flow described | mechanism entirely `[OPEN]`/`[HYPOTHESIS]` |
| Invitation / attribution | — | — | `[CURRENT DIRECTION]`, unbuilt |
| Driver/passenger PWA onboarding | — | `[CURRENT DIRECTION]` | unbuilt, no frontend exists at all |
| Team/network graph, cross-network demand | — | — | `[HYPOTHESIS]`, explicitly do not implement |
| Domino growth loop | — | — | `[HYPOTHESIS]`, unmeasured |

---

No implementation design is contained in this file. See `PIOS_CURRENT_STATE.md` for what is actually built, and `PIOS_MVP_V01.md` for the candidate — not authorized — next-build direction.
