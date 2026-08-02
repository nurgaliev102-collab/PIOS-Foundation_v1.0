# PIOS Product Evidence Log — Draft Backfill Entries (NOT RATIFIED)

> **STATUS: DRAFT — PENDING PRODUCT OWNER CONFIRMATION. NOT PART OF THE EVIDENCE LOG.**
>
> Nothing in this file is an entry in `PIOS_PRODUCT_EVIDENCE.md`. That log's own
> «Журнал доказательств» section remains empty (line 80) and is untouched by this
> document. These are *proposed* entries, drafted at the Product Owner's explicit
> instruction to backfill already-observed pilot findings that currently exist only
> as code comments, so the exact wording can be reviewed before it becomes the
> historical record.
>
> An entry becomes real only when the Product Owner copies it into
> `PIOS_PRODUCT_EVIDENCE.md` (or instructs that it be copied). Until then, no
> Sprint may cite any `E-NNN` below as satisfying that log's own gate (lines 86–92).
>
> Follows the entry format defined by `PIOS_PRODUCT_EVIDENCE.md` "Entry format"
> (lines 46–56): **ID / Дата / Участник / Наблюдение / Связанная гипотеза / Вывод**.
> No field is invented and no field schema is added.

---

## 0. The provenance problem, stated before anything else

This must be resolved by the Product Owner before any entry below is ratified,
because it is the one thing this document cannot resolve by inspection:

- **The ratified log says no pilot has happened.** `PIOS_PRODUCT_EVIDENCE.md`
  line 74: *«Реальный журнал ниже пуст: ни один пилот ещё не проводился.»*
  Line 80: *«Пока пусто — первая запись появится после первого сеанса
  `PIOS_PILOT_REVIEW_PROTOCOL.md`.»* `docs/README.md` line 202 repeats it:
  *"currently empty ... pending the first real `PIOS_PILOT_REVIEW_PROTOCOL.md`
  session."*
- **The code says a pilot did happen.** Fourteen comment sites across
  `frontend/src` and `backend/order-management` are labelled *"First-pilot
  feedback"*, *"First-user-test UX audit"*, *"Pilot UX audit"*, or *"Pilot
  readiness fix"*.

These two cannot both be literally true. Three readings are possible and this
document does **not** choose between them:

1. A real, unrecorded pilot/user session took place, and the log was simply never
   filled in. → The entries below are legitimate backfill.
2. "First-pilot feedback" means *feedback in preparation for* a pilot — an
   internal readiness audit by the team, not observation of an outside user. →
   Most or all of the entries below are not evidence and must not be logged.
3. Some sites are (1) and others are (2), which is what the differing labels
   suggest ("first-pilot **feedback**" vs. "pilot readiness **audit**").

Reading 3 is what the classification in Section 1 assumes, on the strength of the
wording alone. **Every drafted entry therefore carries an explicit ambiguity
marker on Дата, Участник, and session-vs-audit provenance.** No date, no name, no
participant count, and no session identifier appears in any source; none is
guessed here.

Also true, and load-bearing for how much weight these can carry:
`MVR_PILOT_FEEDBACK_TEMPLATE.md` — the form a real session was supposed to
produce — is an unfilled blank template. No filled instance of it exists anywhere
in the repository.

---

## 1. Source inventory — every pilot/observation-flavoured comment found

Re-derived by grep across `frontend/src`, `backend/**`, and `docs/**`; every line
number below was verified against the current file content.

### 1a. Claims of an observed fact — drafted as entries below

| # | Source | Claimed observation |
|---|---|---|
| A | `frontend/src/pages/DriverHome/DriverHome.tsx` lines 21–24 | Driver had to remember to tap «Обновить»; on a real shift that meant missed orders |
| B | `frontend/src/pages/RideRequest/RideRequest.tsx` lines 40–44 | Passenger had no way of knowing the driver accepted, short of the driver phoning |
| C | `frontend/src/persistence/localCurrentOrder.ts` lines 2–4 | Passenger reloaded after ordering and landed on a blank form, with no way back |
| D | `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt` lines 54–55 | A driver's order card showed no passenger name and no submission time |

