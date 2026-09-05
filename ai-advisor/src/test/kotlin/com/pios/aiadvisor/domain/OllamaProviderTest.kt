package com.pios.aiadvisor.domain

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.pios.aiadvisor.api.AssignmentMetrics
import com.pios.aiadvisor.api.DailySnapshot
import com.pios.aiadvisor.api.DriverMetrics
import com.pios.aiadvisor.api.HealthMetrics
import com.pios.aiadvisor.api.OrderMetrics
import com.pios.aiadvisor.api.PilotAnalysisRequest
import com.pios.aiadvisor.api.ProposalMetrics
import com.pios.aiadvisor.api.ReactionTimeMetrics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.web.client.RestClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves [OllamaProvider] end to end against [FakeOllamaServer] — a real
 * Ollama instance is never called (this task's own explicit requirement),
 * and no test in this class requires Ollama to be installed. Mirrors
 * [DeepSeekProviderTest] exactly except where Ollama's own operating model
 * genuinely differs (no fail-closed on a missing key; 403 also classified
 * as AUTH_FAILED) — see those specific tests below.
 */
class OllamaProviderTest {

    private lateinit var server: FakeOllamaServer
    private val secretApiKey = "ollama-super-secret-test-key-do-not-leak"
    private val logAppender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        server = FakeOllamaServer()
        logAppender.start()
        (LoggerFactory.getLogger(OllamaProvider::class.java) as Logger).addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        (LoggerFactory.getLogger(OllamaProvider::class.java) as Logger).detachAppender(logAppender)
        logAppender.stop()
        logAppender.list.clear()
    }

    private fun restClient(timeoutMillis: Long = 5000) = RestClient.builder()
        .baseUrl(server.baseUrl())
        .requestFactory(
            ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                    .withConnectTimeout(Duration.ofMillis(timeoutMillis))
                    .withReadTimeout(Duration.ofMillis(timeoutMillis))
            )
        )
        .build()

    private fun provider(apiKey: String = "", timeoutMillis: Long = 5000) =
        OllamaProvider(restClient(timeoutMillis), apiKey, "qwen2.5")

    private fun realisticRequest() = PilotAnalysisRequest(
        generatedAt = "2026-08-17T12:00:00.000Z",
        periodLabel = "test",
        orders = OrderMetrics(total = 4, completed = 3, cancelled = 1, open = 0),
        proposals = ProposalMetrics(total = 4, accepted = 3, declined = 0, lapsed = 1, withdrawn = 0, open = 0),
        assignments = AssignmentMetrics(total = 3, completed = 3, inProgress = 0),
        drivers = DriverMetrics(total = 2, available = 1, withActivity = 2),
        reactionTime = ReactionTimeMetrics(averageMinutes = null, medianMinutes = null, sampleSize = 0),
        health = HealthMetrics(modulesUp = 5, modulesTotal = 5)
    )

    private fun emptyOrdersRequest() = realisticRequest().copy(
        orders = OrderMetrics(0, 0, 0, 0),
        proposals = ProposalMetrics(0, 0, 0, 0, 0, 0)
    )

    private fun successBody(content: String) = """
        {"choices":[{"message":{"content":${jsonString(content)}},"finish_reason":"stop"}]}
    """.trimIndent()

    private fun jsonString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .let { "\"$it\"" }

    @Test
    fun `a successful response is parsed into a real PilotAnalysisResultDto, with metrics computed locally not by the model`() {
        server.enqueue(
            200,
            successBody("""{"status":"attention","summary":"Есть на что обратить внимание.","keyFindings":["finding-1"],"risks":["risk-1"],"recommendations":["rec-1"]}""")
        )

        val outcome = provider().analyze(realisticRequest())

        val success = outcome as AIProviderOutcome.Success
        assertEquals("attention", success.result.status)
        assertEquals("Есть на что обратить внимание.", success.result.summary)
        assertEquals(listOf("finding-1"), success.result.keyFindings)
        assertEquals("ollama", success.result.providerName)
        // Metrics come from calculateAcceptanceRate/CompletionRate/CancellationRate applied to
        // realisticRequest()'s own numbers, never from the model's own JSON.
        assertEquals(0.75, success.result.metrics.acceptanceRate) // 3 accepted / (3 accepted + 0 declined + 1 lapsed)
        assertEquals(0.75, success.result.metrics.completionRate) // 3 completed / 4 total orders
        assertEquals(0.25, success.result.metrics.cancellationRate) // 1 cancelled / 4 total orders
    }

    // --- No fail-closed on a missing key (deliberate difference from DeepSeekProvider) ---

    @Test
    fun `with no api key configured, the request is sent with no Authorization header at all, and still succeeds`() {
        server.enqueue(200, successBody("""{"status":"ok","summary":"x"}"""))

        val outcome = provider(apiKey = "").analyze(realisticRequest())

        assertTrue(outcome is AIProviderOutcome.Success)
        assertNull(server.lastAuthorizationHeader)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `with an api key configured, the Authorization header carries Bearer plus the configured key`() {
        server.enqueue(200, successBody("""{"status":"ok","summary":"x"}"""))

        provider(apiKey = secretApiKey).analyze(realisticRequest())

        assertEquals("Bearer $secretApiKey", server.lastAuthorizationHeader)
    }

    @Test
    fun `a 401 from the provider maps to AUTH_FAILED, without leaking the response body into any log line`() {
        server.enqueue(401, """{"error":{"message":"Invalid credentials: $secretApiKey"}}""")

        val outcome = provider(apiKey = secretApiKey).analyze(realisticRequest())

        val failure = outcome as AIProviderOutcome.Failure
        assertEquals(FailureReason.AUTH_FAILED, failure.reason)
        assertEquals(401, failure.providerStatusCode)
        assertNoSecretOrResponseBodyLogged()
    }

    @Test
    fun `a 403 (reverse proxy in front of local Ollama) also maps to AUTH_FAILED, not UNAVAILABLE`() {
        server.enqueue(403, """{"error":"forbidden"}""")

        val outcome = provider(apiKey = secretApiKey).analyze(realisticRequest())

        val failure = outcome as AIProviderOutcome.Failure
        assertEquals(FailureReason.AUTH_FAILED, failure.reason)
        assertEquals(403, failure.providerStatusCode)
    }

    @Test
    fun `a 429 maps to RATE_LIMITED`() {
        server.enqueue(429, """{"error":"rate limited"}""")

        val outcome = provider().analyze(realisticRequest())

        val failure = outcome as AIProviderOutcome.Failure
        assertEquals(FailureReason.RATE_LIMITED, failure.reason)
        assertEquals(429, failure.providerStatusCode)
    }

    @Test
    fun `a 500 maps to UNAVAILABLE`() {
        server.enqueue(500, """{"error":"internal error"}""")

        val outcome = provider().analyze(realisticRequest())

        val failure = outcome as AIProviderOutcome.Failure
        assertEquals(FailureReason.UNAVAILABLE, failure.reason)
        assertEquals(500, failure.providerStatusCode)
    }

    @Test
    fun `a request that exceeds the configured timeout maps to TIMEOUT, not UNAVAILABLE`() {
        server.delayMillis = 2000
        server.enqueue(200, successBody("""{"status":"ok","summary":"too slow to matter"}"""))

        val outcome = provider(timeoutMillis = 300).analyze(realisticRequest())

        val failure = outcome as AIProviderOutcome.Failure
        assertEquals(FailureReason.TIMEOUT, failure.reason)
        assertNull(failure.providerStatusCode)
    }

    @Test
    fun `a response whose content is not valid JSON maps to MALFORMED_RESPONSE, not a crash`() {
        server.enqueue(200, successBody("this is not json at all"))

        val outcome = provider().analyze(realisticRequest())

        assertEquals(FailureReason.MALFORMED_RESPONSE, (outcome as AIProviderOutcome.Failure).reason)
    }

    @Test
    fun `a response with an unrecognized status value maps to MALFORMED_RESPONSE`() {
        server.enqueue(200, successBody("""{"status":"extremely-bad","summary":"x"}"""))

        val outcome = provider().analyze(realisticRequest())

        assertEquals(FailureReason.MALFORMED_RESPONSE, (outcome as AIProviderOutcome.Failure).reason)
    }

    @Test
    fun `a response with no choices at all maps to MALFORMED_RESPONSE`() {
        server.enqueue(200, """{"choices":[]}""")

        val outcome = provider().analyze(realisticRequest())

        assertEquals(FailureReason.MALFORMED_RESPONSE, (outcome as AIProviderOutcome.Failure).reason)
    }

    @Test
    fun `a completely malformed top-level response body maps to MALFORMED_RESPONSE, not a thrown exception`() {
        server.enqueue(200, "not even json")

        val outcome = provider().analyze(realisticRequest())

        assertEquals(FailureReason.MALFORMED_RESPONSE, (outcome as AIProviderOutcome.Failure).reason)
    }

    // --- PIOS Intelligence Trend Context -- data-quality fix (2026-08-17) ---

    private val historyDay16 = DailySnapshot(
        date = "2026-08-16",
        orders = OrderMetrics(total = 12, completed = 9, cancelled = 2, open = 0),
        proposals = ProposalMetrics(total = 12, accepted = 9, declined = 1, lapsed = 0, withdrawn = 0, open = 0),
        assignments = AssignmentMetrics(total = 9, completed = 9, inProgress = 0),
        activeDrivers = 4
    )
    private val historyDay15 = historyDay16.copy(
        date = "2026-08-15",
        orders = OrderMetrics(total = 9, completed = 7, cancelled = 1, open = 0),
        activeDrivers = 3
    )
    private val currentDaySnapshot = DailySnapshot(
        date = "2026-08-17",
        orders = OrderMetrics(total = 6, completed = 4, cancelled = 1, open = 1),
        proposals = ProposalMetrics(total = 6, accepted = 4, declined = 1, lapsed = 0, withdrawn = 0, open = 1),
        assignments = AssignmentMetrics(total = 4, completed = 4, inProgress = 0),
        activeDrivers = 2
    )

    @Test
    fun `when both currentDay and history are absent, the prompt sent to the model is unchanged -- no trend section at all`() {
        server.enqueue(200, successBody("""{"status":"ok","summary":"x"}"""))

        provider().analyze(realisticRequest())

        val body = server.lastRequestBody!!
        assertTrue(body.contains("Не изобретай числа"), "base prompt content must still be present")
        assertFalse(body.contains("ИСТОРИЯ ПО ДНЯМ"), "no history section should be added when history is null")
        assertFalse(body.contains("ТЕКУЩИЙ ДЕНЬ"), "no current-day section should be added when currentDay is null")
    }

    @Test
    fun `when currentDay and history are both present, the prompt labels ALL-PERIOD, CURRENT DAY, HISTORICAL DAILY and LIVE separately`() {
        server.enqueue(200, successBody("""{"status":"ok","summary":"x"}"""))
        val requestWithTrendContext = realisticRequest().copy(
            currentDay = currentDaySnapshot,
            history = listOf(historyDay16, historyDay15)
        )

        provider().analyze(requestWithTrendContext)

        val body = server.lastRequestBody!!
        assertTrue(body.contains("ВЕСЬ ПЕРИОД НАБЛЮДЕНИЯ"))
        assertTrue(body.contains("LIVE"))
        assertTrue(body.contains("ТЕКУЩИЙ ДЕНЬ"))
        assertTrue(body.contains("2026-08-17"))
        assertTrue(body.contains("ИСТОРИЯ ПО ДНЯМ"))
        assertTrue(body.contains("2026-08-16"))
        assertTrue(body.contains("2026-08-15"))
        assertTrue(body.contains("НЕЛЬЗЯ напрямую сравнивать"))
        assertTrue(body.contains("РАЗНЫЕ метрики"))
        assertTrue(body.contains("придумывай"))
        assertTrue(body.contains("Не изобретай числа"))
    }

    @Test
    fun `when history is present but currentDay is absent, the prompt honestly states the day-over-day comparison is unavailable, instead of substituting the cumulative snapshot`() {
        server.enqueue(200, successBody("""{"status":"ok","summary":"x"}"""))
        val requestWithHistoryOnly = realisticRequest().copy(history = listOf(historyDay16, historyDay15))

        provider().analyze(requestWithHistoryOnly)

        val body = server.lastRequestBody!!
        assertFalse(body.contains("ТЕКУЩИЙ ДЕНЬ"), "no current-day section should be fabricated when currentDay is null")
        assertTrue(body.contains("ИСТОРИЯ ПО ДНЯМ"))
        assertTrue(body.contains("Текущий день недоступен"))
    }

    @Test
    fun `zero orders returns the insufficient-data result without ever calling Ollama`() {
        val outcome = provider().analyze(emptyOrdersRequest())

        val success = outcome as AIProviderOutcome.Success
        assertEquals("unknown", success.result.status)
        assertEquals("ollama", success.result.providerName)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the configured API key never appears in any log line this provider writes, across every failure path`() {
        server.enqueue(401, """{"error":"$secretApiKey leaked in body would be bad"}""")
        provider(apiKey = secretApiKey).analyze(realisticRequest())
        server.enqueue(500, """{"error":"another body"}""")
        provider(apiKey = secretApiKey).analyze(realisticRequest())
        server.enqueue(200, successBody("not json"))
        provider(apiKey = secretApiKey).analyze(realisticRequest())

        assertNoSecretOrResponseBodyLogged()
    }

    private fun assertNoSecretOrResponseBodyLogged() {
        val allMessages = logAppender.list.joinToString("\n") { it.formattedMessage }
        assertFalse(allMessages.contains(secretApiKey), "log output must never contain the API key")
        assertFalse(allMessages.contains("Invalid credentials"), "log output must never contain the provider's own response body text")
        assertTrue(logAppender.list.isNotEmpty(), "expected at least one log line to have been written")
    }
}
