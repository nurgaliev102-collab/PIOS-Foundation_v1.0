# ADR-078: Dispatch Recovery After Proposal Decline or Lapse — Reopening the `dispatch_requests` Routing Obligation

## Status

**Accepted — 2026-09-16, by the Product Owner, in-session.** Q-1 answered "yes": a decline (not a lapse) now triggers one resumed attempt with the decliner excluded, within the order's original routing window. Q-2 answered "leave configuration as-is": the 5-minute lapse timeout and 2-minute routing window are not changed, so lapse recovery remains, in practice, always Decision A only (terminal `UNFULFILLED`, no second offer) — disclosed, not silently accepted, per Part on timing above.

**Proposed Date:** 2026-09-16. **Ratified Date:** 2026-09-16, by the Product Owner, in-session. **Author:** Architect role. **Ratification:** a human act (`ADR-007`).

All five decisions (A through E) are now authorized for implementation exactly as specified below. Decision B's exclusion-set mechanism, the "window does not restart" rule, and the "no numeric cap, time is the bound" rule all apply as written.

This ADR was scoped as "ordinary engineering: an ADR is required, but no business decision is" — the classification `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 5 gives it (line 243: *"the ADR is mandatory (`ADR-068:177` says so in terms), but the substance is a routing/reliability decision, not a product one, provided it reuses ADR-077's existing window and does not change the driver-selection rule."*).

**Reading the actual code, that classification holds for two of the three decisions below and does not hold for the third.** Stated plainly rather than forced into "Accepted":

- **Decision A** (reopen the routing obligation so a refused order can reach the honest terminal `UNFULFILLED` instead of hanging in `SUBMITTED` forever) offers **no driver to anyone**. It is pure reliability and reverses nothing a Product Owner ratified.
- **Decision C** (the passenger's own price refusal, `Proposal.declinePriceProposal`, stays exactly as it is) preserves a ratified instruction rather than changing one.
- **Decision B** (after a driver refuses, offer the order to the *next* eligible driver) **does decide who gets offered next.** That is the exact question `ADR-068` Part 6 **Q3** put to the Product Owner and the Product Owner answered — *"Q3 — no cascading retry. Confirmed as already stated in Part 4: one `attempt`, one tier evaluation, no descent on a later decline/lapse of the selected driver's own Proposal"* (`ADR-068` line 13, ratified 2026-09-15). `ADR-077` says twice, in terms, that reversing it needs *"its own ADR **and its own Product Owner answer**"* (`ADR-077` line 78; repeated at line 117). The evaluation document's Part 5 summary omits that second half; the ratified ADR text is authoritative over it.

So: **Decisions A and C are ready to ratify as engineering. Decision B is a Product Owner question, re-asked below as Q-1, and is not answered here** (`CLAUDE.md`, "Never Invent Business Rules"; `ADR-002`).

**Basis — stated honestly, because the brief for this ADR asked for a precedent that does not exist.** This ADR was asked to cite "the admissible basis `ADR-076`'s security half used — a confirmed reliability-defect remediation." `ADR-076` contains no such wording: its Status line 11 reads *"**Evidence basis: absent.** No H13/H14 exists in `docs/PIOS_PRODUCT_HYPOTHESES.md` (ends at H12). Registering one is a Product Owner act and is not performed here."* `ADR-077` line 11 says the same. The real defect-remediation precedent in this repository is **`ADR-069` line 18** — *"This ADR is written in response to a **confirmed live production defect**, not a feature request"* — and that ADR's defect was confirmed by direct read-only production query (`ADR-069` lines 30–52). This ADR therefore claims the weaker, true version of that basis:

> **The defect below is confirmed structurally, from source, at the file and line cited — it has *not* been observed in production, and no `E-NNN` entry bears on it.** The adjacent no-proposal case *was* live-confirmed (`docs/PIOS_AI_HANDOFF.md` lines 23 and 86); this one is the same mechanism's own scope gap, already recorded as KNOWN RISK #4 (`PIOS_AI_HANDOFF.md` line 73) and as blocking commercial V1 (`docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` line 63). **No hypothesis is registered by this document** — that is a Product Owner act, and `PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 8 (line 280) explicitly warns against an AI session registering one to unblock its own work. If Decision B is ratified, the Product Owner must supply the Sprint basis under `PIOS_PRODUCT_EVIDENCE.md`'s second admissible basis (lines 115–122) before implementation.

