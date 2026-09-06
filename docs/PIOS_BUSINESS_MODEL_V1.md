# PIOS Business Model v1 — Architect's Strategic Proposal

**Status:** Draft strategic proposal from the architect role (ChatGPT), recorded here for reference. Not yet ratified as a product decision, not yet backed by an ADR, and not yet implemented in any form. Nothing in this document authorizes new backend/frontend work.

**Recorded:** 2026-09-05

**Origin:** This document reproduces, in full, a monetization/business-model proposal authored by the architect role during a conversation with the product owner. It is captured here per the repository's Documentation First principle — strategic direction of this scope should exist in `docs/` even before any implementation decision is made about it — not because it has been approved for execution.

---

## Governing idea

> PIOS зарабатывает на предоставлении водителю инфраструктуры для построения и ведения собственного транспортного бизнеса, а не на максимальном изъятии денег из каждой его сделки.

Classic aggregators (Uber, Lyft, BlaBlaCar) monetize primarily by taking a cut of each transaction. The proposal is that PIOS should instead monetize the *infrastructure* a driver uses to build a durable, personally-owned client base — and treat marketplace transaction fees as only one, deliberately smaller, revenue stream among several.

## 1. Four economic layers

```
                    PIOS
                      │
       ┌──────────────┼──────────────┐
       ↓              ↓              ↓
   NETWORK         MARKETPLACE     BUSINESS
   INFRASTRUCTURE   TRANSACTIONS    TOOLS
       │              │              │
       ↓              ↓              ↓
   подписка       сервисный сбор    SaaS/Pro
                                      │
                                      ↓
                                   B2B
```

Principle: чем больше водитель строит собственный бизнес через PIOS, тем больше PIOS зарабатывает вместе с ним — not the other way around.

## 2. Two participants, two economics

- **Passenger** — entry should stay free or near-free (register, order rides, search drivers, build a network, invite others, use matching, save drivers). The goal is maximizing demand-side volume.
- **Driver** — gets a professional business toolset: profile, vehicle, documents, clients, network, routes, bookings, CRM, analytics, payments, promotion. This is where the primary monetization lives.

## 3. "PIOS Driver OS" framing

The driver-facing product is framed not as a job app but as the operating system of the driver's own transport business — dashboard concept:

```
PIOS DRIVER
Сегодня: 6 поездок, 4 новых клиента, 3 повторных клиента, ₽...
Мои клиенты / Мои поездки / Мои маршруты / Моя сеть / Мой автомобиль / Моя репутация / Мой календарь / Моя аналитика
```

## 4. PIOS Free (baseline, no paywall on getting work)

Profile, verification, vehicle, receiving/accepting orders, reviews, basic client list, personal network, basic intercity rides — free, to avoid gating supply acquisition ("заплати, чтобы вообще получить клиента" is explicitly rejected).

## 5. PIOS Pro (first revenue stream — subscription)

A flat monthly subscription (price not fixed yet — needs market testing) unlocking business tooling: client base / CRM ("Андрей обычно едет в аэропорт по пятницам"), repeat-order prompts, route management, recurring rides, analytics (revenue, client count, repeat rate, average check, revenue by route, vehicle utilization), offer management, promotion, deeper matching.

## 6. Principle: PIOS does not take the driver's client away

Once a passenger becomes a driver's repeat client through the platform, PIOS's role shifts from "found you this client" to "helps you keep serving them" — explicitly modeled on Shopify's relationship to a merchant's customers, not Uber's relationship to a driver's rides.

## 7–8. Two-fee structure by client origin

- **New client via marketplace** → PIOS may take a transaction/service or lead fee.
- **Repeat client from the driver's own network** → zero or minimal fee, or folded into the Pro subscription.

Rationale given: the more a driver invests in their own network, the less their economics depend on marketplace commission — framed as a retention mechanism that isn't artificial lock-in.

## 9. In-app visibility of this split

Proposed driver-facing screen showing new-vs-repeat client counts, commission paid, and "savings" from own-network rides — making the value exchange explicit rather than implicit.

## 10. Lead generation (second revenue stream)

Selling access to *new* opportunities outside the driver's existing network — e.g., a batch of passengers wanting a specific intercity route — as a paid lead, distinct from the driver's own client relationships remaining free/cheap.

## 11. Promotion (third revenue stream, with a stated risk)

Paid profile/route/offer boosting — but explicitly flagged as risky if it lets "whoever pays more" override match quality; must not undermine trust in matching.

## 12–14. B2B / vahta / group trips (fourth and fifth revenue streams)

- **PIOS Business**: corporate accounts for employee transport, shift-worker (vahta) transport, airport/rail transfers, recurring routes, reporting, SLAs, centralized billing — a B2B2C model, not just a marketplace.
- **Group trips**: matching several passengers going the same direction into one vehicle, with an organizational fee — relevant for airports, stations, shift work, events.

