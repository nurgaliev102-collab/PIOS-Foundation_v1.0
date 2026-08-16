# ADR-059: Ride Contact Disclosure — No Disclosure in the Pilot (Option C), and the Structural Boundary for Any Future One

## Status

**Accepted — Option C: no phone disclosure in this pilot.** Product Owner
ruling, 2026-08-15, on the recommendation recorded below.

**PIOS discloses no participant's phone number to any other participant, on any
screen, through any endpoint, at any point in the ride lifecycle.** No field, no
endpoint, no migration, no DTO change and no frontend change is authorized by
this ADR, and none may be added under it.

**This is a deliberate pilot-scope exclusion, not an oversight, not a backlog
item, and not a gap awaiting a quiet fix.** It was chosen with the alternative
(Option A) fully specified and costed below, and it was chosen for reasons that
are still live: the personal-data Product Decision does not exist, and H5 is
currently measuring whether a ride completes *without* a phone call and has not
yet produced one observation. Anyone who later wants to revisit this must go
through "How this decision may be revisited" at the end of this document —
adding a phone number to any response is a reversal of a ratified decision, not
an increment on top of one.

The structural decisions S1–S6 are **binding regardless**, and remain binding if
the decision is ever reversed. They are written in ADR-042's own style — correct
whichever way the product question is eventually answered.

## Why Option C — the three blockers that stood in front of any disclosure

Three blockers stood in front of any disclosure. Any one of them was sufficient;
all three are real, and **all three survive this ruling** — Option C does not
clear them, it declines to cross them. That distinction matters: the decision
resolves the *product* question by choosing not to disclose, which is precisely
the one answer that requires no personal-data decision to be invented first.

### Blocker 1 — the Product Decision does not exist, and is already named as missing

`docs/PILOT_OPERATION_PLAN.md` Section 7.4, verbatim:

> Ни один ратифицированный документ не говорит, хранит ли PIOS телефон
> пассажира, кто его видит и как долго. ... Это область персональных данных, и
> её нельзя закрыть добавлением поля в форму.
>
> **Что нужно от Product Owner:** решение, нужен ли контакт вообще, — с учётом
> того, что H5 проверяет прямо противоположное («провести поездку без
> звонков»). Возможно, правильный ответ — «нет», и тогда его тоже нужно
> записать. Этот документ ответа не даёт.

That is the gate. `CLAUDE.md` ("Never Invent Business Rules") and ADR-002 both
forbid this ADR from answering it, and the question is not "which field" — it
is *who may see another person's phone number, on what basis, and for how
long.*

**How the ruling interacts with it, precisely.** The Product Owner did not
answer that question; they chose the one option that does not require it to be
answered. Section 7.4's own text anticipates exactly this outcome —
*«Возможно, правильный ответ — "нет", и тогда его тоже нужно записать»* — and
this ADR is where it is written down. The Product Decision named in Section 7.4
therefore remains **outstanding**, and stays a hard prerequisite for any future
disclosure (see "How this decision may be revisited").

### Blocker 2 — it contradicts the hypothesis currently under test

`PIOS_PRODUCT_HYPOTHESES.md` H5 (lines 145–152). Its success criterion, in its
own words: *«Ни в одной из первых трёх завершённых поездок ни водитель, ни
пассажир не звонят друг другу по поводу места посадки»*. H5 is registered,
implemented, and still **«Ожидает проверки»** — the evidence journal
(`PIOS_PRODUCT_EVIDENCE.md` line 80) is empty, so not one observation exists yet.

Shipping a call button before the first pilot session removes the very behaviour
H5 measures. This is not a veto — the Product Owner may decide a contact channel
matters more than the measurement — but it must be a decision made knowingly,
and H5's status must then be corrected honestly rather than left to look
untested for the wrong reason.

**Outcome of the ruling for H5:** it is left intact and measurable. H5 keeps its
«Ожидает проверки» status for the ordinary reason — no observations exist yet —
and not because a product change quietly removed the behaviour it was written to
observe. Nothing in `PIOS_PRODUCT_HYPOTHESES.md` is edited by this ADR.

### Blocker 3 — there is no gate to hang the requirement on

The Product Owner's own requirement is that numbers stay hidden *"until contact
is genuinely permitted by business logic."* Today, that is not implementable on
the endpoints that would carry the data:

