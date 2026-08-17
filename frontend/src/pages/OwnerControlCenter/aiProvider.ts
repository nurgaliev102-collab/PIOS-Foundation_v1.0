import {
  calculateAcceptanceRate,
  calculateCancellationRate,
  calculateCompletionRate,
  type PilotAnalyticsInput,
} from './pilotAnalytics'

/**
 * AI Analyst's own provider boundary — the frontend-only precursor to
 * `docs/ADR/ADR-056-Control-Center-AI-Advisor.md`'s own (still Proposed,
 * not Accepted) `AIProvider` interface. Named and shaped to be a plausible
 * stepping stone toward that ADR's `AIProvider.ask` once it is ratified
 * and a real backend exists behind it — but `analyze` here never leaves
 * the browser: [MockAIProvider] is the only implementation, and it is pure,
 * synchronous rule evaluation over already-fetched numbers, not a network
 * call. Swapping in a real provider later means adding a new class that
 * implements this same interface and pointing [getActiveAIProvider] at it —
 * this file's own callers (`AIAnalystCard.tsx`) do not change.
 */

export type PilotAnalysisStatus = 'ok' | 'attention' | 'critical' | 'unknown'

export interface PilotAnalysisMetricsSummary {
  acceptanceRate: number | null
  completionRate: number | null
  cancellationRate: number | null
}

export interface PilotAnalysisResult {
  status: PilotAnalysisStatus
  summary: string
  keyFindings: string[]
  risks: string[]
  recommendations: string[]
  metrics: PilotAnalysisMetricsSummary
  generatedAt: string
  providerName: string
}

export interface AIProvider {
  readonly name: string
  analyze(input: PilotAnalyticsInput): Promise<PilotAnalysisResult>
}

function formatPercent(rate: number | null): string {
  return rate === null ? 'нет данных' : `${Math.round(rate * 100)}%`
}

function formatMinutes(value: number | null): string {
  return value === null ? '—' : `${Math.round(value)} мин`
}

/** A cancellation rate at or above this is graded `critical` on its own — see {@link MockAIProvider.analyze}. */
const CRITICAL_CANCELLATION_RATE = 0.5
/** A cancellation rate at or above this (but below the critical threshold) contributes to `attention`. */
const ATTENTION_CANCELLATION_RATE = 0.2

/**
 * Deterministic, rule-based analysis over already-computed metrics — never
 * a real language model, and never pretends to be one (this task's own
 * explicit requirement: no random text, no fabricated LLM-sounding prose).
 * Every sentence it produces is a template filled with a real number from
 * [PilotAnalyticsInput]; a metric this provider cannot compute is reported
 * as "нет данных", never invented (`calculateAcceptanceRate`/
 * `calculateCompletionRate`/`calculateCancellationRate` all already return
 * `null` rather than guess — see their own KDoc in `pilotAnalytics.ts`).
 *
 * Thresholds below ([CRITICAL_CANCELLATION_RATE], [ATTENTION_CANCELLATION_RATE])
 * are this MVP's own disclosed heuristic, not a ratified business rule —
 * exactly the kind of number `docs/PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`
 * expects real pilot data to eventually calibrate, not something this task
 * invents as a permanent policy.
 */
export class MockAIProvider implements AIProvider {
  readonly name = 'mock'

