# PIOS Taxi Architecture Convergence Review

Status: **Correction pass over `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` (Task 7), which remains a DRAFT, unapproved.** This document does not replace Task 7 — it identifies specifically where Task 7's analysis was incomplete or too hasty, and proposes corrections. Like Task 7, everything here is a **proposal for architectural review**, not a ratified decision (`.claude/CLAUDE.md`'s own role division: Claude does not redesign architecture proactively; the architect approves, requests an ADR, or blocks). No code, database, schema, API, event contract, or configuration was touched. No ADR was created.

**New source material this review adds, beyond what Task 7 used:** `docs/PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`, `docs/ADR/ADR-037-Network-Management-Module-Bounded-Context-Extension.md`, `docs/PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md`, `docs/PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md` (an uncommitted draft, explicitly not itself a ratified decision), `docs/ADR/ADR-054-...Circle-of-Trust...md`, and direct re-inspection of `Connection.kt` (Passenger Experience) and `PersonProfile.kt`/`ProfileType.kt` (Network Management). These materially change two of Task 7's conclusions — Sections 5 and 8 below explain exactly where and why.

**Tagging convention** (unchanged from Task 7): **FACT** / **DECISION** (this document's own recommendation) / **ASSUMPTION** / **FUTURE**.

---

## 1. Executive Verdict

Task 7 is **directionally correct but was too hasty in exactly one place: Network convergence (its own Section 14).** It correctly refused to make Taxi V1 *depend* on Network Management today (that remains right), but it stated the relationship as "Taxi requires: none" and treated further integration as a vague, optional nicety. New evidence gathered for this review shows that is understating a real, already-ratified product intention: `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` explicitly authorized "order routing that checks for an existing personal relationship" as the actual point of building Network Management, and `ADR-037`'s own Consequences section names the missing Dispatch integration as **deferred, not rejected** — "will need its own ADR when it introduces the first real cross-module dependency." Task 7 treated this as if it had never been decided at all. Section 5 below corrects this.

Everything else in Task 7 — the Trip split (Section 10), the `OrderSubmitted` decision (Section 9), the Payment/Rating boundaries (Sections 11–12), the simplicity classification (Section 23) — holds up under this review and is **kept unchanged**, with targeted strengthening noted where new evidence makes an existing conclusion *more* certain, not different (Sections 2, 7, 11 below).

The single most important correction this review makes is **conceptual, not structural**: Task 7 modeled "repeat relationship" correctly in spirit (Section 21) but underused the actual, ratified language already sitting in the repository — *"Связь даёт преимущество. Качество подтверждает право. Оплата следует за трудом"* (Connection gives advantage. Quality confirms the right. Payment follows the work.) — which is a sharper, product-owner-facing articulation of exactly what Task 7 was reaching for. Section 7 restates the driver-business-platform architecture using this language directly.

## 2. What Task 7 Got Right

- **The Order/Assignment/Trip split** (Task 7 Section 10) — re-validated in Section 11 below against repeat rides, scheduled rides, cancellation, payment, rating, and future services. Holds.
- **The `OrderSubmitted` decision** (Task 7 Section 9) — Option B (Coordinator REST trigger, no Dispatch consumer) is reaffirmed. `EVENT_CATALOG.md`'s own independent classification of `OrderSubmitted` as "not currently a business event" (re-confirmed this review) matches Task 7's reasoning exactly.
- **Refusing to design a ranking/scoring algorithm** — consistent with `ADR-034` Part 2's forbidden-inputs list and `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md`'s own fairness-first ordering, both re-confirmed by the newly-read `PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md` draft, which independently arrives at the same constraint from a different angle (Section 6 below).
- **Payment isolated from Trip for retry/async reasons** (Task 7 Section 11) — unchanged, correct.
- **Cash-first V1 Payment scope** (Task 7 Section 11) — unchanged, correct, and now additionally supported by the newly-found "Ценность распределяется по фактическому вкладу... оплата следует за трудом" principle (Section 7 below) — payment must track who actually did the work, which a cash-confirmed-by-driver model already satisfies structurally.
- **Rejecting a 7th microservice for Trip** (Task 7 Section 23) — unchanged, correct.
- **Not inventing a pricing model** (Task 7 Section 22) — unchanged, correct.

## 3. What Task 7 Got Wrong

- **Section 14 ("Minimal Network capability required for Taxi V1: none")** — incomplete. It is *correct* that Taxi V1 must not *depend* on Network Management to launch, but it failed to account for the already-ratified product intention (Section 5 below) that Network Management's `Person`/`Connection`/`Invitation` model is the intended long-term home for cross-vertical, any-participant relationships — a fact `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.6 states directly: a network where *any* participant (not only a driver) can originate further connections "is exactly what `network-management`'s already-implemented, currently-unused... model was built for." Task 7's framing risks a future engineer reading "requires: none" as "irrelevant," when the ratified record says "deliberately sequenced later," a materially different claim.
- **Section 12 (BlaBlaCar principles) adopted too few principles too vaguely** — it listed "trust," "transparency," "repeat relationships" as present/partial without phasing verification, profile completeness, and bidirectional history explicitly across Pilot/V1/Future. Section 6 below corrects this with an explicit matrix.
- **Section 21 (driver business model) was correct in conclusion but under-cited** — it independently arrived at "not platform-owned, not driver-owned" without knowing `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.1 had already ratified exactly this, in almost the same words, extending the Constitution's own Relationship Protection law explicitly to the driver's side, not only the platform's. This is corrected in Section 7 below, with the stronger citation.
- **Section 8 (Dispatch) implied Circle of Trust's `isPrimary` already functions as a soft priority signal** ("Personal Client priority signal (optional, surfaced not enforced)"). Direct re-verification this review (`grep isPrimary` across Dispatch and Coordinator) found **zero references** — `isPrimary` is consumed nowhere outside Passenger Experience's own read model. It is purely a passenger-facing UI fact today, not even weakly a dispatch input. Task 7's phrasing overstated how "real" this seam already is. Corrected in Section 8 below.
- **The Coordinator's role was frozen as V1's own end-state** rather than staged — Task 7 Section 8 recommended keeping the Coordinator as *the* V1 mechanism without describing how it should recede as relationship-aware routing matures. Section 9 below adds the staged model this task explicitly requested.

## 4. Pilot vs Taxi V1 vs Network

Definitions used consistently through the rest of this document (**DECISION** — this three-way split, while implicit in Task 7, was never stated as its own governing distinction; making it explicit is this review's own structural correction):

- **Pilot** = what is running today, or what the very next, smallest change would add, validated with a handful of real drivers (Agidel — Artur, Regina — per `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md`'s own real scenario). Bias: prove the concept works with real people, minimum new code.
- **Taxi V1** = the first real commercial-readiness milestone for the Taxi vertical specifically — payment exists, rating exists, verification exists, Eligibility exists — but still human-mediated dispatch, still cash-first, still one vertical.
- **Future Network** = capabilities that only make sense once more than one vertical exists, or once Network Management is actually deployed and integrated — cross-vertical reputation, a general (any-participant) relationship graph, business-to-business relationships.

## 5. Network Convergence

**Direct answer to the question this task poses** ("should this duplication remain permanently, converge later, or be replaced by a shared conceptual model without shared mutable storage?"): **converge later, at the conceptual level only — never by merging the two `Connection` tables or the two modules.**

**Why not "remain permanently":** the ratified record (`PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.6, FACT) already identifies that a general, any-participant network graph — the actual long-term PIOS Network vision this task's own prompt describes ("participants from different industries... find each other, transact... build repeat business") — structurally cannot be represented by Passenger Experience's own `Connection` (FACT, re-read this review: two fixed roles, `driverId`/`passengerReference`, "deliberately minimal... not a general relationship model," by its own KDoc). Treating the duplication as permanent would mean Taxi's own relationship model can never grow into the platform vision without a breaking rewrite later.