- `ADR-055` "Explicitly out of scope" (line 116): *"No Dispatch, Order
  Management, or Driver Management endpoint is locked down by this ADR — those
  remain open and are the **named next step**."*
- Consequently `GET /v1/orders`, `GET /v1/proposals?orderId=` and
  `GET /v1/proposals?driverId=` are **unauthenticated**
  (`ProposalController.kt` lines 153–190 take no `Authorization` header at all).
  A phone number placed on any of those responses is disclosed to anyone who
  can reach the module, not to the counterpart.

Hiding the number in the UI is not a gate. Any real gate requires work that
ADR-055 deliberately deferred.

## Context — what the audit actually found, corrected

The originating task recorded the passenger's phone as a research gap. **It is
not a gap, and the answer changes the shape of the problem**, so it is recorded
here before anything else.

**Both parties' phone numbers already exist in PIOS, in exactly one place.**
Since ADR-055 (implemented — commit `0f5e5b7`, "Circle of Trust and real session
authentication"):

- `identity` stores `identities.phone`, unique-indexed, as the **login handle**
  (ADR-055 Decision 2). It is unverified as a *number* — a password proves
  control of the account, not of the phone (ADR-055 "Explicitly out of scope").
- **The passenger registers with phone + password**
  (`frontend/src/pages/PassengerLanding/PassengerLanding.tsx` lines 232, 276,
  435 → `BackendIdentityProvider.register`, lines 51–58 →
  `POST /v1/identities/register`).
- **The driver registers the same way** (`DriverHome.tsx` lines 234, 525, 784).
- **Both are already reachable by identifier without any new capture form:**
  `passengerReference == identityId` (ADR-055 Decision 5), and a driver's
  `Identity` carries `driverId` (ADR-039; `Identity.kt` lines 33–38).

So the real question is not *"where do we capture a phone."* It is:

> **May PIOS disclose an authentication credential as a contact channel — to
> whom, on what basis, and for how long?**

That is squarely the personal-data decision Blocker 1 names.

Two related facts, recorded so a later reader does not re-derive them:

- `PILOT_OPERATION_PLAN.md` Section 7.4's own sentence *«Пассажирская запись
  сегодня — только имя и локальный идентификатор устройства»* is **stale** as
  of ADR-055 and should be read as superseded by this Context. Its *conclusion*
  — that a Product Decision is required — is unaffected and stands.
- `network-management`'s `Person.phone` is a different concept entirely (a
  relationship identity, not an authentication one — ADR-038's own self-critique
  section), and that module remains isolated from every other. It is not a
  candidate location for anything in this ADR.

## Structural Decisions (binding regardless of how the product question is answered)

These constrain **any** future implementation of contact exchange. They are
architectural, so they are decided here; none of them decides whether contact
should exist.

### S1. No phone number is copied into another module's storage.