Corroborating (non-independent) sites for the same four facts, listed so nobody
later mistakes repetition for multiple observations:

- A → `DriverHome.tsx` lines 255–258.
- B → `RideRequest.tsx` lines 96–101, 148–152, 342–345.
- C → `RideRequest.tsx` lines 96–101, 132–134.
- D → `DriverHome.tsx` lines 89–93; `RideRequest.tsx` lines 96–98;
  `api/OrderResponse.kt` lines 20–24; `api/SubmitOrderRequest.kt` lines 25–26;
  `application/SubmitOrderCommand.kt` lines 21–23;
  `persistence/PostgreSQLOrderRepository.kt` lines 91–94;
  `src/test/.../OrderTest.kt` line 79;
  `src/test/.../OrderSubmissionControllerTest.kt` line 61;
  `db/migration/ordermanagement/V7__add_passenger_name_and_created_at.sql`.

### 1b. Ambiguous — drafted but quarantined, needs a Product Owner ruling

| # | Source | Why ambiguous |
|---|---|---|
| E | `frontend/src/pages/RideRequest/RideRequest.tsx` lines 83–90 | Labelled *"First-user-test UX audit"* — the only label in the codebase asserting a **user test**, which would make it evidence. But its content contains no user behaviour at all: it records a code fact (a «Комментарий» field was collected and silently discarded) plus design reasoning about how that *would* read to someone. Nothing observable about a person appears. |

### 1c. Excluded — self-audit or design reasoning, not observation of a user

None of these records anything a person did. Each records a state of the code
found by the team looking at its own interface, plus a judgement about how it
would appear. Under `PIOS_PRODUCT_EVIDENCE.md` line 29 (*«only observed facts —
no interpretation ... What a participant *did* or *said verbatim*»*), none
qualifies.

| # | Source | What it actually records |
|---|---|---|
| F | `frontend/src/components/Spinner/Spinner.tsx` lines 8–14 | Loading states were plain text; judged an "unfinished prototype" signal a first-time user *would* get. Hypothetical user, not an observed one. |
| G | `frontend/src/pages/NotFound/NotFound.tsx` lines 6–12 | An unmatched URL rendered a blank white screen. Code fact found by inspection. |
| H | `frontend/src/components/QRCard/QRCard.tsx` lines 31–37 | The QR graphic was a placeholder encoding nothing. Code fact; "a real defect once this screen became the thing a driver hands a real client" is a forward-looking judgement. |
| I | `frontend/src/components/DriverCard/DriverCard.tsx` lines 7–14 | Driver Home showed a raw system id (`ILDAR001`) as headline. Code fact; explicitly attributed to "the pilot audit", not to a driver. |
| J | `frontend/src/components/DriverCard/DriverCard.tsx` lines 18–26 | Id shown as secondary text became a raw UUID after ADR-039. Explicitly "ADR-039 follow-up" — a document-driven finding. |
| K | `frontend/src/pages/DriverHome/DriverHome.tsx` lines 99–104 | `proposal.orderId` rendered verbatim. Explicitly an "`ARCHITECTURE_VERIFICATION_REPORT.md`-adjacent finding" — a document audit. |
| L | `frontend/src/pages/Coordinator/Coordinator.tsx` lines 72–78 | "deliberately kept this simple for the first pilot" — a forward-looking scoping decision, not a finding at all. |

---

## 2. Drafted entries

Numbering is proposed, not assigned. Because the ratified log is still empty, no
number has yet been consumed; if the Product Owner rejects any entry below, the
remainder should be renumbered **before** ratification, so that
`PIOS_PRODUCT_EVIDENCE.md`'s own "sequential, never reused or renumbered" rule
(line 50) begins from a clean, gapless sequence.

