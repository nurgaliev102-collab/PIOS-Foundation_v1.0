# PIOS Taxi — Product Vision & Definition v2

**Status:** Consolidated product-vision document, authored by the architect (ChatGPT) and product owner across several conversations, compiled here 2026-09-05. This is a **vision and definition document, not an ADR and not an implementation authorization.** It supersedes nothing by itself — it sits alongside, and consolidates, [`PIOS_PRODUCT_VISION.md`](PIOS_PRODUCT_VISION.md) (the original, still-valid ratified vision), [`PIOS_BUSINESS_MODEL_V1.md`](PIOS_BUSINESS_MODEL_V1.md) (money model), and [`PIOS_PROJECT_DESCRIPTION_GAP_ANALYSIS.md`](PIOS_PROJECT_DESCRIPTION_GAP_ANALYSIS.md) (what of this is actually built today). See §50 for how these four documents relate.

**What this document is for:** a single place that defines *what PIOS Taxi is*, so that future scope decisions — MVP, V1, and beyond — get checked against one coherent model instead of argued feature by feature.

**What this document does not do:** it does not raise or replace any ADR, it does not change any code, and none of the illustrative numbers in it (thresholds, prices, feature lists) are ratified business rules. Per this repo's own "Never Invent Business Rules" and "Never Change Architecture Without an ADR" rules, nothing here is buildable until a specific piece of it goes through its own architecture/ADR pass.

---

## 1. What PIOS is

**PIOS Taxi is a person's own personal transportation network.**

It brings together: city rides, rides booked for the future, intercity travel, airport and rail-station trips, recurring and shift-worker (vahta) trips, individual and group trips, a personal network of acquaintances, trusted drivers, professional carriers, and matching to discover new people.

> **PIOS doesn't just find a car for someone. PIOS helps a person organize transport through their own network and the open transport market.**

## 2. Three entities the whole product is built from

Not one object ("Order"), but three:

- **Person** — a human being.
- **Relationship** — a connection between two people.
- **Trip Intent / Trip** — an intention to travel, or an actual trip.

```
PERSON → RELATIONSHIP → NETWORK

PERSON → TRIP INTENT → MATCHING → TRIP → RELATIONSHIP → NETWORK
```

This triad — not "Order" — is the architectural center of gravity the rest of this document builds on.

## 3. The user is not just "a passenger"

The same person can be, at different times: a passenger, a trip organizer, an inviter, a group member, the owner of a personal network, a driver's returning client, a recommender, and — sometimes — a driver. PIOS should not require a separate "social" product next to Taxi: **the network is built into the transport product itself**, not bolted alongside it.

## 4. My Network — the heart of the product

```
МОЯ СЕТЬ
👤 Люди
🚕 Мои водители
🏢 Компании
📍 Места / организации
```

Not a decorative address book. Every connection carries context: where it came from, how many trips happened, when the last interaction was, how frequently the two people interact, whether the connection is mutual, and whether it's usable in matching.

## 5. The network must actually change the outcome of a trip

If it doesn't influence matching, it's decoration nobody uses. On a request, PIOS checks, in order: (1) my drivers, (2) my network, (3) trusted drivers, (4) open PIOS matching — and shows the result accordingly ("Your driver Alexander can do this trip" + "6 other suitable drivers below").

## 6. But the network must never lock a person in

If a person's usual driver isn't available, PIOS falls through to open matching automatically. **Personal network + open marketplace, always both** — never a closed system that fails silently when the preferred option is unavailable.

## 7. Invitation as the network-growth mechanism

An invited person is never "owned" by whoever invited them — they get their own account, their own reputation, their own network, and can invite others in turn. Invitations form a real tree of relationships, but **a user's visible network stays personal** — PIOS doesn't expose someone else's connections without permission.

## 8. The network effect

```
uses PIOS → gets a good driver → saves them → invites acquaintances
  → new user → builds their own network → repeat
```

Important distinction: **the bonus is secondary, the network is primary.** This should not be built or explained as "just a referral program."

## 9–12. The driver is a full participant, not a checkbox

Registration as `Имя + Телефон + Я водитель ✓` is deliberately rejected as too primitive. A driver needs a full profile across four groups:

- **Identity** — name, photo, birth date, phone, city, bio.
- **Driver data** — license, categories, years of experience, required documents, verification status. *(Regulatory note, not yet legally verified by this session: the pasted source material cites a 3-year minimum license-category requirement for a passenger-taxi driver under Russian law — see §49.)*
- **Vehicle** — make, model, year, color, plate, body type, seat count, photos, documents, verification status.
- **Ride conditions** — luggage, pets, child seat, air conditioning, smoking, extra stops, night rides, intercity rides.
- **Work types** — city, airport, rail, intercity, long-distance, recurring routes, group transport.

**One driver profile, multiple trip scenarios** — a driver working city + airport + intercity is one professional profile with several offer types, not several separate driver accounts.

Registration should unlock in stages, not all at once:

```
ACCOUNT → DRIVER → CAR → VERIFICATION → PROFESSIONAL PROFILE → READY TO WORK
```

Each stage has a visible status; functionality unlocks only as required checks complete.

## 13. The driver builds a business

```
МОИ КЛИЕНТЫ · МОИ ПОЕЗДКИ · МОИ ПОВТОРНЫЕ КЛИЕНТЫ
МОИ МАРШРУТЫ · МОЯ РЕПУТАЦИЯ · МОЯ СЕТЬ
```

A driver should be able to see how many people specifically come back to *them* — economically more meaningful than raw order volume. Positioning stays: **"On an aggregator you work for the platform. On PIOS you build your own business using the platform."**

## 14–15. Two directions a trip can form from, and the marketplace between them

- **Demand-first**: a passenger states "I need to get from A to B" → creates a **Trip Intent**.
- **Supply-first**: a driver states "I'm driving A→B tomorrow, N seats free" → creates a **Trip Offer**.

```
TRIP INTENT ↔ MATCHING ↔ TRIP OFFER
```

This is the specific mechanic to borrow from BlaBlaCar's own model (a driver publishes a route, a passenger searches, the system matches — including matching against a route segment, not just full-route equality). Both live sides make PIOS a genuine **Trip Marketplace** — offers and demand coexisting, not just a list of existing rides.

## 16. Intercity as its own strong scenario

BlaBlaCar itself is built around cost-sharing carpooling. PIOS should not copy that constraint — intercity in PIOS can be **commercial**, spanning private drivers, professional drivers, minivans, and carriers, not only ride-sharing.

## 17–20. Regional, airport, and vahta (shift-worker) trips

The regional chain this product should serve well:

```
посёлок → райцентр → город → ЖД/аэропорт → другой регион → вахта
```

Often the need isn't "a taxi service" but **a reliably organized transport route.** Vahta (shift workers) is called out as a strong early specialized use case: known dates, repeated routes, group travel, regular trips to transport hubs — PIOS should support **Recurring Trip Pattern** (e.g., every 30 days, 05:00) and detect when several individually-submitted intents are spatially/temporally close enough to become one group offer (a van, multiple cars, or an existing offer), rather than only ever showing pre-existing rides.

## 21–22. Route-segment matching and smart pickup/drop-off

Matching should work on segments of a published route (A─C─D─B), not only full-route equality — again the specific mechanic BlaBlaCar itself uses. Pickup/drop-off should suggest a compromise point when a home address would force an unreasonable detour, balancing passenger convenience against the driver's route efficiency.

## 23–26. Trust, relationship strength, and mutual reputation

Trust should be layered, not a single score:

```
PHONE VERIFIED + IDENTITY VERIFIED + DRIVER VERIFIED + CAR VERIFIED
+ REVIEWS + TRIP HISTORY + REPEAT RELATIONSHIP + COMMON CONNECTIONS
```

The last two are specifically what differentiates PIOS from a generic rating: **"we've ridden together 7 times" is worth more to a specific user than a general 4.9.** A **Relationship Strength** signal should exist (illustrative example only, not a ratified threshold: 1 trip → acquaintance, 3 → known driver, 7 → trusted, 20 → regular). Reputation should be two-sided: passenger→driver and driver→passenger.

## 27. Preferences engine

A user sets ride preferences once (smoking, pets, luggage size, child seat, music) — for both passenger and driver — and matching becomes progressively personalized around them.

## 28. Booking modes

