# PIOS UX Backlog

Status: Living list, not a spec, not a Product Decision, not a sprint commitment. This document exists to catch small, human-observed improvements to how PIOS *feels* to use, between the moment someone notices them (a founder conversation, a Pilot Review, a driver's offhand comment) and the moment there is enough evidence to justify actually building one. An item moving from here into a real sprint still requires the same discipline as everything else in this repository — reviewed, scoped, shown before commit — this document only prevents good observations from being lost in the meantime.

Nothing here is implemented. Nothing here is authorized. An item's presence in this list is not a promise it will ever be built.

---

## How items get promoted out of this backlog

1. An item is observed or proposed (a conversation, a Pilot Review, a support message).
2. It is recorded here, in the relevant section, in plain language — no ticket format required.
3. Before it becomes a sprint, it is checked against real user behavior where possible (a Pilot Review is the current mechanism for this) rather than promoted on assumption alone.
4. Promotion to a sprint follows this project's own existing discipline (smallest correct change, shown before commit, no invented business rules).

## Sprint 8.5 candidate — Emotional UX

Observed after Sprint 8's Pilot Review readiness assessment: Sprint 8 made PIOS *understandable*. The next open question is whether it also feels like *the passenger's own relationship with their driver*, not just a correctly-labeled form. Not yet implemented — proposed rewording only, pending what an actual Pilot Review shows:

- **Invitation headline.** Current: "Вас пригласил Артур." Candidate: "Артур будет рад видеть вас среди своих постоянных клиентов." or "Теперь вы можете быстро связаться с Артуром, когда понадобится поездка." — moves from a factual statement to relationship language.
- **Post-registration screen.** Current: "Добро пожаловать! Вы успешно подключены к PIOS." Candidate: "Всё готово! Теперь Артур сможет быстрее получать ваши заказы. Когда понадобится поездка — просто откройте PIOS."
- **Order-success screen — currently the biggest gap.** After submitting an order, the passenger sees the order confirmed and the proposal status, but nothing that reads as a moment of reassurance. Candidate screen:
  > 🚖 Заказ отправлен
  > Ваш заказ уже отправлен Артуру. Если он сможет выполнить поездку — вы получите подтверждение. Если нет — PIOS найдёт другого свободного водителя.
  > [Вернуться]
- **Driver's first-client moment.** Currently a connection is recorded silently. Candidate: a small celebratory acknowledgment the first time a driver's invitation is accepted —
  > 🎉 Поздравляем! У вас появился первый клиент в PIOS. Теперь он сможет оформлять поездки через вашу личную ссылку.

## Онбординг

- Анимации при переходах между экранами.
- Настоящие фотографии вместо аватара-заглушки (когда появится реальный способ их получить и хранить).
- Более понятный текст там, где реальные пользователи запнутся (источник — только наблюдение за живыми людьми, не предположение).
- Убрать лишние шаги там, где Pilot Review покажет, что они не нужны.

## Водитель

- Статистика (сколько заказов, сколько клиентов).
- Количество приглашённых клиентов.
- Последний заказ — быстрый доступ.
- Быстрые действия с главного экрана.

## Пассажир

- История поездок.
- Отметка "любимый водитель".
- Повторить прошлый маршрут в один клик.
- Избранные адреса.

## Доверие

- Отзывы о поездках.
- Значок "подтверждённый водитель".
- "Сколько лет вместе" — длительность отношений пассажира с водителем.
- "Сколько поездок совершили" — совместная история.

## Traceability

Originates from the founder's own Sprint 8 acceptance review — the observation that Sprint 8 solved *understanding* (Definition of Done) but not yet *feeling* (Sprint 8.5's own framing), plus a broader recognition that not every improvement worth remembering deserves its own sprint document immediately.
