# PIOS — Current State Audit (2026-09-10)

**Статус: read-only аудит фактического состояния. Не продуктовое решение, не ADR, не план работ.** Документ фиксирует, *что реально есть в коде и в развёртывании на 2026-09-10*, и **не авторизует** ни одного изменения — ни строки кода, ни миграции, ни модуля, ни изменения конфигурации, БД, deployment или production. Он существует как точка отсчёта для будущих архитектурных решений.

**Метод.** Прочитан исходный код (не документы-намерения): пути файлов, классы, Flyway-миграции, `application.yml`, WinSW-/systemd-юниты, листинг `/opt/pios` и `windows-services/`. Каждое утверждение о «том, что есть» подкреплено конкретным файлом. Где текущее поведение расходится с ратифицированным целевым (ADR/vision) — это названо явно как расхождение, без попытки его исправить.

**Что НЕ делалось.** Код не менялся, конфигурация не менялась, БД не трогались, коммиты не создавались, production (`piosapp.ru`) не затрагивался, `network-management` не разворачивался.

**Дисциплина фактов.** `CLAUDE.md` («Never Invent Business Rules», «Always Report Completed Work»); `PIOS_REALITY_AUDIT.md` §19 (инцидент изоляции тестовой БД) учтён.

---

## Executive summary

- **6 backend bounded contexts** (независимо разворачиваемые Spring Boot 3.3.4 / Kotlin, БД-на-модуль, гексагональная раскладка, «reference, not ownership») + **отдельный `ai-advisor`** (не в `backend/settings.gradle.kts`, без БД) + **frontend** (React 19 / Vite, `node server/serve.mjs`).
- **Развёрнуто и обслуживает боевой трафик:** driver-management, passenger-experience, order-management, dispatch, identity, ai-advisor, frontend — на двух окружениях (HOME-PC через WinSW; VPS Beget `62.217.176.214` через systemd, за nginx; `piosapp.ru` → VPS).
- **`network-management` (порт 8085) не развёрнут нигде** — собран, но нет ни WinSW-, ни systemd-юнита, и ни один другой модуль его не читает. `PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` §14: «Taxi V1 has zero runtime dependency on Network Management» — зафиксированное решение.
- **Понятие «Connection» имеет три семантически разных значения** в двух модулях (см. §9). `network-management.Connection` и `passenger-experience.Connection` — разные классы в разных БД, одинаково названные.
- **Рабочий такси-поток замкнут:** регистрация → Circle of Trust → заказ → First Refusal → Proposal (с обязательной ценой) → подтверждение пассажиром → Assignment + Trip → ride progress → Order COMPLETED → milestones водителя.
- **Незавершённые миграции в коде:** Trip/Assignment ride-progress сосуществуют (ADR-063 не закончен); `AssignmentAccepted` и `Trip*` события публикуются, но их никто не слушает; отмена Assignment не реализована.
- **Тесты:** backend ~991 `@Test` в 151 файле; frontend 26 vitest-файлов. Два уровня — constructor-based (InMemory-репозитории) и PostgreSQL-integration против отдельных `pios_<module>_test` БД.

---

## 0. Топология репозитория и фактическое развёртывание

### 0.1 Сборочные единицы

| Единица | Расположение | Gradle | Порт | Развёрнута? |
|---|---|---|---|---|
| driver-management | `backend/driver-management` | multi-module (`backend/settings.gradle.kts`) | 8081 | ✅ HOME-PC (WinSW) + VPS (systemd) |
| passenger-experience | `backend/passenger-experience` | multi-module | 8082 | ✅ HOME-PC + VPS |
| order-management | `backend/order-management` | multi-module | 8083 | ✅ HOME-PC + VPS |
| dispatch | `backend/dispatch` | multi-module | 8084 | ✅ HOME-PC + VPS |
| network-management | `backend/network-management` | multi-module | 8085 | ❌ **не развёрнута нигде** — нет WinSW-юнита в `windows-services/`, нет systemd-юнита на VPS |
| identity | `backend/identity` | multi-module | 8086 | ✅ HOME-PC + VPS |
| ai-advisor | `ai-advisor/` (top-level, **не в `backend/settings.gradle.kts`**) | standalone | 8091 | ✅ HOME-PC + VPS |
| frontend | `frontend/` | Vite / React 19 | 4173 (`node server/serve.mjs`) | ✅ HOME-PC + VPS (nginx впереди) |
| platform-ops | `platform-ops/` (top-level) | standalone | — | WinSW-юнит есть; в VPS-списке отсутствует |

`backend/settings.gradle.kts` включает ровно 6 модулей: `driver-management`, `passenger-experience`, `order-management`, `dispatch`, `network-management`, `identity`. Комментарий там же: *«each module depends on no other»*.

### 0.2 Два боевых окружения

- **HOME-PC** (Windows 10) — 7 служб `pios-*` через WinSW; jar'ы запускаются из `backend/<module>/build/libs/<module>-0.1.0-SNAPSHOT.jar` (`workingdirectory` = каталог модуля); наружу через Tailscale Funnel `home-pc.tail385153.ts.net`.
- **VPS Beget `62.217.176.214`** (Ubuntu, nginx/1.28.3) — те же 7 служб через systemd (`/etc/systemd/system/pios-*.service`, `User=pios`, `ExecStart=/usr/bin/java -jar /opt/pios/<module>/app.jar`, `EnvironmentFile=/etc/pios/<module>.env`); фронт — `/opt/pios/frontend` (`node server/serve.mjs`, каталоги `dist/`, `server/`, без `node_modules` — `serve.mjs` не имеет runtime-зависимостей). `piosapp.ru` резолвится в этот IP напрямую (не через Cloudflare edge).

### 0.3 Общая инфраструктура (обе машины)

- **PostgreSQL 18** — одна инстанция, 6 отдельных БД: `pios_identity`, `pios_driver_management`, `pios_order_management`, `pios_dispatch`, `pios_network_management`, `pios_passenger_experience`. **Никаких shared-таблиц, никаких cross-DB foreign keys.**
- **RabbitMQ** — `127.0.0.1:5672`, `guest/guest`, vhost `/`, `publisher-confirm-type: correlated`.
- **Flyway** — `spring.flyway.enabled: true` в каждом модуле, `locations: classpath:db/migration/<module>` (не shared default — во избежание коллизии `V1__initial_schema.sql` на общем test-classpath), авто-миграция на старте.

### 0.4 Frontend-маршруты (`frontend/src/app/routes.tsx`)

`/` → DriverHome · `/i/:driverCode` → PassengerLanding · `/i/:driverCode/request` → RideRequest · `/me` → MyDrivers · `/coordinator` → Coordinator · `/network-test` → NetworkTest (тест-страница) · `/help/install` → InstallHelp · `/owner` → OwnerControlCenter · `*` → NotFound.

---

## 1. Общая архитектурная модель (из кода)

