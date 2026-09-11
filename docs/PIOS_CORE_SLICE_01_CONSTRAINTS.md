# PIOS Core — Slice 01: зафиксированные ограничения (pre-implementation charter)

**Статус: зафиксированные ограничения перед реализацией. Не ADR, не продуктовое решение, не авторизация кода.** Документ фиксирует границы, внутри которых должна происходить реализация PIOS Core Slice 01, по итогам read-only аудита Taxi (`docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md`, §21–25) и явного указания продукт-овнера от 2026-09-10. Он **не создаёт** модуль, БД, миграцию, событие или endpoint. Реализация Slice 01 требует отдельного шага — см. раздел «Что ещё нужно до первой строки кода».

**Дисциплина.** `CLAUDE.md` («Documentation First», «Architecture First», «No Implementation Before Documentation», «Never Change Architecture Without an ADR»); `.claude/CLAUDE.md` (Stage 1 — Architect обязателен при создании нового bounded context / новой БД / новой межмодульной ссылки).

---

## 1. Зафиксированные ограничения (обязательны, не подлежат ослаблению без отдельного решения)

| # | Ограничение | Что это значит конкретно |
|---|---|---|
| C-1 | **`backend/core` — отдельный bounded context с собственной БД `pios_core`.** | Новый Spring Boot / Kotlin модуль по тому же multi-module паттерну, что и остальные 6 (`domain/application/api/persistence`, plain JDBC, Flyway `classpath:db/migration/core`). Отдельная БД `pios_core` на той же PostgreSQL-инстанции. Никаких shared-таблиц, никаких cross-DB FK (ADR-005/009). Не пакет в другом модуле, не shared library (запрещено `backend/build.gradle.kts`). |
| C-2 | **`network-management` не активировать и не использовать как runtime authority.** | Модуль `backend/network-management` остаётся не развёрнутым (нет WinSW-, нет systemd-юнита). `pios_network_management` БД **не** идёт в production. `networkmanagement/V*` миграции заморожены. Core **не** импортирует, не вызывает, не слушает network-management и не строит на его схеме. Его доменные идеи (Person/Profile/Connection/Invitation) могут быть переосмыслены в Core позже — но не в Slice 01 и не путём «оживления» network-management. |
| C-3 | **Existing Taxi domains и event contracts не изменять.** | `Order`, `OrderStatus`, `Proposal`, `ProposalStatus`, `Assignment`, `AssignmentStatus`, `Trip`, `TripStatus`, `Identity`, `Driver`, `Connection` (passenger-experience), `primary_connections`, `Availability` — код, factory, переходы, инварианты, таблицы, миграции — **не трогать**. Формат RabbitMQ-envelope (`eventId/eventType/eventVersion:1/occurredAt/payload`), routing keys, очереди, DLQ, `processed_events` ledger'ы существующих модулей — **не трогать**. Никаких новых полей в существующих событиях. |
| C-4 | **Slice 01 — read-only projection из уже существующих событий.** | Core только **потребляет** события, которые Taxi-модули уже публикуют сегодня, и записывает из них собственную read-model в `pios_core`. Никакой записи в чужие БД, никаких синхронных вызовов Taxi-модулей, никакой доменной логики поверх Taxi-агрегатов. |
| C-5 | **`identityId` — текущий participant reference для Slice 01.** | Core использует `identityId` (он же `sub` в session token, он же `Order.origin`, он же `PassengerReference`) как ключ «участника» в своей read-model. **Это НЕ объявление `identityId` окончательным универсальным Person ID PIOS.** Канонический Person ID — предмет отдельного решения «Core Data Contract», которое Slice 01 не принимает и не предопределяет. В коде Slice 01 ключ именуется нейтрально (`participantReference` / `participant_reference`), не `personId`. |
| C-6 | **Core ничего не публикует и не мутирует в Taxi.** | Core в Slice 01 **не эмитит** ни одного события (ни в свой outbox, ни в чужой exchange). Core **не вызывает** ни один Taxi endpoint. Core **не пишет** ни в одну БД, кроме `pios_core`. Односторонний поток: Taxi events → Core read-model. |
| C-7 | **Сначала изолированная реализация и QA.** | Slice 01 реализуется и проверяется полностью в изоляции: свой `pios_core_test` для интеграционных тестов, локальный прогон, QA-проверка end-to-end (события приходят → read-model строится → endpoint отдаёт корректную историю → редоставка события не создаёт дубль → недоступность Core не влияет на Taxi). Только после этого — вопрос о деплое. |
| C-8 | **Production deployment Core — только после прохождения QA / safety gate.** | Не разворачивать `pios-core` ни на HOME-PC, ни на VPS, пока QA явно не подтвердит: (а) изоляция БД (`pios_core` ≠ ни одно production-имя, тесты только против `pios_core_test`); (б) отсутствие любого write-пути в чужие БД/контракты; (в) отсутствие влияния на Taxi при остановке/сбое Core; (г) идемпотентность потребления. Gate — явный, не «выкатим и посмотрим». |
| C-9 | **Core не в критическом пути Taxi.** | Остановка, сбой, отставание или полное отсутствие `pios-core` **не должны** влиять на: заказ, proposal, assignment, trip, First Refusal, Circle of Trust, аутентификацию, любой экран frontend. Core подписан на события асинхронно; если очередь Core растёт или consumer лежит — Taxi работает без изменений. Порт Core — отдельный (кандидат 8087), не пересекается с 8081–8086, 8091. |
| C-10 | **Никаких изменений frontend, Order, Proposal, Assignment, Trip, Connection, Primary Driver, Identity или существующих RabbitMQ contracts.** | Явное повторение C-3 + распространение на frontend: `frontend/` (DriverHome, RideRequest, PassengerLanding, Coordinator, MyDrivers, OwnerControlCenter, `routes.tsx`, `apiClient.ts`, identity-провайдеры) — **не трогать** в рамках Slice 01. |

