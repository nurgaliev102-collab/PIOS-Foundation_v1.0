# PIOS Taxi — Product Completion Audit

**Date:** 2026-09-15. **Author:** Claude (implementation engineer), read-only audit — no code changed by this document. **Scope:** live production at `piosapp.ru` (backend commit `53b4337`, frontend commit `ba22008`), full backend source (`backend/*`), full frontend source (`frontend/src/*`), and this session's own live-verified findings (ADR-068, ADR-069, production data-hygiene cleanup).

**Method, stated up front, because the task demands it:** every claim below is backed by one of three kinds of evidence — a file/line citation, a live screenshot taken against production this session (`scratchpad/audit_screens/*.png`, referenced by filename), or a live API/DB check run against `piosapp.ru` earlier this session. Nothing here is inferred from a component's existence or a green test alone. Where I could not verify something live, I say so explicitly rather than assume either way.

---

## 1. Executive Summary

PIOS Taxi has a real, working, non-trivial backend (7 bounded contexts, event-driven, Flyway-migrated, with two production bugs found and fixed live this session — ADR-068 and ADR-069) and a frontend that, in its redesigned screens (Business tabs, Home, onboarding), is genuinely closer to "premium mobile product" than "prototype." That is the honest positive.

The honest negative, stated as plainly as the positive: **PIOS Taxi cannot be sold as a paid subscription today, and cannot onboard a driver who has no pre-existing personal relationship with a passenger.** Both of these are not polish gaps — they are the two facts a driver who is asked "why would I pay for this" or "how do I get my first client" would discover within their first five minutes of real use. Every other finding in this document is smaller than these two.

**Direct answer to the task's own two critical questions, given in full in Sections 17–18, previewed here:** No, a real driver cannot yet build an independent client base *from strangers* through PIOS — every single client-acquisition path in the product today requires the driver to already personally know the passenger (Section 7). And no, there is currently no answer to "what does a driver pay a monthly subscription for" — because no subscription, billing, or paywall mechanism exists anywhere in the codebase (Section 11, verified by full-repository search, zero matches).

---

## 2. Product Promise

Stated by the product owner, restated here as the standard every finding below is measured against, not softened:

> Водитель строит собственный бизнес через PIOS, а не работает на платформу. Оплата следует за трудом (водитель получает деньги за поездку напрямую). Клиент следует за пригласившим (не за платформой). Доверие следует за качеством.

This promise is **structurally honored** by the architecture: PIOS genuinely never touches ride money (no payment/commission code exists — literally nothing to touch), `Proposal.statedPrice` is the driver's own free-text number (ADR-042), and the Circle of Trust / Primary Driver mechanism (ADR-054, ADR-062, and today's ADR-068) is real, live, working code, not aspirational documentation. The promise is **not yet honored operationally** in two ways: (a) there is no mechanism for a driver to get a client who does *not* already know them, which is what "build a business" has to mean for a brand-new driver with zero clients; (b) there is no subscription mechanism at all, so "оплата следует за трудом" is true for the ride but PIOS itself has no revenue model to test against this promise.

---

## 3. Driver Value Proposition

**What PIOS actually, verifiably gives a driver today**, in order of how solid the evidence is:

