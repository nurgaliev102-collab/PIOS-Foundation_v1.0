package com.pios.aiadvisor.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pios.aiadvisor.api.DailySnapshot
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
 * ADR-056 Rollout Step 3 — the first real [AIProvider] implementation.
 * Calls DeepSeek's OpenAI-compatible `POST /v1/chat/completions`
 * (ADR-056 Decision 9's own technical comparison). Selected only when
 * `pios.ai-advisor.provider=deepseek`; [MockAIProvider] stays the default
 * (`matchIfMissing = true` on its own `@ConditionalOnProperty`) precisely
 * so the existing pilot never depends on a funded DeepSeek account
 * existing — this task's own explicit requirement.
 *
 * **What this class never does, structurally, not by care alone:**
 * - Never calls any of the five PIOS domain modules (ADR-056 Decision 1) —
 *   it has no `RestClient` pointed at any of them, only [deepSeekRestClient].
 * - Never logs the API key, the request body, or the response body — every
 *   log line below names a [FailureReason] and, where relevant, a raw HTTP
 *   status code, never text that could carry a secret or a provider's own
 *   free-form error message.
 * - Never returns the API key, or any detail derived from it, in its own
 *   [AIProviderOutcome] — [AIProviderOutcome.Failure.providerStatusCode] is
 *   a bare integer, and [AIProviderOutcome.Success] carries only
 *   [PilotAnalysisResultDto], the same shape [MockAIProvider] already
 *   returns.
 * - Never retries automatically (ADR-056's own explicit prohibition) — one
 *   call, one outcome, every time.
 *
 * **Trust boundary, deliberate (mirrors [MockAIProvider]'s own):** the
 * three rate metrics ([PilotAnalysisMetricsDto]) are computed here, locally,
 * from [PilotAnalysisRequest]'s own numbers — never asked of, or trusted
 * from, the language model's own arithmetic. The model is asked only for
 * the *textual* synthesis (status classification, summary, findings, risks,
 * recommendations); PIOS's own numbers are never subject to a model's own
 * possible arithmetic error.
 *
 * **Zero-orders short-circuit, shared with [MockAIProvider]:** calling the
 * model for `input.orders.total == 0` would spend real money to be told
 * there is nothing to analyze — a fact already knowable from the request
 * alone. This returns the identical "insufficient data" result
 * [MockAIProvider] already returns for the same input, without ever
 * reaching the network.
 */
@Component
@ConditionalOnProperty(name = ["pios.ai-advisor.provider"], havingValue = "deepseek")
class DeepSeekProvider(
    private val deepSeekRestClient: RestClient,
    @Value("\${pios.ai-advisor.deepseek.api-key:}") private val apiKey: String,
    @Value("\${pios.ai-advisor.deepseek.model:deepseek-v4-flash}") private val model: String
) : AIProvider {

    override val name = "deepseek"

    private val logger = LoggerFactory.getLogger(DeepSeekProvider::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    override fun analyze(input: PilotAnalysisRequest): AIProviderOutcome {
        if (apiKey.isBlank()) {
            // Fails closed, the same way OwnerCredentialGate.isConfigured()
            // == false already does for a missing owner credential — never
            // attempted, never a crash, an honest AUTH_FAILED outcome that
            // AdvisorController already maps to "provider_unavailable".
            logger.warn("deepseek.analysis.failed reason={}", FailureReason.AUTH_FAILED)
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
            val chatResponse = deepSeekRestClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .body(
                    DeepSeekChatRequest(
                        model = model,
                        messages = listOf(DeepSeekMessage(role = "user", content = buildPrompt(input, metrics))),
                        responseFormat = DeepSeekResponseFormat()
                    )
                )
                .retrieve()
                .body(DeepSeekChatResponse::class.java)

            val content = chatResponse?.choices?.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                logger.warn("deepseek.analysis.failed reason={} detail=empty_choice", FailureReason.MALFORMED_RESPONSE)
                return AIProviderOutcome.Failure(FailureReason.MALFORMED_RESPONSE, 200)
            }

            val parsed = parseModelJson(content)
                ?: run {
                    logger.warn("deepseek.analysis.failed reason={} detail=unparseable_json", FailureReason.MALFORMED_RESPONSE)
                    return AIProviderOutcome.Failure(FailureReason.MALFORMED_RESPONSE, 200)
                }

            if (parsed.status !in VALID_STATUSES) {
                logger.warn("deepseek.analysis.failed reason={} detail=unrecognized_status", FailureReason.MALFORMED_RESPONSE)
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
            // a raw status code only (ADR-056 Decision 10/11).
            val statusCode = ex.statusCode.value()
            val reason = failureReasonForStatus(statusCode)
            logger.warn("deepseek.analysis.failed reason={} providerStatusCode={}", reason, statusCode)
            AIProviderOutcome.Failure(reason, statusCode)
        } catch (ex: RestClientException) {
            // A timeout can surface two different ways depending on
            // *when* it happens: a connect/send-phase timeout is wrapped as
            // ResourceAccessException, but a timeout while reading the
            // response body (this class's own read timeout, exercised by
            // DeepSeekProviderTest's own slow-server case) surfaces as a
            // plain RestClientException wrapping the same underlying
            // SocketTimeoutException/HttpTimeoutException instead -- both
            // shapes are checked here so classification does not silently
            // depend on which phase the timeout landed in, or on which
            // ClientHttpRequestFactory Spring Boot auto-selected.
            val reason = when {
                ex.cause is SocketTimeoutException || ex.cause is HttpTimeoutException -> FailureReason.TIMEOUT
                ex is ResourceAccessException -> FailureReason.UNAVAILABLE
                // Reached for a 2xx response whose body could not be
                // converted into DeepSeekChatResponse (Jackson
                // deserialization failure) -- the provider answered, just
                // not with the expected shape.
                else -> FailureReason.MALFORMED_RESPONSE
            }
            logger.warn("deepseek.analysis.failed reason={}", reason)
            AIProviderOutcome.Failure(reason, null)
        } catch (ex: Exception) {
            logger.warn("deepseek.analysis.failed reason={} detail=unexpected_exception exceptionType={}", FailureReason.UNAVAILABLE, ex.javaClass.simpleName)
            AIProviderOutcome.Failure(FailureReason.UNAVAILABLE, null)
        }
    }

    private fun parseModelJson(content: String): LlmAnalysis? =
        try {
            objectMapper.readValue(content, LlmAnalysis::class.java)
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

private data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val responseFormat: DeepSeekResponseFormat
)

private data class DeepSeekMessage(val role: String, val content: String)

private data class DeepSeekResponseFormat(val type: String = "json_object")

private data class DeepSeekChatResponse(val choices: List<DeepSeekChoice> = emptyList())

private data class DeepSeekChoice(val message: DeepSeekResponseMessage)

private data class DeepSeekResponseMessage(val content: String)

/** The model's own JSON answer, parsed from [DeepSeekResponseMessage.content] — never trusted for [PilotAnalysisMetricsDto]'s own numbers (see this file's own KDoc). */
private data class LlmAnalysis(
    val status: String,
    val summary: String,
    val keyFindings: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)

/**
 * PIOS Intelligence Trend Context — data-quality fix (2026-08-17): formats
 * [PilotAnalysisRequest.currentDay]/[PilotAnalysisRequest.history] into a
 * prompt section shared by [DeepSeekProvider] and [OllamaProvider] (same
 * package-level sharing pattern as [insufficientDataResult] just below), so
 * the wording a model sees is identical regardless of which provider is
 * active.
 *
 * Returns `""` when both [currentDay] and [history] are absent — the caller
 * then leaves its own prompt byte-for-byte unchanged, exactly preserving old
 * prompt behavior (this task's own explicit backward-compatibility
 * requirement).
 *
 * **Why this is not simply "format the history list"** (the bug this
 * function replaces): [PilotAnalysisRequest]'s own ALL-PERIOD fields
 * (`orders`/`proposals`/`assignments` in the base prompt above) are
 * cumulative over the whole observation period, not "today" — comparing
 * them to a single day of [history] as if both were daily numbers is
 * comparing different scales (e.g. "18 orders all-time" is not a real
 * day-over-day counterpart to "12 orders yesterday"). [currentDay] is the
 * only value on the same daily scale as [history]'s own entries, so this
 * function explicitly labels each section by its real semantics (ALL-PERIOD
 * / CURRENT DAY / HISTORICAL DAILY / LIVE) and states in the prompt itself
 * which comparisons are valid, rather than trusting the model to infer scale
 * from context.
 */
internal fun formatTrendContextForPrompt(currentDay: DailySnapshot?, history: List<DailySnapshot>?): String {
    val hasHistory = !history.isNullOrEmpty()
    if (currentDay == null && !hasHistory) {
        return ""
    }

    val sections = mutableListOf<String>()

    if (currentDay != null) {
        sections.add(
            "ТЕКУЩИЙ ДЕНЬ (${currentDay.date}) — отдельная, НЕ кумулятивная величина, только за сегодня: " +
                "заказы ${currentDay.orders.total} (завершено ${currentDay.orders.completed}, отменено ${currentDay.orders.cancelled}), " +
                "предложения приняты ${currentDay.proposals.accepted} из ${currentDay.proposals.total}, " +
                "поездки завершены ${currentDay.assignments.completed}, activeDrivers (водителей с активностью сегодня) ${currentDay.activeDrivers}."
        )
    }

    if (hasHistory) {
        val lines = history!!.joinToString("\n") { day ->
            "- ${day.date}: заказы ${day.orders.total} (завершено ${day.orders.completed}, отменено ${day.orders.cancelled}), " +
                "предложения приняты ${day.proposals.accepted} из ${day.proposals.total}, " +
                "поездки завершены ${day.assignments.completed}, activeDrivers ${day.activeDrivers}"
        }
        sections.add(
            "ИСТОРИЯ ПО ДНЯМ (последние ${history.size} дн. до сегодня, не включая сегодня; от новых к старым; реальные, уже посчитанные факты PIOS):\n$lines"
        )
    }

    val rules = buildString {
        append(
            "ПРАВИЛА СРАВНЕНИЯ (обязательно): раздел \"Данные пилота\" выше — это ALL-PERIOD, кумулятивные показатели за весь период наблюдения. Их НЕЛЬЗЯ напрямую сравнивать ни с текущим днём, ни с историей по дням как \"выросло/упало\" — это разные по масштабу величины. "
        )
        when {
            currentDay != null && hasHistory ->
                append("Для вывода о тренде по дням сравнивай ТОЛЬКО текущий день с историей по дням — обе величины дневные и сопоставимы между собой. ")
            hasHistory ->
                append("Текущий день недоступен (за сегодня ещё нет ни одной записи с сегодняшней датой), поэтому сравнение \"сегодня против истории\" сделать нельзя — явно сообщи об этом в ответе, если вывод о сегодняшнем дне запрошен, вместо того чтобы для этого использовать кумулятивные данные выше. ")
            else ->
                append("История отсутствует, доступен только текущий день — сравнивать его не с чем, тренд не определяй. ")
        }
        append(
            "Показатель \"на линии СЕЙЧАС\" в разделе \"Данные пилота\" — это LIVE-состояние на текущий момент, а activeDrivers (в текущем дне/истории) — число водителей с хотя бы одной активностью за конкретный день; это РАЗНЫЕ метрики, не называй изменение между ними ростом или падением доступности водителей. Сами числа не пересчитывай — они уже точны. Не придумывай отсутствующие дни."
        )
    }
    sections.add(rules)

    return sections.joinToString("\n\n")
}

/** Shared with [MockAIProvider]'s own identical zero-orders branch — same wording, same reasoning, never spent on a model call. */
internal fun insufficientDataResult(input: PilotAnalysisRequest, metrics: PilotAnalysisMetricsDto, providerName: String): PilotAnalysisResultDto =
    PilotAnalysisResultDto(
        status = "unknown",
        summary = "Недостаточно данных для анализа — за наблюдаемый период не зафиксировано ни одного заказа.",
        keyFindings = emptyList(),
        risks = emptyList(),
        recommendations = listOf("Дождитесь первых заказов в системе и запустите анализ ещё раз."),
        metrics = metrics,
        generatedAt = input.generatedAt,
        providerName = providerName
    )