  async analyze(input: PilotAnalyticsInput): Promise<PilotAnalysisResult> {
    const acceptanceRate = calculateAcceptanceRate(input.proposals)
    const completionRate = calculateCompletionRate(input.orders)
    const cancellationRate = calculateCancellationRate(input.orders)
    const metrics: PilotAnalysisMetricsSummary = { acceptanceRate, completionRate, cancellationRate }

    if (input.orders.total === 0) {
      return {
        status: 'unknown',
        summary: 'Недостаточно данных для анализа — за наблюдаемый период не зафиксировано ни одного заказа.',
        keyFindings: [],
        risks: [],
        recommendations: ['Дождитесь первых заказов в системе и запустите анализ ещё раз.'],
        metrics,
        generatedAt: input.generatedAt,
        providerName: this.name,
      }
    }

    const keyFindings: string[] = [
      `Заказов всего: ${input.orders.total}, завершено: ${input.orders.completed} (completion rate ${formatPercent(completionRate)}).`,
      `Решённых предложений: ${input.proposals.accepted + input.proposals.declined + input.proposals.lapsed} ` +
        `(принято ${input.proposals.accepted}, отклонено ${input.proposals.declined}, просрочено ${input.proposals.lapsed}); ` +
        `acceptance rate ${formatPercent(acceptanceRate)}.`,
      `Водителей всего: ${input.drivers.total}, на линии сейчас: ${input.drivers.available}, ` +
        `получили хотя бы одно предложение: ${input.drivers.withActivity}.`,
    ]
    if (input.reactionTime.sampleSize > 0) {
      keyFindings.push(
        `Среднее время реакции водителя на предложение: ${formatMinutes(input.reactionTime.averageMinutes)} ` +
          `(медиана: ${formatMinutes(input.reactionTime.medianMinutes)}, на основе ${input.reactionTime.sampleSize} предложений).`
      )
    }

    const risks: string[] = []
    if (input.proposals.lapsed > 0) {
      risks.push(`Просроченных (lapsed) предложений: ${input.proposals.lapsed} — водители не ответили вовремя.`)
    }
    if (input.proposals.declined > 0) {
      risks.push(`Отклонённых предложений: ${input.proposals.declined}.`)
    }
    if (cancellationRate !== null && cancellationRate > 0) {
      risks.push(
        `Доля отменённых заказов: ${formatPercent(cancellationRate)} (${input.orders.cancelled} из ${input.orders.total}).`
      )
    }
    if (input.health.modulesTotal > 0 && input.health.modulesUp < input.health.modulesTotal) {
      risks.push(`Не все модули PIOS отвечают: ${input.health.modulesUp} из ${input.health.modulesTotal}.`)
    }
    if (input.drivers.total > 0 && input.drivers.withActivity === 0) {
      risks.push('Ни один водитель ещё не получил ни одного предложения.')
    }

    const recommendations: string[] = []
    if (input.proposals.lapsed > 0) {
      recommendations.push('Проверьте, все ли подключённые водители реально находятся на линии и получают уведомления.')
    }
    if (input.drivers.total > 0 && input.drivers.withActivity === 0) {
      recommendations.push('Убедитесь, что водители делятся своими персональными ссылками с клиентами.')
    }

    const isCritical =
      (input.health.modulesTotal > 0 && input.health.modulesUp < input.health.modulesTotal) ||
      (cancellationRate !== null && cancellationRate >= CRITICAL_CANCELLATION_RATE)
    const isAttention =
      input.proposals.lapsed > 0 ||
      input.proposals.declined > 0 ||
      (cancellationRate !== null && cancellationRate >= ATTENTION_CANCELLATION_RATE) ||
      (input.drivers.total > 0 && input.drivers.withActivity === 0)

    const status: PilotAnalysisStatus = isCritical ? 'critical' : isAttention ? 'attention' : 'ok'

    if (recommendations.length === 0) {
      recommendations.push(
        status === 'ok' ? 'Явных проблем не обнаружено — продолжайте наблюдение.' : 'Проверьте отмеченные риски вручную.'
      )
    }

    const summary =
      status === 'critical'
        ? 'Пилот требует немедленного внимания владельца.'
        : status === 'attention'
          ? 'Пилот работает, но есть моменты, на которые стоит обратить внимание.'
          : 'Пилот работает штатно, явных проблем не обнаружено.'

    return { status, summary, keyFindings, risks, recommendations, metrics, generatedAt: input.generatedAt, providerName: this.name }
  }
}

/**
 * Provider selection point (`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`
 * Section 8/14; ADR-056 Decision 3's "no caller-side knowledge of which
 * implementation is active"). Only `MockAIProvider` exists today —
 * intentionally, per this task's own explicit instruction not to call
 * DeepSeek or Claude yet, and not to read `DEEPSEEK_API_KEY` at runtime.
 *
 * TODO(future): `DeepSeekProvider` — a real implementation of [AIProvider]
 *   calling DeepSeek's chat completion API. Per ADR-056, that call must
 *   originate from a PIOS backend component, never the browser — this
 *   frontend file is not where it will live; it will replace this
 *   function's return value once that backend exists and ADR-056 (or its
 *   successor) is ratified.
 * TODO(future): `ClaudeProvider` — same prerequisite and same placement
 *   constraint as `DeepSeekProvider` above.
 */
export function getActiveAIProvider(): AIProvider {
  return new MockAIProvider()
}
