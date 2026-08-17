import { request } from '../../api/apiClient'
import { toBasicAuthorizationHeader, type OwnerCredential } from './ownerCredential'
import type { PilotAnalyticsInput } from './pilotAnalytics'

/**
 * AI Analyst's own provider boundary (ADR-056: AI Advisor for Owner
 * Control Center, Accepted 2026-08-17). `analyze` calls the real backend
 * component (`ai-advisor`, `POST /v1/advisor/analyze`) — the frontend-local
 * `MockAIProvider` this file used to hold (2026-08-17, "AI Analyst first
 * technical stage") has been moved server-side unchanged
 * (`ai-advisor/src/main/kotlin/com/pios/aiadvisor/domain/MockAIProvider.kt`,
 * same rules, same thresholds) so the analysis logic lives in exactly one
 * place — this file's own job is now only the HTTP call and the outcome
 * mapping, never the analysis itself.
 *
 * `AI_ADVISOR_BASE_URL` follows the same `VITE_*_BASE_URL` convention every
 * other module's base URL already uses (`moduleBaseUrls.ts`) — empty in
 * `.env.local` for the pilot's same-origin design, defaulting to
 * `http://localhost:8091` for local development against a directly-run
 * `ai-advisor`.
 */
const AI_ADVISOR_BASE_URL = import.meta.env.VITE_AI_ADVISOR_BASE_URL ?? 'http://localhost:8091'

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

/** `POST /v1/advisor/analyze`'s own response shape (`PilotAnalysisResponse` on `ai-advisor`, ADR-056 Decision 6). */
interface AdvisorAnalyzeResponse {
  outcome: string
  result: PilotAnalysisResult | null
  message: string | null
}

/**
 * Thrown by [BackendAIProvider.analyze] whenever `ai-advisor` answered with
 * an explicit non-`"ok"` [outcome] (`"provider_unavailable"`,
 * `"budget_exceeded"`, `"invalid_input"`) — distinct from a thrown
 * [ApiError][../../api/apiClient.ApiError] (network failure, wrong owner
 * credential, `ai-advisor` unreachable), so `AIAnalystCard.tsx` can show
 * the owner a specific, honest reason rather than one generic error for
 * every kind of "not analyzed" (this task's own explicit requirement:
 * "недостаточно данных ≠ AI недоступен; AI недоступен ≠ ошибка PIOS;
 * rate limit ≠ backend failure").
 */
export class AdvisorOutcomeError extends Error {
  readonly outcome: string
  readonly userMessage: string

  constructor(outcome: string, userMessage: string) {
    super(`AI Analyst outcome: ${outcome}`)
    this.name = 'AdvisorOutcomeError'
    this.outcome = outcome
    this.userMessage = userMessage
  }
}

/**
 * Calls the real `ai-advisor` backend (ADR-056 Decision 1: it never talks
 * to any domain module — the browser already computed [PilotAnalyticsInput]
 * before calling this). Sends the same owner `Authorization: Basic` header
 * `todayData.ts`'s own owner-authenticated calls already send (ADR-056
 * Decision 4 — a sixth replica of the same credential, not a new one).
 *
 * `outcome === "ok"` is the only case that returns a value; every other
 * outcome throws [AdvisorOutcomeError] carrying the backend's own honest,
 * non-technical `message` — `ai-advisor` never explains *why* in technical
 * terms (no raw provider status code, no exception text), by its own
 * design (ADR-056 Decision 10).
 */
export class BackendAIProvider implements AIProvider {
  readonly name = 'backend'
  private readonly credential: OwnerCredential

  constructor(credential: OwnerCredential) {
    this.credential = credential
  }

  async analyze(input: PilotAnalyticsInput): Promise<PilotAnalysisResult> {
    const response = await request<AdvisorAnalyzeResponse>('/v1/advisor/analyze', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: toBasicAuthorizationHeader(this.credential),
      },
      body: JSON.stringify(input),
      baseUrl: AI_ADVISOR_BASE_URL,
    })

    if (response.outcome !== 'ok' || !response.result) {
      throw new AdvisorOutcomeError(response.outcome, response.message ?? 'Не удалось выполнить анализ.')
    }
    return response.result
  }
}

/**
 * Provider selection point (`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`
 * Section 8/14; ADR-056 Decision 7's "no caller-side knowledge of which
 * implementation is active"). [BackendAIProvider] is the only frontend
 * implementation, and it stays the only one forever — a future
 * `DeepSeekProvider`/`ClaudeProvider` is a **backend** class implementing
 * `ai-advisor`'s own `AIProvider` (Kotlin) interface, per ADR-056 Decision
 * 1: the browser must never hold a provider credential. Nothing in this
 * frontend file changes when that happens; `ai-advisor` simply starts
 * returning `providerName: "deepseek"` instead of `"mock"` in the same
 * response shape this function's caller already handles.
 */
export function getActiveAIProvider(credential: OwnerCredential): AIProvider {
  return new BackendAIProvider(credential)
}
