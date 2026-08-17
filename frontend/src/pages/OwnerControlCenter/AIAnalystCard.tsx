import { useState } from 'react'
import { ActionButton } from '../../components/ActionButton'
import type { ModuleHealth } from './healthPoll'
import type { OwnerCredential } from './ownerCredential'
import { collectPilotAnalyticsInput } from './pilotAnalytics'
import { AdvisorOutcomeError, getActiveAIProvider, type PilotAnalysisResult, type PilotAnalysisStatus } from './aiProvider'
import styles from './OwnerControlCenter.module.css'

const GENERIC_ERROR_MESSAGE = 'Не удалось связаться с сервером. Проверьте интернет и попробуйте ещё раз.'

export interface AIAnalystCardProps {
  credential: OwnerCredential
  healths: ModuleHealth[]
}

type AnalystState = 'idle' | 'loading' | 'error' | 'ready'

const STATUS_LABEL: Record<PilotAnalysisStatus, string> = {
  ok: 'НОРМАЛЬНО',
  attention: 'ВНИМАНИЕ',
  critical: 'КРИТИЧНО',
  unknown: 'НЕДОСТАТОЧНО ДАННЫХ',
}

const STATUS_CLASS: Record<PilotAnalysisStatus, string> = {
  ok: styles.aiStatusOk,
  attention: styles.aiStatusAttention,
  critical: styles.aiStatusCritical,
  unknown: styles.aiStatusUnknown,
}

function formatGeneratedAt(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' })
}

/**
 * "AI-анализ пилота" (`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`, first
 * technical stage — 2026-08-17). Explicitly owner-triggered: data
 * collection and analysis both fire only from [handleAnalyze], never on
 * mount and never on `OwnerControlCenter.tsx`'s own 15s poll — this card
 * holds no `useEffect`, no interval, no subscription of its own, so it
 * cannot contribute to that screen's own poll-overlap surface (the
 * 2026-08-17 incident `OwnerControlCenter.tsx`'s own KDoc documents).
 *
 * Rendered only while the platform is reachable at all (see
 * `OwnerControlCenter.tsx`'s own `statusResult.status !== 'unreachable'`
 * gate around this component, matching `TodayCard`/`EventFeed`'s existing
 * placement) — offering an analysis button PIOS cannot possibly answer
 * would be dishonest in exactly the way this screen's own design already
 * avoids elsewhere.
 */
export function AIAnalystCard({ credential, healths }: AIAnalystCardProps) {
  const [state, setState] = useState<AnalystState>('idle')
  const [result, setResult] = useState<PilotAnalysisResult | null>(null)
  // This task's own explicit requirement: "недостаточно данных ≠ AI
  // недоступен; AI недоступен ≠ ошибка PIOS; rate limit ≠ backend failure" —
  // one generic error message would not honestly distinguish these.
  // `AdvisorOutcomeError` (thrown by `BackendAIProvider` for any
  // `ai-advisor` outcome other than `"ok"`) already carries the backend's
  // own honest, specific message; anything else (network failure, wrong
  // owner credential, ai-advisor unreachable) falls back to one generic,
  // PIOS-side message. "Insufficient data" is not an error at all here —
  // it is `result.status === 'unknown'` inside a normal `'ready'` state,
  // rendered below exactly like any other status.
  const [errorMessage, setErrorMessage] = useState<string>(GENERIC_ERROR_MESSAGE)

  async function handleAnalyze() {
    setState('loading')
    try {
      const input = await collectPilotAnalyticsInput(credential, healths)
      const provider = getActiveAIProvider(credential)
      const analysis = await provider.analyze(input)
      setResult(analysis)
      setState('ready')
    } catch (error) {
      setErrorMessage(error instanceof AdvisorOutcomeError ? error.userMessage : GENERIC_ERROR_MESSAGE)
      setState('error')
    }
  }

  return (
    <section className={styles.aiAnalystCard}>
      <h2 className={styles.sectionTitle}>AI-анализ пилота</h2>

      <ActionButton
        label={state === 'loading' ? 'Анализируем…' : 'Проанализировать пилот'}
        variant="secondary"
        onClick={() => void handleAnalyze()}
        disabled={state === 'loading'}
      />

      {state === 'error' && (
        <p className={styles.aiError} role="alert">
          {errorMessage}
        </p>
      )}

      {state === 'ready' && result && (
        <div className={styles.aiResult}>
          <p className={`${styles.aiStatusBadge} ${STATUS_CLASS[result.status]}`}>{STATUS_LABEL[result.status]}</p>
          <p className={styles.aiSummary}>{result.summary}</p>

          {result.keyFindings.length > 0 && (
            <>
              <p className={styles.aiSubheading}>Ключевые наблюдения</p>
              <ul className={styles.aiList}>
                {result.keyFindings.map((finding) => (
                  <li key={finding}>{finding}</li>
                ))}
              </ul>
            </>
          )}

          {result.risks.length > 0 && (
            <>
              <p className={styles.aiSubheading}>Риски</p>
              <ul className={styles.aiList}>
                {result.risks.map((risk) => (
                  <li key={risk}>{risk}</li>
                ))}
              </ul>
            </>
          )}

          <p className={styles.aiSubheading}>Рекомендации</p>
          <ul className={styles.aiList}>
            {result.recommendations.map((recommendation) => (
              <li key={recommendation}>{recommendation}</li>
            ))}
          </ul>

          <p className={styles.aiSubheading}>Метрики</p>
          <dl className={styles.todayGrid}>
            <dt>Acceptance rate</dt>
            <dd>{result.metrics.acceptanceRate === null ? '—' : `${Math.round(result.metrics.acceptanceRate * 100)}%`}</dd>
            <dt>Completion rate</dt>
            <dd>{result.metrics.completionRate === null ? '—' : `${Math.round(result.metrics.completionRate * 100)}%`}</dd>
            <dt>Cancellation rate</dt>
            <dd>{result.metrics.cancellationRate === null ? '—' : `${Math.round(result.metrics.cancellationRate * 100)}%`}</dd>
          </dl>

          <p className={styles.aiTimestamp}>Время анализа: {formatGeneratedAt(result.generatedAt)}</p>
        </div>
      )}
    </section>
  )
}
