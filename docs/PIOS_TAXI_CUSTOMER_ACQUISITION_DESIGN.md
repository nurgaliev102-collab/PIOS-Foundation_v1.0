# PIOS Taxi — Customer Acquisition Design (Channel 1)

**Date:** 2026-09-15. **Status:** Ratified alongside [ADR-070](ADR/ADR-070-Channel-1-Discovery-Matching-and-Post-Ride-Relationship-Formation.md) — Option B. This is the product-design companion to that ADR's architecture; read the ADR for full technical reasoning and file/line citations. This document does not repeat every citation — it states the design decisions and points at the ADR for proof.

---

## 1. Problem

`docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md` Section 7 found the single largest gap in PIOS Taxi: a driver's client base can only ever grow from relationships they already had before joining PIOS. Every existing path — Circle of Trust, Primary Driver, Trusted-first fallback (ADR-068) — makes an *existing* network more resilient and rewarding. None of them create a *new* one. A driver who joins PIOS with zero clients has no way to get their first one through the product itself.

## 2. Product Principle

Unchanged, and this design is built to *serve* it, not replace it: **«Клиент следует за пригласившим. Оплата следует за трудом. Доверие следует за качеством.»** Discovery matching is a **front door into** the existing Personal Business Network model, not a parallel aggregator. Once a discovery ride completes, the passenger and driver enter the exact same relationship system (Circle of Trust, Primary Driver, repeat-ride priority) that a link-invited passenger already uses — there is no second-class relationship type.

## 3. User Journey

```
Незнакомый пассажир (нет Primary, нет Trusted)
        ↓
открывает PIOS напрямую (не по чьей-то ссылке)
        ↓
указывает откуда/куда, отправляет запрос
        ↓
First Refusal: NoPrimaryDriver (у пассажира никого нет)
        ↓
Fallback Dispatch: Tier 1 (trusted, пусто для незнакомца) → Tier 3 (открытый пул, test/real-сегрегация ADR-069)
        ↓
реальный доступный водитель получает Proposal
        ↓
принимает, называет цену, пассажир подтверждает
        ↓
Assignment → arrive → start → COMPLETED
        ↓
пассажир видит «Добавить в мои водители», называя РЕАЛЬНОГО водителя поездки
        ↓
(если нажал) Connection создаётся — тот же ConnectionEstablished → Tier 1 у ADR-068
        ↓
водитель видит нового клиента в «Мой бизнес → Клиенты»
        ↓
пассажир заказывает того же водителя снова через «Мои водители»
```

## 4. Discovery Model — chosen: **A (request-based matching), reusing the existing chain unchanged**