**Why not "merge the modules now":** this task explicitly forbids it, and the evidence supports the prohibition independently — `ADR-037`'s own Consequences section already reserved the Dispatch-integration question for "its own ADR when it introduces the first real cross-module dependency," precisely because merging prematurely would violate `MODULE_STRUCTURE.md`'s Domain Alignment principle (Task 7 Section 4's own "must not own" boundaries) before there is real evidence for exactly what the merged shape should be. Circle of Trust (Passenger Experience) is working, tested, pilot-proven code (Task 6 Section 5/11) — Section 24's own migration discipline ("preserve working code") applies here as much as anywhere else in this document.

**The convergence model this review recommends (DECISION):**

| Concept | Layer | Reasoning |
|---|---|---|
| **Identity** (phone/password/session) | **Platform-level, already correctly so** | Already shared across driver and passenger roles (FACT — one `Identity` mechanism, Task 6 Section 9); no change needed |
| **Person** (an identity independent of role) | **Platform-level — Network Management's own concept, correctly placed there (ADR-037)** | Task 7 did not need to touch this; re-affirmed here |
| **Profile** (a person's roles: driver/passenger/network member) | **Platform-level in concept (`ProfileType.DRIVER`/`PASSENGER`/`NETWORK_MEMBER`, FACT, re-read this review), Taxi-specific in practice for V1** | Taxi V1 should keep using Driver Management's/Passenger Experience's own thin profile data (Task 7 Section 3) — but the *field names and shape* should not casually diverge from `ProfileType`'s own vocabulary, so a future convergence is a rename, not a redesign |
| **Relationship / Connection (two-role: this driver, this passenger)** | **Taxi-specific — Circle of Trust stays exactly as built** | This is the correct, working, minimal shape for what Taxi V1 actually needs; Section 2.6's own general-graph case is a *different* scenario (Регина inviting her own mother), not Taxi's core loop |
| **Connection (general, any-participant graph)** | **Platform-level — Network Management's own `Connection`, correctly placed, correctly dormant for now** | This is precisely the capability Task 7 Section 14 undersold; it is deliberately unconsumed today, not irrelevant |
| **Invitation** | **Both — Taxi's own personal-invitation-link (`/i/:driverCode`) is a Taxi-specific instance of the same underlying idea Network Management's `Invitation` generalizes** | No convergence needed for V1; naming should stay consistent (both mean "an act that produces a Connection") |
| **Trust (verification, standing, Eligibility)** | **Platform-level in principle, implemented per-vertical today** | Phone verification (Task 7 Section 13) belongs on Identity — already platform-level; Eligibility (Task 7 Section 8) is Taxi-specific standing, but should be *named* so a future vertical's own Eligibility concept doesn't have to be invented from nothing |
| **Reputation** | **Taxi-specific for V1, explicitly designed not to foreclose a platform-level future (Task 7 Section 12's own `personId`-shaped `Rating`, reaffirmed in Section 6/12 below)** | Unchanged from Task 7 |

**The precise mechanism for "shared conceptual model without shared mutable storage" (DECISION):** when/if Network Management is ever activated for Taxi, the correct integration shape is **Passenger Experience's `Connection` referencing a Network Management `Person` id** (the same "reference, not ownership" pattern already proven for `driverReference`/`orderReference` everywhere else in this codebase, FACT) — never a merged table, never Network Management reading Passenger Experience's schema directly. This preserves `MODULE_STRUCTURE.md`'s own boundary rules exactly as they already work today, and is a purely additive migration (a nullable `personId` column on Passenger Experience's `Connection` and `persons` table, added independently, with zero impact on the two-role Circle of Trust scenario Taxi V1 actually runs on) — not proposed for build now, named here so it isn't invented under time pressure later without this reasoning.

**What must eventually become platform-level vs. stay Taxi-specific, stated plainly:**
- **Platform-level, eventually:** Person, general Connection/Invitation graph, cross-vertical Reputation (Task 7 Section 12's own `personId`-based `Rating` shape already anticipates this).
- **Taxi-specific, indefinitely:** Circle of Trust's own `isPrimary` marker (a Taxi UX preference, not a platform fact), Vehicle, Eligibility's concrete Taxi criteria, Order/Assignment/Trip themselves.

## 6. BlaBlaCar-Derived Principles

Corrected, phased matrix (Task 7 Section 12 was too coarse — this replaces it, does not merely append to it):

| Principle | Pilot | Taxi V1 | Future Network | Why |
|---|---|---|---|---|
| Participant profile (name) | **PRESENT** | **PRESENT** | — | Already real (Task 6 Section 4) |
| Profile completeness (photo, vehicle) | **ABSENT** | **TARGET** | — | Task 7 Section 3's own Vehicle/photo additions; cheap, high trust value, no ranking risk |
| Identity verification (phone) | **ABSENT** | **TARGET** | Platform-level once built | Task 7 Section 13; must precede Rating meaning anything (unchanged finding) |
| Trust signals (verified badge, `isPrimary`) | **PARTIAL** (isPrimary exists, unconsumed by dispatch — Section 8) | **TARGET** (verified badge added) | Cross-vertical trust badge | Non-numeric, per this codebase's own hard constraint |
| Experience/history (trip count) | **ABSENT** | **TARGET** | Cross-vertical | A count, never an average score — matches Task 7 Section 12's own `tripCount` design exactly |
| Reputation | **ABSENT** | **TARGET**, non-numeric | Cross-vertical, via Network | Unchanged from Task 7 |
| Transparency before interaction (what the passenger sees before requesting) | **PARTIAL** (driver name only) | **TARGET** (+ trip count, verified badge) | — | Directly serves Fair Dispatch's own "explainable" priority |
| Participant choice | **ABSENT as a marketplace mechanic — deliberately** | **ABSENT as a marketplace mechanic — deliberately** | **Not planned as BlaBlaCar-style driver browsing** | **Explicit exclusion, not a gap**: PIOS is dispatched, not browsed — a passenger does not pick from a list of nearby drivers the way BlaBlaCar's own marketplace works. The one real "choice" PIOS gives a passenger is *retained relationship control* (Circle of Trust's own `isPrimary`, passenger-controlled) — a materially different mechanic, kept exactly as-is |
| Repeat relationships | **PRESENT** | **PRESENT** | Cross-vertical | Already PIOS's actual differentiator (Task 7 Section 21, Section 7 below) |
| Predictable interaction | **PRESENT** (real state machine) | **PRESENT** | — | Unchanged |

**Why "participant choice" is deliberately excluded, stated explicitly (this task's own instruction: "do not copy BlaBlaCar's ridesharing marketplace model"):** BlaBlaCar's core mechanic is a passenger browsing and selecting among multiple advertised drivers/rides before committing. Importing that into PIOS Taxi would directly contradict the Coordinator-mediated/personal-invitation dispatch model this repository has consistently built (Task 7 Section 8) and would re-introduce exactly the kind of driver-comparison/ranking surface the no-numeric-reputation constraint (Section 12/19 of Task 7) exists to prevent. This is named as a **rejected** pattern, not an oversight.

## 7. Driver Business Platform

**Restated using the repository's own strongest available language** (correcting Task 7's under-citation, Section 3 above):

> «Связь даёт преимущество. Качество подтверждает право. Оплата следует за трудом.»
> (Connection gives advantage. Quality confirms the right. Payment follows the work.)
> — the governing principle `PRODUCT_DECISION_TRUST_PRIORITY_MODEL.md` (uncommitted draft) evaluates every dispatch-priority option against; **FACT** of the document's own content, **not itself a ratified decision** (the file's own header states this explicitly).

Three already-**ratified** (not draft) findings this principle rests on (`PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md`, FACT):

1. **Ownership excluded both ways (Section 2.1):** "A client is not the property of a driver" — extending the Constitution's own Relationship Protection law, which previously only bound the *platform*, explicitly onto the driver's side too. This is the precise, citable answer to this task's own "repeat relationship vs. platform-owned customer" question, now shown to be symmetric: neither the platform nor the driver may treat a passenger relationship as owned property.
2. **Origin is preserved, recognized, but not exclusive forever (Section 2.2/2.4):** whoever invited a passenger is recorded and recognized — "recognition and priority of the connection go to the first inviter" — but the *mechanism* of that priority (exclusivity vs. first-refusal vs. something else) is explicitly **[STILL OPEN]**, carried forward as open by this review too (Section 15).
3. **Value follows contribution, payment follows work (Section 2.3/2.5):** "whoever actually performs a given ride is paid for that ride, regardless of who originated the connection." This cleanly separates two things a driver accumulates — **recognition** (who brought this relationship into being) and **earned economic value** (who actually did the driving) — which must never be conflated into a single "owned" asset.

**Architectural consequence for Taxi V1 (DECISION, refining Task 7 Section 21, not replacing it):**

```
Driver's accumulated business value =
    Recognition (Circle of Trust origin — who invited whom, immutable fact)
  + Reputation  (Rating aggregate, Section 6 above — earned, non-numeric)
  + Repeat volume (trip count with this passenger/pair — a fact, not a score)
  + Earnings history (Payment records where this driver was the executor — Section 2.5's "payment follows work")
```

None of these four is transferable, purchasable, or platform-assignable — each is either an immutable historical fact (recognition, volume) or something the driver must continue to earn (reputation, future earnings). This is the concrete, structural difference between "the driver builds a business using PIOS" and "the driver is handed a customer list" — there is no list, only a set of independently-recorded, passenger-controlled relationships (Circle of Trust's `isPrimary`, which the passenger — not the driver — can change or drop at any time, FACT of existing implementation) plus the driver's own accumulating, non-transferable track record alongside them.

## 8. Coordinator Evolution

**Correction to Task 7 (Section 3 above):** Task 7 was right that the Coordinator should not be replaced by an opaque algorithm, but wrong to leave the model static. This task's own three-stage structure is adopted directly (**DECISION**):

**STAGE A — Supervised pilot (CURRENT, FACT):**
```
Passenger → Order → Coordinator (manual, full authority) → Driver
```
Confirmed exactly as implemented (Task 6 Section 5, re-confirmed this review). The Coordinator has no relationship-aware input at all — `isPrimary` is invisible to Coordinator/Dispatch entirely (Section 3 above's own correction).

**STAGE B — Taxi V1 (TARGET, DECISION):**
```
Passenger → Order → [Relationship check: does a Circle-of-Trust isPrimary driver exist for this passenger?]
                        │
              YES ──────┴────── NO
               │                 │
     Offer to that driver   Coordinator (as today)
     first (a first-look,        │
     not exclusivity — per       ▼
     `PRODUCT_DECISION_       Manual proposal, as today
     TRUST_PRIORITY_MODEL`
     draft's own Option 2,
     Section 15 below)
               │
        Declines/lapses
               │
               ▼
     Falls back to Coordinator, exactly as Stage A
```
This is the single, explicit, non-hidden rule `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` itself already authorized in principle ("Order routing that checks for an existing personal relationship before offering the order to the general queue — a single, explicit, transparent rule, not a ranking, score, or learned/automated policy," FACT, direct quote) — Task 7 never connected its own Coordinator design to this already-existing authorization. **The Coordinator does not disappear in Stage B** — it becomes the fallback path and the exception handler (this task's own words), exactly as it already is today for every order without a Circle-of-Trust relationship.

**STAGE C — Future scalable PIOS (FUTURE, not designed here):**
```
Relationship-aware first look (Stage B)
  + Eligibility (Task 7 Section 8)
  + Availability (existing)
  + Scheduled rides (existing, ADR-058)
  + Reputation as a floor, never a rank (Section 6/12)
  + Fair Opportunity Policy for the general-queue fallback (ADR-034's own still-undecided algorithm)
  + Coordinator retained as human escalation path, permanently — not a transitional artifact
```
This document does not design Stage C's own fairness/matching algorithm (unchanged from Task 7 — ADR-034 stays open); it only confirms the Coordinator's permanent role as exception-handler, never fully removed, consistent with "preserve driver autonomy" and "no opaque ranking algorithm" (this task's own explicit constraints).

**Why Stage B is safe to build without contradicting the "no ranking" constraint:** it is a **binary branch** (has a Circle-of-Trust primary driver, or doesn't), not a score — exactly the shape `PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md` already pre-authorized, and exactly the "first look, not exclusivity" shape the Trust Priority Model draft's own Option 2 evaluates most favorably (re-read this review, not reproduced in full here since it is explicitly an unratified draft — flagged in Section 15 as needing a real Product Owner decision before Stage B is built, not before it is *designed*).

## 9. Small-Town/Monotown Architecture

Task 7 Section 20 is **reaffirmed and strengthened**, not corrected — the new evidence in Sections 5–8 above makes the case *stronger*, not different:

- **Stage B's relationship-first routing (Section 8 above) is disproportionately valuable in exactly this environment.** In a village with 1–5 active drivers, "does a trusted relationship already exist" is very often the *only* useful signal — there is no meaningful pool to rank or optimize across in the first place. Where a dense-city aggregator's whole value proposition depends on liquidity (many drivers, many passengers, real-time optimization), PIOS's relationship-first model degrades gracefully to exactly the opposite: it works *best* precisely where liquidity is thinnest, because the "matching problem" a conventional aggregator solves algorithmically is instead solved by an already-existing human relationship.
- **`OrderExpired` (Task 7 Section 7) remains the correct honest-fallback state** when even the Coordinator cannot find a driver — unchanged.
- **Scheduled rides (ADR-058, already real)** matter more here — unchanged from Task 7.
- **Coordinator intervention remains available at every scale** (Stage A/B/C all keep it, Section 8 above) — a monotown deployment could, in principle, run entirely on Stage A/B without ever needing Stage C's own future automation, which is itself a strength: PIOS does not require dense-city scale to be viable, unlike a conventional aggregator.

## 10. Commercial Architecture

Task 7 Section 22's structural recommendations (decomposable `PaymentIntent`, no chosen pricing model, monetization sequenced last) are **kept unchanged**. This review adds the one thing Task 7 was asked to identify but did not state directly: **what economic value PIOS itself actually creates**, so future monetization is designed around that value, not around commission extraction (this task's own explicit instruction).

**PIOS's own economic value-add, stated explicitly (DECISION, synthesizing Sections 5–8 above):**
1. **Trust infrastructure** — the personal-invitation link, Circle of Trust, phone verification, and (Section 12) Rating together let a driver *establish and prove* a repeat relationship faster and more legibly than an informal, offline arrangement could. This is a real service, independent of any single ride.
2. **Matching/fallback capacity** — the Coordinator + (Stage B/C) relationship-aware routing gives a driver access to demand beyond their own existing relationships (Task 7 Section 6's "Platform Order" category) — value the driver could not generate alone.
3. **Payment/settlement infrastructure** (Section 11, Task 7) — once built, removes friction from the transaction itself.
4. **Operational tooling** (Task 7 Section 16) — Owner Control Center-class visibility, which an independent driver could not build for themselves.

**Monetization should attach to these four value points directly** (DECISION) — e.g., a subscription or fee tied to *platform capacity used* (matching, payment processing, tooling) rather than a flat per-ride commission taken regardless of how much of that value a given ride actually drew on. This is the concrete architectural implication of "value follows contribution, payment follows work" (Section 7 above) applied to the *platform's own* revenue, not only the driver's: **PIOS's own revenue should be defensible in the same terms it asks drivers to accept for passengers — traceable to value actually delivered, not extracted merely because a transaction occurred.** This is a principle for a future Product Owner pricing decision to apply, not a pricing model this document chooses.

## 11. Order/Assignment/Trip Validation

Re-validated point by point (this task's own instruction: "keep the Task 7 decision unless new evidence contradicts it"). **No new evidence found that contradicts it.**

- **Repeat rides**: unaffected — each ride is its own `Order`→`Assignment`→`Trip` triple; Circle of Trust/recognition (Section 7 above) lives entirely outside this triple, in Passenger Experience, which is exactly the correct separation (a repeat relationship is not itself an Order-lifecycle concept).
- **Scheduled rides**: unaffected — `requestedPickupAt` stays on `Order` exactly as Task 7 left it (Section 7 of Task 7); no ride-progress concept is needed until a Trip actually begins.
- **Cancellation**: unaffected — `Order.cancel()` remains Order Management's own authority (FACT, unchanged); an in-progress `Trip` being cancelled is a distinct, already-partially-modeled case (`ProposalWithdrawn`/ADR-053's own existing pattern for pre-acceptance cancellation) that Task 7's Trip split does not complicate further.
- **Payment**: directly validated by this review's own Section 7/10 analysis — `PaymentIntent` keyed on `TripCompleted` (Task 7 Section 11) is unaffected by anything found in this review.
- **Rating**: same — keyed on `TripCompleted`, unaffected.
- **Future services beyond taxi**: this is the one place worth stating explicitly, since it was only implicit in Task 7. **"Trip" as a concept — a bounded, executed service distinct from the request (Order) and the match (Assignment) — generalizes cleanly to a future vertical** (a delivery, a repair visit, a lesson) **as a per-vertical concept, not a shared table.** A future vertical would own its own equivalent aggregate (its own name — "Job," "Appointment," whatever fits that vertical), inside its own module, following the exact same Order/Match/Execution three-way split — never a shared "Trip" table Taxi and a future vertical both write to, which would violate the same module-isolation principle (`MODULE_STRUCTURE.md` Section 5) everything else in this system already respects. **Where the prompt asks "if the model is too taxi-specific, identify exactly where"**: the *name* "Trip" is taxi-specific (correctly so — it should not be artificially genericized into something like "ServiceExecution" for Taxi's own code, since that would be premature generalization for a vertical that doesn't exist yet); the *pattern* (Order/Match/Execution as three distinct aggregates in three distinct ownership zones) is not taxi-specific at all and is the part worth preserving as a template.

## 12. Capability Matrix

| Capability | Existing | Pilot | Taxi V1 | Future Network |
|---|---|---|---|---|
| Identity (phone+password) | ✅ | ✅ | ✅ | ✅ (unchanged, already platform-level) |
| Phone verification | ❌ | ❌ | ✅ | ✅ |
| Profile (name) | ✅ | ✅ | ✅ | ✅ |
| Driver profile (photo, bio) | ❌ | ❌ | ✅ | ✅ |
| Vehicle | ❌ | ❌ | ⚠️ optional | ✅ (varies per vertical) |
| Trust (verified badge) | ❌ | ❌ | ✅ | ✅ |
| Rating | ❌ | ❌ | ✅ | ✅ (cross-vertical) |
| Reputation (aggregate view) | ❌ | ❌ | ✅ | ✅ (cross-vertical) |
| Circle of Trust (`isPrimary`) | ✅ | ✅ | ✅ (now consumed by routing — Section 8) | superseded conceptually by general Connection (Section 5), not replaced |
| Repeat relationship (recognition) | ✅ | ✅ | ✅ | ✅ (cross-vertical) |
| Invitation (personal link) | ✅ | ✅ | ✅ | ✅ (Network's own generalized form) |
| Matching (human Coordinator) | ✅ | ✅ | ✅ (as fallback, Stage B) | ✅ (Stage C, still human-escalatable) |
| Relationship-aware first-look routing | ❌ | ❌ | ✅ (Stage B, Section 8) | ✅ |
| Coordinator | ✅ | ✅ (primary) | ✅ (fallback) | ✅ (exception handler, permanent) |
| Trip (as distinct from Assignment) | ❌ | ❌ | ✅ | ✅ (per-vertical equivalent) |
| Payment (cash) | ❌ | ❌ | ✅ | ✅ |
| Payment (card) | ❌ | ❌ | ❌ | ✅ |
| Earnings view | ❌ | ❌ | ✅ | ✅ |
| Notifications (in-app status) | ⚠️ (implicit via polling) | ⚠️ | ✅ (explicit capability) | ✅ |
| Communication (P2P messaging) | ❌ | ❌ | ❌ | ❌ — not proposed even as Future; would require re-opening ADR-059 |
| Operations (Coordinator/Owner Control Center) | ✅ | ✅ | ✅ (+ dispute/payment-problem queue) | ✅ |
| Business accounts | ⚠️ (`OrderOrigin` supports it, unused) | ❌ | ❌ | ✅ |
| Network identity (`Person`) | ✅ (built, dormant) | ❌ | ❌ | ✅ (activated) |
| Network relationships (general graph) | ✅ (built, dormant) | ❌ | ❌ | ✅ (activated) |
| Cross-vertical reputation | ❌ | ❌ | ❌ (schema-ready only) | ✅ |
| Cross-vertical transactions | ❌ | ❌ | ❌ | ✅ |
| Monetization (any mechanism) | ❌ | ❌ | ⚠️ architecture-ready only | ✅ |

**Legend**: ✅ built/planned at this stage · ⚠️ partial or schema-only · ❌ not present, not planned at this stage.

## 13. Revised Target Architecture

**The conceptual chain this task asks for, with each link mapped to a current or target PIOS concept:**

```
IDENTITY          → Identity module (phone/password/session) — FACT, already platform-level
    ↓
PROFILE           → Driver Management/Passenger Experience thin profile (Taxi-specific for V1);
                     conceptually aligned to Network's own ProfileType vocabulary (Section 5)
    ↓
TRUST             → Phone verification (Identity) + Eligibility (Driver Management) — V1 REQUIRED
    ↓
RELATIONSHIP      → Circle of Trust (`Connection`/`isPrimary`, Taxi-specific) today;
                     Network Management's general `Connection` (platform-level) once activated
    ↓
DEMAND            → Order (Order Management) — FACT, unchanged
    ↓
OPPORTUNITY       → Proposal (Dispatch) — FACT, unchanged (the pre-commitment fact, ADR-035)
    ↓
MATCHING          → Coordinator (Stage A/pilot) → Relationship-first routing + Coordinator
                     fallback (Stage B/Taxi V1) → + Eligibility/Fairness policy (Stage C/future)
    ↓
TRANSACTION       → Trip → Payment (new, Sections 10-11 of Task 7) — V1 REQUIRED
    ↓
REPUTATION        → Rating (new, Task 7 Section 12) — V1 REQUIRED, non-numeric
    ↓
REPEAT BUSINESS   → Recognition + Reputation + Repeat volume + Earnings (Section 7 above) —
                     the driver's own accumulated, non-transferable business value
    ↓
MONETIZATION      → Attached to real platform value-add (Section 10 above), not commission
                     extraction — architecture-ready, not activated in V1
```

**"What is PIOS Taxi actually becoming," answered directly (per this task's own requirement):** not another ride-hailing app that matches anonymous supply to anonymous demand for a commission — a **trust-and-relationship infrastructure layer that happens to be instantiated first for taxi rides**, where the passenger-driver relationship is the primary asset the whole chain above exists to protect and grow, and where the platform's own business is providing the infrastructure (identity, trust, matching fallback, transaction processing, operational tooling) that makes those relationships easier to form and safer to rely on — never owning or intermediating the relationships themselves. Taxi is the first, concrete proof of this chain; every layer above (Identity, Trust, Relationship, Reputation) is written in terms general enough that a second vertical would reuse them directly rather than reinvent them (Section 5, Section 11's "future services" analysis).

## 14. Required Changes to Task 7

Specific, itemized edits Task 7 should receive if/when it is revised (not applied automatically by this review — per this task's own "do not modify existing architecture documents" instruction):

1. **Section 14 (Network)** should be rewritten to state: "Taxi V1 has zero *runtime dependency* on Network Management, but Network Management's `Person`/general `Connection` model is the deliberately-deferred, already-intended home for cross-vertical relationships (`ADR-037`'s own Consequences section) — not an accident of sequencing, and not merely optional."
2. **Section 8 (Dispatch)** should drop the phrase "Personal Client priority signal (optional, surfaced not enforced)" as if it were a partial existing capability, and instead describe Stage B (Section 8 above) as a concrete, explicitly-authorized-in-principle V1 target.
3. **Section 12 (BlaBlaCar principles)** should be replaced with the phased Pilot/V1/Future matrix in Section 6 above.
4. **Section 21 (driver business model)** should cite `PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.1/2.3/2.5 directly, and adopt the four-part "accumulated business value" breakdown in Section 7 above.
5. **Section 25 (implementation order)** should insert Stage B relationship-first routing (Section 8 above) as a named step, sequenced after Eligibility (existing step 2) and before Payment/Rating (existing steps 5–6) — it has no dependency on either and delivers real pilot value (Section 9 above) independently.
6. **Section 27 (required ADRs)** should add a tenth item: **"Relationship-first routing (Stage B)"** — formalizing the binary, non-ranking Circle-of-Trust-aware routing rule as its own ADR, since `ADR-037` explicitly requires one before Network-adjacent cross-module behavior is built, and this Stage B design, while not itself touching Network Management, is close enough in spirit that the same discipline should apply.

## 15. Decisions Requiring Explicit Product Owner Approval

Carried forward and consolidated (none resolved by this review, consistent with its own evidence-grading discipline):

- **The concrete mechanism of "priority of connection"** (`PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.4, **[STILL OPEN]**) — this review's own Stage B design (Section 8) assumes a "first look, not exclusivity" shape, matching the Trust Priority Model draft's own most-favorably-evaluated option, but that draft is explicitly **not** a ratified decision, and Stage B must not be built until the Product Owner actually chooses among its evaluated options (or a different mechanism entirely).
- **Whether Personal-Client-directed orders are routed through Dispatch's assignment decision at all** (`PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 4, still open) — Stage B's own design assumes yes, routed but with a first-look preference; this is an assumption pending confirmation, not a decision this review makes.
- **Module ownership for the general (any-participant) Connection scenario** (`PRODUCT_DECISION_PERSONAL_NETWORK_VALUE_DISTRIBUTION.md` Section 2.6, explicitly flagged, not resolved) — Section 5 above's recommendation (Network Management owns it, Passenger Experience references it by id) is this review's own architectural judgment, not yet a Product Owner ratification.
- **The commission/subscription/fee model itself** (Section 10 above) — architecture-ready, business-model choice untouched.
- **Whether ADR-059 (no contact disclosure in the pilot) should ever be revisited** for P2P communication (Task 7 Section 15) — not reopened by this review.
- **Driver document verification requirements** (Task 7 Section 13) — no ratified checklist exists; still open.

## 16. Proposed Final Implementation Sequence

Revises Task 7 Section 25 with Section 8's Stage B inserted at its correct dependency point; every other step is unchanged from Task 7:

1. Phone verification (Identity) — unchanged from Task 7.
2. Eligibility gate (Driver Management) — unchanged from Task 7.
3. `OrderExpired` state (Order Management) — unchanged from Task 7.
4. **Stage B relationship-first routing (Dispatch/Passenger Experience, new)** — depends only on steps already-existing Circle of Trust (no new dependency), sequenced here because it delivers real pilot/small-town value (Section 9) immediately and has no dependency on Trip/Payment/Rating below. **Blocked on the Product Owner decision in Section 15 above** — this step cannot begin implementation until that decision exists, even though its design can proceed now.
5. Trip split (Dispatch) — unchanged position from Task 7 (step 4 there).
6. Payment (new module, cash-only V1) — unchanged from Task 7.
7. Rating (new module) — unchanged from Task 7.
8. Notifications capability — unchanged from Task 7.
9. Vehicle entity, driver document verification — unchanged from Task 7.
10. Individuated operator accounts — unchanged from Task 7.
11. Commercialization activation — unchanged from Task 7, now additionally informed by Section 10 above's "attach monetization to real value-add" principle.

---

## Final Report

**File created:**
- `docs/PIOS_TAXI_ARCHITECTURE_CONVERGENCE_REVIEW.md`

**Files modified:** none.

**Files deleted:** none.

**Tests/checks executed:** none — documentation-only task.

**Production databases touched:** NO
**RabbitMQ touched:** NO
**Production services restarted:** NO