For longer/intercity trips, two modes: **Instant** (matches → book immediately) and **Request** (driver reviews the passenger's profile, accepts or declines). Note: the price-confirmation feature already shipped for city rides (driver states a price, passenger confirms or declines) is structurally a Request-style step, though scoped to today's single-ride flow, not intercity booking.

## 29. Pre-trip communication, built in

Passengers and drivers should be able to clarify details ("can I bring a large suitcase," "where do we meet," "is there a child seat") **inside PIOS**, not by being pushed out to a third-party messenger.

## 30. Trip reliability state machine

```
Confirmed → Driver on the way → Arrived → Passenger boarded → Trip started → Trip completed
```
plus separately tracked: Cancelled, No-show, Late, Problem, Dispute.

## 31. Goal-aware airport/rail scenario

Not "take me to the airport" but goal-aware: given a flight time, PIOS computes a recommended departure time with a buffer, using travel-time estimation. Same idea for rail.

## 32–34. Return trips, repeat trips, and trip memory

After booking A→B, PIOS should proactively ask about a return trip and let the user pre-save it — valuable for vahta workers, business trips, students, airport runs, and regular intercity routes. After a completed trip: rate → save driver to network → next time the system says **"Your driver Alexander is available"** instead of showing a generic list of 17 drivers. PIOS should remember the pairing (person ↔ driver ↔ route ↔ trip count) and surface it proactively.

## 35. Purpose-of-trip awareness

Not just "from/to" but **why**: airport, rail, work, vahta, business trip, going home, an event — so the system can better choose timing, vehicle type, luggage handling, route, return-trip suggestion, and group matching.

## 36–40. Saved routes, trip groups, organizers, driver routes, and supply forecasting

- **My Route** — a saved template ("Дом → аэропорт") with a recurring schedule.
- **My Group / Trip Group** — a vahta-style community mechanic: one person creates a group ("Вахта Казань — Ноябрьск"), others join, and the group gets one shared route/schedule/transport arrangement — a potentially strong B2C/B2B2C mechanic.
- **Trip organizer** — one person can create a **Group Trip Request** on behalf of several people ("we need 7 people from Bugulma to Kazan airport"); PIOS looks for one minivan, several cars, or an existing offer.
- **Driver's own recurring routes** — a driver can declare a standing schedule ("Казань→Уфа Пн/Ср/Пт"), giving PIOS ongoing knowledge of their transport behavior.
- **Supply forecast** — if a pattern of ~40 similar requests appears every month on a given route, that's a signal of stable demand PIOS can use to actively recruit drivers into that corridor ("17 people are looking for transport on this route tomorrow"), and even connect supply to demand before an order exists ("8 passengers in Bugulma tomorrow at 06:00 need Kazan" → a driver opts in → new supply is created).

## 41. Business model

Multiple revenue engines rather than one commission, consistent with — and not a departure from — [`PIOS_BUSINESS_MODEL_V1.md`](PIOS_BUSINESS_MODEL_V1.md): transaction/service fee where justified, a Driver Pro subscription (client management, analytics, promotion, priority, recurring-route tools), B2B (vahta, corporate staff transport, airport/rail, regular corporate routes), group-transport organization fees, and marketplace promotion tools. No specific rate is fixed here — same caveat as the existing business-model doc.

## 42. Why a regional market may be the right entry point

A megacity means huge competition (100 cars, 1000 passengers). A regional area might mean 20 drivers, 500 regular passengers, and a handful of stable routes — with a real, underserved problem: organizing transport between towns and major transit hubs. There, PIOS can become **a local transportation network**, not just another app.

## 43. Relationship density as PIOS's specific advantage

In a small city, people already know each other, share employers, share routes, live nearby, work the same vahta rotation, use the same airport, take the same train. **The network already exists in real life — PIOS digitizes it.** In a megacity a person is one of millions; this advantage is much weaker there.

## 44. What PIOS explicitly is *not*

Not "Uber without commission." Not "BlaBlaCar for taxi." Not a social network for drivers. Not a plain carrier marketplace. It's the combination of all of the pieces above — no single one of them is the product.

## 45. The core flywheel

```
1. Person arrives → 2. creates a trip intent → 3. PIOS finds a solution
→ 4. person gets a ride → 5. trust forms → 6. a connection appears
→ 7. it enters the personal network → 8. the next trip gets easier
→ 9. the user invites acquaintances → 10. new people build their own networks
→ 11. the network gets denser → 12. matching improves
→ 13. repeat trips increase → 14. PIOS's value grows
```

## 46. What NOT to build in v1 — discipline

Explicitly deferred: a full social network, dozens of transport types, a complex recommendation engine, an AI dispatcher, the entire monetization stack at once, national coverage, hundreds of route types. Building all of this at once produces endless, undirected development — the opposite of what a foundation-phase project needs.

## 47. The three loops that must be proven first

1. **Repeat ride**: passenger → trip → driver → connection saved → repeat trip. *(Already built and live — see [`PIOS_PROJECT_DESCRIPTION_GAP_ANALYSIS.md`](PIOS_PROJECT_DESCRIPTION_GAP_ANALYSIS.md) §2 "loop 1.")*
2. **Intercity / planned trip**: passenger → future/intercity trip → driver's route → matching → booking → trip. *(Not built — the single largest concrete gap identified.)*
3. **Invitation → own network**: passenger invites an acquaintance → they register → they build their own network. *(Partially built.)*

If these three hold up, "there is a real PIOS, not just a nicely-styled taxi MVP" (the architect's own framing).

## 48. Canonical one-paragraph definition

> **PIOS Taxi is a person's personal transportation network, letting them organize a trip now or in advance, find transport in their own trusted network or through open matching, join other passengers for a shared trip, find or publish an intercity route, and after the trip keep the relationship with the driver as part of their personal transportation network. The driver gets not just orders but their own professional profile, reputation, vehicle, routes, and a growing client base. Every trip can simultaneously solve a transport need, create a new trusted connection, and expand the PIOS network.**

Regional, intercity, airport, and vahta trips are treated not as an extra feature but as **one of the potentially key markets where PIOS's Network-first concept has a genuine structural advantage** over a generic aggregator.

## 49. Legal/regulatory layer — flagged, not resolved

The source material for this document asserts that, as of the material's own reference point, Russian taxi regulation includes a Ministry of Transport order (cited as Приказ Минтранса №161, effective 2026-09-01) governing passenger/baggage road transport rules, and a separate requirement (cited: Article 12 of the taxi-driver-requirements law) that a passenger-taxi driver hold the relevant license category for at least 3 years. **This session has not independently verified either citation and is not a source of legal advice.** The architect's own point stands regardless of the exact citation: licensing/permits, the fрахтование (charter) contract model, driver/vehicle documentation, liability, and insurance need to be designed *alongside* the product architecture — not retrofitted after the fact — before any driver-facing legal/compliance claim is made in-product. This needs real legal counsel before it becomes a product decision, not an inference from a pasted citation.

## 50. How this document relates to what already exists

- [`PIOS_PRODUCT_VISION.md`](PIOS_PRODUCT_VISION.md) — the original ratified vision (the aggregator-vs-PIOS positioning, the driver-first UX ordering, "vision vs. reality today" as of the last pilot). Still valid; this document elaborates and structures it further, it does not contradict it.
- [`PIOS_BUSINESS_MODEL_V1.md`](PIOS_BUSINESS_MODEL_V1.md) — the money-model detail this document's §41 summarizes; treat that file as the fuller version of that one topic.
- [`PIOS_PROJECT_DESCRIPTION_GAP_ANALYSIS.md`](PIOS_PROJECT_DESCRIPTION_GAP_ANALYSIS.md) — the honest, code-verified account of which pieces of *this* vision already exist today (First Refusal as a narrow version of §5's network-priority matching, the `network-management` module as a narrow version of §2/§4's Person/Relationship, the price-confirmation feature as a narrow version of §28's Request booking, and so on) versus which are pure vision with zero code footprint (all of intercity/Trip Intent/Trip Offer/route-segment matching/groups/vahta — §14–22, §36–40 above).

## 51. What happens next

This document does not select a milestone. Per §46–47 and the gap analysis's own recommendation, the next real decision is still: **which one unproven loop (intercity/planned trips, or two-sided trust/ratings, or deeper Driver OS tooling) gets built first** — a product-owner/architect call, to be made deliberately rather than inferred from how much of this document sounds exciting.