## 15. Payments (sixth revenue stream — explicitly deferred)

Becoming the financial intermediary (processing, payouts, corporate billing, escrow-like mechanics) is listed as a real future stream but explicitly **not** something to build first — a real transport network needs to exist before this makes sense.

## 16. Partner services (seventh revenue stream)

Referral revenue from an ecosystem around the professional driver: insurance, auto service, tires, car wash, fuel, leasing, credit, parts, accounting.

## 17–18. Core philosophy

> PIOS должен зарабатывать не на том, что водитель становится зависимее от платформы, а на том, что водитель становится успешнее благодаря платформе.

Explicit anti-goal: PIOS must never compete with a driver for that driver's own clients — "This is very similar to Shopify's logic, much more than Uber's logic."

## 19–20. Two-circuit model and a draft pricing table (illustrative, not fixed)

| Уровень | Что получает водитель | Монетизация |
|---|---|---|
| Free | Базовый профиль и поездки | 0 |
| Pro | CRM + сеть + аналитика + бизнес-инструменты | подписка |
| Pro+ | Расширенный matching + продвижение | повышенная подписка |
| Marketplace | Новые клиенты | service/lead fee |
| Business | Корпоративные заказы | B2B subscription/contract |
| Group | Групповые перевозки | fee |
| Payments | Расчёты | transaction fee |
| Partner Services | Авто/страхование/финансы | referral revenue |

No rates are fixed here — the proposal explicitly defers pricing to experimentation.

## 21–22. "Business Score" concept

Instead of (or alongside) a star rating, show the driver a business-health dashboard: client count, repeat-client count, ride count, rating, recurring-client count, active routes, network connections, 30-day growth — reframing PIOS as an operating panel for a small transport business, and describing five stages of driver relationship to the platform culminating in "мне дорого уходить из PIOS, потому что здесь вся моя бизнес-инфраструктура" as the intended (non-coercive) retention mechanism.

## 23–24. Two flywheels

1. **Marketplace flywheel**: more drivers → more supply → better matching → more passengers → more rides → more relationships → more personal networks → more invites → compounding growth.
2. **Business-network flywheel**: more of a driver's own clients → more value from Driver OS tooling → more Pro subscriptions → more PIOS revenue → more product investment → better product.

The second is framed as the one that differentiates PIOS from a plain aggregator.

## 25. Recommended MVP scope (three streams, not seven)

1. Marketplace fee — only on new clients/deals PIOS actually originated.
2. Driver Pro — a modest monthly subscription for business tooling.
3. B2B/Group — once real regional routes and shift-worker (vahta) demand exist.

## 26–28. Proposed KPIs (in addition to GMV)

- **Driver-Owned Business Volume** — share of a driver's total turnover coming from their own network vs. marketplace-originated rides; strategic goal is growing this share over time (a moat mechanic, not a metric to suppress).
- **Repeat Relationship Rate** — share of new rides that convert into a repeat relationship.
- **Driver Business Retention** — growth in each driver's own client count over time, not just app-usage retention.

## 29. Named failure modes to avoid

- Free-for-drivers-and-monetize-with-ads — judged a weak model.
- Flat 20–30% marketplace commission on every ride — judged to directly contradict the "build your own business" positioning. (BlaBlaCar and Lyft's 2026 fee-cap changes are cited as evidence the market is already moving toward more predictable driver-side costs, not less.)

## 30–32. Positioning statement

> В агрегаторе водитель продаёт своё время платформе. В PIOS водитель использует платформу, чтобы построить собственный транспортный бизнес.

PIOS provides: demand + clients + infrastructure + network + matching + CRM + reputation + routes + payments + B2B. The driver provides: vehicle + time + professional service + client relationships. The passenger provides: demand + relationships + network growth.

## Suggested next step (from the architect)

Not "invent more revenue streams" — instead, build a unit-economics model for one concrete regional scenario (a city + airport/rail + intercity + shift-worker/vahta demand) and compute driver, passenger, and platform economics for it, to test whether this model is actually profitable rather than just internally consistent.

---

*This document is a faithful record of the architect's proposal as presented. It has not been reviewed against [PIOS_PRODUCT_VISION.md](PIOS_PRODUCT_VISION.md) or [PIOS_PRODUCT_STRATEGY_V1.md](PIOS_PRODUCT_STRATEGY_V1.md) for consistency, and no ADR has been raised from it. Per this repository's "Never Invent Business Rules" rule, none of the fees, subscription tiers, or thresholds named above are to be treated as implemented product behavior until the product owner ratifies them explicitly.*