---

## 2. Что Slice 01 делает (в границах выше)

- Новый модуль `backend/core`, БД `pios_core`, миграция `V1__initial_schema` (участники + история-событий + idempotency ledger).
- `@RabbitListener` на уже публикуемые события: `OrderSubmitted`, `OrderCompleted`, `OrderCancelled`, `AssignmentCompleted` (legacy routing `assignment.completed`), `PrimaryConnectionDesignated`, `DriverAvailabilityChanged`.
- Идемпотентная запись в собственную read-model (`core_processed_events(event_id PK)`).
- Один endpoint: `GET /v1/core/participants/{participantReference}/history` (Bearer: `sub == participantReference` или owner Basic).
- Свой `GET /v1/health/core` (owner Basic), по образцу существующих `HealthController`.
- Тесты: constructor-based (`InMemory*`) + PostgreSQL-integration против `pios_core_test` (тот же захардкоженный-имя паттерн `PostgreSQLTestDatabase`, без override через env/property).

## 3. Что Slice 01 НЕ делает

- Не вводит `Relationship`, `Trust`, `Capability`, `Need`, `Opportunity`, `Transaction` как мутируемые сущности.
- Не активирует `network-management`.
- Не трогает `passenger-experience`, `dispatch`, `order-management`, `driver-management`, `identity`, `ai-advisor`, `frontend`.
- Не добавляет новых событий и не меняет существующие.
- Не принимает решение о каноническом Person ID.
- Не подключает LLM / AI-провайдера.
- Не деплоится в production до QA-gate.

---

## 4. Что ещё нужно до первой строки кода Slice 01

Создание нового bounded context и новой БД — это **Stage 1 (Architect)** по `.claude/CLAUDE.md`, и `MODULE_STRUCTURE.md §8` (расширяющий `ADR-017`/`ADR-018`) требует **ADR до создания модуля, а не после**. Порядок:

1. **ADR: PIOS Core Bounded Context (Slice 01 scope)** — фиксирует: новый модуль + БД, владение (read-model «история участника»), отсутствие исходящих контрактов, отсутствие write-путей в чужие БД, `participantReference` (не `personId`), список потребляемых событий, порт, деплой вне критического пути, QA-gate как условие production. Ссылается на этот charter.
2. **(опционально, если продукт-овнер хочет зафиксировать продуктово)** короткая запись, что «канал накопления истории участника» — намеренная фича, а не техническая деталь; и что вопрос канонического Person ID (`Core Data Contract`) остаётся открытым.
3. Только после ратификации ADR — реализация Slice 01 строго по нему, затем изолированный QA, затем safety-gate, затем (и только тогда) деплой.

**Этот документ не заменяет шаг 1.** Он фиксирует ограничения, внутри которых ADR и реализация должны остаться.

---

## 5. Pre-flight checklist (проверяется на каждом шаге реализации и в QA)

- [ ] Ни один файл вне `backend/core/**`, `docs/**`, новые WinSW/systemd юниты — не изменён.
- [ ] `backend/settings.gradle.kts` — добавлен только `include("core")`, ничего больше.
- [ ] Ни одна существующая миграция (`ordermanagement/`, `dispatch/`, `drivermanagement/`, `passengerexperience/`, `identity/`, `networkmanagement/`) не тронута.
- [ ] `backend/core` не содержит `pios.<other-module>.base-url`, не импортирует ни один `com.pios.<other>` тип.
- [ ] Core `application.yml` содержит ровно один `spring.datasource.url` → `pios_core`.
- [ ] Core не пишет `save`/`INSERT`/`UPDATE` ни в какую БД, кроме `pios_core`.
- [ ] Core не содержит `RabbitTemplate.convertAndSend` / `EventPublisher` / `OutboxRepository` с реальной записью — только `@RabbitListener`.
- [ ] `PostgreSQLTestDatabase` (core) → `jdbc:.../pios_core_test`, имя захардкожено, без env/property override.
- [ ] Все потребляемые события уже публикуются в проде сегодня (сверка с `docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md` §17).
- [ ] Ключ участника в схеме и API — `participant_reference` / `participantReference`, не `person_id`/`personId`.
- [ ] Остановка `pios-core` не ломает ни один Taxi-сценарий (проверено в QA явно).
- [ ] Нет деплоя `pios-core` на HOME-PC/VPS до прохождения QA/safety-gate.
- [ ] Нет коммита/пуша без явной просьбы.

---

## Источники

`docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md` (§16 БД-границы, §17 события, §20 production safety, §21–25 semantic diff и Slice 01 boundary); `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` §14; `CLAUDE.md`; `.claude/CLAUDE.md` (Stage 1 workflow); `docs/MODULE_STRUCTURE.md` §8; `docs/ADR/ADR-005`, `ADR-009`, `ADR-017`, `ADR-018`, `ADR-026`, `ADR-037`, `ADR-055`. Указание продукт-овнера от 2026-09-10 (в задании к этому документу).

## Изменённые файлы

`docs/PIOS_CORE_SLICE_01_CONSTRAINTS.md` (новый). Код, схема БД, миграции, конфигурация, deployment, production, `network-management` — **не затронуты**. Модуль `backend/core` **не создавался**.
