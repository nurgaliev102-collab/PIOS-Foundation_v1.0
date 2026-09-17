# PIOS Taxi — Handoff / Доверенная замена: Open Business Questions for the Product Owner

**Type:** decision-support document. **Not a decision, not an ADR, not an implementation authorization.** Prepared per explicit instruction, 2026-09-17.

**Purpose:** `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 4 already contains a full concrete technical design for Handoff/доверенная замена (a new Dispatch-owned `Handoff` aggregate, `Trip.executingDriver`, three-way consent) and Part 6 named the business questions that design cannot answer on its own. This document takes those questions one at a time and lays out concrete options with their consequences, so a decision can be made in one sitting rather than by re-deriving the trade-offs from scratch. **No option below is recommended over another** — `CLAUDE.md`'s "Never Invent Business Rules" and `.claude/CLAUDE.md`'s decision-authority rule both apply directly: this seat inspects and lays out, it does not decide.

**Standing reminder, unchanged by this document:** `ADR-054` Part 5 bans "Team, Fleet, crew, or delegation mechanism of any kind," and that ban is not touched, narrowed, or proposed for change here — any answer below still requires its own narrow, dated `ADR-015`-style supersession pointer into `ADR-054`, written only after these questions are answered, not before. Handoff also does not begin implementation on the strength of this document — a Sprint basis (hypothesis or confirmed defect, per `PIOS_PRODUCT_EVIDENCE.md`) is still required first, and registering that hypothesis is a Product Owner act (`PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 8).

---

## OQ-1 — May a driver hand off a commitment at all, and if so, at which points?

The evaluation's Part 4 design assumes handoff is possible; it does not assume *when*.

| Option | What it means | Consequences |
|---|---|---|
| **A — No handoff at all** | Keep the feature declined. A driver who can't fulfill a commitment lets it decline/lapse; `ADR-078` (just shipped) already recovers that honestly to `UNFULFILLED` or a substitute *PIOS itself* selects via the ordinary tiers. | Zero new complexity, zero new trust surface. A driver's business fully depends on their own personal availability — no continuity story for illness, breakdown, or a double-booking. This is the status quo. |
| **B — Only before arrival** | A handoff may be proposed any time after `Assignment` exists but before the trip's `arrive` transition. | Covers the common case ("I can't make it") cleanly — the passenger hasn't physically met anyone yet. Does not cover a breakdown mid-route. Smallest state-machine surface: `Trip` is still in a single, well-understood pre-execution state when the handoff resolves. |
| **C — Any time up to completion, including mid-ride** | A handoff may be proposed even after `arrive`/`start`. | Covers more real scenarios (mechanical failure mid-trip) but raises a genuine physical-world question this document cannot resolve in software terms alone: a passenger already in a moving vehicle would need to be handed to a different physical driver mid-transit — a safety/logistics question, not just a data-model one. Also complicates `Trip.executingDriver`'s semantics for a trip already `IN_PROGRESS`. |

## OQ-2 — Is "my driver named this substitute" itself a trust claim PIOS renders to the passenger?

`ADR-068:166` already rejected one form of driver-to-driver vouching ("explicit driver-declared peers," candidate N-c) as introducing a delegation/trust-transfer concept. A handoff's consent screen risks re-introducing the same shape by implication.

| Option | What it means | Consequences |
|---|---|---|
| **A — Yes, shown as an explicit endorsement** | The screen says something like *«Ваш водитель Артур попросил Регину выполнить эту поездку»*, framed as a recommendation. | Gives the passenger reassurance and context. Directly re-opens the vouching question `ADR-068` closed in a different context — would need that closure explicitly revisited, not silently reinterpreted. |
| **B — Neutral, factual language only** | *«Эту поездку выполнит другой водитель — Регина»*, no endorsement framing, no reference to the original driver's judgment. | Consistent with the existing anti-vouching stance and `DRIVER_IDENTITY_DESIGN_DECISION.md` §4 without needing to revisit either. Likely weaker passenger reassurance, which may lower the accept rate of the handoff itself. |
| **C — Neutral language, substitute shown exactly as any other driver would be** | Same as B, plus the substitute's name and (if genuinely known) real-time availability — the same two facts `getDriver`/`listDrivers` already expose publicly for any driver, nothing more. | Cleanest fit with existing precedent; adds no new category of disclosure. Still requires deciding B vs. this explicitly, since "exactly as any other driver" still needs the underlying language settled. |