`Связанная гипотеза` is `—` on every entry, and that is a finding in itself, not
an omission: `PIOS_PRODUCT_HYPOTHESES.md` registers only H1 (will a driver send
his personal link) and H2 (does naming the inviting driver improve registration
completion). None of the four observations bears on either.

---

> **E-001** · Дата: **не зафиксирована в источнике** · Участник: **не
> зафиксирован** (роль: «водитель»; имя, количество участников и сеанс
> неизвестны)
>
> Наблюдение: водителю приходилось помнить, что нужно нажать «Обновить», чтобы
> увидеть новый заказ; на реальной смене это означало пропущенные заказы.
> Дословно, как записано в источнике (англ.): *"a driver had to remember to tap
> «Обновить» to see a new order — on a real shift that meant missed orders."*
> Источник дополнительно фиксирует, что интервал автообновления 2–5 секунд был
> **запрошен** (*"the requested 2-5s range"*) — то есть требование исходило от
> человека, а не было выбрано разработчиком.
>
> Связанная гипотеза: — (ни H1, ни H2 этого не касаются).
>
> Вывод: проблема в интерфейсе, не в ценностном предложении — водитель не
> отказывался от механизма, а не мог вовремя узнать о заказе.
>
> Источник: `frontend/src/pages/DriverHome/DriverHome.tsx` строки 21–24
> (подтверждающая реализация — строки 255–258).
>
> **⚠ Неоднозначность (не разрешена):** источник помечен «First-pilot feedback»,
> но не называет ни участника, ни дату, ни количество людей, ни сеанс. Формулировка
> «на реальной смене это означало пропущенные заказы» может быть как наблюдённым
> фактом, так и выводом команды. Раздел 0 этого документа — обязательное чтение
> перед ратификацией.

---

> **E-002** · Дата: **не зафиксирована в источнике** · Участник: **не
> зафиксирован** (роль: «пассажир»)
>
> Наблюдение: у пассажира не было способа узнать, что водитель принял заказ, —
> кроме звонка от самого водителя. Дословно (англ.): *"a passenger used to have
> no way of knowing the driver accepted their order short of the driver calling
> them."*
>
> Связанная гипотеза: —.
>
> Вывод: проблема в интерфейсе — заказ доходил до водителя, но результат не
> возвращался пассажиру внутри продукта; привычный канал (звонок) закрывал
> пробел вместо PIOS.
>
> Источник: `frontend/src/pages/RideRequest/RideRequest.tsx` строки 40–44
> (подтверждающие — строки 96–101, 148–152, 342–345).
>
> **⚠ Неоднозначность (не разрешена):** сформулировано как свойство системы («не
> было способа»), а не как действие конкретного человека. Наблюдал ли кто-то, что
> водитель действительно звонил, — из источника не следует. Участник, дата и
> количество случаев не зафиксированы.

---

> **E-003** · Дата: **не зафиксирована в источнике** · Участник: **не
> зафиксирован** (роль: «пассажир»)
>
> Наблюдение: пассажир, перезагрузивший страницу после оформления заказа,
> оказывался на пустой форме «Заказать поездку», без возможности вернуться к уже
> созданному заказу. Дословно (англ.): *"a passenger who reloaded the page after
> ordering landed back on a blank «Заказать поездку» form, with no way back to
> the order they had just placed."*
>
> Связанная гипотеза: —.
>
> Вывод: проблема в интерфейсе; отдельно ценно то, что пассажир **сам вернулся**
> к экрану после заказа — но это возвращение внутри одной сессии, а не
> добровольное повторное открытие продукта, о котором говорит раздел «Самый важный
> сигнал» (`PIOS_PRODUCT_EVIDENCE.md` строка 44), и толковать его как таковое
> нельзя.
>
> Источник: `frontend/src/persistence/localCurrentOrder.ts` строки 2–4
> (подтверждающие — `RideRequest.tsx` строки 96–101, 132–134).
>
> **⚠ Неоднозначность (не разрешена):** это самое конкретное поведенческое
> описание из всех четырёх (конкретное действие — перезагрузка — и конкретный
> результат). Но участник, дата и число случаев не зафиксированы; неизвестно
> также, был ли это внешний пассажир или член команды.