- **6 bounded contexts**, каждый — независимо разворачиваемый Spring Boot 3.3.4 / Kotlin-модуль, **своя БД**, **никаких shared-таблиц, никаких cross-DB foreign keys** (ADR-005/009/019).
- Пакеты везде одинаковые: `com.pios.<module>.{domain, application, api, persistence}` — гексагональная раскладка.
- Кросс-модульная связь **только двумя способами**:
  1. асинхронные события через RabbitMQ + transactional outbox;
  2. один синхронный REST-вызов passenger-experience → order-management (`POST /v1/orders`) — по коду тестов помечен как «dead/unused»; реальный фронт шлёт `POST /v1/orders` напрямую.
- **«Reference, not ownership»**: между модулями передаются только голые строковые идентификаторы (`OrderReference`, `DriverReference`, `PassengerReference`, `OrderOrigin`), никогда имя/телефон/адрес. Narrow-reference-типы — по одному на модуль, намеренно НЕ переиспользуются между модулями.
- Каждый репозиторий имеет две реализации: `InMemory*` (юнит-тесты) и `PostgreSQL*`.
- Каждый модуль с событиями: `<module>_outbox` таблица + `OutboxRelay` + `OutboxRelayScheduler` (poll каждые 2000 мс) + `RabbitMQEventPublisher`.
- **Аутентификация:** stateless подписанные HMAC-SHA256 токены (ADR-055), **без таблицы сессий**. `SessionTokenVerifier` **реплицирован в 5 модулях** (identity, passenger-experience, order-management, dispatch, driver-management — каждый свой файл `api/SessionTokenVerifier.kt`, не общий). Токен несёт `sub` (identityId), `drv` (driverId | null), `exp`. Общий секрет `pios.session.secret` должен совпадать во всех модулях; при пустом — всё fail-closed (401).

---

## 2. Модуль identity — `com.pios.identity`, порт 8086, БД `pios_identity`

**Назначение** (из `domain/Identity.kt` KDoc): аутентификационный якорь одного человека, **намеренно отдельный** от `network-management.Person` («идентичность отношений» ≠ «идентичность аутентификации», ADR-038 self-critique).

### 2.1 Domain

- `Identity(id: IdentityId, phone: Phone?, driverId: String?, createdAt: Instant)` — **единственный персистентный агрегат**. `phone` опционален и **не верифицирован**. `driverId` (ADR-039) — плоская строка-ссылка на driver-management, не FK; driver-management ничего не знает про Identity.
- `Credential` (sealed interface) → `PasswordCredential(identityId, passwordHash, passwordSalt, iterations, createdAt)` — PBKDF2-HMAC-SHA256, salt на credential (ADR-055).
- **Непортированные доменные формы** (файлы есть, таблиц нет, ничем не используются): `Device`, `Session` (KDoc: «resolved answer implemented elsewhere» — stateless tokens), `VerificationChallenge` + `VerificationMethod{SMS_OTP, SILENT_NETWORK_AUTH, TELEGRAM}`. Явно помечены как «shape only».

### 2.2 БД (`db/migration/identity/`)

| Миграция | Содержимое |
|---|---|
| `V1__initial_schema` | `identities(id TEXT PK, phone TEXT, created_at TIMESTAMPTZ NOT NULL)` |
| `V2__add_driver_id` | `ALTER TABLE identities ADD COLUMN driver_id TEXT` (ADR-039) |
| `V3__create_identity_credentials` | `CREATE UNIQUE INDEX ux_identities_phone ON identities(phone) WHERE phone IS NOT NULL` + `identity_credentials(identity_id TEXT PK REFERENCES identities(id), password_hash, password_salt, iterations INTEGER, created_at)` |

### 2.3 API — `IdentityController`, `/v1/identities`

| Метод | Назначение | Auth |
|---|---|---|
| `POST /register` | телефон+пароль → Identity + PasswordCredential → `AuthResponse` (сессионный токен) | нет |
| `POST /login` | телефон+пароль → `AuthResponse` | нет |
| `GET /me` | по Bearer → своя Identity | Bearer |
| `GET /{id}` | Identity по id | Bearer |
| `POST /{id}/driver` | привязать `driverId` (ADR-039) | Bearer |
| `GET /v1/health/identity` | health | owner Basic |

### 2.4 Инфраструктура

RabbitMQ **нет**, outbox **нет**, событий не публикует и не потребляет. Тесты: 10 файлов.

---

## 3. Модуль network-management — `com.pios.networkmanagement`, порт 8085, БД `pios_network_management` — **НЕ РАЗВЁРНУТ**

**Sprint 7A: PIOS Network Foundation (ADR-037).** Наиболее «PIOS Core»-подобный модуль. Из `domain/Connection.kt` KDoc: *«Sprint 7A gives this fact no meaning beyond its own existence — it is not read by Dispatch, Order Management, or any order-routing decision»*.

### 3.1 Domain (4 агрегата)

- `Person(id: PersonId, name: String, phone: String?, createdAt)` — человек независимо от роли. `require(name.isNotBlank())`.
- `PersonProfile(id, personId, type: ProfileType, createdAt)` — **одна роль = одна строка**; человек с двумя ролями = две строки. `ProfileType{DRIVER, PASSENGER, NETWORK_MEMBER}`.
- `Connection(id, fromPersonId, toPersonId, type: ConnectionType, createdAt)` — **направленная** связь Person→Person (`fromPersonId` = инвайтер/owner, «Артур → Регина»). `require(from != to)`. `ConnectionType{INVITED, CONNECTED}`.
- `Invitation(id, creatorPersonId, code, status, createdAt)` + `use()` (CREATED→USED). `InvitationStatus{CREATED, USED, EXPIRED}` — **`EXPIRED` никогда не выставляется** (нет TTL/джоба; disclosed limitation в KDoc).

### 3.2 БД (`db/migration/networkmanagement/V1__initial_schema` — единственная миграция)

`persons(id, name, phone, created_at)` · `person_profiles(id, person_id REFERENCES persons, type, created_at)` · `connections(id, from_person_id REFERENCES persons, to_person_id REFERENCES persons, type, created_at)` · `invitations(id, creator_person_id REFERENCES persons, code TEXT UNIQUE, status, created_at)`. **Внутримодульные FK — настоящие** (одна БД).

### 3.3 API

- `PersonController` `/v1/persons`: `POST` · `GET /{id}` · `GET /{id}/profiles` · `GET /{id}/connections`
- `ProfileController` `/v1/profiles`: `POST`
- `ConnectionController` `/v1/connections`: `POST` (прямое создание)
- `InvitationController` `/v1/invitations`: `POST` · `POST /{code}/accept` (accept → создаёт `Connection` типа `CONNECTED`)
- **Health-контроллера нет. Аутентификации нет ни на одном endpoint.**

### 3.4 Инфраструктура

RabbitMQ **нет**, outbox **нет**. Событий не публикует и не потребляет. Тесты: 13 файлов. Из frontend упоминается только тест-страницей `/network-test`.

---

## 4. Модуль driver-management — `com.pios.drivermanagement`, порт 8081, БД `pios_driver_management`

### 4.1 Domain

- `Driver(id: DriverId, availability, displayName: String?, createdAt: Instant?, isTest, vehicleMake/Model/Color/PlateNumber: String?, vehicleSeatCount: Int?, acceptsLongDistanceTrips: Boolean)` + `updateVehicle(...)`, `updateLongDistancePreference(accepts)`, `declareAvailability(newAvailability): DriverAvailabilityChanged?` (событие только при реальной смене состояния).
- `Availability{AVAILABLE, UNAVAILABLE}`.
- `DriverMilestones` (read-model), `RideStreakCalculator`, `PriceParser` (digits-only парсинг, ADR-065).

