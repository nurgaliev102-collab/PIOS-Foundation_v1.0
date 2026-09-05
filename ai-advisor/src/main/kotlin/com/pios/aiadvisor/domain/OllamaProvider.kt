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
 * PIOS Intelligence, first local-model step — a locally-hosted Qwen model
 * served through Ollama's own OpenAI-compatible `POST /v1/chat/completions`
 * endpoint. Selected only when `pios.ai-advisor.provider=ollama`;
 * [MockAIProvider] stays the default (`matchIfMissing = true` on its own
 * `@ConditionalOnProperty`) precisely so the existing pilot never depends on
 * Ollama being installed — this task's own explicit requirement.
 *
 * Mirrors [DeepSeekProvider] structurally — same trust boundary, same error
 * classification shape, same zero-orders short-circuit via the shared
 * [insufficientDataResult] — the two differ only where Ollama's own
 * operating model genuinely differs from DeepSeek's:
 *
 * - **No fail-closed on a missing API key.** DeepSeek is a paid cloud API
 *   that always requires one; Ollama is, by design, a local, unauthenticated
 *   HTTP server with no credential of its own — the *normal*, expected
 *   deployment has [apiKey] blank, and this class sends no `Authorization`
 *   header at all in that case (never a manufactured [FailureReason.AUTH_FAILED]
 *   for the ordinary, no-key local case). When [apiKey] is configured (e.g.
 *   Ollama proxied behind an authenticating reverse proxy, or a future
 *   hosted Qwen endpoint reusing this same class), it is sent as
 *   `Authorization: Bearer <key>`, same shape as [DeepSeekProvider].
 * - **403, not only 401, is classified as [FailureReason.AUTH_FAILED]**
 *   (this task's own explicit requirement) — a reverse proxy in front of a
 *   local Ollama instance is a real, anticipated deployment shape this
 *   class must not mis-classify as [FailureReason.UNAVAILABLE].
 *
 * **What this class never does, structurally, not by care alone** (the same
 * four guarantees [DeepSeekProvider]'s own KDoc states, restated here for
 * this class specifically):
 * - Never calls any of the five PIOS domain modules — it has no `RestClient`
 *   pointed at any of them, only [ollamaRestClient].
 * - Never logs the API key, the request body, or the response body — every
 *   log line below names a [FailureReason] and, where relevant, a raw HTTP
 *   status code, never text that could carry a secret or the model's own
 *   free-form output.
 * - Never returns the API key, or anything derived from it, in its own
 *   [AIProviderOutcome].
 * - Never retries automatically — one call, one outcome, every time.
 *
 * **Trust boundary, identical to [MockAIProvider]/[DeepSeekProvider]:** the
 * three rate metrics ([PilotAnalysisMetricsDto]) are computed here, locally,
 * from [PilotAnalysisRequest]'s own numbers — never asked of, or trusted
 * from, the model's own arithmetic. The model is asked only for the
 * *textual* synthesis (status classification, summary, findings, risks,
 * recommendations).
 *
 * **Zero-orders short-circuit, shared with [MockAIProvider]/[DeepSeekProvider]:**
 * calling a local model for `input.orders.total == 0` still spends real
 * CPU/GPU time to be told there is nothing to analyze — a fact already
 * knowable from the request alone. This returns the identical
 * "insufficient data" result the other two providers already return for the
 * same input, without ever reaching Ollama.
 *
 * No memory, no history, and no autonomous action of any kind — this class
 * performs exactly one Read → Analyze → Report round trip per [analyze]
 * call, the same boundary ADR-056 already fixes for every provider.
 */
@Component
@ConditionalOnProperty(name = ["pios.ai-advisor.provider"], havingValue = "ollama")
class OllamaProvider(
    private val ollamaRestClient: RestClient,
    @Value("\${pios.ai-advisor.ollama.api-key:}") private val apiKey: String,
    @Value("\${pios.ai-advisor.ollama.model:qwen2.5}") private val model: String
) : AIProvider {

    override val name = "ollama"

    private val logger = LoggerFactory.getLogger(OllamaProvider::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    override fun analyze(input: PilotAnalysisRequest): AIProviderOutcome {
        val metrics = PilotAnalysisMetricsDto(
            acceptanceRate = calculateAcceptanceRate(input.proposals),
            completionRate = calculateCompletionRate(input.orders),
            cancellationRate = calculateCancellationRate(input.orders)
        )

        if (input.orders.total == 0) {
            return AIProviderOutcome.Success(insufficientDataResult(input, metrics, name))
        }

        return try {
            val requestBody = OllamaChatRequest(
                model = model,
                messages = listOf(OllamaMessage(role = "user", content = buildPrompt(input, metrics))),
                responseFormat = OllamaResponseFormat()
            )
            val requestSpec = ollamaRestClient.post().uri("/v1/chat/completions")
            val chatResponse = (if (apiKey.isNotBlank()) requestSpec.header("Authorization", "Bearer $apiKey") else requestSpec)
                .body(requestBody)
                .retrieve()
                .body(OllamaChatResponse::class.java)

            val content = chatResponse?.choices?.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                logger.warn("ollama.analysis.failed reason={} detail=empty_choice", FailureReason.MALFORMED_RESPONSE)
                return AIProviderOutcome.Failure(FailureReason.MALFORMED_RESPONSE, 200)
            }

            val parsed = parseModelJson(content)
                ?: run {
                    logger.warn("ollama.analysis.failed reason={} detail=unparseable_json", FailureReason.MALFORMED_RESPONSE)
                    return AIProviderOutcome.Failure(FailureReason.MALFORMED_RESPONSE, 200)
                }

            if (parsed.status !in VALID_STATUSES) {
                logger.warn("ollama.analysis.failed reason={} detail=unrecognized_status", FailureReason.MALFORMED_RESPONSE)
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
            // a raw status code only, mirroring DeepSeekProvider.
            val statusCode = ex.statusCode.value()
            val reason = failureReasonForStatus(statusCode)
            logger.warn("ollama.analysis.failed reason={} providerStatusCode={}", reason, statusCode)
            AIProviderOutcome.Failure(reason, statusCode)
        } catch (ex: RestClientException) {
            // Same unified classification DeepSeekProvider's own KDoc explains:
            // a connect/send-phase timeout surfaces as ResourceAccessException,
            // a read-phase timeout surfaces as a plain RestClientException
            // wrapping SocketTimeoutException/HttpTimeoutException, and a 2xx
            // response whose body will not deserialize surfaces as a plain
            // RestClientException with neither cause.
            val reason = when {
                ex.cause is SocketTimeoutException || ex.cause is HttpTimeoutException -> FailureReason.TIMEOUT
                ex is ResourceAccessException -> FailureReason.UNAVAILABLE
                else -> FailureReason.MALFORMED_RESPONSE
            }
            logger.warn("ollama.analysis.failed reason={}", reason)
            AIProviderOutcome.Failure(reason, null)
        } catch (ex: Exception) {
            logger.warn("ollama.analysis.failed reason={} detail=unexpected_exception exceptionType={}", FailureReason.UNAVAILABLE, ex.javaClass.simpleName)
            AIProviderOutcome.Failure(FailureReason.UNAVAILABLE, null)
        }
    }

    private fun parseModelJson(content: String): OllamaLlmAnalysis? =
        try {
            objectMapper.readValue(content, OllamaLlmAnalysis::class.java)
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
            401, 403 -> FailureReason.AUTH_FAILED
            429 -> FailureReason.RATE_LIMITED
            in 500..599 -> FailureReason.UNAVAILABLE
            else -> FailureReason.UNAVAILABLE
        }
    }
}

private data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val responseFormat: OllamaResponseFormat
)

private data class OllamaMessage(val role: String, val content: String)

/** Ollama's OpenAI-compatible endpoint honors `response_format` the same way DeepSeek's does, on recent versions; a model that ignores it still has its output run through [OllamaProvider]'s own JSON parse/validate path, never trusted blindly either way. */
private data class OllamaResponseFormat(val type: String = "json_object")

private data class OllamaChatResponse(val choices: List<OllamaChoice> = emptyList())

private data class OllamaChoice(val message: OllamaResponseMessage)

private data class OllamaResponseMessage(val content: String)

/**
 * The model's own JSON answer, parsed from [OllamaResponseMessage.content] —
 * never trusted for [PilotAnalysisMetricsDto]'s own numbers (see this file's
 * own KDoc). Named distinctly from [DeepSeekProvider]'s own private
 * `LlmAnalysis` — both are top-level, file-private declarations in the same
 * `com.pios.aiadvisor.domain` package, and Kotlin's top-level `private`
 * limits *visibility*, not the class's binary name, so two files in one
 * package cannot both declare a top-level class named `LlmAnalysis`.
 */
private data class OllamaLlmAnalysis(
    val status: String,
    val summary: String,
    val keyFindings: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)