---

> **E-004** · Дата: **не зафиксирована в источнике** · Участник: **не
> зафиксирован** (роль: «водитель»)
>
> Наблюдение: карточка заказа у водителя не показывала ни имени пассажира, ни
> времени поступления заказа. Дословно (англ.): *"a driver's order card showed no
> passenger name and no submission time."*
>
> Связанная гипотеза: —.
>
> Вывод: проблема в интерфейсе — водитель видел факт заказа, но не видел, от кого
> и когда он пришёл.
>
> Источник:
> `backend/order-management/src/main/kotlin/com/pios/ordermanagement/domain/Order.kt`
> строки 54–55 (подтверждающие — `DriverHome.tsx` строки 89–93; `RideRequest.tsx`
> строки 96–98; `api/OrderResponse.kt` строки 20–24; `api/SubmitOrderRequest.kt`
> строки 25–26; `application/SubmitOrderCommand.kt` строки 21–23;
> `persistence/PostgreSQLOrderRepository.kt` строки 91–94;
> `V7__add_passenger_name_and_created_at.sql`).
>
> **⚠ Неоднозначность (не разрешена):** описывает состояние экрана, а не
> действие или реплику человека. Просил ли водитель эту информацию сам, или
> отсутствие заметила команда, — из источника не следует. Участник, дата и число
> случаев не зафиксированы.

---

### Quarantined — requires a Product Owner ruling before it is numbered at all

> **[E-005 — НЕ ПРИСВАИВАТЬ НОМЕР ДО РЕШЕНИЯ PO]** · Дата: не зафиксирована ·
> Участник: не зафиксирован
>
> Наблюдение (как записано): форма заказа собирала поле «Комментарий», которое
> никуда не отправлялось (`notes` оставалось только локальным). Дословно (англ.):
> *"this form used to also collect a «Комментарий» field that was never actually
> sent anywhere."*
>
> Связанная гипотеза: —.
>
> Вывод: **не формулируется** — см. ниже.
>
> Источник: `frontend/src/pages/RideRequest/RideRequest.tsx` строки 83–90.
>
> **⚠ Почему в карантине:** это единственное место в кодовой базе, где сказано
> «First-**user-test**» — то есть прямо заявлен пользовательский тест, что сделало
> бы запись доказательством. Но само содержание не описывает ни одного действия
> человека: это факт о коде плюс рассуждение о том, как это *читалось бы*
> («is worse than not asking at all — it reads as broken»). Включить это как
> наблюдение значило бы записать в журнал фактов проектное рассуждение; исключить
> — значило бы отбросить единственную прямую ссылку на пользовательский тест.
> Решение за Product Owner: **был ли реальный пользовательский тест, и наблюдал ли
> кто-то, как человек заполнял это поле?** Если да — запись переформулируется под
> то, что человек сделал. Если нет — запись не вносится вообще.

---

## 3. Sprint 5 — objective-to-evidence traceability

Sprint 5 objective as previously recommended: **"Honest order outcome and the
passenger's return path"**, in three parts.

| Sprint 5 scope item | Justifying evidence | Strength of the link |
|---|---|---|
| **(a) False-positive decline/lapse display** — a `DECLINED` or `LAPSED` proposal is rendered to the passenger as `✅ Водитель уведомлён о заказе. Он свяжется с вами, как только будет готов.` (`RideRequest.tsx` line 164 collapses every non-`ACCEPTED` status to `OPEN`; label at line 58) | **E-002** | **Partial.** E-002 observed the *absence* of any outcome signal. Sprint 5 (a) is about a *wrong* outcome signal — the residual of the same gap after the E-002 fix, but not itself observed. See Section 4. |
| **(b) No re-order path** — `localCurrentOrder.ts` exposes only `getCurrentOrderId`/`saveCurrentOrderId`, with no clear/reset; `RideRequest.tsx` lines 135–140 send the passenger to the `confirmed` step forever, including after `COMPLETED` | **E-003** | **Direct on the premise, partial on the need.** E-003 is the observed fact that a passenger comes back to this screen and expects to find state there. It does **not** observe anyone wanting a *second* order. |
| **(c) Unfulfillable re-proposal promise** — `PassengerLanding.tsx` line 35 tells the passenger *«PIOS поможет найти другого свободного водителя»*, while `attemptProposal` (`RideRequest.tsx` lines 254–267) only ever proposes to the one `driverCode`, and no re-proposal path exists anywhere | **NONE** | **No evidence.** See Section 4. |

