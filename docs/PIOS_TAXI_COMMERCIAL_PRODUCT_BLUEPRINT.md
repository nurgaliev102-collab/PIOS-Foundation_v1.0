# PIOS Taxi — Commercial Product Blueprint

**Date:** 2026-09-15. **Status:** Research and planning only. No code, ADR, database, or production change was made to produce this document. Builds directly on `docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md`, `docs/PIOS_TAXI_CUSTOMER_ACQUISITION_DESIGN.md`, `docs/PIOS_TAXI_MONETIZATION_VALUE_MAP.md`, and ADR-068/069/070 — all read in full, plus today's own live production E2E proof of the discovery→relationship→repeat-ride loop. Where this document repeats an earlier finding, it cites the source rather than re-deriving it; where it goes further, that is marked explicitly.

**Classification discipline used throughout, per the task's own requirement:**
- **VERIFIED** — proven by code + test + live production evidence this session.
- **PARTIALLY VERIFIED** — implementation exists, but the full user scenario has not been proven end-to-end.
- **MISSING** — no implementation exists.
- **BROKEN** — implementation exists but behaves incorrectly.
- **PRODUCT GAP** — technically works, but does not yet create sufficient user value.

---

## 1. Executive Summary

PIOS Taxi has, as of today, a genuinely working core: registration, vehicle profile, online/offline, order→proposal→price→assignment→trip lifecycle, Circle of Trust, Primary Driver, Trusted-first Fallback Dispatch, test/real data segregation, and — as of this session — real discovery matching for a passenger with zero prior relationship, with the full loop (stranger → match → ride → saved relationship → repeat order) **proven live on production**, not simulated.

What does not yet exist, stated as plainly as the working parts: **any monetization mechanism at all** (zero billing/subscription code anywhere), **any reputation signal** (deliberate, by product decision, but still a real gap for a stranger meeting a driver for the first time), and a **"Мой бизнес" dashboard that is richer than a single completed-ride counter for most drivers today** because the product is brand-new and has no real usage history yet.

The honest, single-sentence state of the product: **PIOS Taxi's technical foundation for "independent driver business network" is real and load-bearing; its commercial and reputational layers are not built yet, and neither should be invented casually — both require explicit Product Decisions this document identifies rather than assumes.**

---

## 2. Current Product Reality

Seven backend bounded contexts (`identity`, `driver-management`, `passenger-experience`, `order-management`, `dispatch`, `network-management` [formally superseded, ADR-064, unused], `core` [not deployed]), a React/TypeScript frontend with a token-driven design system, deployed on a single Linux VPS via systemd, Postgres per module, RabbitMQ for cross-module events. Three ADRs ratified and shipped to production *this same day*: ADR-068 (Trusted-first fallback), ADR-069 (test/real segregation — closed a real defect where a live passenger could have been matched to a fixture driver), ADR-070 (Channel 1 discovery matching, Option B relationship formation). All three are **VERIFIED** by live E2E against `piosapp.ru`, not merely unit-tested.

---

## 3. Driver Journey

