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

---

## Traceability

Каждая запись `Доказательства` ссылается на `PIOS_PRODUCT_EVIDENCE.md`; наблюдения, которые не дотягивают до полноценной гипотезы (мелкие формулировки, полировка интерфейса), уходят в `PIOS_UX_BACKLOG.md`, не сюда.