Of the four options the task named (A/B/C/D), **A** is correct for PIOS specifically because the matching decision (who gets this passenger) is already made entirely by the existing, ratified Fallback Dispatch chain (First Refusal → Tier 1 → Tier 3) — there is no need to build a second decision-making mechanism. **Not B** (nearby/available driver discovery, i.e. a directory the passenger browses) — the task's own explicit constraint forbids a "выбери любую машину" catalog experience, and PIOS's whole product thesis is "PIOS находит подходящего водителя," not "here is a list." **Not C** (route-based matching) — no geographic/routing capability exists in PIOS today (`ADR-034` Part 2's forbidden-inputs list explicitly excludes geographic matching), and inventing one is out of this Sprint's scope. **Not D** (hybrid) — nothing here needs hybridizing; A alone fully satisfies the requirement with zero new dispatch logic.

**What this actually requires, precisely (ADR-070 Part 1):** a new frontend entry point that submits `POST /v1/orders` with `passengerReference` only (no `driverId`-derived context) and **does not** set `explicitDriverIntent: true` — letting the existing chain match. No backend change to the matching decision itself.

## 5. Matching

Entirely inherited, unchanged, from ADR-068/ADR-069 — this is the single biggest reason Channel 1 is cheap to build. In order:

1. First Refusal checks the passenger's Primary Driver. If one exists and is eligible, they get the ride — **a discovery order never displaces an existing relationship** (ADR-070 Part 4(a), Gate 1).
2. If no primary, `NoPrimaryDriver` triggers Fallback Dispatch with no exclusions.
3. Tier 1 (trusted drivers, available, `is_test`-matched) — for a true stranger this is empty, but for a passenger with *some* trusted drivers who simply has no primary, this still protects their existing network first (Gate 2).
4. Tier 3 (open marketplace, longest-idle-available, `is_test`-matched, phantom-proof per ADR-069) — the actual stranger-matching case.

## 6. Fairness

Already correct, already live, already proven this session (ADR-068's own E2E: driver B selected over driver C despite C being available first, purely because B was trusted — and *within* a tier, the tie-break is `ORDER BY updated_at ASC`, i.e. longest-idle-first, which is exactly the fair-rotation property the task asked for). **No `ORDER BY created_at LIMIT 1` and no "always the same driver" exists anywhere in this chain.** Nothing in Channel 1 needs to touch this. If real discovery volume later reveals the longest-idle tie-break isn't distributing fairly enough among many idle drivers, that is a future, separately-evidenced Sprint — not invented here.

## 7. Proposal

Unchanged (ADR-070 Part 1, constraint 4). The driver-facing Proposal — order, route/pickup, notes, accept/decline, messaging — is exactly what already exists; a discovery-matched driver's experience of receiving and accepting a ride is indistinguishable from any other Proposal.

## 8. Customer Ownership — how a completed ride becomes a relationship

**Option B (ratified): passenger-confirmed, reusing the existing "Добавить в мои водители" mechanism exactly, corrected to name the driver who actually completed the ride rather than a URL route parameter.**

Why not automatic (Option A): `docs/PRODUCT_DECISION_CIRCLE_OF_TRUST.md` Decision 3 and business rules 2–3 **already, explicitly, three times over, forbid** inferring circle-of-trust membership from completing a ride. That is a ratified Product Decision this Sprint does not have standing to silently reverse. ADR-070 Part 3 fully designs the automatic mechanism in case a future, separate Product Decision amends that document — it is not built now.

**The mechanism, concretely:** after `COMPLETED`, the passenger's own already-authorized read of the ride (assignment/proposal status, which the passenger's client already polls) tells them which driver actually completed it. The existing "Добавить в мои водители" action — same endpoint (`POST /v1/connections`), same idempotency (a pair that already has a Connection is a no-op, both by application logic and by the `UNIQUE (driver_id, passenger_reference)` constraint) — is offered, naming that real driver. One tap, passenger-initiated, fully compliant with the ratified Product Decision.

## 9. Repeat Ride

Untouched (ADR-070 Part 4(b)). Once the Connection exists, the passenger's existing "Мои водители" → "Заказать снова" path is exactly what a link-invited passenger already uses — `explicitDriverIntent: true`, direct Proposal to the named driver, never re-entering the discovery/fallback chain. A passenger who explicitly wants their own driver back never gets rerouted to a stranger, structurally, not by convention.

## 10. Network Effect

A discovery-matched driver, once saved, becomes a Tier 1 (trusted) candidate for that passenger going forward (ADR-068), exactly like any link-formed connection — no second-class relationship. If that passenger later recommends the driver to someone else, today's product still requires the new person to get that driver's specific link (Section 6, step 12 of the audit already names this as a smaller, separate gap) — **not solved by this Sprint**, and not invented here.

## 11. Anti-client-stealing rules

Both rules the task named are already structurally guaranteed, not newly built (ADR-070 Part 4, verified against source):

- **A driver cannot displace a passenger's existing primary/trusted driver.** Two gates: (1) Fallback Dispatch never even runs when a primary is available; (2) within Fallback Dispatch, Tier 1 always precedes Tier 3.
- **A driver cannot make themselves anyone's primary**, discovery or otherwise (`PRODUCT_DECISION_CIRCLE_OF_TRUST.md` business rule 3, unchanged) — Connection creation is always passenger-initiated, and primary designation is only ever set by the passenger's own explicit act, never automatically, never by a driver.

## 12. Passenger Value

The discovery-matched passenger gets exactly what any PIOS passenger gets once matched — visible driver identity and availability, price stated before commitment, direct messaging, and (new, via this Sprint) a clear, honest path to keep the driver they liked. What they do **not** get, honestly named rather than glossed (matches audit Section 4/6): no rating/review signal about the driver before the first ride (deliberate product stance, audit Section 3), and no price benchmark against a market rate. This Sprint does not invent either.

## 13. Driver Value

A driver with zero clients can, for the first time, receive an order from someone who didn't already know them — the literal thing Section 7 of the audit found missing. Once that ride completes and the passenger saves them, it shows up exactly where every other client does: "Мой бизнес → Клиенты," "Постоянных клиентов," "Новых клиентов" — no new screen, no parallel bucket for "discovery clients" vs "referral clients." This is deliberate: from the driver's perspective, a client is a client, regardless of channel.

## 14. Monetization Implications

Not built in this Sprint (explicitly out of scope, per the task's own instruction). Named here because it's the honest connective tissue to the audit's Section 11 finding: Channel 1 is what makes "PIOS helps me get new clients, not just keep old ones" a defensible part of a future subscription's value proposition — see the separate `docs/PIOS_TAXI_MONETIZATION_VALUE_MAP.md`.

## 15. Architecture

Full detail in ADR-070. Summary: **zero changes to the dispatch decision chain.** One new frontend entry point (driverless order submission). One frontend correction (post-ride save action names the real driver, not a route param). No new backend module, event, table, or cross-module contract of any kind.

## 16. API Changes

**None to any existing contract.** `POST /v1/orders` already accepts a driverless submission (`passengerReference` only) — this Sprint is the first real caller to actually send one. No field is added, removed, or changed on any existing endpoint.

## 17. DB Changes

**None.** No migration in any module. This Sprint is entry-point and read-path wiring only.

## 18. Security

Unchanged authorization model throughout (ADR-054 Part 6's standing limitation is not solved and must not be described as solved). The new entry point requires the same passenger session-token authentication `POST /v1/orders` already requires from `RideRequest.tsx` today — no new anonymous surface, no new unauthenticated write path. Driver-identity disclosure to the passenger, once matched, stays within `ADR-059`/`ADR-060`/`ADR-066`'s existing limits — no new field beyond what the passenger's own already-authorized reads already expose.

## 19. Acceptance Criteria

Identical to the task's own Definition of Done, items 1–14 (items 15–20 are UI/process items tracked separately in the implementation report, not this design document):

1. A passenger with no primary/trusted connection can submit an order with no driver link.
2. PIOS matches them to a real, available, correctly `is_test`-segregated driver via the existing chain.
3. Fairness is inherited (longest-idle tie-break, no fixed favorite).
4–5. Test/phantom drivers cannot be selected (ADR-069, already proven).
6–8. Proposal, accept, full ride lifecycle — unchanged, already proven this session.
9–10. `COMPLETED` → passenger can explicitly save the real driver → appears in "Мой бизнес → Клиенты."
11. Passenger can repeat-order that same driver.
12–13. Existing primary/trusted relationships retain priority — structurally guaranteed, unchanged.
14. No automatic "theft" of a client by another driver — Option B requires passenger action; no automatic write exists.