### 4.2 БД (`db/migration/drivermanagement/`, V1–V10)

| Миграция | Содержимое |
|---|---|
| `V1` | `drivers(id TEXT PK, availability TEXT NOT NULL)` |
| `V2` | `driver_management_outbox` |
| `V3` | `+ display_name TEXT` |
| `V4` | `+ created_at TIMESTAMPTZ` |
| `V5` | `+ is_test BOOLEAN NOT NULL DEFAULT FALSE` |
| `V6` | `driver_management_processed_events(event_id PK)` + `driver_milestones(driver_id PK REFERENCES drivers, completed_rides_count BIGINT, current_streak_weeks INT, last_completed_at)` |
| `V7` | `+ repeat_clients_count INT` + `driver_management_order_passengers(order_id PK, passenger_reference)` + `driver_client_rides(driver_id REFERENCES drivers, passenger_reference, ride_count INT, PK(driver_id, passenger_reference))` |
| `V8` | `+ total_stated_earnings BIGINT, + unpriced_rides_count INT` + `driver_ride_stated_prices(event_id PK REFERENCES processed_events, driver_id REFERENCES drivers, stated_price_raw, stated_price_parsed BIGINT, recorded_at)` |
| `V9` | `+ vehicle_make/model/color/plate_number TEXT, + vehicle_seat_count INTEGER` |
| `V10` | `+ accepts_long_distance_trips BOOLEAN NOT NULL DEFAULT FALSE` |

### 4.3 API — `DriverController`, `/v1/drivers`

| Метод | Auth |
|---|---|
| `POST /v1/drivers` | нет (first-contact, id = caller-generated unguessable UUID) |
| `GET /{driverId}` | нет (публично — для превью приглашения) |
| `GET /v1/drivers` | нет (публично, весь список, без фильтрации) |
| `POST /{driverId}/vehicle` | Bearer, `drv == driverId` (self-only) |
| `POST /{driverId}/long-distance-preference` | Bearer, self-only |
| `GET /{driverId}/milestones` | Bearer, self-only |
| `POST /{driverId}/availability` | Bearer, self-only (Task 25) |
| `GET /v1/health/driver-management` | owner Basic |

`DriverResponse` публично отдаёт: `id, availability, displayName, registeredAt, isTest, vehicleMake/Model/Color/PlateNumber, vehicleSeatCount, acceptsLongDistanceTrips`.

### 4.4 RabbitMQ

- **Публикует:** `DriverAvailabilityChanged` (routing `driver.availability.changed`) через outbox.
- **Потребляет:**
  - `OrderSubmittedListener` → пишет `driver_management_order_passengers(order_id, passenger_reference)` (для последующей repeat-client корреляции).
  - `AssignmentCompletedListener` → `AssignmentCompletedApplicationService` обновляет `driver_milestones` (completed count, streak, repeat clients, earnings) + `driver_ride_stated_prices`.

Тесты: 38 файлов.

---

## 5. Модуль order-management — `com.pios.ordermanagement`, порт 8083, БД `pios_order_management`

### 5.1 Domain

- `Order` — **private constructor**, factory `Order.submit(...)`. Поля: `id, status, origin: OrderOrigin, destination: String?, passengerName: String?, createdAt: Instant?, pickupAddress: String?, requestedPickupAt: Instant?, isTest, explicitDriverIntent: Boolean, passengerCount: Int?`.
- `OrderStatus{SUBMITTED, COMPLETED, CANCELLED}` — **только это**. ASSIGNED/ACCEPTED/ride-states **намеренно НЕ в Order** (KDoc: они в dispatch).
- `OrderOrigin(reference: String)` `@JvmInline value class` — «источник заказа»; по ADR-060 Decision 2 = `identityId` аутентифицированного пассажира.
- Методы: `complete()`, `cancel()` — оба только из `SUBMITTED` (`check(status == SUBMITTED)`).
- `submit()` содержит `require(passengerCount == null || passengerCount > 0)`.

### 5.2 БД (`db/migration/ordermanagement/`, V1–V12)

`orders(id TEXT PK, status TEXT NOT NULL)` + аддитивные колонки: `V4` `+ origin (NOT NULL), + destination (NOT NULL)` · `V5` **DROP COLUMN destination** (backward-compat revert) · `V6` `+ destination TEXT NULL` · `V7` `+ passenger_name, + created_at` · `V8` `+ pickup_address` · `V9` `+ requested_pickup_at TIMESTAMPTZ` · `V10` `+ is_test BOOLEAN NOT NULL DEFAULT FALSE` · `V11` `+ explicit_driver_intent BOOLEAN NOT NULL DEFAULT FALSE` · `V12` `+ passenger_count INTEGER NULL`. Плюс `V2` `order_management_outbox`, `V3` `order_management_processed_events(event_id PK)`.

### 5.3 API

- **`OrderSubmissionController`** `POST /v1/orders` — **Bearer, `sub == request.passengerReference`** (P0 fix, `PIOS_DATA_FLOW_CODE_AUDIT.md` §5). Owner/Basic-ветки нет намеренно.
- **`OrderQueryController`** `GET /v1/orders` — **3 взаимоисключающих режима** (ADR-060), auth разрешается до формы параметров:
  1. `Basic` (owner) **без** параметров → все заказы;
  2. `Bearer` + `?passengerReference=<sub>` → свои заказы (фильтр `origin.reference == sub`);
  3. `Bearer` (`drv != null`) + `?ids=<uuid,...>` (≤ `MAX_IDS`=100) → подмножество по id. **Не проверяет, что у водителя есть proposal на эти id** — disclosed, product-owner-accepted limitation (ADR-060 Decision 3).
  Любая другая комбинация → 400/401/403, никогда silent empty list, никогда 404 для неизвестного id.
- **`OrderCancellationController`** `POST /v1/orders/{orderId}/cancel` — Bearer.
- `GET /v1/health/order-management` — owner Basic.

### 5.4 RabbitMQ

- **Публикует:** `OrderSubmitted` (payload: `orderId, passengerReference` (=origin), `explicitDriverIntent, isTest`), `OrderCompleted`, `OrderCancelled`.
- **Потребляет:** `AssignmentAcceptedListener` (идемпотентность через `order_management_processed_events`), `AssignmentCompletedListener` → `Order.complete()` (ADR-041: синхронизация статуса заказа с завершением поездки).

Тесты: 39 файлов.

---

## 6. Модуль dispatch — `com.pios.dispatch`, порт 8084, БД `pios_dispatch` — самый крупный (68 тест-файлов)

Владеет **4 агрегатами** + **2 проекциями** (read-model из событий чужих модулей).

### 6.1 Proposal (`domain/Proposal.kt` — private constructor, factory `propose(...)`)

Поля: `id, order: OrderReference, driver: DriverReference, createdAt: Instant?, isTest, passengerReference: PassengerReference?` (ADR-066) + мутабельные `status, respondedAt, statedPrice: String?, statedEtaMinutes: Int?`.

