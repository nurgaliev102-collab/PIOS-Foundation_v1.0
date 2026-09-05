package com.pios.aiadvisor.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pios.aiadvisor.api.PilotAnalysisMetricsDto
import com.pios.aiadvisor.api.PilotAnalysisRequest
import com.pios.aiadvisor.api.PilotAnalysisResultDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import kotlin.math.roundToInt

/**
 * PIOS Intelligence — real hosted Qwen provider, via Alibaba Cloud
 * DashScope's OpenAI-compatible `POST /chat/completions` endpoint (base URL
 * already includes `/compatible-mode/v1`, so no `/v1` is repeated in the
 * request path here — matches the real, documented DashScope compatibility
 * surface, not a guessed one). Selected only when
 * `pios.ai-advisor.provider=qwen`; [MockAIProvider] stays the default
 * (`matchIfMissing = true` on its own `@ConditionalOnProperty`) precisely so
 * the existing pilot never depends on a funded Qwen/DashScope account
 * existing — this task's own explicit requirement.
 *
 * Mirrors [DeepSeekProvider] structurally and behaviorally, **not**
 * [OllamaProvider]: Qwen here is a paid, hosted cloud API (DashScope), the
 * same operating model DeepSeek has — so, like DeepSeek and unlike the
 * local/unauthenticated Ollama case, a missing API key fails closed rather
 * than sending an unauthenticated request. The API key is read only from
 * Spring configuration (`pios.ai-advisor.qwen.api-key`, this task's own
 * explicit requirement) — Spring Boot already lets a deployment override
 * any configuration property via an environment variable
 * (`PIOS_AI_ADVISOR_QWEN_API_KEY`, its own standard relaxed-binding rules)
 * without this class reading `System.getenv()` itself, and never accepts a
 * key from a request or any other source.
 *
 * This task's own explicit constraints: [AIProvider] itself is untouched —
 * this class only implements it, the same way [MockAIProvider]/
 * [DeepSeekProvider]/[OllamaProvider] already do; `AdvisorController` and
 * the frontend are untouched — this is a new, additive `@Component` only
 * selected when `provider=qwen`, invisible to every existing caller.
 *
 * **What this class never does, structurally, not by care alone** (the same
 * guarantees [DeepSeekProvider]/[OllamaProvider]'s own KDoc already state):
 * - Never calls any of the five PIOS domain modules — it has no `RestClient`
 *   pointed at any of them, only [qwenRestClient].
 * - Never logs the API key, the request body, or the response body — every
 *   log line below names a [FailureReason] and, where relevant, a raw HTTP
 *   status code, never text that could carry a secret or the model's own
 *   free-form output.
 * - Never returns the API key, or anything derived from it, in its own
 *   [AIProviderOutcome].
 * - Never retries automatically — one call, one outcome, every time.
 *
 * **Trust boundary, identical to [MockAIProvider]/[DeepSeekProvider]/
 * [OllamaProvider]:** the three rate metrics ([PilotAnalysisMetricsDto]) are
 * computed here, locally, from [PilotAnalysisRequest]'s own numbers — never
 * asked of, or trusted from, the model's own arithmetic. The model is asked
 * only for the *textual* synthesis (status classification, summary,
 * findings, risks, recommendations).
 *
 * **Zero-orders short-circuit, shared with the other three providers:**
 * calling a paid cloud model for `input.orders.total == 0` would spend real
 * money to be told there is nothing to analyze — a fact already knowable
 * from the request alone. This returns the identical "insufficient data"
 * result the other providers already return for the same input, without
 * ever reaching the network.
 *
 * Trend context ([PilotAnalysisRequest.currentDay]/[PilotAnalysisRequest.history])
 * is included via the same shared [formatTrendContextForPrompt] every other
 * model-backed provider already uses — no separate logic to keep in sync.
 *
 * No memory, no history of its own, and no autonomous action of any kind —
 * this class performs exactly one Read → Analyze → Report round trip per
 * [analyze] call, the same boundary ADR-056 already fixes for every provider.
 */