## OQ-3 — After a consented handoff, whose ride count and earnings increment?

Today `driver_milestones.completed_rides_count` and the earnings sum both increment from `AssignmentCompleted.driverId`, which is currently always the same as `Assignment.driver`. A handoff makes "who committed" and "who executed" different for the first time.

| Option | What it means | Consequences |
|---|---|---|
| **A — Executing driver only** | Whoever actually drove gets the ride-count/earnings credit. | Matches "payment follows the work" as a literal reading. The committing driver — who held the client relationship and arranged the substitute — gets no credit at all for facilitating the ride, even in "Мой бизнес" stats. |
| **B — Committing driver only** | The driver who originally took the Assignment keeps the credit regardless of who executed. | Matches "the relationship stays with whoever created it" cleanly for CRM/milestone purposes. Directly conflicts with "payment follows the work" if earnings figures are meant to reflect who was actually paid for driving. |
| **C — Split by purpose** | Relationship-facing facts (repeat-client flag, "Мой бизнес" stats) credit the **committing** driver; ride-count/earnings credit the **executing** driver. | Most consistent with both stated principles simultaneously. Requires the most implementation work: `AssignmentCompleted` needs an additive `executingDriverId` field (same shape as `ADR-065`'s own precedent for additive event fields), and two different attribution rules replace today's single one. |

## OQ-4 — If the passenger refuses the handoff, what happens to the commitment?

Part 4's design defaults to "the original driver remains committed" without asking whether that's actually the intended behavior.

| Option | What it means | Consequences |
|---|---|---|
| **A — Original driver stays committed (Part 4's default)** | Nothing changes on refusal; the order proceeds exactly as if no handoff had been proposed. | No new state or policy needed — reuses `ADR-078`'s existing decline/lapse recovery if the original driver later can't fulfill it either. Passenger may be left waiting on a driver they already implicitly know is uncertain. |
| **B — Passenger may cancel without penalty at refusal** | Refusing the handoff also surfaces a no-penalty cancel action. | More passenger-friendly, gives an explicit exit instead of waiting on the original driver's own eventual decline. Requires a new carve-out in `ADR-053`'s cancellation rules, which were not designed with this trigger in mind. |
| **C — Both, on the same screen** | A alone happens automatically; B is offered as an explicit additional action. | Most complete for the passenger, but doubles the policy and UI surface that needs designing and testing. |

## OQ-5 — Does any fee, split, or commission attach to a handoff?

The core principle "no commission on a driver's own client" says nothing about a *different* driver executing that client's ride.

| Option | What it means | Consequences |
|---|---|---|
| **A — No commission, ever** | The core principle extends unmodified to the substitute. | Fully consistent with the existing principle. Gives the substitute no direct incentive from PIOS itself — any compensation between the two drivers happens entirely off-platform and informally. |
| **B — A commission on the substitute's earnings for this one ride only** | PIOS takes a cut specifically from the executing (non-relationship-owning) driver's stated price for this ride. | Arguably does not violate "no commission on a driver's *own* client," since the substitute isn't that client's driver. But this would be PIOS's first commission mechanic anywhere in the product — a precedent far bigger than this one feature, and would need its own separate justification, not one folded into Handoff. |
| **C — Off-platform, PIOS carries no financial role at all** | PIOS records the handoff but touches no money in connection with it. | Simplest; also matches the fact that Settlement itself (OQ-8) doesn't exist in the product yet, so there is nothing to attach a commission to even mechanically. |

## OQ-6 — May a substitute hand off again (chained handoff)?

Part 4 proposes "no, in v1" as a scoping choice, explicitly not a product answer.

| Option | What it means | Consequences |
|---|---|---|
| **A — No chaining (v1 scope)** | `fromDriver` on any `Handoff` is always the original committing driver; a substitute who also can't make it falls back to ordinary decline/lapse recovery (`ADR-078`), not a second handoff. | Simplest, bounds the state space, avoids chain-length edge cases entirely. |
| **B — Chaining allowed** | A substitute may themselves name a further substitute. | More resilient to a second driver also becoming unavailable, but multiplies the OQ-3 (credit) and OQ-2 (trust framing) questions across however many drivers end up in the chain. |

## OQ-7 — Does a handoff cap exist (per driver, per day, per client)?

| Option | What it means | Consequences |
|---|---|---|
| **A — No cap** | Any number of handoffs is permitted. | Simplest; trusts individual drivers. Risk: an uncapped pattern where one "driver" account is functionally operating as an informal dispatcher routing rides to several other people is exactly the crew/fleet shape `ADR-054` was written to prevent — a cap-free handoff could become a de facto backdoor around that ban in practice, even though nothing about it violates the ban in name. |
| **B — A numeric cap (e.g. N per driver per week)** | Handoffs beyond the cap are refused. | Guards against the pattern above, but the number itself would be an invented business rule with no evidence behind it yet — exactly what `CLAUDE.md` forbids inventing from this seat. |
| **C — No formal cap now, usage monitored/reported** | No enforcement, but handoff frequency per driver becomes visible (e.g. in an owner-facing view), so a real pattern is caught before acting rather than guessed at in advance. | Defers the numeric decision until real data exists — consistent with this project's own evidence-gated Sprint discipline (`PIOS_PRODUCT_EVIDENCE.md`). Requires no new invariant, only a query/reporting surface, so it is the lowest-commitment option if the concern in A is taken seriously without wanting to invent B's number today. |

---

## Adjacent questions from the same evaluation, not Handoff-specific

The evaluation document's Part 6 lists ten open questions in total; the seven above are the ones Handoff's own design cannot proceed without. Three more appear in the same list but belong to separate, not-yet-scoped decisions — named here only so nothing is lost, not expanded on, since they are not this document's subject:

- **OQ-8 (Settlement)** — what event constitutes "settled," and is a settlement record required for a ride to count complete. This is Settlement evidence work (`PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` §7, NEXT STEP item 3 in `docs/PIOS_AI_HANDOFF.md`), not Handoff — Handoff's own OQ-5 above depends on Settlement existing at all, but resolving OQ-8 is a separate task with its own document, if and when the Product Owner takes it up.
- **OQ-9 (ADR-065 price parsing)** — whether the digits-only stated-price parsing rule was ever formally confirmed, given its own text says it required product-owner confirmation and none is recorded. Relevant background for any future Settlement work, not something Handoff's design depends on.
- **OQ-10 (Fallback Tier 2 / Network)** — whether `ADR-073`'s `invitedByDriverId` changes the answer to `ADR-068`'s Q2. Entirely unrelated to Handoff; it's `ADR-068`'s own reserved question, unchanged by anything in this document.

---

## How to use this document

Each table above is independent — answering OQ-1 does not constrain the answer to OQ-2, and so on — except where noted (OQ-5 depends on OQ-8; OQ-3's "split" option C requires the same additive-event-field mechanism regardless of which other options are chosen). A partial answer is useful: even "OQ-1: B, OQ-6: A, everything else deferred" is enough to scope a first, narrow version of Handoff, if that is the Product Owner's preference. Nothing here needs to be answered all at once.

When ready, the next step is: the Product Owner states which options are chosen (or explicitly defers a given question, which is itself a valid answer — e.g. "ship without a cap, revisit OQ-7 after real usage data exists"), a Sprint hypothesis is registered per `PIOS_PRODUCT_EVIDENCE.md`, and only then does an ADR narrowly superseding `ADR-054` Part 5 get written, followed by implementation per Part 4's existing technical design.