Supporting-but-not-justifying entries: **E-001** and **E-004** are real entries
if ratified, but neither bears on Sprint 5's objective. Citing them for Sprint 5
would be stretching them, and this document does not.

---

## 4. Sprint 5 scope that still lacks supporting evidence

Stated plainly rather than by stretching an entry:

1. **(c) The unfulfillable re-proposal promise has no evidence at all.** No
   observation records any passenger reading that FAQ line, expecting another
   driver, or being disappointed when none arrived. It is a genuine
   code-versus-copy inconsistency found by inspection — the same category as
   items F–L in Section 1c, which this document excluded from the log for exactly
   that reason. It should not be admitted to the log as evidence, and Sprint 5
   cannot cite an `E-NNN` for it.
2. **(a) The false-positive display was never observed.** The mis-rendering is a
   verifiable code fact, but no participant was ever observed being misled by it.
   E-002 covers the adjacent, earlier gap only.
3. **(b) No observed demand for a second order.** E-003 shows a passenger
   returning *within one order's lifetime*. Nothing observed shows a passenger
   wanting to place another. Note that `PIOS_PRODUCT_EVIDENCE.md` line 44 makes
   voluntary repeat use *the single most important signal* — so this is precisely
   the gap the project's own discipline says matters most, and precisely the one
   the current evidence cannot fill.

**Consequence for the gate.** Under `PIOS_PRODUCT_EVIDENCE.md` lines 86–92, a
Sprint needs at least one logged entry. If E-002 and E-003 are ratified, Sprint 5
parts (a) and (b) clear that bar — on partial links, disclosed above. Part (c)
does not clear it on any reading. Two honest options for (c), neither chosen
here: drop it from Sprint 5, or reduce it to a wording change registered in
`PIOS_UX_BACKLOG.md` (whose own promotion rule, lines 9–14, requires exactly this
kind of check against real behaviour before a backlog item becomes a sprint).

**Recommended next step, independent of any of the above:** run one real
`PIOS_PILOT_REVIEW_PROTOCOL.md` session. Every ambiguity in this document
dissolves the moment one session is observed and logged with a date and a
participant, and the resulting entries would be stronger than any backfill can be.

---

## Traceability

| This document | Source of authority |
|---|---|
| Entry field schema (ID/Дата/Участник/Наблюдение/Связанная гипотеза/Вывод) | `PIOS_PRODUCT_EVIDENCE.md` lines 46–56 |
| "Only observed facts", interface/value/business-model distinction | `PIOS_PRODUCT_EVIDENCE.md` lines 29, 34–40 |
| Voluntary repeat use as the strongest signal | `PIOS_PRODUCT_EVIDENCE.md` line 44 |
| Sprint gate ("минимум одно доказательство") | `PIOS_PRODUCT_EVIDENCE.md` lines 86–92 |
| Hypothesis register (H1, H2) referenced by `Связанная гипотеза` | `PIOS_PRODUCT_HYPOTHESES.md` lines 99–115 |
| Session mechanics an entry is supposed to originate from | `PIOS_PILOT_REVIEW_PROTOCOL.md` (Участники, Сценарии, «После пилота») |
| Where non-evidence UX observations belong instead | `PIOS_UX_BACKLOG.md` lines 9–14 |

This document creates no product rule, no architectural decision, and no ADR. It
changes no ratified file.
