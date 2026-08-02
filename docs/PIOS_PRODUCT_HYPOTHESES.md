# PIOS Product Hypotheses

Status: Living hypothesis log for the product team, not a spec, not an ADR, not a Product Decision. Nothing here is implemented by being written down. Its entire purpose is to force every future feature through one discipline: **a feature is built to test a stated hypothesis about real user behavior, or to fix a problem already observed in a real user — never because it seemed like a good idea.**

This complements, but is distinct from, `PIOS_UX_BACKLOG.md`: the backlog holds small, low-stakes wording/polish ideas; this document holds bets about whether a whole mechanism (an invitation flow, a screen, a feature) actually creates value for a real driver or passenger.

---

## The central question

Not "как сделать PIOS лучше?" — a question that assumes PIOS is already the right answer and only needs polish. The question every hypothesis here ultimately serves is harder:

> Почему человек захочет отказаться от своего сегодняшнего способа заказа такси?

The most dangerous mistake a startup can make is falling in love with its own solution rather than the problem. Being certain a driver's personal link is a great idea proves nothing on its own — only a real driver's own behavior does.

---

## The team rule

> Ни одна новая функция не попадает в продукт только потому, что она кажется хорошей. Она должна либо решать проблему, замеченную у реального пользователя, либо проверять заранее сформулированную гипотезу.

And the question that comes before every decision is not "нравится ли нам PIOS?" but:

> Какое доказательство у нас есть, что людям это нужно?

## The commitment, made in advance

If a pilot shows that drivers don't want to send the link, that passengers would rather just call, or that people see no difference between PIOS and an ordinary messenger — **we do not look for excuses.** We record the hypothesis as not confirmed and look for a different model. That is not a failure; it is months, possibly years, of work saved.

The reverse is equally binding: if drivers start sending links to new clients unprompted, if passengers come back on their own, if people recommend PIOS without being asked — that is stronger evidence than any presentation, and it is recorded here with the same rigor as a disconfirmation.

## Three ways a pilot answer can land