@Component
@ConditionalOnProperty(name = ["pios.ai-advisor.provider"], havingValue = "qwen")
class QwenProvider(
    private val qwenRestClient: RestClient,
    @Value("\${pios.ai-advisor.qwen.api-key:}") private val apiKey: String,
    @Value("\${pios.ai-advisor.qwen.model:qwen-plus}") private val model: String
) : AIProvider {

    override val name = "qwen"

    private val logger = LoggerFactory.getLogger(QwenProvider::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    override fun analyze(input: PilotAnalysisRequest): AIProviderOutcome {
        if (apiKey.isBlank()) {
            // Fails closed, mirroring DeepSeekProvider -- Qwen/DashScope is a
            // paid cloud API with no unauthenticated mode, so a missing key
            // is never attempted, never a crash, an honest AUTH_FAILED
            // outcome AdvisorController already maps to "provider_unavailable".
            logger.warn("qwen.analysis.failed reason={}", FailureReason.AUTH_FAILED)
            return AIProviderOutcome.Failure(FailureReason.AUTH_FAILED, null)
        }

        val metrics = PilotAnalysisMetricsDto(
            acceptanceRate = calculateAcceptanceRate(input.proposals),
            completionRate = calculateCompletionRate(input.orders),
            cancellationRate = calculateCancellationRate(input.orders)
        )

        if (input.orders.total == 0) {
            return AIProviderOutcome.Success(insufficientDataResult(input, metrics, name))
        }

        return try {
            val chatResponse = qwenRestClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .body(
                    QwenChatRequest(
                        model = model,
                        messages = listOf(QwenMessage(role = "user", content = buildPrompt(input, metrics))),
                        responseFormat = QwenResponseFormat()
                    )
                )
                .retrieve()
                .body(QwenChatResponse::class.java)

            val content = chatResponse?.choices?.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                logger.warn("qwen.analysis.failed reason={} detail=empty_choice", FailureReason.MALFORMED_RESPONSE)
                return AIProviderOutcome.Failure(FailureReason.MALFORMED_RESPONSE, 200)
            }

            val parsed = parseModelJson(content)
                ?: run {
                    logger.warn("qwen.analysis.failed reason={} detail=unparseable_json", FailureReason.MALFORMED_RESPONSE)
                    return AIProviderOutcome.Failure(FailureReason.MALFORMED_RESPONSE, 200)
                }

            if (parsed.status !in VALID_STATUSES) {
                logger.warn("qwen.analysis.failed reason={} detail=unrecognized_status", FailureReason.MALFORMED_RESPONSE)
                return AIProviderOutcome.Failure(FailureReason.MALFORMED_RESPONSE, 200)
            }

            AIProviderOutcome.Success(
                PilotAnalysisResultDto(
                    status = parsed.status,
                    summary = parsed.summary,
                    keyFindings = parsed.keyFindings,
                    risks = parsed.risks,
                    recommendations = parsed.recommendations,
                    metrics = metrics,
                    generatedAt = input.generatedAt,
                    providerName = name
                )
            )
        } catch (ex: RestClientResponseException) {
            // Never ex.responseBodyAsString, never ex.message wholesale --
            // a raw status code only, mirroring DeepSeekProvider/OllamaProvider.
            val statusCode = ex.statusCode.value()
            val reason = failureReasonForStatus(statusCode)
            logger.warn("qwen.analysis.failed reason={} providerStatusCode={}", reason, statusCode)
            AIProviderOutcome.Failure(reason, statusCode)
        } catch (ex: RestClientException) {
            // Same unified classification DeepSeekProvider/OllamaProvider's
            // own KDoc explains: a connect/send-phase timeout surfaces as
            // ResourceAccessException, a read-phase timeout surfaces as a
            // plain RestClientException wrapping SocketTimeoutException/
            // HttpTimeoutException, and a 2xx response whose body will not
            // deserialize surfaces as a plain RestClientException with
            // neither cause.
            val reason = when {
                ex.cause is SocketTimeoutException || ex.cause is HttpTimeoutException -> FailureReason.TIMEOUT
                ex is ResourceAccessException -> FailureReason.UNAVAILABLE
                else -> FailureReason.MALFORMED_RESPONSE
            }
            logger.warn("qwen.analysis.failed reason={}", reason)
            AIProviderOutcome.Failure(reason, null)
        } catch (ex: Exception) {
            logger.warn("qwen.analysis.failed reason={} detail=unexpected_exception exceptionType={}", FailureReason.UNAVAILABLE, ex.javaClass.simpleName)
            AIProviderOutcome.Failure(FailureReason.UNAVAILABLE, null)
        }
    }

    private fun parseModelJson(content: String): QwenLlmAnalysis? =
        try {
            objectMapper.readValue(content, QwenLlmAnalysis::class.java)
        } catch (ex: Exception) {
            null
        }

    private fun buildPrompt(input: PilotAnalysisRequest, metrics: PilotAnalysisMetricsDto): String {
        val base = """
        Ты аналитик пилотного проекта такси-платформы PIOS. Проанализируй агрегированные показатели пилота ниже и верни ТОЛЬКО валидный JSON, без пояснений вне JSON, в точности такой структуры:
        {"status": "ok" | "attention" | "critical", "summary": "одно предложение", "keyFindings": ["..."], "risks": ["..."], "recommendations": ["..."]}

        Данные пилота — ВЕСЬ ПЕРИОД НАБЛЮДЕНИЯ, кумулятивно с начала (${input.periodLabel}), это НЕ данные за один день:
        - Заказы: всего ${input.orders.total}, завершено ${input.orders.completed}, отменено ${input.orders.cancelled}, открыто ${input.orders.open}
        - Предложения: всего ${input.proposals.total}, принято ${input.proposals.accepted}, отклонено ${input.proposals.declined}, просрочено ${input.proposals.lapsed}, отозвано ${input.proposals.withdrawn}
        - Поездки (assignments): всего ${input.assignments.total}, завершено ${input.assignments.completed}, в процессе ${input.assignments.inProgress}
        - Водители: всего ${input.drivers.total}, на линии СЕЙЧАС (LIVE, текущий момент, не дневная величина) ${input.drivers.available}, получили хотя бы одно предложение за весь период ${input.drivers.withActivity}
        - Время реакции водителя: среднее ${formatMinutesForPrompt(input.reactionTime.averageMinutes)}, медиана ${formatMinutesForPrompt(input.reactionTime.medianMinutes)}, на основе ${input.reactionTime.sampleSize} предложений
        - Здоровье платформы: ${input.health.modulesUp} из ${input.health.modulesTotal} модулей отвечают
        - Acceptance rate: ${formatPercentForPrompt(metrics.acceptanceRate)}
        - Completion rate: ${formatPercentForPrompt(metrics.completionRate)}
        - Cancellation rate: ${formatPercentForPrompt(metrics.cancellationRate)}

        Не изобретай числа, которых нет выше. Отвечай по-русски, простым языком, без технических терминов PIOS.
        """.trimIndent()
        val trendContext = formatTrendContextForPrompt(input.currentDay, input.history)
        return if (trendContext.isEmpty()) base else "$base\n\n$trendContext"
    }

    private fun formatPercentForPrompt(rate: Double?): String = if (rate == null) "нет данных" else "${(rate * 100).roundToInt()}%"

    private fun formatMinutesForPrompt(value: Double?): String = if (value == null) "нет данных" else "${value.roundToInt()} мин"

    companion object {
        private val VALID_STATUSES = setOf("ok", "attention", "critical")

        private fun failureReasonForStatus(statusCode: Int): FailureReason = when (statusCode) {
            401 -> FailureReason.AUTH_FAILED
            402 -> FailureReason.QUOTA_EXCEEDED
            429 -> FailureReason.RATE_LIMITED
            in 500..599 -> FailureReason.UNAVAILABLE
            else -> FailureReason.UNAVAILABLE
        }
    }
}

private data class QwenChatRequest(
    val model: String,
    val messages: List<QwenMessage>,
    val responseFormat: QwenResponseFormat
)

private data class QwenMessage(val role: String, val content: String)

private data class QwenResponseFormat(val type: String = "json_object")

private data class QwenChatResponse(val choices: List<QwenChoice> = emptyList())

private data class QwenChoice(val message: QwenResponseMessage)

private data class QwenResponseMessage(val content: String)

/**
 * The model's own JSON answer, parsed from [QwenResponseMessage.content] —
 * never trusted for [PilotAnalysisMetricsDto]'s own numbers (see this file's
 * own KDoc). Named distinctly from [DeepSeekProvider]'s own `LlmAnalysis`
 * and [OllamaProvider]'s own `OllamaLlmAnalysis` — all three are top-level,
 * file-private declarations in the same `com.pios.aiadvisor.domain`
 * package, and Kotlin's top-level `private` limits *visibility*, not the
 * class's binary name, so two files in one package cannot both declare a
 * top-level class with the identical simple name.
 */
private data class QwenLlmAnalysis(
    val status: String,
    val summary: String,
    val keyFindings: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)