`identities.phone` is Identity-owned data. Copying it into `order-management`,
`dispatch`, `driver-management` or `passenger-experience` storage is precisely
what the reference-not-ownership rule forbids (ADR-005, ADR-009, ADR-019;
established for `network-management` in ADR-037 and reused for `Identity` in
ADR-039: *"a plain string pointing at a ... `DriverId`, never a foreign key and
never ownership"*). Only identifiers cross module lines.

This also rules out the superficially attractive "publish the phone into
`driver-management`'s `Driver` read model via the outbox" option: an event
carrying a phone number would put Identity-owned personal data into another
module's database permanently, with no retention rule and no way to withdraw it.
Rejected.

### S2. Any disclosure must be gated server-side, by the module that owns the fact making contact permissible.

Dispatch owns `Assignment` and therefore owns the fact "this ride is committed"
(ADR-040). A gate enforced anywhere else is not a gate. Since Dispatch is
unauthenticated today (Blocker 3), gating requires Dispatch to become a token
**verifier** — the replicated `SessionTokenVerifier` shape ADR-055 Decision 1
already ratified (*replicate per module, never centralize*, because
`MODULE_STRUCTURE.md` Section 4 forbids shared business code). `identity`
remains the sole issuer.

Client-side hiding, an obscure URL, or "only the UI knows the id" are **not**
gates and may not be offered as one.

### S3. Dispatch cannot authorize the passenger side today, and closing that is a real contract change.

`Proposal` and `Assignment` hold an `OrderReference` and a `DriverReference`
and nothing else (`Proposal.kt` lines 40–45). **Dispatch does not know who the
passenger is.** It can check a driver (`token.drv == assignment.driver`) but has
nothing to compare a passenger's `token.sub` against.

Closing that requires one additional narrow reference — `passengerReference` as
a plain string on `Proposal`, supplied on `POST /v1/proposals` and verified
`== token.sub`, exactly the pattern ADR-055 Decision 4 already ratified for
`POST /v1/connections`. That is permissible under reference-not-ownership (it is
an identifier, not data), but it is a **breaking change to an existing
contract** and must be named in whatever ADR authorizes it, never slipped in as
an implementation detail.

### S4. `identity`'s self-only disclosure rule may not be relaxed silently.

`IdentityController.kt` lines 119–123: `GET /v1/identities/{id}` returns **403**
unless `token.sub == id`, per ADR-055 Decision 3. Every path that shows one
person another person's number amends that rule. Any such amendment is an
append-only Update block on ADR-055 plus an explicit decision here — not a new
endpoint added quietly beside it.

### S5. `tel:` / `sms:` links are architecturally free. The cost is entirely disclosure and gating.

No dependency, no telephony provider, no server involvement, no new
infrastructure — the Product Owner's "no paid external dependency" constraint is
irrelevant to this feature, and satisfying it proves nothing about whether the
feature is safe. Nobody should read "it's just a link" as "it's a small change."

### S6. Permanently unauthorized under every branch.

In-app chat or calling; masked, proxied or virtual numbers; storing call or
message records; any bulk or search-based contact lookup; exposing a number
before an `Assignment` exists; exposing a number on any unauthenticated
endpoint; retaining a disclosed number in any module other than `identity`;
carrying a phone number in any event payload or outbox record; and any use of
`network-management` for this purpose. Each of these is either excluded by a
ratified document cited above or is a new capability requiring its own Product
Decision and ADR.

## Options as presented to the Product Owner, and the one chosen

Preserved in full, including the two that were not chosen, so that a future
reader can see what was actually weighed rather than having to trust that
something was.

### Option C — no phone disclosure at all. **CHOSEN — Product Owner, 2026-08-15.**

Cost: zero code, zero risk, zero personal-data exposure. Rationale: the pilot
runs on the personal-network premise — the passenger arrived through *this
driver's own invitation link* (ADR-054, H1) and in the overwhelming majority of
cases already has that person's number by ordinary means. H5 is currently
measuring whether the ride completes without a call, and has not yet produced a
single observation. `PILOT_OPERATION_PLAN.md` Section 7.4 explicitly names this
as a legitimate answer: *«Возможно, правильный ответ — "нет", и тогда его тоже
нужно записать»*. If the first sessions show participants reaching for the
phone anyway, that is exactly the `E-NNN` evidence that would justify Option A
on a real basis rather than an assumed one.

### Option A — Dispatch serves a per-assignment contact view. Not chosen; the only sound way to build it if it is ever wanted.

Shape, if a future Product Decision authorizes contact — recorded so that a
reversal has a specified target and cannot be improvised:

- `GET /v1/assignments/{assignmentId}/contact`, `Authorization: Bearer`
  required; 401 without a valid token; 403 for a caller who is neither this
  assignment's driver nor its passenger; 404 before an `Assignment` exists.
- Dispatch stores **no phone** (S1). It resolves the counterpart's number by
  identifier from `identity` at read time.
- Requires, all four together: (a) the Product Decision; (b) a narrow
  `identity` lookup usable by a service caller, amending ADR-055 Decision 3
  (S4); (c) `passengerReference` on `Proposal` (S3); (d) `SessionTokenVerifier`
  replicated into Dispatch (S2) — Dispatch's first authentication of any kind.

Honest cost: four contract changes across two modules, the first
Dispatch → Identity server-to-server read, and a first-ever disclosure of one
person's credential to another. **This is not a minimal change**, and it should
not be presented as one.

### Option B — capture a separate, order-scoped contact number. Rejected.

Adding `passengerPhone` to the order form (the `pickupAddress` precedent) and a
contact number to `Driver` would avoid touching `identity` — but it still needs
the whole of S2 and S3 to be disclosed safely, and it creates a **second,
divergent copy** of the same person's number with no retention rule, in a
module with no personal-data boundary. `PILOT_OPERATION_PLAN.md` Section 7.4
answers this option directly: *«её нельзя закрыть добавлением поля в форму»*.
Rejected as strictly worse than A.

## What this ADR authorizes

**No code, in either direction.** Option C is a decision *not* to build, so it
produces no field, no endpoint, no migration, no DTO change and no frontend
change. Nothing in `backend/**` or `frontend/**` is touched by this ADR, and
nothing may be touched under it.

What it does produce is binding all the same:

- **S1–S6 are in force now**, not from some future date. They constrain every
  module today — most concretely, no phone number may appear in any
  `OrderResponse`, `ProposalResponse`, `DriverResponse`, event payload or outbox
  record, and `identity`'s self-only disclosure rule (ADR-055 Decision 3) stands
  unamended.
- **The record about where phone numbers already live is corrected** (Context
  above), so nobody re-derives the stale "the passenger has no phone" premise
  from `PILOT_OPERATION_PLAN.md` Section 7.4.
- **The pilot has a stated answer to give participants.** If a driver or
  passenger asks why there is no call button, the answer is a decision with a
  reason, not a shrug.

### Consequences of choosing C, stated plainly

- **Positive.** Zero personal-data exposure, zero new attack surface, zero code,
  and — the one that matters most — H5 stays measurable. The first pilot
  sessions can still answer whether a ride completes without a phone call,
  which is the question the product is currently asking.
- **Negative, and accepted knowingly.** A driver who needs to reach a passenger
  mid-ride has no in-product way to do it. `PILOT_OPERATION_PLAN.md` A-12
  records the workaround people will reach for — typing a number into the free-text
  «Откуда» field — and Section 7.4 is explicit that this *"происходит вне
  замысла продукта и не должно предлагаться участникам как инструкция."* It must
  not be taught to participants as a workaround, and if it happens anyway, that
  is an observation for `PIOS_PRODUCT_EVIDENCE.md`, not a defect to patch
  mid-pilot.

## How this decision may be revisited

Not by an implementation task, not by a UX tweak, and not by adding "just the
driver's number, just on the confirmed screen." Reversal requires, in order:

1. **Evidence.** `E-NNN` entries in `PIOS_PRODUCT_EVIDENCE.md` from real pilot
   sessions showing participants actually needing contact — which is exactly
   what H5 exists to observe. This is the whole reason C was recommended: it
   keeps the question answerable.
2. **A Product Decision** — recommended filename
   `docs/PRODUCT_DECISION_RIDE_CONTACT_DISCLOSURE.md` — answering, at minimum:
   whether a contact channel is wanted at all; whose number may be shown to
   whom; whether the login phone may serve as the contact number or a separate
   one is required; from which moment and until which moment; and what happens
   to the disclosure after the ride ends. This is personal data, and it is the
   Product Owner's decision, not the architect's and not the developer's.
3. **An append-only amendment to this ADR** selecting Option A, plus the
   append-only Update block on ADR-055 that Option A's amendment to Decision 3
   requires. This document is not deleted or rewritten when that happens
   (`CLAUDE.md`: "Never Delete Documentation").

Until all three exist, S6 stands and the answer is no.

## Traceability

| Source | Relationship |
| --- | --- |
| `docs/PILOT_OPERATION_PLAN.md` Section 7.4 (A-12) | Names this exact missing Product Decision, before this ADR existed; Blocker 1 |
| `PIOS_PRODUCT_HYPOTHESES.md` H5 (lines 145–152) | Tests the opposite behaviour and has zero observations; Blocker 2 |
| `docs/ADR/ADR-055-Session-Authentication-and-Password-Credential.md` | Decisions 2, 3, 4, 5 and the out-of-scope line 116 — where the phones live, the self-only rule, and why no gate exists yet |
| `docs/ADR/ADR-039-Identity-Driver-Association.md`, `ADR-037` | Reference-not-ownership, the rule behind S1 |
| `docs/ADR/ADR-040-Assignment-Ride-Lifecycle.md` | Why Dispatch owns the fact that gates contact (S2) |
| `docs/ADR/ADR-038-Identity-Module-Bounded-Context-Foundation.md` | Why `network-management`'s `Person` is not a candidate here |
| `docs/ADR/ADR-057-Driver-Stated-Time-To-Pickup.md`, `ADR-058-Scheduled-Pickup-Time-On-Order.md` | The two capabilities from the same request that *were* authorized; this one deliberately was not |