A hypothesis rarely comes back simply "yes" or "no" — recording only Подтверждена/Не подтверждена would lose the most useful case. Example, for H1 (the driver's personal link):

- **Подтверждается.** «Я сразу отправил ссылку десяти постоянным клиентам.» — a strong, unambiguous signal.
- **Подтверждается частично.** «Идея хорошая, но мне неудобно каждый раз объяснять людям, что это такое.» — the mechanism itself is not the problem; onboarding is. The hypothesis is not disconfirmed, but a *different* problem was just found and belongs in `PIOS_UX_BACKLOG.md` or as a new hypothesis of its own, not silently folded into H1's own result.
- **Не подтверждается.** «Мне проще сказать: "Позвони мне".» — valuable precisely because it forces the next real question: *why* does the existing way still win? That question, not the disappointment of a failed hypothesis, is what the team investigates next.

## Four fundamental questions behind every hypothesis

Beneath any specific hypothesis, a pilot is really testing four things, in this order — a later question is worth investigating only once the earlier ones hold:

1. **Есть ли проблема?** Do people actually feel the pain we're trying to solve at all?
2. **Наше решение лучше существующего?** Not "is it good" — is it better than a phone call, WhatsApp, or Telegram, specifically?
3. **Готовы ли люди менять привычку?** Even a genuinely better solution can still lose to an established habit.
4. **Возвращаются ли люди сами?** The strongest signal of all — one-time use without a self-motivated return means the value created so far is not yet enough.

## People are first users, not testers

We do not study what people say about PIOS. We study what they do. Someone can say the app is nice and never open it again; someone can say nothing and send their link to five clients that same day. Registration completed, link actually sent, a second order placed days later — these are the signals that answer whether PIOS is actually needed, not stated opinions.

The single most important question to ask, and the hardest to hide from:

> Если завтра PIOS исчезнет, тебе будет всё равно или ты расстроишься?

If someone would not care, we have not yet created value for them — no matter how politely they answered every other question.

---

## The cycle every feature now goes through

```
Гипотеза → Пилот → Наблюдение → Вывод → Только потом изменение продукта
```

Not: idea → build → improve. A feature idea is first rewritten as a falsifiable hypothesis before anything is built or kept.

## Hypothesis template

Every entry uses the same five fields:

- **Гипотеза** — the specific, falsifiable claim.
- **Почему мы так думаем** — the reasoning behind it, not evidence yet.
- **Как проверим** — the concrete, small-scale test (who, how many, what they're asked to do).
- **Критерий успеха** — a number or a specific observable behavior, decided before the test runs.
- **Доказательства** — the `E-NNN` entries from `PIOS_PRODUCT_EVIDENCE.md` this result is actually based on. Empty until observation begins.
- **Результат** — Подтверждена / Частично подтверждена / Не подтверждена / Ожидает проверки. Decided only from the accumulated evidence entries listed above, never from a single conversation — see that log's own discipline section.

## What we measure

Not only technical metrics — observed behavior, for both roles:

**Водитель:**
- Отправил ли он ссылку хотя бы одному клиенту без напоминания?
- Попросил ли он ещё одну ссылку?
- Вернулся ли в приложение на следующий день?

**Пассажир:**
- Закончил ли регистрацию?
- Сделал ли заказ?
- Сделал ли второй заказ через несколько дней?

---

## Hypothesis Log

### H1 — Личная ссылка побуждает водителя приглашать клиентов

- **Гипотеза.** Если водитель получит личную ссылку, он будет приглашать клиентов.
- **Почему мы так думаем.** Водитель хочет сохранить постоянных клиентов, а личная ссылка — самый простой способ дать им прямой доступ к себе, минуя чужую систему распределения заказов.
- **Как проверим.** Первые три водителя (Pilot Review Protocol v1.0).
- **Критерий успеха.** Не менее двух из трёх отправят ссылку минимум пяти клиентам.
- **Доказательства.** —
- **Результат.** Ожидает проверки.

### H2 — Явное указание «кто пригласил» повышает завершение регистрации

- **Гипотеза.** Если пассажир сразу поймёт, что его пригласил конкретный водитель, то вероятность завершения регистрации увеличится (по сравнению с безликой формой регистрации).
- **Почему мы так думаем.** Доверие к конкретному человеку выше, чем доверие к анонимному сервису — приглашение от Артура по имени должно снижать порог недоверия, который останавливает регистрацию в обычном приложении.
- **Как проверим.** Наблюдение за пассажирами в Pilot Review Protocol v1.0 — где именно они останавливаются, задают ли вопрос «кто это?».
- **Критерий успеха.** Ни один пассажир не спрашивает вслух и не выглядит растерянным на экране «Вас пригласил {имя}» — переход к регистрации происходит без подсказки.
- **Доказательства.** —
- **Результат.** Ожидает проверки.

### H3 — Видимая цена, названная водителем, снимает главную неопределённость заказа

- **Гипотеза.** Если пассажир видит цену, которую назвал водитель, он не станет выяснять её отдельно (звонком или сообщением) и не столкнётся с неожиданной суммой в момент поездки.
- **Почему мы так думаем.** Сегодня стоимость узнают устно — по телефону или уже при посадке. Это и есть главная неопределённость заказа даже у знакомого водителя: маршрут известен, сумма — нет. Мы предполагаем, что видимая заранее цена убирает лишний шаг и снимает эту неопределённость. Это рассуждение, а не доказательство: журнал `PIOS_PRODUCT_EVIDENCE.md` на 2026-08-01 пуст, и именно поэтому H3 — гипотеза, а не вывод.
- **Как проверим.** Driver MVP v1.1 показывает пассажиру цену, названную водителем, на экране подтверждённого заказа — после того как водитель принял заказ. Наблюдение за первыми тремя парами «водитель + пассажир» по `PIOS_PILOT_REVIEW_PROTOCOL.md`. Проверяется **только видимость**: пассажир видит предложенную цену; решение — вне PIOS, до появления отдельного механизма. Никакой кнопки, подтверждения или отказа в продукте нет, поэтому согласование цены здесь не проверяется и проверено быть не может.
- **Критерий успеха.** Ни один из первых трёх пассажиров не звонит и не пишет водителю, чтобы уточнить стоимость, после того как она появилась у него на экране; и ни один не говорит в разговоре после поездки, что сумма оказалась неожиданной.
- **Доказательства.** —
- **Результат.** Ожидает проверки.

> **Основание Sprint.** H3 — первая гипотеза, использованная как основание для Sprint по правилу, добавленному в `PIOS_PRODUCT_EVIDENCE.md` 2026-08-01: зарегистрированная гипотеза — допустимое основание, и такой Sprint **производит** доказательства, а не потребляет их. H3 зарегистрирована до начала Driver MVP v1.1. Статус остаётся «Ожидает проверки» до появления записей `E-NNN`: сам факт того, что под гипотезу сделан Sprint, её не подтверждает. Архитектурная часть — `docs/ADR/ADR-042-Stated-Ride-Price-Minimal-Model.md`, Amendment 2026-08-01 (round 3), R9/R10/R11.
>
> **Отдельно, чтобы это не потерялось при первом же пилоте:** если пассажир увидит цену и попытается отказаться, не найдя в PIOS такой возможности, — это **наблюдение, а не дефект**. Его место в `PIOS_PRODUCT_EVIDENCE.md`, и оно станет первым входом в будущее Product Decision **«Управление жизненным циклом заказа»** (ADR-042, Open Question 10). Чинить это внутри спринта, добавив кнопку, нельзя.

### H4 — Предприниматель готов платить за PIOS, если платформа помогает сохранять и развивать клиентский поток

**Исправлено 2026-08-01.** Предыдущая версия H4 проверяла удовлетворённость и удержание (те же поведенческие сигналы, что уже использует H1), а не готовность платить — совпадение с H1 отмечено при сведении документов в `PIOS_PRODUCT_STRATEGY_V1.md`. Ниже — исправленная версия: та же гипотеза, четыре собственных сигнала, ни один не заимствован у H1.

- **Гипотеза.** Предприниматель готов платить за PIOS, если платформа помогает ему сохранять и развивать собственный клиентский поток.
- **Почему мы так думаем.** `PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` фиксирует, что PIOS — это инфраструктура, которую предприниматель использует для своего бизнеса, а не платформа, распределяющая заказы от своего имени. Из этого следует, что ценность, за которую готовы платить, не может быть местом в очереди (`PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md`, Section 8: оплата не должна покупать приоритет — это ограничение не зависит от того, какая коммерческая модель будет в итоге принята) — она может быть только инструментом. Отправка ссылки и возврат в приложение (сигналы H1) показывают, что водителю не мешает пользоваться PIOS — но не показывают, что он заплатил бы за неё, если бы это было платно. Готовность платить — отдельное поведенческое утверждение и проверяется отдельно.
- **Как проверим.** Наблюдение за первыми водителями по `PIOS_PILOT_REVIEW_PROTOCOL.md`, после того как они провели хотя бы одну смену через личную ссылку, по четырём сигналам:
  1. **Намерение продолжить использование.** Водитель сам спрашивает про доступ к следующей смене или ссылку на неё — не только соглашается, когда ему предлагают.
  2. **Подключение собственных клиентов.** Отправляет ссылку реальному постоянному клиенту (не тестовому контакту, не одному из трёх участников пилота), без напоминания.
  3. **Готовность платить подписку.** Прямой вопрос после смены: «Ты бы платил за это каждый месяц?» — единственный из четырёх сигналов, основанный на словах, а не на действии; согласно правилу этого документа («Люди сначала пользователи, а не тестировщики») ответ фиксируется, но не считается сильнее сигналов 1, 2 и 4.
  4. **Понимание ценности.** Ответ на уже существующий в этом документе вопрос: «Если завтра PIOS исчезнет, тебе будет всё равно или ты расстроишься?» — засчитывается только содержательная причина, связанная с сохранением или ростом собственного клиентского потока, а не общая вежливость.
- **Критерий успеха.** У не менее чем двух из первых трёх водителей — все четыре сигнала одновременно: оба поведенческих сигнала (1 и 2) и содержательные ответы на оба вопроса (3 и 4). Один сигнал у одного водителя, или все четыре у разных водителей, не подтверждает гипотезу.
- **Доказательства.** —
- **Результат.** Ожидает проверки.

---

## Traceability

Каждая запись `Доказательства` ссылается на `PIOS_PRODUCT_EVIDENCE.md`; наблюдения, которые не дотягивают до полноценной гипотезы (мелкие формулировки, полировка интерфейса), уходят в `PIOS_UX_BACKLOG.md`, не сюда.