`ProposalStatus{OPEN, PRICE_PROPOSED, ACCEPTED, DECLINED, LAPSED, WITHDRAWN}`.

**Root invariant:** максимум один `OPEN` proposal на order — в коде (`propose()` проверяет переданную коллекцию) **и в БД** (`V14` partial unique index `proposals_one_open_per_order ON proposals(order_reference) WHERE status='OPEN'`).

**Переходы:**
- `proposePrice(price, eta?)` — OPEN→PRICE_PROPOSED, **цена обязательна и non-blank** (`require(statedPrice.isNotBlank())`);
- `confirmPrice()` — PRICE_PROPOSED→ACCEPTED (акт пассажира);
- `declinePriceProposal()` — PRICE_PROPOSED→DECLINED;
- `accept(price?, eta?)` — OPEN→ACCEPTED (legacy путь);
- `decline()` — OPEN→DECLINED;
- `lapse()` — OPEN→LAPSED (по таймауту; `pios.proposal.lapse.timeout-minutes: 5`, `ProposalLapseScheduler` poll 30000 мс);
- `withdraw()` — OPEN→WITHDRAWN (при отмене заказа, ADR-053).

**Продуктовое правило (2026-09-05, из KDoc):** водитель обязан назвать цену → `PRICE_PROPOSED`; пассажир отдельным актом соглашается (`confirm-price`) или отказывается (`decline-price`); при отказе заказ просто закрывается, **без переброса на другого водителя**.

### 6.2 Assignment (`domain/Assignment.kt` — private constructor, factory `create(...)`)

Поля: `id, order, driver, isTest` + `status, statusChangedAt, arrivedAt, startedAt, completedAt`.
`AssignmentStatus{CREATED, ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED}`.
**Инвариант «один Assignment на order»** — только проверка `create()` против переданной коллекции; **БД-констрейнта на это нет** (только на `trips.assignment_id`).
Методы `accept/arrive/start/complete` — **в коде остались, покрыты юнит-тестами, но ride-progress через них в проде больше НЕ идёт** (см. 6.3).
**Отмена Assignment (`DOMAIN_MODEL` §6) не реализована** — в `AssignmentStatus` нет CANCELLED, в `Assignment.kt` нет метода.

### 6.3 Trip (`domain/Trip.kt` — ADR-063, private constructor, factory `create(assignment, existingTrip?)`)

Поля: `id, assignmentId, order, driver, isTest` + `status, statusChangedAt, arrivedAt, startedAt, completedAt`.
`TripStatus{CREATED, ARRIVED, IN_PROGRESS, COMPLETED}`.
**Создаётся синхронно в тот же commit, что и Assignment** (`DispatchAssignmentApplicationService.createTripFor`), триггер — `OrderAssigned` (не `AssignmentAccepted`, который *не публикует ни один боевой путь* — прямо зафиксировано в KDoc `Trip.kt` и `DispatchAssignmentApplicationService.kt`).
**Инвариант «1 Trip на Assignment»** — код (`create()`) + **БД** (`V12` `trips.assignment_id TEXT NOT NULL UNIQUE REFERENCES assignments(id)` — единственный настоящий cross-aggregate FK, т.к. обе таблицы в одной БД).
Методы `arrive/start/complete` — **теперь единственный валидатор ride-progress** (Task 12 convergence): `AssignmentController.arrive/start/complete` → `DispatchAssignmentApplicationService.{arrive,start,complete}Assignment` → `Trip.arrive/start/complete` (+ self-heal: если Trip нет — создаёт на месте).
**Dual-publish** (окно миграции ADR-063): каждый переход публикует и `Trip*` (routing `trip.arrived/started/completed` — **никто не слушает**), и legacy `Assignment*` (`assignment.arrived/started/completed` — слушает order-management + driver-management).

### 6.4 Проекция DriverAvailability (`V2`: `driver_availability(driver_reference PK, available, updated_at)` + `driver_availability_processed_events(event_id PK)`)

Локальная копия доступности водителей из события `DriverAvailabilityChanged`. `DriverAvailabilityChangedListener` → `DriverAvailabilityProjectionApplicationService`. KDoc: *«Dispatch is not the source of truth for a driver's availability»*.

### 6.5 Проекция PrimaryDriverRecord (`V13`: `primary_driver_records(passenger_reference PK, driver_reference, updated_at)` + `primary_driver_processed_events(event_id PK)`)

Локальная копия «у пассажира X основной водитель Y» из событий passenger-experience `PrimaryConnectionDesignated` / `PrimaryConnectionCleared`. `PrimaryConnectionEventListener` → `PrimaryDriverProjectionApplicationService`. **Читается только логикой First Refusal** (§7.4).

### 6.6 БД dispatch (`db/migration/dispatch/`, V1–V15)

`V1` `assignments(id, order_reference, driver_reference, status)` · `V2` `driver_availability` + processed_events · `V3` `dispatch_outbox` · `V4` `proposals(id, order_reference, driver_reference, status)` · `V5` `+ assignments.status_changed_at` · `V6` `+ proposals.stated_price TEXT` · `V7` `+ proposals.created_at, + responded_at` · `V8` `+ assignments.arrived_at/started_at/completed_at` · `V9` `order_cancelled_processed_events` · `V10` `+ proposals.stated_eta_minutes INTEGER` · `V11` `+ proposals.is_test, + assignments.is_test` · `V12` `trips` (+ UNIQUE FK) · `V13` `primary_driver_records` + processed_events · `V14` partial unique index `proposals_one_open_per_order` · `V15` `+ proposals.passenger_reference TEXT NULL` (ADR-066). Все `status` — plain TEXT, без CHECK-констрейнта.

### 6.7 API

**`ProposalController`, `/v1/proposals`** (все write-методы: Bearer или owner Basic):

| Метод | Проверка |
|---|---|
| `POST /v1/proposals` | `passengerReference` обязателен (400 если пусто); для Bearer `== sub` (403); для owner Basic — без sub-проверки |
| `POST /{id}/accept` | Bearer/Basic (legacy путь) |
| `POST /{id}/propose-price` | водитель называет цену → PRICE_PROPOSED |
| `POST /{id}/confirm-price` | владелец или `sub == proposal.passengerReference` (fail-closed если reference `null`) |
| `POST /{id}/decline-price` | то же |
| `POST /{id}/decline` | Bearer/Basic |
| `POST /{id}/lapse` | Bearer/Basic |
| `GET /{id}` | 401/404/403 — пассажир-владелец, водитель proposal'а, или owner |
| `GET /v1/proposals?driverId=` | Bearer, `drv == driverId` (ADR-060) |
| `GET /v1/proposals?orderId=` | owner → весь список; Bearer-пассажир → только свои строки |

**Остаточный gap (ADR-066 Decision 10, знаемо оставлен):** аутентифицированный пассажир может создать proposal на чужой `orderId` под своей же идентичностью (nuisance, не disclosure).

**`AssignmentController`, `/v1/assignments`:**