| Claim | Verified how |
|---|---|
| Gets orders from own personal link/QR, direct to them, no queue with other drivers | Live E2E this session: `/i/{driverId}` → order → proposal only to that driver (First Refusal, ADR-062). Screenshot `02b_passenger_landing_real.png`. |
| Sets own price per ride, keeps 100% of it | `ProposePriceRequest`/`confirmPrice` read directly; no commission/fee code exists anywhere in `backend/`. |
| Sees an honest "Сегодня" business snapshot — completed rides, streak, repeat clients, new clients, lifetime referred count, earnings | Screenshot `04_driver_business_overview.png`, live, real data (all zero for a fresh test driver — an honest zero, per H6's own no-fake-congratulation rule, verified in `DriverHome.tsx:1898-2012`). |
| Sees own "Мои пассажиры" list with a re-share action per named client | `DriverHome.tsx:2015-2081` — confirmed to render only when `connections.length > 0`; not visually verified with real data this session (fresh test driver had zero connections), so the *populated* state is verified by source read, not a live screenshot. |
| Gets fallback priority for *their own trusted circle* when they decline/lapse a ride, before a stranger — i.e., PIOS actively protects a passenger from leaving the driver's own network | **Verified live, today, end to end** — this is ADR-068, shipped and proven in this session's own production E2E (Trusted driver B selected over available-earlier stranger C). This is a genuinely strong, differentiated, verified claim. |
| A completed ride never silently gets offered to a phantom/test driver instead of a real one | **Verified live, today** — ADR-069, production defect found and fixed this session. |

**What PIOS does *not* give a driver, despite the promise implying it should:**

- **No way to acquire a client PIOS didn't already know the driver's existing relationships with.** See Section 7 — this is the single largest gap in this document.
- **No reputation mechanism of any kind**, by explicit design decision (`DriverTrustIndicator.tsx:52-60` KDoc: *"never a rating, count, rank, or any fabricated trust score"*). This is a deliberate, principled choice (avoiding a fake star-rating theater), but it also means "формировать личную репутацию" (journey step 4, requested by this task) has no product surface at all beyond "Основной" (primary) badge and raw completed-ride counts. A driver cannot show a prospective stranger-client anything that says "people trust me" except their own word.
- **No subscription, so no paid-tier value to describe.** See Section 11.
- **No notifications** — a driver who isn't looking at the app when an order arrives has no way to learn about it (Section 12/13, verified: zero notification infrastructure anywhere in `frontend/src`, confirmed by repo-wide search).

---

## 4. Passenger Value Proposition

**What's real:**

- A passenger invited by a specific driver lands on a page that names that driver, shows their availability, and explains "заказ приходит напрямую ему — и больше никому" (screenshot `02b_passenger_landing_real.png`) — a genuinely clear, honest, differentiated pitch versus "submit to a pool."
- `MyDrivers.tsx` gives passengers a "Мои водители" list with a designated primary and (per this session's ADR-068 work) that primary's decline now degrades gracefully to another driver *the passenger already trusts*, not a stranger — this is a real, verified value: **a passenger's bad-timing experience (driver declines) no longer means falling out of their own trusted network.**
- Price is visible before commitment (`ProposePriceRequest`/passenger `confirm-price`) — no "meet the driver and find out the price" uncertainty, a documented, deliberate H3 hypothesis this session's own evidence discipline already tracks.

**What's missing, matching the task's explicit refusal to accept "просто удобно":**

- **No concrete monetary or reliability incentive over an aggregator.** A passenger who already has a Yandex Taxi/Uber account gets a car in under a minute with zero relationship required; PIOS's pitch is entirely relational ("your own driver"), which is real but fragile — it works only for a passenger who already has one good driver relationship to anchor to. A brand-new passenger with *no* driver link has literally no path into PIOS at all (Section 7 — this is the same Channel 1 gap, from the passenger's side).
- **No visible reason to prefer PIOS's price over an aggregator's estimate** — the app shows the driver's own stated price, but nothing benchmarks it, explains why it might be better, or builds any expectation of "PIOS is fair/cheaper." That may be correct product philosophy (no algorithmic pricing promise should be invented, per `CLAUDE.md`), but it also means the stated passenger benefit in this document's own brief ("более выгодные условия") has **no product mechanism today**, and this audit will not invent one.

---

## 5. Driver Journey Audit (Путь A)

| # | Step | What user sees | Can do | Unclear | Drop-off risk | Value delivered | Evidence |
|---|---|---|---|---|---|---|---|
| 1 | Узнал о PIOS | Nothing in-product — acquisition is 100% off-platform (a friend/driver tells them) | N/A | How does a driver with zero PIOS contacts ever hear about it in a way the product itself supports? | **High — no answer exists.** | None yet | Confirmed: no marketing/landing surface outside `/i/{driverId}` and `/` exists in `frontend/src/pages` |
| 2 | Зарегистрировался | Phone + password form, clean typography, dark theme | Register | Password toggle shows literal 🙈/👁️ emoji, not an icon — first-impression cheapness exactly where the task named it as forbidden | Low-moderate (cosmetic, not blocking) | Account exists | `09_registration_form.png`; `PasswordInput.tsx:67` |
| 3 | Создал профиль | Name step (not screenshotted this session but present per `DriverHome.tsx` state machine) | Enter name | — | Low | — | Code read only, not live-verified this session |
| 4 | Добавил автомобиль | Clean form: марка/модель/цвет/номер/места | Save vehicle | — | Low | Passenger sees vehicle on invite page | `07_driver_profile.png` |
| 5 | Вышел online | One card, "Я на линии" / "Уйти с линии" toggle | Toggle availability | Screen is otherwise almost entirely empty (huge dead space below the card) | Low, but a missed moment — nothing here builds confidence that "going online" did anything | Availability flips (verified via DB this session) | `03_driver_home_work.png` |
| 6 | Получил первый заказ | Proposal card (not screenshotted; verified live via API this session) | Accept/decline, message, set price | — | Low | — | Live E2E this session, full proposal lifecycle proven |
| 7 | Принял заказ | Price negotiation → Assignment created | propose-price, confirm-price | — | Low | — | Live E2E this session: 200/200/201 |
| 8 | Выполнил поездку | arrive → start → complete | 3 explicit actions | — | Low | — | Live E2E this session, all 200, Trip → COMPLETED |
| 9 | Получил оплату | **Nothing in-product.** Payment happens entirely outside PIOS (cash/transfer between driver and passenger, by design — PIOS never touches ride money) | — | A driver might reasonably expect *some* confirmation screen; there is none | Low (by design, but worth naming so it isn't mistaken for a bug) | Money is the driver's, in full — the promise's strongest-kept part | Confirmed: zero payment-processing code anywhere in `backend/` |
| 10 | Увидел результат своей работы | "Мой бизнес → Обзор": completed rides, streak, repeat clients, new clients today, lifetime referred, earnings-by-stated-price | View | Earnings tile only renders once backend actually returns a number (`typeof milestones?.totalStatedEarnings === 'number'`) — correctly conditional, not fabricated, but worth knowing it can be silently absent | Low | Real, honest, verified live | `04_driver_business_overview.png`; `DriverHome.tsx:1980-2010` |
| 11 | Получил повторного клиента | Counted in "Постоянных клиентов" tile; the mechanism (repeat ride via passenger's "Мои водители") is real and live | — | — | Low | Real | Code + live E2E confirms the data path (`driver_milestones.repeat_clients_count`, confirmed via DB this session) |
| 12 | Получил нового клиента через сеть | **Only if the new client already has this driver's personal link.** No mechanism exists for a client to arrive at a driver "through the network" without already having that driver's specific link/QR. | — | This is the Channel 1 gap — see Section 7 | **Critical** | None for a driver starting from zero | Repo-wide search, Section 7 |
| 13 | Сформировал клиентскую базу | "Клиенты" tab shows a bare list, name-or-honest-fallback, one repeated "Поделиться" action per row | View, re-share | No per-client history, no "last ride", no "заказать снова от лица клиента" — this is a list, not yet a CRM | Low-moderate | Partial — the data exists (repeat count, connections), the UI surface is minimal | `05_driver_business_clients.png` (empty state); `DriverHome.tsx:2015-2081` (populated-state source read) |
| 14 | Использовал PIOS снова | Standard login, session persistence | Login | — | Low | — | `10_login_form.png` |
| 15 | Понял, за что платит подписку | **Nothing. No subscription screen, no pricing, no paywall, no tier distinction exists anywhere in the product.** | — | Everything | **Critical** | None — there is nothing to understand | Repo-wide search, zero hits beyond false positives; Section 11 |

---

## 6. Passenger Journey Audit (Путь B)

| # | Step | What user sees | Unclear | Drop-off risk | Value | Evidence |
|---|---|---|---|---|---|---|
| 1 | Узнал о PIOS | Off-platform, via a driver's own link/QR only | — | High if driver never shares the link | None until step 2 | By design (Channel 2) |
| 2 | Открыл ссылку | Onboarding walkthrough auto-plays first, **using a hardcoded example name "Артур" instead of the real inviting driver's name** — then the real, personalized page | The walkthrough momentarily shows a different name than the real driver; not a data bug (verified: real page correctly shows the true name), but confusing sequencing | Low-moderate, first-impression confusion | — | `02_passenger_landing_invite.png` vs `02b_passenger_landing_real.png` |
| 3 | Нашёл водителя | Already resolved — the link *is* the driver. **No search/browse exists for a passenger with no link at all.** | Same Channel 1 gap as driver side | **Critical for any passenger without a link** | — | Section 7 |
| 4 | Создал заказ | Order form (not screenshotted this session; verified live via API) | — | Low | — | Live E2E this session |
| 5 | Получил предложение | Proposal appears, price visible once driver states one | — | Low | Predictability | Live E2E this session |
| 6 | Увидел водителя | Name, availability, primary badge — no photo (deliberately, per `DriverTrustIndicator.tsx` KDoc, no photo field exists in the data model at all) | A passenger may reasonably want a photo for trust; this is a real, named, un-invented gap | Low-moderate | Partial | `DriverTrustIndicator.tsx:6-14` |
| 7 | Согласовал цену | confirm-price / decline-price | — | Low | Real | Live E2E this session |
| 8 | Совершил поездку | Standard status progression | — | Low | Real | Live E2E this session |
| 9 | Оценил результат | **Nothing — no rating, no feedback mechanism exists.** | Deliberate (Section 3) but means a passenger with a bad experience has no in-product way to say so | Moderate — silent dissatisfaction is invisible to the product | None | `DriverTrustIndicator.tsx` KDoc |
| 10 | Сохранил водителя | Automatic via Circle of Trust on first connection; visible in "Мои водители" | — | Low | Real, verified this session (`trusted_driver_records`) | Live DB check this session |
| 11 | Повторил поездку | "Мои водители" → order again | — | Low | Real | `MyDrivers.tsx` |
| 12 | Рекомендует другому | **Passenger has no "share this driver" mechanism of their own** — only the driver's own link exists; a passenger cannot hand their own trusted driver's link to a friend from inside "Мои водители" without manually copying the URL themselves (no share button on that screen, confirmed by source read) | This is a second-order Channel-1-adjacent gap, smaller than Section 7's | Low-moderate | Weak | `MyDrivers.tsx` — no share affordance found |

---

## 7. Business-building Capability — the central finding

**The direct answer to the task's central question is: today, no — not from zero.**

Every single path by which a new client reaches a driver in the current product requires the client to already be in possession of that specific driver's personal link or QR code:

- `PassengerLanding.tsx` — the only entry point for a passenger, always keyed by `driverId` in the URL.
- `POST /v1/connections` — always driver-initiated-by-reference; there is no "browse available drivers" endpoint or screen anywhere in `backend/passenger-experience` or `frontend/src`.
- Fallback Dispatch (ADR-068, live today) makes the *existing* network more resilient — a passenger who already knows several drivers gets routed among them intelligently — but it **creates zero new relationships**. It cannot help a driver with zero existing clients get their first one.

This means: a driver's client base can only ever grow by that driver's own personal, offline effort (telling people, handing out a QR code) — PIOS today is a **retention and resilience tool for relationships a driver already has**, not an **acquisition tool for relationships they don't**. That is the literal gap between "агрегатор" (which acquires strangers for you) and what PIOS currently is. This is not a contradiction of the product promise — the promise never claimed PIOS finds strangers — but it does mean the specific journey step this task asks about ("получил нового клиента через сеть," "может ли водитель построить бизнес") has a hard, structural, honest **no** for any driver who starts with zero personal contacts.

```
PROBLEM: No platform-matched acquisition path exists for a driver with zero pre-existing passenger relationships.
USER IMPACT: A brand-new driver with no personal network cannot acquire a single client through PIOS itself.
CURRENT IMPLEMENTATION: 100% Channel-2 (personal-link) only; zero marketplace/matching surface.
REQUIRED CHANGE: A net-new architectural capability — some form of platform-side passenger-to-driver matching for passengers with no existing link — which is itself a major, ADR-scale decision (bounded-context and possibly domain-model impact on Dispatch/Order Management), not a UI addition.
PRIORITY: P1 (not P0 — the existing Channel-2 product is real and valuable for drivers who do have a starting network; this gap blocks only the *zero-to-one* driver, not every driver).
ACCEPTANCE CRITERIA: A driver with zero existing connections can receive at least one real order from a passenger who did not already have that driver's personal link, through some product-approved mechanism, live in production.
```

---

## 8. Client Retention

**Solid, verified, real.** This is the part of the product doing exactly what the promise says:

- Circle of Trust (ADR-054) + Primary Driver (ADR-062) + today's ADR-068 fallback ordering together mean a passenger's relationship survives a single driver's decline/lapse without falling back to a stranger.
- `driver_milestones` (repeat_clients_count, current_streak_weeks) gives the driver visible, honest, non-fabricated retention signal.
- No gap of the same severity as Section 7 exists here. The one real, smaller gap: the "Мои пассажиры" list (Section 5, step 13) doesn't yet surface *which* clients are at risk of lapsing (e.g., "haven't ordered in 30 days") — a retention product would normally highlight that. Worth naming, not urgent.

## 9. Trust / Circle of Trust

Verified working end-to-end, live, this session, including the just-shipped Trusted-first fallback (ADR-068). This is the single most differentiated, best-executed mechanic in the entire product relative to an aggregator. No further gap beyond what Sections 5–7 already name (no photo, no rating).

## 10. Referral / Network Mechanics

- **Driver → passenger** (Channel 2): fully real, live, verified (QR + link + "Мои пассажиры").
- **Passenger → passenger** (word of mouth about a *driver*): no in-product mechanism (Section 6, step 12).
- **Driver → driver**: confirmed, by this session's own architectural audit (ADR-064, re-confirmed while drafting ADR-068 this session), **no data model for this exists anywhere in PIOS** — "there is no driver-to-driver referral record anywhere in the codebase." A driver cannot refer another driver into PIOS through the product at all.

## 11. Monetization Readiness

**Zero.** Verified by a full, targeted repository search (`grep -rli "subscription|billing|payment|stripe|invoice" backend/*/src/main`) returning only two hits, both false positives (a RabbitMQ "subscription" in the messaging sense, and a comment stating *"no payment record"* exists by design). There is:

- No subscription entity, table, or migration anywhere.
- No billing/payment integration of any kind.
- No tier/plan distinction in any frontend screen (confirmed across every screenshot taken this session — Profile, Business, Home all show zero monetization surface).
- No free/paid feature gate anywhere in the code.

```
PROBLEM: No monetization infrastructure exists at any layer — data model, backend, or frontend.
USER IMPACT: There is no way to charge a driver for anything, and no way for a driver to understand what a subscription would even cover.
CURRENT IMPLEMENTATION: None. Confirmed via exhaustive repo search.
REQUIRED CHANGE: A ratified Product Decision on what the subscription actually buys (this document deliberately does not invent one, per CLAUDE.md's "never invent business rules"), followed by its own ADR (new bounded context or an extension of an existing one), then implementation.
PRIORITY: P0 for the *decision*, P1 for the *implementation* — the commercial launch this task is scoped around cannot happen without at least the decision being made, but building it is not on this audit's critical path to "usable by 10 drivers tomorrow."
ACCEPTANCE CRITERIA: A ratified Product Decision document exists naming exactly what a paying driver gets that a non-paying driver doesn't (or doesn't exist, if the decision is "not yet, first prove retention") — before any implementation begins.
```

## 12. UI/UX Audit

**Overall finding, stated honestly against the task's own skepticism:** the redesigned surfaces (Business tabs, Home, onboarding slide copy, forms) are genuinely closer to "premium" than "prototype" — clean typography (a serif display face + clean sans body, consistent throughout), a coherent dark palette, real SVG icons in the bottom nav and business tiles, honest empty/zero states everywhere I checked. This is not damning by faint praise — screenshots `01`, `04`, `05`, `06`, `07` back this up directly. **But the task's specific fear — emoji where a UI icon belongs — is confirmed true, and it's in the worst possible place.**

```
PROBLEM: The password visibility toggle, shared by every password field in the entire app (Driver Home, Passenger Landing, Owner Control Center login — per the component's own KDoc), renders literal 🙈 / 👁️ emoji instead of an icon.
USER IMPACT: The very first interactive control a new user touches (registration, screenshot 09) looks like a prototype, undermining the "serious service" impression the rest of the screen earns.
CURRENT IMPLEMENTATION: frontend/src/components/PasswordInput/PasswordInput.tsx:67 — `{visible ? '🙈' : '👁️'}`
REQUIRED CHANGE: Replace with two small SVG icons (eye / eye-slash), matching the stroke-based icon language already used correctly everywhere else in the redesign (bottom nav, business tiles — see screenshots 03/04).
PRIORITY: P0 for the visual audit specifically — cheap, isolated, one shared component, touches Registration/Login/Owner Control Center all at once.
ACCEPTANCE CRITERIA: Screenshot of Registration and Login shows an SVG icon, not an emoji glyph, in both light and dark theme, at 44px minimum touch target.
```

```
PROBLEM: A literal 🚕 taxi emoji and a generic person-silhouette emoji appear in the real (non-onboarding) PassengerLanding "Как работает PIOS" explainer list.
USER IMPACT: Same category as above — a passenger's very first real content view.
CURRENT IMPLEMENTATION: Confirmed live, screenshot 02b_passenger_landing_real.png.
REQUIRED CHANGE: Replace with SVG icons matching the design system.
PRIORITY: P1.
ACCEPTANCE CRITERIA: Same screen, no emoji glyphs, SVG icons only.
```

```
PROBLEM: The onboarding walkthrough's first passenger-side slide shows a hardcoded example name ("Артур") instead of the actual inviting driver's real name, even though the real page immediately underneath correctly personalizes.
USER IMPACT: A passenger invited by "Aleksei" (or any real driver) briefly sees a different name during the walkthrough animation — confusing, reads as a bug even though it is "only" a canned demo sequence.
CURRENT IMPLEMENTATION: Confirmed live, screenshot 02_passenger_landing_invite.png vs 02b (component: PassengerOnboarding.tsx, not fully read this session — flagged from screenshot evidence, source-level root cause not yet isolated).
REQUIRED CHANGE: Either personalize the walkthrough's example to the real driver's name, or make unmistakably clear (visually) that the walkthrough is a generic explainer, not live data.
PRIORITY: P2 — confusing, not blocking, self-resolves within seconds as the real page loads.
ACCEPTANCE CRITERIA: Walkthrough either shows the real driver's name or is visually distinguished from real content.
```

**Other UI/UX observations, lower severity, honestly graded rather than inflated:**

- Driver Home's "Главное" (default landing) tab is nearly empty below the online/offline card (screenshot `03`) — not broken, but a missed first-screen opportunity; the actually-rich content ("Мой бизнес") is one tap away, unadvertised.
- "Клиенты" tab conflates "share your link" and "see your client base" into one screen with no visual separation between the two different jobs.
- No accessibility regression found in what was checked (focus-visible states, 44px touch targets — matches this session's own earlier redesign QA).

## 13. Critical Blockers

Using the task's own strict definition (feature is "done" only if user understands → acts → gets result → understands value), these are the items that fail that bar entirely, not partially:

1. **No Channel 1 (platform matching) — Section 7.** Blocks "build a business from zero."
2. **No monetization — Section 11.** Blocks "sell a subscription" outright — literally nothing to sell.
3. **Password-toggle emoji — Section 12.** Blocks the "premium first impression" requirement specifically, in the specific screens the task named as most important (Login/Registration).

Notably **not** a blocker despite being new and easy to imagine as risky: today's ADR-068/ADR-069 deploy. Both are shipped, live, and proven correct by this session's own production E2E — they do not appear in this list.

## 14. Important Gaps

- No driver reputation/rating surface beyond raw counts (Section 3).
- No push/in-app notification system at all (confirmed, zero matches in repo search) — a driver away from the screen misses orders silently.
- No per-client history/CRM depth in "Мои пассажиры" (Section 5, step 13).
- No passenger-side "recommend this driver to a friend" affordance (Section 6, step 12).
- No driver-to-driver referral capability at any layer (Section 10).
- No passenger rating/feedback mechanism (Section 6, step 9) — deliberate, but leaves dissatisfaction invisible.

## 15. Polish

- Onboarding walkthrough's hardcoded example name (Section 12).
- "Как работает PIOS" emoji icons on the real PassengerLanding page (Section 12).
- Driver Home's sparse default tab (Section 12).
- "Клиенты" tab's dual-purpose framing (Section 12).

## 16. Recommended Implementation Sequence

Not a commitment, not started, no code written for any of this — a sequencing recommendation only, per the task's own explicit prohibition on beginning implementation during this audit.

1. **P0 — password-toggle icon fix.** Trivial, isolated, high first-impression payoff, touches every auth screen at once.
2. **P0 — Product Decision on subscription value** (not implementation — the decision). Everything else about monetization depends on this being answered honestly first.
3. **P1 — Channel 1 architecture decision** (Stage 1 Architect review, likely a new ADR given the bounded-context implications already flagged in this session's own ADR-068/069 work).
4. **P1 — PassengerLanding emoji-icon cleanup.**
5. **P2 — client-list depth, passenger-side driver recommendation, onboarding personalization, Driver Home default-tab richness.**

## 17. Definition of Commercially Ready

PIOS Taxi is commercially ready when, in addition to everything already true today (working dispatch, trust-aware fallback, honest business metrics, full ride lifecycle — all live-verified this session):

- A driver with zero existing personal contacts can acquire at least one real client through the product itself (Section 7 closed).
- A ratified answer exists to "what does a paying driver get" (Section 11's decision, even if the answer is "polish and reliability, not new gated features").
- The Login/Registration screens show no placeholder-shaped visual elements (Section 12's P0 item closed).

**As of 2026-09-15, none of these three conditions are met.** PIOS Taxi is not commercially ready.

## 18. Definition of Paid Driver Ready

A driver is "paid-driver ready" — i.e., could honestly be asked to pay a monthly subscription — when they can answer, from their own in-product experience, the question "what do I get for this money that I don't get for free today." **As of this audit, that question has no honest answer, because no free/paid distinction exists at all.** This is not a polish gap; it is the literal absence of a commercial product on top of a real operational one.

---

## Final Answers

**«Если завтра дать PIOS Taxi 10 независимым водителям и попросить их использовать его как рабочий инструмент, что произойдёт?»**

Each of the 10 would be able to: register, add their car, go online, get orders from clients they already personally invited, negotiate price, complete rides, and see an honest, real "Мой бизнес" summary of their activity — all of this is live and verified working today. What would **not** happen: none of them would acquire a single client they didn't already know before joining PIOS, because that path does not exist in the product (Section 7). For a driver who joins already carrying an existing client base, PIOS would function as a genuinely good retention and reliability tool from day one. For a driver hoping PIOS itself would bring them new business, it would not, and there is no honest way to promise it would.

**«За что конкретно они будут готовы платить ежемесячную подписку?»**

**No answer exists yet — stated directly, per the task's own instruction to say so rather than invent one.** Not because the underlying product has no value (Sections 3, 8, and 9 show real, differentiated value), but because no monetization layer of any kind has been built, decided, or even scoped at the data-model level (Section 11). Answering this honestly is the single highest-leverage next step in this document's own recommended sequence — above any further feature work.
