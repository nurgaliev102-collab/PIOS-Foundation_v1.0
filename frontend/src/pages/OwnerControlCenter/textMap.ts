import type { PilotModule } from './moduleBaseUrls'

/**
 * The mandatory wording table, `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md`
 * Section 6: "Разработчик реализует именно её, а не придумывает
 * формулировки." Every string here is plain language — no "HTTP", no
 * status code, no "outbox", no "endpoint" (the table's own forbidden-words
 * list). `network-management` is not represented: it appears nowhere on
 * this surface (ADR-043).
 */
export const MODULE_UNREACHABLE_TEXT: Record<PilotModule['key'], string> = {
  'order-management': 'Новые пассажиры сейчас не могут оформить заказ.',
  dispatch: 'Водители сейчас не получают новые заявки и не могут завершить поездку.',
  'driver-management': 'Личные ссылки водителей не открываются — новый пассажир не увидит, к кому он попал.',
  'passenger-experience': 'Пассажир может оформить заказ, но не закрепляется за своим водителем.',
  identity: 'Новый водитель сейчас не сможет зарегистрироваться.',
}

/** Same wording used for a module that answered but reported its own database unreachable — the person-facing effect is identical to not answering at all. */
export const MODULE_DOWN_TEXT = MODULE_UNREACHABLE_TEXT

export const QUEUE_DELAY_TEXT: Partial<Record<PilotModule['key'], string>> = {
  dispatch: 'Заказы принимаются, но доходят до водителей с задержкой.',
  'order-management': 'Завершённые поездки отмечаются в заказах с задержкой.',
}
