> **STATUS: DRAFT / REVIEW REQUIRED**
> Это не архитектурное решение и не одобренная документация. Это находка (verification finding), полученная в ходе read-only проверки кода, предназначенная как основание для рассмотрения архитектором (ChatGPT) и продуктовым владельцем — не как готовый к использованию источник истины и не как разрешение начинать любую новую работу. Файл сознательно не закоммичен (см. `git status`) до explicit review.

# ARCHITECTURE_VERIFICATION_REPORT.md

Read-only повторная верификация (не доверяя четырём более ранним отчётам на слово), сфокусированная на трёх открытых вопросах Этапа 1 `DEVELOPMENT_ROADMAP.md` и на трёх заданных вопросах этого спринта. Каждое утверждение ниже подкреплено конкретным прочитанным файлом.

---

## Проверенные файлы

**Прочитаны полностью в рамках этого прохода** (в дополнение к четырём более ранним отчётам в этой же scratchpad-папке, которые использовались только как отправная точка, не как источник истины):

- `backend/order-management/src/test/kotlin/com/pios/ordermanagement/verification/MvpVerticalSliceScenarioTest.kt`
- `frontend/src/pages/PassengerLanding/PassengerLanding.tsx`
- `frontend/src/pages/PassengerLanding/invitationSource.ts`
- `frontend/src/pages/RideRequest/RideRequest.tsx`
- `frontend/src/pages/DriverHome/DriverHome.tsx`
- `frontend/src/pages/NetworkTest/NetworkTest.tsx` (частично, первые ~60 строк — достаточно для подтверждения назначения)
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/DispatchAssignmentApplicationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/application/ProposalAssignmentOrchestrationService.kt`
- `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/persistence/AssignmentAcceptedListener.kt`
- `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/OrderStatus.kt`
- `backend/network-management/src/main/kotlin/com/pios/networkmanagement/api/InvitationController.kt`
- `docs/PRODUCT_BASELINE_V2.md` (раздел 17 «Repository Impact Analysis», раздел 18, строки 140–217)
- `docs/PIOS_PRODUCT_EVIDENCE.md` (полностью)
- `docs/ADR/ADR-006-Documentation-Driven-Development.md`
- `docs/ADR/ADR-009-Domain-Isolation.md`
- `docs/ADR/ADR-017-Bounded-Context-Strategy.md`
- `backend/network-management/build.gradle.kts`
- `backend/driver-management/build.gradle.kts`
- `backend/passenger-experience/build.gradle.kts`
- `backend/order-management/build.gradle.kts`
- Файловые листинги: `backend/network-management/src/main/**` (полный), `backend/order-management/src/main/**/AssignmentAccepted*`, `backend/dispatch/src/main/**/*Topology*` и `*OutboxRelay*`
- `grep`-поиск по `backend/driver-management/src`, `backend/passenger-experience/src` на слово "invitation" (регистронезависимо)
- `grep`-поиск по `frontend/src` на слова "8085", "network-management", "Invitation"
- `git log`/`git merge-base` для установления хронологии коммита `docs/PRODUCT_BASELINE_V2.md` (`a9b90fa`, 2026-07-20 14:11) относительно коммита, добавившего outbox-запись Dispatch (`41b9b24`, 2026-07-20 17:45) и RabbitMQ-консьюмера `AssignmentAcceptedListener` (тот же коммит `41b9b24`)

**Ранее прочитанные отчёты, использованные только как ориентир**, не как источник истины: `PROJECT_ARCHITECTURE.md`, `FEATURE_INVENTORY.md`, `EXTENSION_POINTS.md`, `DEVELOPMENT_ROADMAP.md` (все — тот же scratchpad-каталог, не в `docs/`).

---

## Найденные реализации

### Вопрос 1 — цепочка Driver → Invitation → Passenger → Order → Proposal → Assignment

Обнаружена **реальная, задействованная в продакшн-коде фронтенда и бэкенда цепочка**, отличная и от того, что описывает `MvpVerticalSliceScenarioTest.kt`, и от наивного прочтения слова "Invitation" как ссылки на агрегат Network Management.

1. **Driver создаёт "приглашение"**: `DriverHome.tsx` строит ссылку `invitationLinkFor(driver.id)` (см. `currentDriver.ts`, не открывался построчно, но путь подтверждён импортом и использованием) и кладёт в `QRCard` (`linkTo="/i/${driver.id}"`). Никакого вызова `POST /v1/invitations` (Network Management) здесь нет — ссылка строится чисто из `DriverId`, полученного через `GET /v1/drivers/{driverId}` (Driver Management, порт 8081). Подтверждено прямым чтением `DriverHome.tsx` строк 201–242 и 279–297.
2. **Passenger переходит по ссылке и "регистрируется"**: маршрут `/i/:driverCode` → `PassengerLanding.tsx`. Разрешение приглашения идёт через `invitationSource.ts::getInvitationByDriverCode`, который **буквально вызывает `GET /v1/drivers/:driverId` (Driver Management)** и читает поле `displayName` — комментарий в файле (строки 4–13) прямо говорит: «resolves a real invitation through Driver Management's already-existing `GET /v1/drivers/:driverId` — the mocked `MOCK_INVITATIONS` dictionary this file used since Sprint 2 is gone... `driverCode` in the URL is the driver's own real `DriverId`, not a separate invitation code». Никакого обращения к Network Management (порт 8085) или к `InvitationController.kt` нет — подтверждено `grep`-поиском (`invitationSource.ts`/`PassengerLanding.tsx` не содержат ни "8085", ни "network-management", ни "Invitation" как имя эндпоинта).
3. **Passenger подтверждает регистрацию → создаётся Connection**: `PassengerLanding.tsx::handleNameSubmit` (строки 101–133) вызывает `POST /v1/connections` **на Passenger Experience (порт 8082, `PASSENGER_EXPERIENCE_BASE_URL`)**, не на Network Management. Это создаёт `Connection` из `passenger-experience` (passenger↔driver reference), отдельную от `Connection` из `network-management` (Person↔Person edge).
4. **Passenger создаёт Order**: `RideRequest.tsx::handleSubmit` (строки 97–124) вызывает `POST /v1/orders` на Order Management (порт 8083) — реальный, боевой эндпоинт `OrderSubmissionController.kt`.
5. **Order автоматически порождает Proposal**: сразу после успешного создания заказа `RideRequest.tsx::attemptProposal` (строки 133–146) вызывает `POST /v1/proposals` на Dispatch (порт 8084) с `{orderId, driverId: driverCode}` — тем самым водителем, чья ссылка привела пассажира. Это реальный вызов `ProposalController.createProposal` → `ProposalApplicationService.handle`.
6. **Proposal становится Assignment**: `DriverHome.tsx` показывает открытые Proposal'ы драйвера (`GET /v1/proposals?driverId=...`) и на "Accept" вызывает `POST /v1/proposals/{id}/accept`. На бэкенде это `ProposalController.acceptProposal` → `ProposalAssignmentOrchestrationService.acceptProposal` (файл прочитан полностью), которая внутри одной транзакции (ADR-036) принимает Proposal и вызывает `DispatchAssignmentApplicationService.handleWithinCallerTransaction(...)`, создавая реальный `Assignment` (статус `CREATED`) и публикуя `OrderAssigned` через outbox. Комментарий в `DriverHome.tsx` (строки 73–79) прямо подтверждает: «Accepting one creates the Assignment it precedes automatically, on the backend... this page does not call `/v1/assignments` itself».

**Важная находка, не отмеченная ни в одном из четырёх более ранних отчётов**: цепочка, которую реально использует фронтенд (Proposal → `ProposalAssignmentOrchestrationService` → `Assignment.CREATED`), — это **не тот же путь**, который проверяет `MvpVerticalSliceScenarioTest.kt`. Тест использует устаревший прямой путь `AssignOrderCommand` → `DispatchAssignmentApplicationService.handle(...)` (см. строки 136–150 теста), полностью минуя `Proposal`/`ProposalController`/`ProposalAssignmentOrchestrationService`. Тест также не идёт через `AssignmentAccepted` в продакшн-эквивалентном смысле — он вызывает `acceptAssignment` напрямую на объекте `Assignment`, а не через реальный HTTP-эндпоинт.

**Также важно**: даже в реальном (не тестовом) пути через `ProposalAssignmentOrchestrationService`, вызывается только `handleWithinCallerTransaction` (создание Assignment, `OrderAssigned`), но **не** `acceptAssignment` (перевод Assignment в `ACCEPTED`, публикация `AssignmentAccepted`). Отдельный эндпоинт `POST /v1/assignments/{id}/accept` (`AssignmentController.kt`) существует, но ничто во фронтенд-коде его не вызывает — он относится к «deprecated but not removed... kept for manual override» пути `Coordinator.tsx`. Значит: **`AssignmentAccepted` и его реальный, боевой RabbitMQ-консьюмер (`AssignmentAcceptedListener.kt`, подтверждено прочтением файла, находится в `src/main`, не только в `build/`) в продакшн-потоке фронтенда сегодня не срабатывают** — только Assignment-статус `CREATED` достигается автоматически.

### Аудит открытого вопроса (а) из `DEVELOPMENT_ROADMAP.md` Этап 1 — покрывает ли `MvpVerticalSliceScenarioTest.kt` цепочку REST → outbox → relay → RabbitMQ → consumer «в одном тесте»

**Ответ: НЕТ, не покрывает.** Прочитан файл целиком (179 строк). Факты:

- Тест **не делает ни одного HTTP-вызова** — все шаги вызывают Kotlin-классы application-слоя напрямую (`OrderSubmissionRequestHandler.handle(...)`, `DriverAvailabilityApplicationService.handle(...)`, `DispatchAssignmentApplicationService.handle(...)` и т.д.), а не `OrderSubmissionController`/`AssignmentController`/`ProposalController`.
- Тест **не использует outbox** — ни один из сервисов конструируется с реальным `outboxRepository`; все используют дефолт `NoOpOutboxRepository`/`NoOpTransactionRunner` (нигде явно не передаётся `OutboxRepository`, конструкторы вызываются с одним аргументом — репозиторием).
- Тест **не использует RabbitMQ** — нет упоминания `RabbitTemplate`, `@RabbitListener`, топологии, брокера. "Публикация" события — это просто вызов `publisher.toContractPayload(event)`, преобразующий доменное событие в DTO в памяти, и немедленная передача этого DTO в обработчик другого модуля, тоже в памяти, в одном тестовом методе.
- Собственный KDoc теста (строки 36–84) сам это признаёт: «This is a verification scenario only (ADR-027) — it creates no production orchestration»; «Every value that actually crosses from one module's output into another module's input does so only through the already-established ADR-027 primitive contract payloads and handlers».

Вывод: это тест **контрактных границ между модулями на уровне application-сервисов в памяти**, а не сквозной интеграционный тест REST→outbox→relay→RabbitMQ→consumer. Такой сквозной тест (проходящий через реальный HTTP, реальный Postgres outbox, реальный relay-scheduler и реальный RabbitMQ) в репозитории **не найден** в рамках этой верификации (не гарантирует полного отсутствия — не выполнялся полнотекстовый поиск по всем `*IntegrationTest.kt` файлам во всех пяти модулях для явного исключения, см. «Риски» ниже).

### Аудит открытого вопроса (б) — связь `PassengerLanding.tsx`/`invitationSource.ts` с Network Management's `Invitation`

**Ответ: НЕТ связи. Полностью подтверждено, не «неясно».** См. пункты 1–3 выше. `invitationSource.ts` явно и осознанно **не использует** `network-management`'s `Invitation`-агрегат; вместо этого симулирует «приглашение» через уже существующий `GET /v1/drivers/:driverId`, где `driverCode` в URL — это буквально `DriverId`, не код приглашения. Собственный комментарий файла подтверждает это как намеренное дизайн-решение Sprint 7B, а не пробел. Реальный `InvitationController.kt` (Network Management, порт 8085, `POST /v1/invitations`, `POST /v1/invitations/{code}/accept`) существует, реализован, но не вызывается никаким production-кодом фронтенда — только `NetworkTest.tsx` вызывает Network Management (создание Person, Connection), и `NetworkTest.tsx` сам себя описывает как «Not a real product screen: a minimal, internal page... per this sprint's own scope», не использует `Invitation`-эндпоинты вовсе (только Person/Connection, судя по прочитанным первым 60 строкам и списку интерфейсов `PersonRecord`/`ConnectionListItem`).

### Аудит открытого вопроса (в) — расхождение кода Dispatch и `PRODUCT_BASELINE_V2.md` раздела 17

**Расхождение подтверждено, и хронология установлена однозначно через `git merge-base --is-ancestor`.**

- `docs/PRODUCT_BASELINE_V2.md` — коммит `a9b90fa`, 2026-07-20 14:11:19+03:00, «Product Baseline v2.0 after CORE validation». Раздел 17 (строки 170–171) утверждает: `OrderAssigned` — «not yet published via outbox/RabbitMQ — only a contract-verification stub... exists», и `AssignmentAccepted` — «MISSING (implementation)... no listener/topology file found in `order-management`».
- Код, который эти утверждения опровергает — коммит `41b9b24`, 2026-07-20 17:45:20+03:00, «Tranche 1: Dispatch Event Publishing Completion» — вносит и `outboxRepository.save(outboxRecordFor(...))` в `DispatchAssignmentApplicationService.kt` (обе перегрузки, для `OrderAssigned` и `AssignmentAccepted`), и файл `AssignmentAcceptedListener.kt` (реальный `@RabbitListener` в `order-management/src/main`, коммит `b794d8f` того же дня добавил его в Dispatch-эквивалентную форму, `41b9b24` подтверждён как коммит "Tranche 1" завершения).
- `git merge-base --is-ancestor a9b90fa 41b9b24` подтверждает: `a9b90fa` (документ) — предок `41b9b24` (код), то есть **документ определённо старше кода на ~3.5 часа в тот же день**, а не наоборот.

**Вывод**: `docs/PRODUCT_BASELINE_V2.md` раздел 17 объективно устарел относительно текущего кода. Код опережает документ — это не «расхождение непонятно в чью пользу», а прямо подтверждаемый факт: документ был написан до того, как эта функциональность была реализована, и с тех пор не обновлялся. И `OrderAssigned`-публикация, и `AssignmentAccepted`-консьюмер сегодня существуют как реальный, рабочий, боевой код — но (см. Вопрос 1 выше) `AssignmentAccepted` в реальном фронтенд-потоке никогда не публикуется, потому что ничто не вызывает `acceptAssignment` после `ProposalAssignmentOrchestrationService`. Так что документ неверен насчёт «код не существует», но и наивное «раз код существует, значит фича полностью работает end-to-end с фронтенда» тоже неверно — реальный триггер (Accept Assignment) не задействован продакшн-потоком.

### Вопрос 2 — где живёт "Driver invites Passenger" в коде сегодня

Изолированность `network-management` **подтверждена напрямую**, не только по комментарию в `build.gradle.kts`:

- `network-management/build.gradle.kts`: только `spring-boot-starter-web`, JDBC/Flyway/Postgres — **нет** `spring-boot-starter-amqp`, нет `testImplementation(project(...))` какого-либо другого модуля. Полностью прочитан.
- `driver-management/build.gradle.kts`, `passenger-experience/build.gradle.kts`, `order-management/build.gradle.kts`: полностью прочитаны. Ни один не содержит `network-management` ни в production-, ни в test-scope зависимостях.
- Полный листинг `backend/network-management/src/main/**`: ни `Outbox*`, ни `RabbitMQ*`-файлов нет (есть `TransactionRunner.kt`/`NoOpTransactionRunner.kt`/`SpringTransactionRunner.kt` — но это just for local Postgres-транзакции, не для событий).
- `grep -rli "invitation"` по `driver-management/src` и `passenger-experience/src` **не дал ни одного совпадения** — ни в продакшн-, ни в тестовом коде.

**Вывод — "Driver invites Passenger" как реальная связь через агрегат `Invitation` (Network Management) НЕ существует в коде.** То, что реально работает под этим названием в продукте — это описанная в Вопросе 1 отдельная, самодельная механика: ссылка `/i/{driverId}` (просто DriverId в URL) + `GET /v1/drivers/{id}` (Driver Management) для отображения имени + `POST /v1/connections` (Passenger Experience, не Network Management) для записи факта знакомства. Она полностью не пересекается с `network-management`'s `Person`/`PersonProfile`/`Connection`/`Invitation`, которые существуют, реализованы (`InvitationController.kt`, `CreateInvitationApplicationService.kt`, `AcceptInvitationApplicationService.kt` — все прочитаны или подтверждены листингом), но не вызываются никаким production-путём, только `NetworkTest.tsx`, которая сама себя определяет как внутреннюю тестовую страницу, не продуктовый экран.

### Вопрос 3 — архитектурные ограничения перед следующими фичами

Из прямого чтения:

- **ADR-006 (Documentation-Driven Development)**: «Implementation of any capability does not begin until documentation describing that capability exists at every layer... A change that introduces implementation without a corresponding, already-existing document is not accepted. Documentation gaps discovered during implementation are treated as documentation defects to be resolved before implementation continues» (строка 17). Статус ADR — **"Proposed"**, не "Accepted"/"Ratified" (проверено прямым чтением заголовка «## Status» → «Proposed»), что само по себе не отменяет применимость (по правилам CLAUDE.md всё равно действует как governing rule), но стоит зафиксировать точно: формальный статус документа не "Accepted".
- **ADR-009 (Domain Isolation)**: «everything within [a domain] — its internal reasoning, its owned data..., and its internal logic — is accessible to other domains only through that domain's declared interaction style... or the events it publishes... No domain shares internal logic or internal representations with another domain outside those declared means» (строка 17). Тоже статус **"Proposed"**.
- **ADR-017 (Bounded Context Strategy)**: называет восемь контекстов (Order Management, Dispatch, Driver Management, Passenger Experience, Payments, Administration, Analytics, Notifications); «This ADR names the contexts only; it does not define any entity, aggregate, class, or relationship within or between them» (строка 13). Статус тоже **"Proposed"**. (Девятый контекст, Network Management, добавлен позже ADR-037 — не перепроверялся в этом проходе; ранее подтверждено `PROJECT_ARCHITECTURE.md`.)
- **`docs/PIOS_PRODUCT_EVIDENCE.md`**: журнал доказательств (раздел «Журнал доказательств», строка 78–80) буквально пуст: «Пока пусто — первая запись появится после первого сеанса `PIOS_PILOT_REVIEW_PROTOCOL.md`». Правило готовности к спринту (строки 86–90): «Перед началом любого нового Sprint — один обязательный вопрос: Какое доказательство из журнала делает этот Sprint необходимым? Если ответа нет — не начинаем.»
- **Границы модулей из build.gradle.kts (проверено во всех пяти файлах напрямую)**: ни один модуль не имеет production-зависимости на другой модуль; все межмодульные Gradle-зависимости — `testImplementation`, явно закомментированы как используемые только для контрактной верификации, явно исключены из деплоймого артефакта.

**Конкретные, применимые ограничения перед следующим Sprint:**

1. Никакая новая продуктовая функциональность не начинается без записи в `docs/PIOS_PRODUCT_EVIDENCE.md`, обосновывающей её необходимость (правило самого документа, строки 86–104) — журнал сегодня пуст, значит формально ни один новый Sprint не может начаться прямо сейчас.
2. Любое изменение, вводящее реальную связь между `network-management` и любым другим модулем (например, «настоящее» Driver→Passenger приглашение через `Invitation`), требует **новой ADR**, расширяющей ADR-037 — сам ADR-037 (не перечитывался в этом проходе целиком, но процитирован тремя более ранними отчётами и подтверждён отсутствием кода-связи здесь) явно резервирует это как «not authorized here».
3. Никакая новая production-зависимость Gradle между модулями (кроме `testImplementation` для контрактной верификации) не должна появляться без соответствующего архитектурного решения — сегодняшнее состояние (проверено во всех пяти `build.gradle.kts`) на 100% чистое, и это единственный технически проверяемый инвариант из всех.
4. Обновление `docs/PRODUCT_BASELINE_V2.md` раздела 17 (строки 170–171) требуется независимо от freeze — это фактическая документационная ошибка (документ утверждает отсутствие кода, которого объективно уже полтора года как нет), а не архитектурное решение; попадает под ADR-006's «Documentation gaps discovered... are treated as documentation defects to be resolved... not as license to proceed undocumented» — только в обратную сторону: здесь код опередил документ, а не документ разрешил implementation без документации, но принцип «single source of truth» всё равно нарушен, пока это не исправлено.
5. Любая работа над `OrderStatus`, добавляющая состояние «assigned»/«accepted», — это изменение уже ратифицированного жизненного цикла `Order` (см. `OrderStatus.kt`'s собственный KDoc, строки 8–11: «outside this task's scope; they are not modeled here»), не тривиальное расширение — требует явного решения архитектора, ADR-009's правило о недопустимости «изобретения» бизнес-семантики применимо буквально.

---

## Подтверждено / Не подтверждено

| Звено цепочки | Вердикт | Обоснование |
|---|---|---|
| Driver создаёт/отправляет "Invitation" | **ЧАСТИЧНО ПОДТВЕРЖДЕНО** | Реальный, рабочий механизм существует (`DriverHome.tsx` → `/i/{driverId}` ссылка), но это **не** агрегат `Invitation` из Network Management — это самодельная механика на основе `DriverId`. Формально работает, но не то, что подразумевает архитектурная документация Sprint 7A. |
| Passenger регистрируется через Invitation | **ЧАСТИЧНО ПОДТВЕРЖДЕНО** | `PassengerLanding.tsx`/`invitationSource.ts` реально разрешают ссылку и проводят онбординг (проверено построчно), но через `GET /v1/drivers/{id}` (Driver Management), не через `network-management`'s `InvitationController`. Работает как продукт, не как задокументированная архитектура Network Management. |
| Passenger создаёт Order | **ПОДТВЕРЖДЕНО** | `RideRequest.tsx::handleSubmit` → реальный `POST /v1/orders` → `OrderSubmissionController.kt` (Order Management). Прямой, боевой REST-вызов, без прослоек-заглушек. |
| Order производит Proposal | **ПОДТВЕРЖДЕНО** | `RideRequest.tsx::attemptProposal`, вызывается автоматически сразу после подтверждения заказа → реальный `POST /v1/proposals` → `ProposalController.createProposal` → `ProposalApplicationService.handle`. Не через Coordinator, не вручную — автоматически, в продакшн-потоке. |
| Proposal становится Assignment | **ПОДТВЕРЖДЕНО** (для статуса CREATED) / **НЕ ПОДТВЕРЖДЕНО** (для статуса ACCEPTED и события `AssignmentAccepted`) | `DriverHome.tsx`'s «Accept» → `POST /v1/proposals/{id}/accept` → `ProposalAssignmentOrchestrationService.acceptProposal` (прочитан полностью) реально создаёт `Assignment` (`CREATED`, публикует `OrderAssigned`). Но ничто во фронтенде не вызывает `acceptAssignment`/`POST /v1/assignments/{id}/accept` — так что `AssignmentAccepted` и связанный с ним рабочий консьюмер в `order-management` (`AssignmentAcceptedListener.kt`, реален и находится в `src/main`) в реальном пользовательском потоке никогда не срабатывают. |
| Q2: "Driver invites Passenger" через Network Management's Invitation | **НЕ ПОДТВЕРЖДЕНО** | Прямым чтением `invitationSource.ts` (комментарий в файле сам это признаёт), `grep`-поиском по `driver-management`/`passenger-experience` (ноль совпадений на "invitation"), и `grep`-поиском по `frontend/src` (только `NetworkTest.tsx`, не продуктовый экран, обращается к Network Management, и не к `Invitation`-эндпоинтам конкретно). `network-management` полностью изолирован — ни в одном `build.gradle.kts` пяти модулей нет ссылки на него ни в production, ни в test scope. |

---

## Риски

1. **Самый серьёзный архитектурный риск**: тот факт, что "Driver invites Passenger" реально работает в продукте, но не через задокументированный агрегат `Invitation`, создаёт две параллельные, несогласованные модели одного и того же продуктового понятия — одна в `network-management` (`Person`/`Connection`/`Invitation`, полностью реализована, но мертва по вызовам), другая — распределённая между `driver-management` (реальный `DriverId` как суррогат кода приглашения) и `passenger-experience` (`Connection` как факт знакомства). Любой, кто в будущем начнёт "достраивать" Network Management, предполагая, что фронтенд уже его использует, ошибётся и либо сломает реальный поток, либо продублирует его.
2. **`AssignmentAccepted` выглядит рабочим (есть реальный консьюмер, есть outbox-запись, есть тесты), но реальный пользовательский путь его не производит.** Кто-то, полагающийся на "Order Management знает, что заказ назначен" (например, для будущего статуса заказа, Этап 5 дорожной карты), обнаружит, что в проде это состояние никогда не наступает, потому что ничто не вызывает Accept Assignment после Proposal-потока — только устаревший, никем не используемый ручной путь через `Coordinator.tsx`/`AssignmentController`.
3. **`MvpVerticalSliceScenarioTest.kt` не является гарантией сквозной работоспособности REST→outbox→relay→RabbitMQ→consumer**, вопреки тому, что его название и расположение в `verification/`-пакете могут внушить читателю. Если кто-то планирует новую фичу, предполагая "у нас уже есть сквозной интеграционный тест этой цепочки", это предположение ложно — тест проверяет только контрактные границы application-сервисов в памяти.
4. **`docs/PRODUCT_BASELINE_V2.md` активно вводит в заблуждение** насчёт состояния Dispatch-публикации — если архитектор или продуктовый владелец примут решения на основе раздела 17 буквально, они будут планировать "инфраструктурную доработку", которая на самом деле уже сделана (хотя и не полностью задействована со стороны триггера).
5. Не проверено полным поиском по всем `*IntegrationTest.kt`/`*Test.kt` файлам во всех пяти модулей на предмет **действительно ли где-то существует настоящий сквозной REST→RabbitMQ тест** — есть шанс (не проверенный в этом проходе), что он существует под другим именем, не `MvpVerticalSliceScenarioTest.kt`. Это не меняет вывод про сам файл `MvpVerticalSliceScenarioTest.kt`, но означает, что заявление "сквозного теста вообще нет в репозитории" — не полностью проверено, только заявление про конкретно названный тестовый файл.

---

## Что нужно исправить перед следующим Sprint

По приоритету:

1. **Зафиксировать у архитектора/продуктового владельца: считается ли текущая "Driver invites Passenger" механика (DriverId-как-ссылка + Driver Management + Passenger Experience Connection) итоговой продуктовой моделью, или временным суррогатом, который должен быть заменён на настоящую интеграцию с `network-management`'s `Invitation`.** Это прямое архитектурное решение (по духу ADR-009/ADR-037), не техническая деталь — без него любое дальнейшее развитие либо приглашений, либо Network Management рискует либо закрепить суррогат навсегда, либо выбросить рабочий продуктовый поток ради недостроенной архитектуры.
2. **Обновить `docs/PRODUCT_BASELINE_V2.md` раздел 17** (строки 170–171), чтобы отразить, что `OrderAssigned`-публикация и `AssignmentAccepted`-консьюмер реализованы, но одновременно явно задокументировать, что реальный фронтенд-поток не достигает `AssignmentAccepted` (пункт 5 выше) — это документационный, не кодовый, фикс, разрешённый ADR-006 как "documentation defect" даже вне freeze.
3. **Явно решить, является ли отсутствие вызова Accept Assignment после Proposal-принятия намеренным продуктовым решением или незакрытым пробелом.** Если Order Management когда-либо должен узнавать "заказ назначен" (Этап 5 дорожной карты), сегодняшний код этого не достигает автоматически ни при каких действиях пользователя в проде.
4. **Явно переименовать или задокументировать, что `MvpVerticalSliceScenarioTest.kt` — это контрактный тест application-слоя, а не сквозной интеграционный тест**, либо дополнить его (или создать отдельный) реальным REST→outbox→relay→RabbitMQ→consumer тестом, если такая гарантия действительно нужна перед дальнейшим строительством на этой цепочке.
5. Всё это — по-прежнему верификационная/документационная работа, не новая функция. Согласно `docs/PIOS_PRODUCT_EVIDENCE.md`, ни один Sprint с новым продуктовым поведением не должен начинаться, пока журнал доказательств пуст, — этот пункт распространяется на любые дальнейшие действия сверх пунктов 1–4 выше.