| Метод | Auth |
|---|---|
| `POST /v1/assignments` | (прямое создание — путь Coordinator) |
| `GET /v1/assignments?orderId=` | — |
| `POST /{id}/arrive` \| `/start` \| `/complete` | Bearer, водитель этого Assignment (Task 23) |
| `GET /v1/health/dispatch` | owner Basic |

### 6.8 RabbitMQ (dispatch)

- **Публикует:** `OrderProposed` (`order.proposed`), `OrderAssigned` (`order.assigned`), `AssignmentAccepted` (`assignment.accepted` — **не публикуется боевым путём**), `AssignmentArrived/Started/Completed` (`assignment.*`), `TripArrived/Started/Completed` (`trip.*`), `ProposalDeclined/Lapsed/Withdrawn`.
- **Потребляет:** `DriverAvailabilityChangedListener`, `OrderSubmittedFirstRefusalListener`, `OrderCancelledListener` (→ withdraw OPEN proposal), `PrimaryConnectionEventListener`.

### 6.9 Транзакционные границы (ADR-036)

`ProposalAssignmentOrchestrationService.confirmPrice` / `acceptProposal` — **одна общая транзакция** на: чтение Proposal + запись Proposal (ACCEPTED) + чтение existing assignments + запись Assignment + Trip + outbox. Либо всё, либо ничего. `*WithinCallerTransaction`-методы на `ProposalApplicationService` и `DispatchAssignmentApplicationService` существуют именно для этого. Намеренное узкое исключение из «одна транзакция на агрегат».

---

## 7. Circle of Trust / Primary Driver / First Refusal — полный механизм

Три связанных, но раздельно расположенных понятия.

### 7.1 «Circle» и «Primary» живут в passenger-experience (порт 8082, БД `pios_passenger_experience`)

**`Connection` (`passenger-experience/domain/Connection.kt`) — ≠ `network-management.Connection`:**
`Connection(id, driverId: DriverReference, passengerReference: PassengerReference, createdAt)`. Смысл (KDoc): *«passenger reached PIOS through a specific driver's own invitation link»*. Без status, без type. Таблица (`V1`): `connections(id, driver_id, passenger_reference, created_at, UNIQUE(driver_id, passenger_reference))` — **открытие ссылки идемпотентно на уровне БД**.

**`primary_connections` (`V2`, ADR-054) — «Circle of Trust»:**
```sql
ALTER TABLE connections ADD CONSTRAINT connections_passenger_reference_id_key UNIQUE (passenger_reference, id);

CREATE TABLE primary_connections (
    passenger_reference TEXT PRIMARY KEY,
    connection_id       TEXT NOT NULL,
    designated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT primary_connections_connection_fkey
        FOREIGN KEY (passenger_reference, connection_id)
        REFERENCES connections (passenger_reference, id)
        ON DELETE CASCADE
);
```
Отдельная таблица, не колонка (ADR-054 Part 2): single-primary-инвариант = PRIMARY KEY; композитный FK гарантирует «primary принадлежит кругу этого пассажира»; `ON DELETE CASCADE` — удаление connection чистит primary автоматически.
**«Designation, never creation»** (`SetPrimaryConnectionApplicationService`): только помечает существующий Connection, никогда не создаёт новый.

### 7.2 API Circle of Trust — `passenger-experience/ConnectionController`, `/v1/connections` — **все 5 endpoint'ов Bearer (ADR-055 Decision 4)**

| Метод | Проверка |
|---|---|
| `POST /v1/connections` | `request.passengerReference == sub` (403); 201 первый раз, 200 при повторе (идемпотентность) |
| `GET ?driverId=` | `driverId == drv` (403); ответ **без** `isPrimary` |
| `GET ?passengerReference=` | `== sub` (403); ответ **с** `isPrimary` |
| `POST /{connectionId}/primary` | смотрит connection, `passengerReference == sub` иначе **404** (не 403 — чтобы не подтверждать существование чужого connectionId) |
| `DELETE /{connectionId}` | не-владельцу тоже 204, удаляет ничего (идемпотентность ADR-054) |

### 7.3 Событийный мост passenger-experience → dispatch (ADR-062)

`SetPrimaryConnectionApplicationService.handle` → upsert `primary_connections` + outbox `PrimaryConnectionDesignated{passengerReference, driverId}` (routing `primary.connection.designated`) в **одной транзакции**. `passenger_experience_outbox` (`V3`) — **первое событие, которое этот модуль вообще публикует** (ADR-054 Part 1 не строил ни одного; ADR-062 авторизовал это узкое добавление).
dispatch `PrimaryConnectionEventListener` → проекция `primary_driver_records` (§6.5).

### 7.4 First Refusal — механизм (dispatch)

`FirstRefusalApplicationService.attempt(order, passengerReference, isTest, explicitDriverIntentDeclared)` → `FirstRefusalOutcome`:

| Условие | Outcome |
|---|---|
| `explicitDriverIntentDeclared == true` | `ExplicitDriverIntentDeclared` — **полный no-op**, никаких чтений |
| нет записи в `primary_driver_records` | `NoPrimaryDriver` |
| primary недоступен (`driver_availability`) | `PrimaryDriverIneligible(driver)` |
| иначе | `proposalApplicationService.handle(ProposeDriverCommand(order, primaryDriver, passengerReference))` → `Proposed(proposal)` либо `AlreadyAttempted` (root invariant / БД-констрейнт) |

**Runtime-интеграция (Task 16):** `OrderSubmittedFirstRefusalListener` слушает `OrderSubmitted` на своей очереди → вызывает `FirstRefusalApplicationService.attempt` **напрямую** (не через промежуточный application service). Извлекает `orderId, passengerReference, explicitDriverIntent, isTest` из payload.

- **Идемпотентность:** отдельного ledger нет — защищают root invariant (`propose()`) + `proposals_one_open_per_order` (`V14`). **Disclosed residual:** если та же `eventId` придёт **после** того, как первый proposal уже разрешился, — возможен второй proposal (окно намного шире retry-политики: 3 попытки, backoff ≤ 2000 мс; не считается блокером).
- **Автоматического fallback НЕТ:** если First Refusal не сработал, `OrderSubmitted` НЕ порождает «обычное» назначение — это делает человек (Coordinator) вручную через `POST /v1/proposals` или `POST /v1/assignments`.

