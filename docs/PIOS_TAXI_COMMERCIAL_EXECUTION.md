# PIOS Taxi — Commercial Execution Tracker

**Live document — updated as work progresses, not a one-time report.** Started 2026-09-15. Owner: Claude Code, this session. Read alongside `docs/PIOS_TAXI_PRODUCT_COMPLETION_AUDIT.md`, `docs/PIOS_TAXI_COMMERCIAL_PRODUCT_BLUEPRINT.md`, ADR-068/069/070.

| Цель | Текущее состояние | Что сделано | Доказательство |
|---|---|---|---|
| Новый пассажир находит водителя | **Реализовано** | ADR-070, `/request` entry point, Fallback Dispatch Tier 3 | Live E2E, twice, production, today — see prior session report |
| Водитель получает нового клиента | **Реализовано** | Same mechanism + `POST /v1/connections` (passenger-confirmed) | Live E2E — `driver_milestones.completed_rides_count=1`, connection created |
| Клиент становится постоянным | **Реализовано** | `explicitDriverIntent` repeat path, unchanged, proven with a discovery-formed relationship | Live E2E — repeat order returned the same driver |
| Мой бизнес | **Частично** — real data, honest zero-states, but client list is name+count only, no per-client history | В работе (этот этап) | — |
| Пассажирская ценность | **Частично** — strong for returning passengers, weak first-time trust signal | Не начато в этом этапе | — |
| Репутация | **Не начато** — blocked on a product-model decision (see below) | Ожидает решения product owner | — |
| Подписка | **Отсутствует** — no code anywhere | Не начато в этом этапе | — |
| UI | **Частично** — emoji defects fixed today; broader Apple-like pass not done this task | Emoji/hardcoded-name fixes (previous session turn) | Screenshots from prior turn |
| Production | Frontend at `2cbec44`, backend (dispatch/driver-management/passenger-experience) unchanged since ADR-068/069 deploy | Verified this session | SHA256 + service health, prior turns |

*(Table updated at the end of each phase below, not left stale.)*

## Progress Log

- **2026-09-15, Этап 1–3 начаты.** Product-model decision made by product owner (not by Claude, per rule 26): reputation/trust signal will be a **non-numeric fact from completed rides** (e.g. "N completed rides"), not a rating — consistent with `DriverTrustIndicator.tsx`'s existing documented principle, so this is additive, not a reversal.
- Two architecture reviews launched in parallel: (A) engagement layer — client CRM depth, passenger-facing trust signal, minimal notification layer; (B) growth/monetization foundation — driver-to-driver referral (single-hop, explicitly anti-MLM), subscription/billing *architecture only* (no price decided, no real payment provider, baseline functionality stays free).
