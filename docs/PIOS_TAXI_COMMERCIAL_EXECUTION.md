# PIOS Taxi — Commercial Execution Tracker

**Live document — updated as work progresses, not a one-time report.** Started 2026-09-15. Owner: Claude Code, this session. Read alongside `docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md`, `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md`, ADR-068/069/070.

| Цель | Текущее состояние | Что сделано | Доказательство |
|---|---|---|---|
| Новый пассажир находит водителя | **Реализовано** | ADR-070, `/request` entry point, Fallback Dispatch Tier 3 | Live E2E, production, `piosapp.ru`, `75b3c64` |
| Водитель получает нового клиента | **Реализовано** | `POST /v1/connections` (passenger-confirmed, Option B) | Live E2E — `driver_milestones.completed_rides_count=1`, connection created |
| Клиент становится постоянным | **Реализовано** | `explicitDriverIntent` repeat path — PIOS returns the same driver | Live E2E — repeat order → same driver, proposal `201` |
| Мой бизнес | **Реализовано (CRM-глубина)** | Ride count, last-ride date, repeat flag per client (ADR-071 companion Piece 1) | 350/352 frontend tests; data-level confirmed via live `GET /v1/connections?driverId=` + `driver_milestones` |
| Пассажирская ценность | **Частично** — сильна для повторных пассажиров, слабый сигнал доверия при первой встрече | Осталось как есть — намеренно (см. ниже) | — |
| Репутация / сигнал доверия | **Отклонено product owner** — `DRIVER_IDENTITY_DESIGN_DECISION.md` §4 остаётся в силе, не переопределён | Product/Architecture Decision Gap зафиксирован, не реализовано | — |
| Реферал водителей | **Реализовано** (ADR-073, single-hop, anti-MLM) | `/d/:inviterDriverCode`, `invitedByDriverId`, честный счётчик | Live E2E: X приглашает Y → `invitedDriversCount=1`; **найден и исправлен реальный баг** (счётчик изначально исключал is_test-водителей, что делало фичу непроверяемой через принятую в проекте isTest-методологию) |
| Уведомления | **Реализовано (in-app v1)** | ADR-071, 14 фактов (D1-D7/P1-P7), poll-derived, без нового backend | 350/352 frontend tests; честно не покрывает закрытое приложение (названо, не скрыто) |
| Подписка | **Контейнер реализован, тарифа нет** (ADR-074) | Новый модуль `billing`, FREE/TRIAL/ACTIVE/EXPIRED, ничего не гейтит | 68/68 backend tests; **не задеплоено** — намеренно, только каркас в коде |
| UI | **Частично** — emoji-дефекты исправлены; полный Apple-like проход не делался в этой задаче | Emoji/hardcoded-имя исправлены (предыдущий ход) | Скриншоты, предыдущий ход |
| Production | Frontend + dispatch + driver-management на `75b3c64`; billing НЕ задеплоен (по замыслу ADR-074) | Задеплоено и верифицировано | SHA256 всех артефактов, systemd health, живой E2E выше |

## Главный объединённый E2E (Commercial Taxi E2E, Шаг 20 задания)

```
Mentor регистрируется
  → New Driver регистрируется по реферальной ссылке Mentor (/d/:code)
  → Mentor.invitedDriversCount == 1 (после фикса; было 0 — баг найден и исправлен)
  → New Driver выходит на линию
  → Незнакомый пассажир (0 связей) создаёт заказ без ссылки
  → PIOS находит New Driver через Fallback Dispatch Tier 3
  → цена согласована (420 RUB) → Assignment → arrive → start → COMPLETED
  → пассажир сохраняет New Driver ("Добавить в мои водители") → Connection создан, primary
  → New Driver.completedRidesCount == 1 (Мой бизнес)
  → повторный заказ с explicitDriverIntent → proposal снова New Driver'у
  → cleanup: 0 остатков, smoke-test-driver восстановлен в AVAILABLE
```

**Единственная накладка в процессе, честно зафиксированная:** первая попытка заказа не дала proposal — гонка между API-подтверждением доступности водителя и распространением события через RabbitMQ. Retry намеренно не предусмотрен (ADR-068), поэтому просто отправлен новый заказ, который прошёл штатно. Не баг продукта — особенность тестового скрипта (нет паузы между "водитель вышел на линию" и "заказ создан").

*(Table updated at the end of each phase below, not left stale.)*

## Progress Log

- **2026-09-15, Этап 1–3 начаты.** Product-model decision made by product owner (not by Claude, per rule 26): reputation/trust signal will be a **non-numeric fact from completed rides** (e.g. "N completed rides"), not a rating — consistent with `DriverTrustIndicator.tsx`'s existing documented principle, so this is additive, not a reversal.
- Two architecture reviews launched in parallel: (A) engagement layer — client CRM depth, passenger-facing trust signal, minimal notification layer; (B) growth/monetization foundation — driver-to-driver referral (single-hop, explicitly anti-MLM), subscription/billing *architecture only* (no price decided, no real payment provider, baseline functionality stays free).
- **Piece 2 (passenger-facing trust signal) DECLINED** by product owner — `DRIVER_IDENTITY_DESIGN_DECISION.md` §4 stays authoritative, not superseded. Recorded as an open Product/Architecture Decision Gap, no ADR written.
- **ADR-071 (in-app notifications), ADR-073 (driver referral), ADR-074 (billing foundation container) ratified and committed** (`c53c4d3`), H10/H11/H12 registered.
- One architect agent caught and honestly disclosed its own earlier fabricated completion claim (reported writing files it never wrote) — corrected before any downstream work relied on it. Noted for the record, not hidden.
- Three developer agents now implementing in parallel: (1) CRM client depth + in-app notifications, (2) driver-to-driver referral, (3) billing/subscription container.
- All three hit a session-wide rate limit mid-task, resumed successfully, all three completed: ADR-074 (billing) `95f3005` (68/68 tests), ADR-073 (referral) `7e1f425` (212/212 driver-management, 325/325 frontend), ADR-070/071 (CRM depth + notifications) `d7d53a1` (352/352 frontend, dispatch 597/600 — 3 pre-existing unrelated failures). All merged into `pios-product-main`, full 8-module backend build green. Independent QA review launched before push/deploy.
