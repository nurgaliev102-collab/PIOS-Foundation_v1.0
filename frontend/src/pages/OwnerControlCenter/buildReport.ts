import type { ModuleHealth } from './healthPoll'
import type { OwnerStatusResult } from './statusEvaluation'
import type { TodaySnapshot } from './todayData'

/**
 * Builds the plain-text report `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md`
 * Section 5.6 specifies — the one place technical detail is allowed to
 * live (module names, timestamps, identifiers), because this text is
 * meant to be pasted to whoever fixes the problem, not read on the
 * screen. Contains **no money, no field, no line** — ADR-043 Decision 6
 * is unconditional and this function never reads `statedPrice` from
 * anywhere, since none of its own inputs ([ModuleHealth], [OwnerStatusResult],
 * [TodaySnapshot]) carry it in the first place.
 */
export function buildOwnerReport(
  statusResult: OwnerStatusResult,
  healths: ModuleHealth[],
  today: TodaySnapshot,
  now: Date
): string {
  const timestamp = now.toLocaleString('ru-RU')
  const timezoneOffsetMinutes = -now.getTimezoneOffset()
  const timezoneLabel = `UTC${timezoneOffsetMinutes >= 0 ? '+' : ''}${Math.round(timezoneOffsetMinutes / 60)}`

  const statusLine =
    statusResult.status === 'ok'
      ? 'РАБОТАЕТ'
      : statusResult.status === 'delayed'
        ? 'РАБОТАЕТ С ОГОВОРКОЙ'
        : statusResult.status === 'configuration'
          ? 'НУЖНА ПРОВЕРКА НАСТРОЕК'
          : statusResult.status === 'unreachable'
            ? 'НЕТ СВЯЗИ С ПУЛЬТОМ'
            : 'ТРЕБУЕТ ВНИМАНИЯ'

  const lines: string[] = []
  lines.push('=== PIOS — отчёт о состоянии ===')
  lines.push(`Дата и время: ${timestamp} (${timezoneLabel})`)
  lines.push('Отчёт составлен: Owner Control Center v1.0')
  lines.push('')
  lines.push('--- ЧТО ПРОИСХОДИТ ---')
  lines.push(`Состояние: ${statusLine}`)
  if (statusResult.problemLines.length > 0) {
    statusResult.problemLines.forEach((line) => lines.push(`Проблема: ${line}`))
  }
  if (statusResult.delayLine) {
    lines.push(`Задержка: ${statusResult.delayLine}`)
  }
  lines.push('')
  lines.push('--- СОСТОЯНИЕ СЛУЖБ ---')
  for (const health of healths) {
    const statusWord = health.outcome === 'up' ? 'OK' : health.outcome === 'down' ? 'БАЗА НЕДОСТУПНА' : health.outcome === 'unauthorized' ? 'ПАРОЛЬ НЕ ПРИНЯТ' : 'НЕТ ОТВЕТА'
    const outboxWord =
      health.outboxPending === null
        ? '-'
        : `очередь ${health.outboxPending}` +
          (health.outboxOldestAgeSeconds ? `, старейшая ${Math.round(health.outboxOldestAgeSeconds / 60)} мин` : '')
    lines.push(`${health.module.padEnd(22)} ${statusWord.padEnd(18)} ${outboxWord}`)
  }
  lines.push('')
  lines.push('--- СЕГОДНЯ ---')
  lines.push(`Водителей: ${today.counters.driversTotal}, на линии: ${today.counters.driversAvailable}`)
  lines.push(
    `Заказов создано: ${today.counters.ordersCreated} ` +
      `(выполнено ${today.counters.ordersCompleted}, в работе ${today.counters.ordersInProgress}, отменено ${today.counters.ordersCancelled})`
  )
  lines.push('')
  lines.push('--- ПОСЛЕДНИЕ СОБЫТИЯ ---')
  if (today.events.length === 0) {
    lines.push('Событий пока нет.')
  } else {
    today.events.slice(0, 20).forEach((event) => {
      const eventTime = new Date(event.at).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
      lines.push(`${eventTime}  ${event.text}`)
    })
  }
  lines.push('')
  lines.push('--- ТЕХНИЧЕСКИЕ ДЕТАЛИ ДЛЯ CLAUDE ---')
  lines.push(`Адрес: ${window.location.origin}`)
  lines.push(`Устройство: ${navigator.userAgent}`)
  lines.push('=== конец отчёта ===')

  return lines.join('\n')
}