| Stage | Exists? | Where | Really works? | Prod E2E proof | Key API | What driver sees | What's missing |
|---|---|---|---|---|---|---|---|
| Регистрация | Yes | `frontend/src/pages/DriverHome` auth steps, `POST /v1/identities/register` | Yes | **VERIFIED** (today, multiple runs) | `/v1/identities/register` | Phone + password form | Password toggle now fixed (was emoji, fixed today) |
| Профиль (имя) | Yes | `DriverHome.tsx` name step | Yes | **VERIFIED** | `PATCH` driver name (via associate flow) | Name entry | — |
| Автомобиль | Yes | `DriverHome.tsx` "Моя машина" form | Yes | **VERIFIED** (screenshot `07_driver_profile.png`, audit) | driver-management vehicle fields | Марка/модель/цвет/номер/места | — |
| Online/offline | Yes | `DriverHome.tsx` "Главное" tab | Yes | **VERIFIED** | `POST /v1/drivers/{id}/availability` | Toggle, plain confirmation | Screen is nearly empty below the toggle (audit, screenshot `03`) — a missed first-impression opportunity, not a defect |
| Получение первого заказа — **from an existing relationship** (Primary/Trusted) | Yes | First Refusal, Fallback Tier 1 | Yes | **VERIFIED** (ADR-068 E2E, earlier today) | `POST /v1/proposals` (system-created) | Proposal card, route, notes | — |
| Получение первого заказа — **from a total stranger, zero prior relationship** | Yes, as of today | Fallback Tier 3, now reachable via `/request` (ADR-070) | Yes | **VERIFIED** (this session's own live E2E, twice) | Same Proposal API, driver-agnostic on the passenger's side | Identical Proposal card — no visual distinction between a discovery match and a link-based order | Driver has no way to know "this is a brand-new client, not someone who already knew me" — a real, small, easy PRODUCT GAP |
| Поездка (accept → price → assignment → arrive → start) | Yes | `ProposalController`, `AssignmentController` | Yes | **VERIFIED** | `propose-price`, `confirm-price`, `arrive`, `start` | Standard flow | — |
| Завершение | Yes | `complete` endpoint, `Trip` aggregate (ADR-063) | Yes | **VERIFIED** | `POST /v1/assignments/{id}/complete` | Status → COMPLETED | — |
| Сохранение клиента | Yes, as of today (Option B) | `handleSaveDriver`-equivalent, now driver-agnostic (`RideRequest.tsx`), `POST /v1/connections` | Yes, but **passenger-initiated, not driver-initiated** | **VERIFIED** (today's E2E) | `POST /v1/connections` | Driver has no visibility into *whether* the passenger did this — they just see the client appear (or not) | Driver cannot prompt/remind a passenger to save them — real, minor PRODUCT GAP |
| Повторная поездка | Yes | `explicitDriverIntent: true` path, unchanged | Yes | **VERIFIED** (today's E2E) | `POST /v1/orders` + `POST /v1/proposals` direct | Standard flow, no re-discovery | — |
| Клиентская база | Yes | "Мой бизнес → Клиенты" (`DriverHome.tsx`) | Yes, but minimal | **VERIFIED** existence, **PARTIALLY VERIFIED** as a real business tool (no real driver has used it with volume yet) | `GET /v1/connections?driverId=` | Name-or-honest-fallback, one repeated "Поделиться" action | No per-client history, no last-ride date, no distinguishing "new" vs "returning" inline (aggregate counts exist, per-row detail does not) — **PRODUCT GAP**, named already in the audit |
| Повторные клиенты | Yes | `driver_milestones.repeat_clients_count` | Yes | **VERIFIED** (data path), not yet observed at real volume | `driver_milestones` table | "Постоянных клиентов" tile | — |
| Доход | Partial | `driver_milestones.total_stated_earnings`, `unpriced_rides_count` | Yes, but **only for rides with a numeric-parseable stated price** (confirmed today: "380 RUB" failed to parse into `stated_price_parsed`) | **PARTIALLY VERIFIED** — the tile itself is correct and honest, but the underlying parser silently under-counts | Same milestones table | "Заработано по вашим ценам" tile, with an honest "ещё N поездок без цены" caveat | The parser gap is real but was already disclosed by the UI's own caveat text — not hidden, just imperfect |
| Репутация | **No**, by deliberate product decision | `DriverTrustIndicator.tsx` KDoc: *"never a rating, count, rank, or any fabricated trust score"* | N/A | N/A | — | Only "Основной" badge and availability | **MISSING**, and named as deliberate, not an oversight — Section 10 covers why this still needs a decision |
| Рост бизнеса | Partial | "Мой бизнес → Обзор" dashboard | Yes, structurally | **VERIFIED** (screenshot `04`), honest zero-state, real data wiring | `driver_milestones`, `connections` | Completed rides, streak weeks, repeat clients, new clients today, lifetime referred, earnings | No trend-over-time view (this week vs last week) — named already in the earlier Commercial Completion Matrix analysis this session referenced, still not built |

---

## 4. Passenger Journey

| Stage | Exists? | Really works? | Prod E2E proof | What's missing |
|---|---|---|---|---|
| Первый заказ (via link) | Yes | Yes | **VERIFIED** | — |
| Первый заказ (discovery, no link) | Yes, as of today | Yes | **VERIFIED** (today) | — |
| Выбор/получение водителя | System-chosen (discovery) or link-chosen | Yes | **VERIFIED** | No passenger-facing explanation of *why* this driver was chosen (trust-tier vs open pool) — minor, not blocking |
| Доверие | Name, availability, primary badge, no photo, no rating | Partial | **VERIFIED** as built; **PRODUCT GAP** as a trust signal for a stranger | No photo (data model has none — audit Section 6), no rating (Section 10) |
| Поездка | Full lifecycle | Yes | **VERIFIED** | — |
| Оценка качества | **None** | N/A | N/A | **MISSING** — no passenger-side rating/feedback of any kind |
| Сохранение водителя | Yes (Option B, explicit) | Yes | **VERIFIED** (today) | — |
| Мои водители | Yes, `MyDrivers.tsx` | Yes | **VERIFIED** (existing feature, pre-dates this session) | — |
| Повторный заказ, конкретный водитель | Yes | Yes | **VERIFIED** (today) | — |
| История отношений | Partial — connection exists, ride history exists per-driver on the driver's side; passenger-side ride history was not verified this session | **PARTIALLY VERIFIED** | Not re-checked this session | Worth a dedicated pass, not assumed either way |
| Собственная сеть доверенных водителей | Yes, Circle of Trust supports multiple connections + one primary | Yes | **VERIFIED** (ADR-054, live for months; ADR-068 fallback proven live today) | — |

**Why is it worth it for a passenger to use PIOS instead of just calling a taxi app?** The honest, evidence-based answer, not a slogan: **a passenger who has already found one good driver gets a materially better experience the second time — a known person, a stated price before commitment, direct messaging, and (as of today) a graceful fallback to someone they already trust if that driver is busy, instead of a stranger.** That is real and differentiated. It is **not yet** a strong answer for a passenger's *first* interaction with PIOS — discovery matching (today) removes the "I need someone's link" barrier, but a first-time passenger gets no stronger a promise than "PIOS will find you *someone*," with no rating, no photo, and no price benchmark. **This is recorded as a genuine, current limit on passenger value, not concealed.**

---

## 5. Driver Business Model — the central question

**Why should an independent driver pay PIOS monthly?** Per `docs/PIOS_TAXI_MONETIZATION_VALUE_MAP.md` (written earlier today), the real, already-built sources of value, ranked by how load-bearing they are to the product's own stated thesis:

1. **Channel 1 discovery matching (today's work)** — the only mechanism that can bring a driver a client they did not already know. This is the strongest possible pitch for a subscription, because it is the one thing an independent driver structurally cannot buy or build alone.
2. **Trusted-first Fallback (ADR-068)** — protects a driver's own client relationships even through their own unavailability; a client who would have gone to a stranger stays in the driver's own network instead.
3. **Personal link/QR + Circle of Trust** — the infrastructure of "my own business, not a gig."
4. **"Мой бизнес" dashboard + client list** — visibility into whether the business is actually growing.

**What is core paid value vs. baseline free functionality — a genuine distinction, not asserted lightly:** The baseline "a driver can always earn through PIOS" guarantee (registration, going online, receiving and completing a ride, keeping 100% of the stated price) must almost certainly stay free — PIOS taking money to let a driver *work at all* would contradict "оплата следует за труду" and would functionally become the commission-based aggregator model this product explicitly rejects. The candidates for paid value are the **business-building layer on top**: discovery matching reach, the analytics dashboard's depth, and possibly client-list tooling. **This document does not decide the exact boundary** — that is Section 21's first required Product Decision, not something to assume here.

---

## 6. Passenger Value Proposition

Covered in Section 4. Summary: real and differentiated for a *returning* passenger with an existing driver relationship; honest and present but comparatively weak for a *first-time* passenger, who gets a working match but no trust signal beyond "PIOS found someone."

---

## 7. Network Effect

Tracing the two loops the task asks about, against actual code, not aspiration:

**Driver → new passenger → ride → trust → client → repeat ride.** **VERIFIED, self-sustaining, live-proven today.** Once a driver has a client, ADR-068's Trusted-first fallback means that relationship gets *reinforced* every time it survives an unavailability — this loop genuinely strengthens itself without manual intervention.

**Passenger → new driver → ride → save → repeat order.** **VERIFIED**, same evidence.

**Quality → trust → more repeat orders → more valuable network.** **PARTIALLY VERIFIED at best — this is where the network stops self-sustaining and still needs manual/external input.** There is no reputation signal (Section 10) feeding back into anything — a driver's "quality" today only shows up as raw completed-ride and repeat-client counts, never surfaced to a *new* stranger passenger deciding whether to trust a first match. **This is the one link in the chain the task specifically asked about that is not yet closed by the architecture — it requires a genuine product decision (does PIOS ever introduce any quality signal, and if so what), not a technical fix.**

**Passenger→passenger and driver→driver referral** (a passenger recommending a driver to a friend, or a driver referring another driver into PIOS): **MISSING** at the data-model level — confirmed this session (ADR-064, ADR-070 research) that no driver-to-driver edge exists anywhere in PIOS, and a passenger has no in-product "share this driver" action beyond manually copying a link. **Real gap, named, not invented around.**

---

## 8. Existing Capabilities (condensed inventory, full detail in Sections 3–4)

Registration/login, driver profile + vehicle, online/offline, Circle of Trust, Primary Driver, First Refusal, Trusted-first Fallback Dispatch (ADR-068), test/real segregation (ADR-069), discovery matching for strangers (ADR-070), full Proposal→Price→Assignment→Trip lifecycle, messaging (tied to a Proposal, **not gated by ride completion** — confirmed this session, a passenger and driver can still message after `COMPLETED`; not necessarily wrong, just unrestricted, worth a product decision on whether that's intended long-term), ride history (driver side), "Мой бизнес" dashboard, personal invite link/QR, "Мои водители" (passenger side), post-ride explicit relationship saving.

---

## 9. Product Gaps (technically works, value insufficient — distinct from Missing/Broken)

- **Client list depth** — a bare name+share-button list, no per-client history (Section 3, "Клиентская база" row).
- **First-discovery-match trust signal** — a stranger driver looks identical to a known one in the Proposal UI (Section 3).
- **Driver Home default tab sparseness** — real value ("Мой бизнес") is one tap away, unadvertised (carried over from the audit).
- **Messaging not gated by ride state** — works, but may not be the intended long-term behavior; a product decision, not a bug.
- **No earnings trend over time** — only a point-in-time snapshot exists.

---

## 10. Broken / Missing / Partial — consolidated

| Item | Classification |
|---|---|
| Monetization / billing / subscription | **MISSING** (confirmed zero code, repo-wide, this session's audit) |
| Driver reputation / rating | **MISSING** (deliberate product decision, not an oversight) |
| Passenger rating / feedback | **MISSING** |
| Driver photo on passenger-facing screens | **MISSING** (no data field exists) |
| Push/in-app notifications | **MISSING** (confirmed, repo-wide search, audit) |
| Driver-to-driver referral | **MISSING** (no data model, ADR-064) |
| Passenger "recommend this driver" action | **MISSING** |
| Earnings parser for non-numeric stated prices | **PARTIALLY BROKEN** — silently under-counts, but the UI honestly discloses the gap via "ещё N поездок без цены" rather than hiding it |
| Discovery-vs-known-driver visual distinction | **MISSING** (Section 3) |
| Everything in Sections 3/4 marked VERIFIED | **VERIFIED**, no regression risk identified in researching this document |

---

## 11. Commercial Value Map

Reproduced from `docs/PIOS_TAXI_MONETIZATION_VALUE_MAP.md` (written today) — see that document for the full FEATURE→DRIVER VALUE→PASSENGER VALUE→BUSINESS RESULT→WHY DRIVER WOULD PAY table. Highest-value row, updated with today's new capability: **Channel 1 discovery matching is now the single strongest subscription pitch in the product** — it is the one thing a driver structurally cannot replicate alone, and it is now live and proven.

---

## 12. Subscription Value Proposition

**Not decided by this document** — per the earlier value map's own closing line, inventing a price or feature-gate here would be exactly the fabricated business rule this codebase's own conventions forbid. What *can* be stated honestly: the strongest, evidence-backed candidate shape is **"access to Channel 1 discovery matching + full business analytics depth,"** with the baseline earn-a-living functionality (registration, going online, completing rides the driver already has access to, keeping 100% of the ride price) staying free. This is a recommendation for Section 21's Product Decision, not a ratified answer.

---

## 13. Commercial Product Scope

### MUST HAVE (already true or required to honestly call PIOS commercially ready)
- Registration, profile, vehicle, online/offline — **already true**.
- Full ride lifecycle — **already true**.
- Circle of Trust, Primary Driver, Trusted fallback — **already true**.
- Discovery matching for a stranger passenger — **already true, as of today**.
- Post-ride relationship formation, client list, repeat ride — **already true, as of today**.
- A ratified Product Decision on subscription value (not yet built — Section 21).
- Honest client-list depth improvement (per-client last-ride/history) — small, high-leverage.

### SHOULD HAVE
- A visible signal distinguishing a first-time discovery match from a known-driver order.
- Earnings trend over time in "Мой бизнес."
- A minimal notification mechanism (order arrived, message received) — named as a real gap in the earlier audit, not re-litigated here.

### LATER
- Driver reputation/rating (requires its own product decision on whether PIOS ever introduces one at all, given the deliberate current stance).
- Passenger rating/feedback.
- Driver-to-driver referral.
- Driver photo.

---

## 14. UX/UI Requirements

Per the earlier audit (Section 12 there), the redesigned surfaces (Business tabs, Home, onboarding copy) are genuinely close to the "Apple-like" bar already — clean typography, real SVG iconography, honest empty states. The concrete emoji defects the task worried about (PasswordInput 🙈/👁️, PassengerLanding 🚖, onboarding hardcoded "Артур") were **found and fixed this session**, live on production. Remaining UX work is prioritization, not a redesign: the Product Gaps in Section 9, not a visual overhaul.

---

## 15. Technical Architecture Impact

**None required for anything in Section 13's MUST HAVE list beyond what already shipped today.** Everything proven live this session (ADR-068/069/070) required zero net-new bounded contexts and, for Channel 1 specifically, zero backend changes at all. A future subscription/billing capability (Section 12) would be the first genuinely new bounded-context-scale addition — sized and scoped only once Section 21's Product Decision exists.

## 16. Data Model Impact

**None from today's Channel 1 work** (verified — no migration in any module). A future subscription mechanism would need its own schema, in its own module or an extension of `identity`/`driver-management` — not decided here.

## 17. API Impact

**None from today's work** — `SubmitOrderRequest` and every existing contract are unchanged. A future subscription/billing capability would need new endpoints, not designed here.

---

## 18. Risks

- **Opening Tier 3 as a front door (ADR-070) changes what a stale Trusted-projection costs** — previously "nothing," now "a stranger." Accepted knowingly per ADR-070's own ratification, but worth monitoring as real usage grows.
- **PIOS still has no authorization layer** (`ADR-054` Part 6, carried forward unresolved through every ADR this session touched) — a materially larger concern now that Channel 1 is a genuinely more open front door.
- **The dormant-test-fixture-driver problem found and worked around live today** (roughly twenty historical Kotlin integration-test driver rows, `is_test=true`, permanently "idle" since mid-August, always winning the longest-idle tie-break) is now cleaned up in production — but nothing prevents it recurring if integration tests are ever again run directly against a shared/production-adjacent broker. Worth a standing operational safeguard, not designed here.
- **No reputation signal is a real risk specifically for Channel 1**, since that is exactly the scenario (a stranger meeting a stranger) where a trust signal matters most and is least present.

---

## 19. Product Decisions Required (not made by this document)

1. **What does a paying driver actually get** (Section 12) — the single most important open decision.
2. **Does PIOS ever introduce any reputation/quality signal**, and if so, what shape (a rating? a completion-rate badge? something else entirely non-numeric)? This is a real product-philosophy question, not an engineering one, given the codebase's own deliberate current stance.
3. **Should messaging close after a ride completes?** Currently open-ended; may be intentional, may not be.
4. **Is a driver photo ever introduced**, given the current no-photo, no-rating "identity is the face" stance?
5. **What happens when a subscription lapses** (task's own Section 5 question) — cannot be answered before Decision 1 exists.

---

## 20. Definition of Commercially Ready

Building on the earlier audit's own definition (Section 17 there), updated with what shipped today: PIOS Taxi is commercially ready when, in addition to everything already true (full ride lifecycle, trust-aware fallback, **now including real stranger-to-client discovery, live-proven**):

- A ratified Product Decision exists naming exactly what a paying driver gets (Section 19, Decision 1) — **still not met**.
- Client-list depth is enough that "Мой бизнес → Клиенты" functions as a real, usable business tool, not just a name-and-count list — **not yet met**.
- The Login/Registration first impression is free of placeholder-shaped elements — **met, as of today**.

**As of 2026-09-15, PIOS Taxi has closed its single largest functional gap (Channel 1) but is still not commercially ready — the blocker has moved from "can a driver get a new client at all" (solved) to "is there a decided, honest reason to charge for using PIOS" (not yet decided).**

---

## 21. Phased Implementation Plan (planning only — not started)

**Phase 0 (done, this session):** Channel 1 discovery matching, live-proven. No further action.

**Phase 1 (next, recommended):** Resolve Product Decision 1 (Section 19) — subscription value. This is a Product Owner / Architect act, not an implementation task, and nothing downstream should be built before it.

**Phase 2:** Once Phase 1 is decided — implement whatever subscription/access-gating shape it specifies, sized to that decision (unknown until Phase 1 completes).

**Phase 3:** Client-list depth improvement (per-client history) — small, valuable, no architecture decision required, buildable independently of Phase 1/2.

**Phase 4:** Notification mechanism, discovery-match visual distinction, earnings trend — SHOULD HAVE items, sequenced after the MUST HAVE items above.

**Not sequenced here, pending their own Product Decisions:** reputation/rating, driver photo, driver-to-driver referral, messaging-lifecycle rules.

---

# Direct Answers to the Four Questions

**1. Что конкретно заставит водителя платить PIOS каждый месяц?**

Honestly: **not yet decided, and this document does not invent an answer.** The strongest *candidate*, backed by what's actually built and proven today, is access to Channel 1 discovery matching plus full business-analytics depth — the one thing a driver cannot replicate alone. But naming a candidate is not the same as a ratified decision, and none exists. **Recorded as Product Gap, not masked.**

**2. Почему пассажиру выгодно пользоваться PIOS?**

For a returning passenger with an existing driver relationship: genuinely, provably yes — a known driver, price stated up front, direct messaging, and (as of today) graceful fallback to someone already trusted rather than a stranger. For a first-time passenger: PIOS removes the "need someone's link" barrier (as of today) but offers no stronger trust signal than any other new match — **a real, current limit, not concealed.**

**3. Что должно быть реализовано, чтобы водитель без клиентской базы мог реально построить свой бизнес через PIOS?**

**As of today, this is no longer a gap — it is proven.** Discovery matching (ADR-070), live E2E'd twice on production this session: a stranger passenger reached a driver with zero prior clients, completed a ride, explicitly saved that driver, and ordered them again, with PIOS returning the same driver. The mechanism a driver-with-nothing needs to get their first client through the product itself now exists and works.

**4. Как выглядит PIOS Taxi в состоянии, когда нам не стыдно предложить водителю платную подписку?**

When, in addition to everything proven today: a ratified answer exists to Question 1 above, and the "Мой бизнес → Клиенты" screen is a real enough business tool that a driver would miss it if it disappeared — not just a name-and-count list. **Neither condition is met yet.** Everything else this document found solid (the ride lifecycle, the trust mechanics, the discovery loop) is already strong enough to stand behind; the commercial layer on top of it is what's still missing, honestly, not glossed over.