---

## Context

### The defect, read from source

`ADR-077` created a durable routing obligation per order and a bounded retry over it. The mechanism stops the moment any proposal exists:

`backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestApplicationService.kt` lines 77–82:

```kotlin
// A manual offer or an earlier event delivery wins. In particular,
// never declare an order unfulfilled after *any* proposal exists.
if (proposals.findByOrder(order).isNotEmpty()) {
    requests.markOffered(orderId)
    return
}
```

`markOffered` is terminal for the sweep: `V19__dispatch_requests.sql` lines 25–28 (`dispatch_requests_due_only_when_pending`) force `next_attempt_at IS NULL` for any non-`PENDING` state, and the partial index at lines 31–33 only serves `state = 'PENDING'`. The row can never be swept again.

Now follow a refusal through:

| Fact | Where it is recorded | What happens to the routing obligation |
|---|---|---|
| Driver declines an `OPEN` proposal | `Proposal.decline`, `domain/Proposal.kt` lines 273–280 → `ProposalApplicationService.declineProposal` lines 297–304 | **Nothing.** Row stays `OFFERED`. |
| Proposal lapses (timeout sweep) | `Proposal.lapse`, `Proposal.kt` lines 293–300 → `ProposalLapseApplicationService.lapseStaleProposals` lines 126–131 | **Nothing.** Row stays `OFFERED`. |

The only recovery that exists is narrower than it looks. `ProposalController.attemptFallbackAfterDecline` (`api/ProposalController.kt` lines 470–479) and `ProposalLapseApplicationService.attemptFallbackAfterLapse` (lines 147–163) both return early unless the refusing driver *was the passenger's current primary driver*:

```kotlin
val primary = primaryDriverRepository.findByPassenger(passengerReference) ?: return
if (primary.primaryDriverId != proposal.driver) {
    return
}
```