**`explicit_driver_intent`** (order-management `V11`) — записывается атомарно при `submit`, летит в `OrderSubmitted`, гарантирует «явный выбор пассажира всегда выигрывает» без гонки (submit всегда причинно раньше любого consumer'а).

---

## 8. Order → Proposal → Assignment → Trip — жизненный цикл (сквозной, из кода)

```
1. Пассажир: POST /v1/orders (Bearer, sub == passengerReference)
   → order-management: Order.submit() → status SUBMITTED, origin = identityId
   → outbox OrderSubmitted{orderId, passengerReference, explicitDriverIntent, isTest}

2а. dispatch OrderSubmittedFirstRefusalListener → FirstRefusalApplicationService.attempt
    → если у пассажира есть primary driver, он доступен и intent не declared:
       Proposal.propose(order, primaryDriver, passengerReference) → status OPEN
       → outbox OrderProposed
    → driver-management OrderSubmittedListener → пишет order_passengers(orderId, passengerReference)

2б. ИЛИ Coordinator / RideRequest: POST /v1/proposals {orderId, driverId, passengerReference}
       → Proposal.propose(...) → OPEN

3. Водитель: POST /v1/proposals/{id}/propose-price {statedPrice, statedEtaMinutes?}
   → Proposal OPEN → PRICE_PROPOSED

4. Пассажир: POST /v1/proposals/{id}/confirm-price
   → ProposalAssignmentOrchestrationService.confirmPrice (ОДНА общая транзакция, ADR-036):
      Proposal PRICE_PROPOSED → ACCEPTED
      + Assignment.create(order, driver) → status CREATED (+ outbox OrderAssigned)
      + Trip.create(assignment) → status CREATED           ← синхронно, тот же commit
   (при декрайне: POST /decline-price → Proposal DECLINED, заказ закрыт, ничего больше)

5. Ride progress (водитель, DriverHome): POST /v1/assignments/{id}/arrive|start|complete
   → DispatchAssignmentApplicationService → Trip.arrive/start/complete
      (Assignment.status при этом НЕ меняется — frozen remnant)
   → dual outbox: TripArrived/Started/Completed (никто не слушает)
                + AssignmentArrived/Started/Completed (legacy)

6. assignment.completed →
   - order-management AssignmentCompletedListener → Order.complete() → status COMPLETED
   - driver-management AssignmentCompletedListener → driver_milestones (+ride, streak, repeat client, earnings)
```

### 8.1 Наблюдения из кода (расхождения с целевым)

- `AssignmentAccepted` (событие + routing `assignment.accepted`) **не публикуется ни одним боевым путём** (подтверждено в KDoc `Trip.kt` / `DispatchAssignmentApplicationService.kt`). `POST /v1/assignments/{id}/accept` существует, normal-flow его не вызывает.
- **Дублирование ride-progress:** `AssignmentStatus{ARRIVED, IN_PROGRESS, COMPLETED}` и методы `Assignment.arrive/start/complete` остались в коде и покрыты юнит-тестами, но в проде не пишутся. ADR-063 target = их удаление; Task 12 остановился раньше «чтобы не осиротить исторические строки».
- **`TripArrived/Started/Completed` события** публикуются, но их **никто не слушает** (dual-publish окно ADR-063 не закрыто).
- **Отмена Assignment** (`DOMAIN_MODEL` §6) — **не реализована**.

### 8.2 «Repeat order» / история поездок

- **Отдельной сущности «история поездок» нет.** История = строки `assignments` / `trips` в БД dispatch + `driver_milestones` / `driver_client_rides` (агрегаты-счётчики) в driver-management.
- **«Repeat order» для пассажира** = экран `/me` (`MyDrivers.tsx`): читает `GET /v1/connections?passengerReference=<sub>` → показывает список водителей (primary первым) → кнопка «Заказать поездку» ведёт на `/i/{driverId}/request`. **Backend-фичи «повторить заказ» нет** — это переход на форму нового заказа к тому же водителю.
- **«Repeat client» для водителя** = `driver_client_rides.ride_count >= 2` → инкремент `driver_milestones.repeat_clients_count`, показывается на DriverHome.
- `SPRINT_PILOT_BLOCKERS.md` P0-1 «Repeat ride impossible after first resolved order» — исторический блокер, разрешён тем, что `proposals_one_open_per_order` — **partial** index (`WHERE status='OPEN'`): новый proposal на тот же order после разрешения предыдущего не блокируется.

---

## 9. Понятие «Connection» — три семантически разных значения

| # | Где | Тип | Смысл | Направление | Читается кем |
|---|---|---|---|---|---|
| 1 | `network-management/domain/Connection.kt`, таблица `connections` (БД `pios_network_management`) | `Connection(id, fromPersonId, toPersonId, type: {INVITED, CONNECTED}, createdAt)` | Направленная социальная связь Person→Person («Артур пригласил Регину»). Абстрактная сеть отношений. | Направленное (`from` = инвайтер) | **Никем.** Модуль не развёрнут, событий не шлёт, dispatch/order/passenger его не читают |
| 2 | `passenger-experience/domain/Connection.kt`, таблица `connections` (БД `pios_passenger_experience`) | `Connection(id, driverId: DriverReference, passengerReference: PassengerReference, createdAt)` | Факт: пассажир пришёл в PIOS по личной ссылке конкретного водителя. Основа Circle of Trust. | Пара driver↔passenger, без направления-инвайта | `MyDrivers.tsx`, `PassengerLanding.tsx`, First Refusal (косвенно через primary) |
| 3 | `passenger-experience`, таблица `primary_connections` (`V2`) | `primary_connections(passenger_reference PK, connection_id, designated_at)` | «Основной водитель» пассажира (ровно один). Circle of Trust в узком смысле. | passenger → его выбранный driver | dispatch (проекция `primary_driver_records`) → First Refusal |
| — | dispatch, `primary_driver_records` (`V13`) | проекция #3 | Локальная копия «primary» для First Refusal | — | `FirstRefusalApplicationService` |

**Ключевое:** #1 (network) и #2 (passenger-experience) — **разные классы в разных модулях в разных БД, одинаково названные**. #1 моделирует «сеть отношений вообще»; #2 — «один прозрачный маршрут заказа» (KDoc: *«this sprint's own scope is one transparent route, not a general relationship model»*). `network-management` — заготовка под будущий PIOS Core; `passenger-experience.Connection` — то, что реально работает в такси-потоке.

Плюс narrow-reference-типы: `dispatch/OrderReference`, `dispatch/DriverReference`, `order-management/OrderOrigin`, `passenger-experience/PassengerReference`, `dispatch/PassengerReference` — по одному на модуль, намеренно НЕ переиспользуются (ADR-005/009).

---

## 10. События, outbox, RabbitMQ

**Паттерн (везде одинаковый):** доменное изменение + `INSERT` в `<module>_outbox` в одной PostgreSQL-транзакции → `OutboxRelayScheduler` (poll 2000 мс) → `OutboxRelay` → `RabbitMQEventPublisher` → topic exchange. Consumer: `@RabbitListener` на выделенной очереди, валидирует `eventType` + `eventVersion`, при ошибке — bounded retry (3 попытки, backoff ≤ 2000 мс) → dead-letter queue (ADR-031). Идемпотентность — `<module>_processed_events(event_id PK)` там, где переигрывание эффекта было бы неверным.

**Envelope:** `{eventId (UUID, генерится 1 раз), eventType, eventVersion: 1, occurredAt, payload: {...}}`. `payload` в БД — plain TEXT (JSON-строка), не JSONB (ADR-030 открыт).

| Событие | Публикует | Routing key | Потребляют |
|---|---|---|---|
| `DriverAvailabilityChanged` | driver-management | `driver.availability.changed` | dispatch (`driver_availability` проекция) |
| `OrderSubmitted` | order-management | `order.submitted` | dispatch (First Refusal), driver-management (`order_passengers`) |
| `OrderCompleted` | order-management | `order.completed` | — |
| `OrderCancelled` | order-management | `order.cancelled` | dispatch (withdraw OPEN proposal) |
| `OrderProposed` | dispatch | `order.proposed` | — |
| `OrderAssigned` | dispatch | `order.assigned` | — (Trip создаётся in-process, не по событию) |
| `AssignmentAccepted` | dispatch (**не публикуется боевым путём**) | `assignment.accepted` | order-management (`order_management_processed_events`) |
| `AssignmentArrived/Started/Completed` | dispatch | `assignment.arrived/started/completed` | `assignment.completed` → order-management (`Order.complete()`) + driver-management (milestones) |
| `TripArrived/Started/Completed` | dispatch | `trip.arrived/started/completed` | **никто** (dual-publish окно) |
| `PrimaryConnectionDesignated` | passenger-experience | `primary.connection.designated` | dispatch (`primary_driver_records`) |
| `PrimaryConnectionCleared` | passenger-experience | `primary.connection.cleared` | dispatch |

**Модули без RabbitMQ вообще:** identity, network-management.

**Синхронный REST между модулями — ровно один:** passenger-experience `RestClientOrderSubmissionClient` → order-management `POST /v1/orders` (`pios.order-management.base-url: http://127.0.0.1:8083`). По коду тестов (`PassengerOrderSubmissionEndToEndTest`, `RestClientOrderSubmissionClientTest`) — путь помечен как «dead/unused»; реальный фронт шлёт `POST /v1/orders` напрямую.

---

## 11. Тесты и их связь с БД

| Модуль | Тест-файлов |
|---|---|
| dispatch | 68 |
| order-management | 39 |
| driver-management | 38 |
| passenger-experience | 19 |
| network-management | 13 |
| ai-advisor | 14 |
| identity | 10 |
| **backend всего** | **151 файл, ~991 `@Test`-метод** |
| **frontend** | **26 файлов (vitest), ~264 теста** |

**Два уровня backend-тестов:**

1. **Constructor-based, без Spring-контекста** — большинство. `InMemory*Repository`, локальная чеканка HS256-токенов (`issueToken(sub, drv, ttl)` — хелпер продублирован по тест-файлам).
2. **PostgreSQL integration** — файлы `*PostgreSQL*` (~4 на модуль). Каждый модуль: `test/.../persistence/PostgreSQLTestDatabase.kt` — `object` с `DataSource by lazy`, URL захардкожен `jdbc:postgresql://127.0.0.1:5432/pios_<module>_test` (**отдельная `_test`-БД, не production**), Flyway мигрирует те же `src/main/resources/db/migration/<module>` при первом обращении.
   - **Инцидент (зафиксирован в KDoc `PostgreSQLTestDatabase.kt` и `PIOS_REALITY_AUDIT.md` §19, 2026-09-02):** раньше `PostgreSQLTestDatabase` (dispatch) указывал на `pios_dispatch` напрямую; `./gradlew build` во время read-only аудита записал реальные строки в production. После этого имя `_test` захардкожено без возможности override через env/property.
   - Требует запущенного локального PostgreSQL с БД `pios_<module>_test`; RabbitMQ-интеграционные тесты — запущенного RabbitMQ.
3. **Cross-module contract tests** — напр. dispatch имеет test-scoped зависимость на order-management (общий classpath миграций → Flyway `locations` пиннится, иначе коллизия `V1__initial_schema.sql`).

**Известная нестабильность:** `PostgreSQLAssignmentLifecycleTest` (dispatch) использует фиксированные литеральные id (не UUID-суффиксы) → флап при повторных полных прогонах из-за остаточных строк. Дефект теста, не кода.

---

## 12. ai-advisor — `com.pios.aiadvisor`, порт 8091, **без БД, standalone**

- Отдельный top-level модуль, **не в `backend/settings.gradle.kts`**, свой Gradle.
- `AIProvider` интерфейс (`name`, `analyze(PilotAnalysisRequest): AIProviderOutcome`) — «never throws, returns outcomes as values» (ADR-056 Decision 7).
- 4 реализации: `MockAIProvider` (**default**), `DeepSeekProvider`, `OllamaProvider` (локальный), `QwenProvider` (DashScope, INTERNATIONAL эндпойнт — Singapore). Выбор через `@ConditionalOnProperty("pios.ai-advisor.provider")`.
- `AdvisorController` — за owner Basic (та же credential, что у 6 модулей, ADR-056 Decision 4) + `BudgetGuard` (лимит вызовов/день/месяц) + `RequestSizeLimitFilter` (8192 байта).
- Получает **только агрегированные числовые метрики** (`PilotAnalysisRequest`), **не свободный текст, не PII** (ADR-056 Decision 12).
- **GigaChat** ратифицирован в `PRODUCT_DECISION_CONTROL_CENTER_AI.md` §9 как первый провайдер, но **в коде отсутствует** — расхождение.
- Frontend: `OwnerControlCenter/AIAnalystCard.tsx` + `aiProvider.ts` + `pilotAnalytics.ts`.
- Тесты: 14 файлов (`FakeDeepSeekServer`, `FakeOllamaServer`, `FakeQwenServer` — локальные заглушки).

---

## 13. PIOS Core vs Taxi — классификация компонентов

**«Core» = не зависит от такси-специфики (заказ / поездка / водитель), переиспользуемо для любой Personal Business Network.**

### 13.1 Соответствует будущему PIOS Core (role-agnostic, не знает про поездки)

| Компонент | Почему Core | Оговорка |
|---|---|---|
| **identity** целиком | Аутентификация одного человека; `Identity` явно role-agnostic (KDoc); `driverId` — единственная такси-примесь, опциональная плоская ссылка | `Identity.driverId` — единственное место, где Core знает про такси; вычищается тривиально |
| **network-management** целиком | `Person` / `PersonProfile` / `Connection` / `Invitation` — чистая модель «люди + роли + направленные связи + приглашения»; `ProfileType{DRIVER,PASSENGER,NETWORK_MEMBER}` — enum-значения, не структура; ничего про Order/Trip | **Не развёрнут, никем не читается. Заготовка.** `EXPIRED` не реализован. Нет auth. Нет health |
| **Транспортный паттерн** (outbox + relay + RabbitMQ envelope + `processed_events` + dead-letter) | Инфраструктурный, дублируется идентично в 4 модулях, не зависит от домена | — |
| **«Reference, not ownership» + БД-на-модуль + гексагон** | Архитектурные принципы, не такси-код | — |
| **Circle of Trust — сама идея** (passenger designates one primary provider) | «Постоянные отношения клиент↔поставщик» — прямо из `PIOS_PRODUCT_VISION.md`, обобщается на не-такси | Реализация привязана к `passengerReference`/`driverId` строкам, живёт в модуле с именем `passenger-experience` |
| **stateless session tokens** (`sub` + `drv`) | Механизм Core; `drv` claim — единственная такси-примесь в токене | — |

### 13.2 Исключительно Taxi (знает про заказ / поездку / назначение)

| Компонент | Такси-специфика |
|---|---|
| **order-management** целиком | `Order`, `OrderStatus{SUBMITTED,COMPLETED,CANCELLED}`, `pickup_address`, `destination`, `requested_pickup_at`, `passenger_count` — модель поездки |
| **dispatch** целиком | `Proposal` / `Assignment` / `Trip`, `stated_price`, `stated_eta_minutes`, ride-progress `ARRIVED/IN_PROGRESS/COMPLETED`, First Refusal (привязка заказа к водителю) |
| **driver-management** | Availability (`AVAILABLE/UNAVAILABLE`), `vehicle_*`, `accepts_long_distance_trips`, `driver_milestones` (completed rides, earnings) — всё про водителя-в-поездке |
| **passenger-experience** | `Connection(driverId, passengerReference)` завязан на «driver» и на маршрут заказа; `RestClientOrderSubmissionClient` → order-management |
| **ai-advisor** | `PilotAnalysisRequest` метрики — orders / proposals / assignments / reaction time — такси-KPI |
| **frontend** целиком | DriverHome, RideRequest, Coordinator, PassengerLanding — такси-экраны |

### 13.3 Пограничные / смешанные

- **passenger-experience** — концептуально Core (Circle of Trust — обобщаемая идея постоянных отношений), но код завязан на `driverId` и на submit заказа. Чтобы стать Core, `Connection` должен обобщиться до «participant ↔ provider» и потерять зависимость от order submission.
- **`Identity.driverId`** — одна строка, отделяющая identity от чистого Core.
- **`SessionToken.drv` claim** — то же.
- **network-management vs passenger-experience** — **дублируют понятие «связь между людьми»**. network-management — «правильная» Core-модель, но мёртвая; passenger-experience — рабочая, но такси-специфичная. **Ключевая точка будущей консолидации:** либо network-management поглощает роль, либо `passenger-experience.Connection` обобщается.

---

## 14. Сводка «что есть vs чего нет» (фактически, из кода)

### 14.1 Есть и работает в проде (`piosapp.ru` / VPS)

- Регистрация / логин (identity, phone + password, stateless tokens)
- Профиль водителя: доступность, машина, «беру дальние поездки», ссылка-приглашение
- Пассажир по ссылке → Circle of Trust (connection) → выбор primary driver
- Заказ (Bearer-auth) с pickup / destination / scheduled time / passenger count
- First Refusal: авто-proposal основному водителю при submit
- Proposal с обязательной ценой + ETA → подтверждение / отказ пассажиром
- Assignment + Trip (синхронно) → ride progress (arrive / start / complete) → Order COMPLETED
- Milestones водителя: completed rides, streak, repeat clients, earnings
- «Мои водители» (`/me`) — возврат к водителю
- Coordinator-экран (ручное назначение)
- Owner Control Center + AI Advisor (mock provider по умолчанию)
- Отмена заказа → withdraw OPEN proposal

### 14.2 Есть в коде, но НЕ в проде / НЕ используется

- **network-management** (порт 8085) — собран, не развёрнут, никем не читается
- `AssignmentAccepted` событие + `POST /assignments/{id}/accept` — боевой путь не вызывает
- `Assignment.arrive/start/complete` + `AssignmentStatus.{ARRIVED,IN_PROGRESS,COMPLETED}` — заморожены, ride-progress идёт через Trip
- `TripArrived/Started/Completed` события — публикуются, никто не слушает
- `Trip.kt` полный lifecycle существует параллельно Assignment (ADR-063 migration не завершён)
- identity: `Device`, `Session`, `VerificationChallenge` — только доменные формы, таблиц нет
- passenger-experience `RestClientOrderSubmissionClient` → order-management — «dead path» (фронт шлёт напрямую)
- GigaChat провайдер (ратифицирован в Product Decision, не написан)

### 14.3 Нет вообще

- Отмена Assignment (`DOMAIN_MODEL` §6 называет, кода нет)
- Верификация телефона (SMS / OTP / SNA / Telegram)
- Отдельная сущность «история поездок» / «повторить заказ» (только счётчики + переход на форму)
- Аутентификация на network-management
- TTL / expiry у Invitation
- Раскрытие телефона между водителем и пассажиром (ADR-059 = не раскрывать; «Вариант A» решён продукт-овнером, но не реализован и не прошёл архитектуру)
- AI-канал обратной связи / support (только черновики-анализы: `PIOS_SUPPORT_AND_CONTACT_CHANNEL_ROADMAP.md`, `PIOS_AI_FEEDBACK_INTELLIGENCE_ARCHITECTURE_ANALYSIS.md`)

---

## Источники (прочитано для аудита; ничего не изменено)

**Конфигурация / деплой:** `backend/settings.gradle.kts`; все `backend/*/src/main/resources/application.yml`; `ai-advisor/src/main/resources/application.yml`; все `backend/*/src/main/resources/db/migration/**/*.sql`; `windows-services/` (листинг); VPS `systemctl list-units --type=service | grep pios`, `/opt/pios/` (листинг), `/etc/systemd/system/pios-dispatch.service`, `/etc/systemd/system/pios-frontend.service`; `windows-services/dispatch/pios-dispatch.xml`, `windows-services/frontend/pios-frontend.xml`; `nslookup piosapp.ru`.

**Domain:** `network-management/{Person, PersonProfile, Connection, Invitation, ProfileType, ConnectionType, InvitationStatus}`; `order-management/{Order, OrderStatus, OrderOrigin, OrderSubmitted}`; `driver-management/{Driver, Availability}`; `dispatch/{Proposal, ProposalStatus, Assignment, AssignmentStatus, Trip, TripStatus, OrderReference, DriverReference, PassengerReference}`; `identity/{Identity, Credential, Session, Device, VerificationChallenge}`; `passenger-experience/{Connection, PassengerReference, PrimaryConnectionDesignated}`.

**API:** `PersonController`, `ProfileController`, `ConnectionController` (network), `InvitationController`, `IdentityController`, `DriverController`, `OrderSubmissionController`, `OrderQueryController`, `OrderCancellationController` (по grep), `ProposalController`, `AssignmentController` (по grep), `passenger-experience/ConnectionController`, `HealthController`.

**Application / persistence:** `FirstRefusalApplicationService`, `DispatchAssignmentApplicationService`, `ProposalAssignmentOrchestrationService`, `SetPrimaryConnectionApplicationService`, `OrderSubmittedFirstRefusalListener`, `driver-management/AssignmentCompletedListener`, `order-management/AssignmentCompletedListener`, `dispatch/persistence/PostgreSQLTestDatabase.kt`; листинги `dispatch/application/*`, `passenger-experience/application/*`, `ai-advisor/**`; `ai-advisor/domain/AIProvider.kt`.

**Frontend:** `frontend/src/app/{App, routes}.tsx`; `frontend/src/pages/MyDrivers/MyDrivers.tsx`; листинг `frontend/src/**`.

**Cross-reference (не источник фактов, только сверка расхождений):** `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` §14; `docs/PIOS_REALITY_AUDIT.md` §19; `docs/SPRINT_PILOT_BLOCKERS.md` P0-1; `docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` §9; `docs/ADR/ADR-054`, `ADR-056`, `ADR-059`, `ADR-062`, `ADR-063`, `ADR-066`.

## Изменённые файлы

`docs/PIOS_CURRENT_STATE_AUDIT_2026-09-10.md` (новый). Код, схема БД, миграции, конфигурация, deployment, production — **не затронуты**. `network-management` deployment **не создавался**.
