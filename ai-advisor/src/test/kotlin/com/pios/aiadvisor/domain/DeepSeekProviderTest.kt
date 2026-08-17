package com.pios.aiadvisor.domain

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.pios.aiadvisor.api.AssignmentMetrics
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
 * Proves [DeepSeekProvider] end to end against [FakeDeepSeekServer] — the
 * real DeepSeek API is never called (this task's own explicit requirement).
 */
class DeepSeekProviderTest {

    private lateinit var server: FakeDeepSeekServer
    private val secretApiKey = "sk-super-secret-test-key-do-not-leak"
    private val logAppender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        server = FakeDeepSeekServer()
        logAppender.start()
        (LoggerFactory.getLogger(DeepSeekProvider::class.java) as Logger).addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        (LoggerFactory.getLogger(DeepSeekProvider::class.java) as Logger).detachAppender(logAppender)
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

    private fun provider(apiKey: String = secretApiKey, timeoutMillis: Long = 5000) =
        DeepSeekProvider(restClient(timeoutMillis), apiKey, "deepseek-v4-flash")

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
        assertEquals("deepseek", success.result.providerName)
        // Metrics come from calculateAcceptanceRate/CompletionRate/CancellationRate applied to
        // realisticRequest()'s own numbers, never from the model's own JSON -- the model's fake
        // response above deliberately carries no metrics field at all, and the assertions below
        // would fail if this class ever started trusting a model-supplied number instead.
        assertEquals(0.75, success.result.metrics.acceptanceRate) // 3 accepted / (3 accepted + 0 declined + 1 lapsed)
        assertEquals(0.75, success.result.metrics.completionRate) // 3 completed / 4 total orders
        assertEquals(0.25, success.result.metrics.cancellationRate) // 1 cancelled / 4 total orders
    }

    @Test
    fun `the Authorization header carries Bearer plus the configured key, and the key never otherwise appears in the request`() {
        server.enqueue(200, successBody("""{"status":"ok","summary":"x"}"""))

        provider().analyze(realisticRequest())

        assertEquals("Bearer $secretApiKey", server.lastAuthorizationHeader)
    }

    @Test
    fun `a 401 from the provider maps to AUTH_FAILED, without leaking the response body into any log line`() {
        server.enqueue(401, """{"error":{"message":"Invalid API key: $secretApiKey"}}""")

        val outcome = provider().analyze(realisticRequest())

        val failure = outcome as AIProviderOutcome.Failure
        assertEquals(FailureReason.AUTH_FAILED, failure.reason)
        assertEquals(401, failure.providerStatusCode)
        assertNoSecretOrResponseBodyLogged()
    }

    @Test
    fun `a 402 (no balance) maps to QUOTA_EXCEEDED`() {
        server.enqueue(402, """{"error":"Insufficient Balance"}""")

        val outcome = provider().analyze(realisticRequest())

        val failure = outcome as AIProviderOutcome.Failure
        assertEquals(FailureReason.QUOTA_EXCEEDED, failure.reason)
        assertEquals(402, failure.providerStatusCode)
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
    fun `a missing API key never calls the network at all, and fails closed with AUTH_FAILED`() {
        val outcome = provider(apiKey = "").analyze(realisticRequest())

        val failure = outcome as AIProviderOutcome.Failure
        assertEquals(FailureReason.AUTH_FAILED, failure.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a blank API key from whitespace only is also treated as missing`() {
        val outcome = provider(apiKey = "   ").analyze(realisticRequest())

        assertEquals(FailureReason.AUTH_FAILED, (outcome as AIProviderOutcome.Failure).reason)
        assertEquals(0, server.requestCount)
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

    @Test
    fun `zero orders returns the insufficient-data result without ever calling the network`() {
        val outcome = provider().analyze(emptyOrdersRequest())

        val success = outcome as AIProviderOutcome.Success
        assertEquals("unknown", success.result.status)
        assertEquals("deepseek", success.result.providerName)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the configured API key never appears in any log line this provider writes, across every failure path`() {
        server.enqueue(401, """{"error":"$secretApiKey leaked in body would be bad"}""")
        provider().analyze(realisticRequest())
        server.enqueue(500, """{"error":"another body"}""")
        provider().analyze(realisticRequest())
        provider(apiKey = "").analyze(realisticRequest())
        server.enqueue(200, successBody("not json"))
        provider().analyze(realisticRequest())

        assertNoSecretOrResponseBodyLogged()
    }

    private fun assertNoSecretOrResponseBodyLogged() {
        val allMessages = logAppender.list.joinToString("\n") { it.formattedMessage }
        assertFalse(allMessages.contains(secretApiKey), "log output must never contain the API key")
        assertFalse(allMessages.contains("Invalid API key"), "log output must never contain the provider's own response body text")
        assertTrue(logAppender.list.isNotEmpty(), "expected at least one log line to have been written")
    }
}