**So for every order whose proposal went to a named driver (`ADR-076`'s `requestedDriverId` path) or to a Fallback-selected driver, a decline or a lapse produces no recovery of any kind and no terminal status.** The order stays `SUBMITTED` in Order Management forever, the passenger's screen shows a saved order that will never move, and — unlike the no-proposal case `ADR-077` fixed — no `DispatchExhausted` is ever emitted, because the expiry branch (`DispatchRequestApplicationService.kt` lines 84–88) is unreachable once the row left `PENDING`.

This is strictly worse than the case `ADR-077` closed: there, the order at least reached an honest terminal state within two minutes.

### Why this is `ADR-068` being obeyed, not a bug in `ADR-077`

`ADR-077` Decision 4 (lines 70–78) states this outcome deliberately and correctly: the short-circuit *is* the mechanism that preserves `ADR-068` Part 4's ratified **"no retry on decline/lapse"** (`ADR-068` lines 177 and 186). Nothing was overlooked. The ratified position simply has a consequence — permanent silence — that was acceptable when "unmatched" meant "Coordinator-visible" and is not acceptable now that Dispatch owns a terminal `UNFULFILLED` the passenger is shown.

### One timing fact that changes the shape of this ADR

`ProposalLapseApplicationService`'s timeout defaults to **5 minutes** (`@Value("\${pios.proposal.lapse.timeout-minutes:5}")`, line 108). `ADR-077`'s routing window is **2 minutes** from `OrderSubmitted.occurredAt` (`DispatchRequestApplicationService.kt` lines 53 and 149).

**A proposal therefore cannot lapse before its order's routing window has already expired** — under the "continue the original window" rule recommended below, a lapse can never produce a second offer, only the honest terminal status. The lapse half of this ADR is consequently *entirely* Decision A, with no substitution possible at all. Only a **decline** — which a driver can perform seconds after receiving the offer — can land inside a live window and reach Decision B. This is disclosed rather than discovered later, and it is named as Q-2 below, because making lapse recovery actually re-offer anyone would require changing one of those two numbers (both configuration, not ratified business rules — `ADR-077` line 119, `ADR-051`/`ADR-052`).

---

## Decision

### Decision A — reopen the routing obligation on a negative resolution, so a refused order can reach a terminal state (**no substitution; ready to ratify**)

When a `Proposal` for an order transitions to `DECLINED` via `Proposal.decline` (the driver's own refusal) or to `LAPSED` via `Proposal.lapse`, Dispatch returns that order's `dispatch_requests` row from `OFFERED` to `PENDING` with `next_attempt_at = now`, **leaving `submitted_at` and `expires_at` untouched.**

Consequences of that single change, with nothing else added:

- If the window has already expired (always true for a lapse, per the 5-minute/2-minute fact above), the very next sweep takes the expiry branch, writes `DispatchExhausted`, and the order becomes `UNFULFILLED` — the passenger is told the truth instead of waiting forever.
- If the window is still open but no offer can be made (see the exclusion rule below), each sweep is a no-op and the order reaches `UNFULFILLED` at expiry.
- **No driver is offered the order who would not have been offered it before.** That property is what makes this half engineering rather than product.

Three mechanical requirements, each forced by code that exists today:

1. **The reopen is guarded by the row's own state, read `FOR UPDATE`.** Reopen **only** from `OFFERED`. A row in `CANCELLED` or `UNFULFILLED` is terminal and must not be revived — `ProposalApplicationService.handle` lines 129–133 already refuse a proposal for either state, and reviving the row would create a `PENDING` obligation that can never produce an offer. `DispatchRequestRepository` (`application/DispatchRequestRepository.kt` lines 21–29) has **no such method today**; it needs one (working name `reopenIfOffered(orderId, nextAttemptAt)`), conditional in SQL, not read-then-write.
2. **`WITHDRAWN` is excluded.** `Proposal.withdraw` (`Proposal.kt` lines 314–321) fires only because the order was cancelled (`ADR-053`), and `OrderCancelledApplicationService` already writes the `CANCELLED` tombstone. Reopening on withdraw would fight that tombstone.
3. **The short-circuit predicate at `DispatchRequestApplicationService.kt` line 79 must narrow.** `proposals.findByOrder(order).isNotEmpty()` would immediately re-mark the reopened row `OFFERED` on the next sweep, undoing the reopen. It must become "any proposal in `OPEN`, `PRICE_PROPOSED`, or `ACCEPTED`" — i.e. any proposal that is unresolved or resolved *positively*. The load-bearing comment on line 78 stays true in the sense that matters: an order is never declared unfulfilled while a live or accepted offer exists.

**The reopen must be written in the same transaction as the proposal's own resolution.** Today the decline path calls its fallback attempt *after* `declineProposal` returns (`ProposalController.kt` lines 455–456), outside that write's transaction. If the reopen is wired the same way and fails, the row stays `OFFERED` and the order strands exactly as it does now — the defect would appear fixed and silently not be. Placement is therefore a constraint, not an implementation preference: the reopen belongs inside `ProposalApplicationService.declineProposal`/`lapseProposal`'s existing `transactionRunner.run` boundary (lines 297–304, 322–329), or in a service that wraps both.

### Decision B — on a resumed attempt, offer the next eligible driver, excluding every driver who has already refused this order (**RATIFIED 2026-09-16 — Q-1 answered "yes"**)

This is the half that reverses `ADR-068` Q3. The Product Owner has answered Q-1 "yes"; this decision is now authorized for implementation exactly as specified below.

Once the row is `PENDING` again, `attemptOffer` (`DispatchRequestApplicationService.kt` lines 97–128) runs unchanged — and unchanged is wrong, in three separate ways that all point at the same missing input:

- **Named-driver branch** (lines 99–114): would re-offer the identical driver who just declined, every 5 seconds, for the rest of the window. `Proposal.propose` (`Proposal.kt` lines 335–355) only blocks a *second `OPEN`* proposal, so a `DECLINED` one does not stop it.
- **First Refusal branch** (lines 119–124): `FirstRefusalApplicationService.attempt` (`application/FirstRefusalApplicationService.kt` lines 131–137) takes **no `excludeDrivers` parameter at all**. A primary driver who just declined would be re-offered immediately.
- **Fallback branch** (line 126): `FallbackDispatchApplicationService.attempt` *does* take `excludeDrivers` (lines 153–158) but is called here with the default empty set, so the refusing driver — still `available = true` — is the first candidate `ORDER BY updated_at ASC` returns.

**The rule, if ratified:** a resumed attempt derives `excludeDrivers` from the order's own proposals already loaded at line 79 — every driver whose proposal for this order is `DECLINED` or `LAPSED` — and applies it to all three branches. Concretely:

- the named-driver branch is **skipped entirely** when the named driver is in the exclusion set (it is never replaced by a different driver — `Proposal.kt` line 241's ratified principle, *"PIOS does not substitute a random driver for the one a client came to"*, means an order that named a driver falls through to `UNFULFILLED`, it does not get a substitute);
- the `explicitDriverIntent`-without-ID early return (line 118) is unchanged — `ADR-062`'s complete-no-op guarantee is untouched;
- First Refusal gains an `excludeDrivers: Set<DriverReference> = emptySet()` parameter, additive with a default, following this module's established optional-parameter convention, and skips an excluded primary;
- Fallback receives the same set, which `ADR-068` Part 2 property 2 already requires to apply identically to every tier.

**What this does and does not change about selection:** the tier order (Trusted → Network → Open), the `updated_at ASC` tie-break, Tier 2's emptiness, and `ADR-069`'s test/real gate are all untouched — a resumed attempt is the *same* evaluation over a candidate set with the refusing driver removed, which is exactly what `excludeDrivers` already means at the two pre-existing decline/lapse call sites (`ADR-068` Context, the three-trigger table). **But the outcome is that a second driver is offered an order after a first driver said no, and that is `ADR-068` Q3's subject matter, answered "no" by the Product Owner on 2026-09-15.** It cannot be ratified by an architect.

**Second and subsequent refusals: no numeric cap.** The window is the cap. Retries are 5 seconds apart inside a 2-minute window, so at most ~24 attempts exist in total regardless of how many refusals occur, and the exclusion set strictly grows, so the candidate set strictly shrinks and cannot cycle. Inventing a "maximum 3 drivers per order" rule would be inventing a business rule (`ADR-002`); asserting "unlimited" without saying so would be worse. Stated explicitly: **the bound is time, not count.**

**The window continues from the original `OrderSubmitted.occurredAt`; it does not restart.** Recommended, with the reasoning rather than as an assertion:

- `expires_at` is already `submitted_at + 2 minutes` (`DispatchRequestApplicationService.kt` line 53) and `submitted_at` is the event's own `occurredAt`. Leaving both untouched is also the smaller change.
- A restart makes the passenger's total wait a function of how many drivers refuse — unbounded in principle, and `ADR-077` line 137 already flags two minutes as *"the longest a passenger will wait — a number nobody has validated against real passenger behavior."* Letting refusals extend it silently re-decides that number without anyone deciding it.
- The cost is disclosed, not hidden: a decline at t=115s leaves 5 seconds, i.e. effectively no recovery, and a lapse leaves none at all (see Q-2). The honest lever for that is the window length, which is configuration an operator sets (`ADR-077` line 119) — not a restart rule invented here.

### Decision C — the passenger's own price refusal is **not** in scope and is preserved exactly (**ready to ratify**)

`ProposalStatus.DECLINED` is reachable from two entirely different facts, and treating "the Proposal transitioned to `DECLINED`" as one trigger would silently reverse a ratified product instruction:

| Method | Who acts | Ratified meaning |
|---|---|---|
| `Proposal.decline` (`Proposal.kt` lines 273–280) | the **driver**, refusing an `OPEN` offer | no obligation exists before acceptance; a decline violates nothing |
| `Proposal.declinePriceProposal` (`Proposal.kt` lines 255–262) | the **passenger**, refusing the price the driver stated | *"Product Owner instruction, 2026-09-05: the request simply closes — no renegotiation, **no automatic reroute to another driver**"* (`Proposal.kt` lines 236–243) |

**This ADR triggers only on `Proposal.decline` and `Proposal.lapse`.** A passenger price refusal leaves the routing obligation exactly where it is, and the order closes as it does today. Reopening it would substitute a driver for a passenger who has just refused this ride's terms — the opposite of what the 2026-09-05 instruction says, and a product decision no one has taken.

### Decision D — the `DispatchExhausted` contract stays at version 1 with `reason = NO_OFFER_WITHIN_WINDOW`

Tempting and wrong to add a second reason code. `backend/order-management/src/main/kotlin/com/pios/ordermanagement/persistence/DispatchExhaustedListener.kt` lines 20 and 23 validate **strictly**:

```kotlin
require(envelope.get("eventVersion")?.asInt() == 1) { "Unsupported event version" }
require(data.get("reason")?.asText() == "NO_OFFER_WITHIN_WINDOW") { "Unsupported exhaustion reason" }
```

Any new reason value emitted by a deployed Dispatch would be rejected by the currently-deployed Order Management consumer and dead-lettered — and `PIOS_AI_HANDOFF.md` KNOWN RISK #3 already records that a dead-lettered `DispatchExhausted` wedges the order in `SUBMITTED` with no alert. So: **no payload change.** The literal string becomes slightly imprecise (an offer was made and refused), which is a naming cost, not a correctness one. If a distinct reason is ever wanted, it is a `ADR-030` widening that must be deployed consumer-before-producer, exactly the order already used for `ADR-075`/`076`/`077` (`PIOS_AI_HANDOFF.md` line 92) — named here so it is not attempted casually.

### Decision E — the two existing immediate-fallback call sites stay, and the duplication is disclosed

If Decision B is ratified, `ProposalController.attemptFallbackAfterDecline` and `ProposalLapseApplicationService.attemptFallbackAfterLapse` become partially redundant: the sweep would do the same thing within 5 seconds, for a wider set of cases. They are kept anyway — they produce an offer *immediately* rather than up to 5 seconds later, which matters to a waiting passenger.

Racing them is safe by construction and by an already-proven mechanism, not by timing luck: `Proposal.propose`'s one-open-proposal check (`Proposal.kt` line 342) and the `proposals_one_open_per_order` unique index (V14) collapse the loser to `FallbackDispatchOutcome.AlreadyAttempted` (`FallbackDispatchApplicationService.kt` lines 174–186). **Two mechanisms now aim at one outcome; that is real duplication debt and is recorded as such, not as a feature.** Consolidating them is a later, separate change.

---

## What this ADR does not authorize

Any change to the tier algorithm, the `updated_at ASC` tie-break, Tier 2's emptiness, or `ADR-069`'s test/real gate; any cascading *descent across tiers* on a negative result (a resumed attempt is one complete evaluation, identical to `ADR-077` Decision 5's narrowing); broadcast or parallel offers; reopening a `CANCELLED` or `UNFULFILLED` routing obligation; re-opening an `UNFULFILLED` **Order**; substituting any driver for a passenger's explicitly named driver (`Proposal.kt` line 241); any recovery after a passenger price refusal (Decision C); any change to `DispatchExhausted`'s payload or version (Decision D); any change to the lapse timeout or the routing window **values** — both remain configuration (`ADR-077` line 119) and neither may be cited as a product decision; any new cross-module call, read, or dependency, including on `network-management`; any pricing, commission, or matching rule (`ADR-002`).

---

## Consequences

### Positive

- The worst remaining silent-stranding path in the ride flow closes: an order whose only offer was refused reaches a terminal, passenger-visible `UNFULFILLED` instead of sitting in `SUBMITTED` forever. This is Decision A alone — it does not depend on Q-1 being answered "yes".
- The fix is entirely inside `dispatch`, reuses the table, scheduler, outbox, and event contract `ADR-077` already shipped and production already runs, and adds no table, no event, no endpoint, and no cross-module surface.
- The passenger-facing honesty property `ADR-077` established (`RideStatus.tsx`/`RideRequest.tsx` observe `UNFULFILLED` and offer a new order) is reused unchanged — no frontend work is implied.
- Reversibility is real: the reopen is one conditional SQL update: disabling it restores today's behavior exactly.

### Negative

- If Decision B is ratified, `ADR-068` Q3 — a Product-Owner-ratified answer — is reversed, and a driver who declines can now see the same order offered to someone else within seconds. That is a product-visible change in what a decline *means*, and it is the reason this ADR is not self-ratifying.
- The lapse path gains a terminal status but, as configured today, gains no second offer at all (5-minute lapse timeout vs 2-minute window). A reader who assumes "decline/lapse recovery" means "someone else gets the ride" will be wrong for lapse. Q-2.
- A decline near the end of the window leaves too little time to matter. Recovery quality now depends on *when* the refusal arrives — an uneven behavior with no product rule behind it.
- Duplication debt: two mechanisms aiming at the same recovery (Decision E).
- The `NO_OFFER_WITHIN_WINDOW` reason string becomes imprecise for refused orders (Decision D) — accepted deliberately to avoid a DLQ-wedging contract change.
- `DispatchExhausted` volume rises, since orders that previously produced no event now produce one. The DLQ alarm `ADR-077` line 135 called "not optional" and `PIOS_AI_HANDOFF.md` KNOWN RISK #3 records as still missing becomes more load-bearing, not less.

---

## Blocking Prerequisites — resolved

1. **Q-1 (Product Owner) — resolved 2026-09-16, "yes."** After a driver declines an offer, Dispatch may offer the same order to the next eligible driver (excluding the decliner) within the order's remaining routing window. Decision B is authorized.
2. **Q-2 (Product Owner) — resolved 2026-09-16, "leave configuration as-is."** Lapse recovery remains inert under today's numbers (5-minute lapse timeout vs. 2-minute routing window): a lapse still yields only the terminal `UNFULFILLED`, never a second offer. Only an explicit decline can land inside a live window. Disclosed here, not silently shipped.
3. **Sprint basis (Product Owner).** No `E-NNN` bears on this, and no hypothesis is registered by this document (Status, original text). The Product Owner's in-session ratification of this ADR (ratifying both the reliability fix and the reversal of `ADR-068` Q3) is the basis for this Sprint, recorded here per `PIOS_PRODUCT_EVIDENCE.md`'s admissible-basis requirement — not a registered `H-NNN` hypothesis, since this is a routing/reliability decision ratified directly by the Product Owner, not a behavioral claim requiring "как проверим / критерий успеха."
4. **`ADR-068` amendment pointer — promoted to ratified.** Updated in place from PENDING RATIFICATION to RATIFIED 2026-09-16, per `ADR-015`. `ADR-068`'s text is not deleted (`CLAUDE.md`, "Never Delete Documentation") — the original Q3 ratification stands as what was decided 2026-09-15, with the 2026-09-16 reversal recorded alongside it.
5. **`V19__dispatch_requests.sql` stale comment — fixed.** Line 1 corrected from `-- V20:` to `-- V19:` (doc-matches-code correction, not an architecture change).
6. **DLQ monitoring on `DispatchExhausted`** — still absent (`PIOS_AI_HANDOFF.md` KNOWN RISK #3), and this ADR increases traffic on that queue. Not a blocker for this ADR's own implementation, but named again here so it is not lost.

---

## Related ADRs

- [ADR-077](ADR-077-Dispatch-Routing-Obligation-Bounded-Retry-And-Unfulfilled-Order.md) — the mechanism extended here. Its Decision 4 (the deliberate no-proposal-only scope) is what this ADR closes; its window, interval, table, scheduler, and `DispatchExhausted`/`OrderUnfulfilled` contract are reused unchanged.
- [ADR-068](ADR-068-Relationship-Ordered-Fallback-Dispatch.md) — Part 4's **"No retry on decline/lapse"** is the passage Decision B would partially supersede, narrowly. The tier algorithm, the `updated_at ASC` tie-break, Tier 2's emptiness, `excludeDrivers` semantics, and Part 1's projection contract are **not** superseded. Pointer added in place, dated, per `ADR-015`.
- [ADR-069](ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md) — test/real gate, enforced unchanged on every resumed attempt; also the precedent for a defect-remediation ADR's own Status wording.
- [ADR-062](ADR-062-Primary-Driver-First-Refusal-Passenger-Experience-Dispatch-Contract.md) — `ExplicitDriverIntentDeclared`'s complete-no-op guarantee, preserved by the unchanged early return.
- [ADR-076](ADR-076-Server-Authorized-Named-Driver-Offer.md) — the `requestedDriverId` path; Decision B explicitly refuses to substitute a driver on it.
- [ADR-051](ADR-051-Proposal-Lifecycle-Resolution-Ownership.md) / [ADR-052](ADR-052-Proposal-Lapse-Resolution-Mechanism.md) — lapse ownership and mechanism, unchanged; the timeout remains configuration.
- [ADR-053](ADR-053-Proposal-Resolution-on-Order-Cancellation.md) — `withdraw` is excluded from this ADR's trigger (Decision A, requirement 3).
- [ADR-030](ADR-030-Event-Schema-Versioning-Strategy.md) / [ADR-032](ADR-032-Reliable-Event-Publication-Strategy.md) — outbox and versioning reused; Decision D declines to widen the contract.
- [ADR-015](ADR-015-Evolution-Strategy.md) — the in-place, dated, narrow supersession discipline followed for the `ADR-068` pointer.
- [ADR-002](ADR-002-Dispatch-Engine.md) — matching rules are out of architectural scope; Q-1 and the no-cap decision apply it.

## References

Read for this ADR, none modified (working tree, `pios-product-main`, 2026-09-16; the task brief records HEAD as `3c970bf` — not independently verified by this document):

- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestApplicationService.kt` (lines 43–59, 61–71, 73–95, 97–128, 148–152)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchRequestRepository.kt` (lines 5, 21–29)
- `backend/dispatch/src/main/resources/db/migration/dispatch/V19__dispatch_requests.sql` (lines 1, 11, 16–33)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalApplicationService.kt` (lines 128–157, 272–279, 297–304, 322–329)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalLapseApplicationService.kt` (lines 63–101, 108, 126–131, 147–163)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FallbackDispatchApplicationService.kt` (lines 86–109, 126–158, 174–186)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/FirstRefusalApplicationService.kt` (lines 131–137, 186–191)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt` (lines 420–467, 469–479, 492–499)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/domain/Proposal.kt` (lines 236–243, 255–262, 273–280, 293–300, 314–321, 335–355)
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/persistence/DispatchExhaustedListener.kt` (lines 17–27)
- `docs/ADR/ADR-068-Relationship-Ordered-Fallback-Dispatch.md` (lines 13, 149, 172–179, 186, 202)
- `docs/ADR/ADR-077-Dispatch-Routing-Obligation-Bounded-Retry-And-Unfulfilled-Order.md` (lines 11, 70–78, 117, 119, 135, 137)
- `docs/ADR/ADR-069-Test-Real-Segregation-In-Fallback-Driver-Selection.md` (lines 5, 18, 22, 30–52)
- `docs/ADR/ADR-076-Server-Authorized-Named-Driver-Offer.md` (line 11)
- `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` (lines 7, 83, 123, 243, 266–272, 280)
- `docs/PIOS_AI_HANDOFF.md` (lines 23, 54, 72–73, 86, 92)
- `docs/PIOS_TAXI_COMMERCIAL_EXECUTION.md` (lines 61, 63, 69)
- `docs/PIOS_PRODUCT_EVIDENCE.md` (lines 76, 84–97, 115–129)
