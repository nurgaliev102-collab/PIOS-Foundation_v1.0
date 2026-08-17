package com.pios.aiadvisor.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pios.aiadvisor.domain.MockAIProvider
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the real chain end to end — `AdvisorController` wired to a real
 * [OwnerCredentialGate], a real [BudgetGuard], and the real [MockAIProvider]
 * (no mocks of any kind) — mirroring this project's own constructor-based
 * "integration test" convention (`HealthControllerTest` in every domain
 * module already tests its own controller this same way, without a live
 * HTTP server). This is the Kotlin-level equivalent of ADR-056's own
 * "Owner Control Center → POST /v1/advisor/analyze → ai-advisor →
 * MockAIProvider → response" path; the real HTTP round trip is additionally
 * verified live via curl and a real browser (see this task's own delivery
 * report).
 */
class AdvisorControllerTest {

    private val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private val iterations = 1000
    private val password = "owner-password"
    private val passwordHash = deriveKey(password, salt, iterations)
    private val mapper: ObjectMapper = jacksonObjectMapper()

    private fun gate() = OwnerCredentialGate(
        configuredUsername = "owner",
        configuredPasswordHash = Base64.getEncoder().encodeToString(passwordHash),
        configuredPasswordSalt = Base64.getEncoder().encodeToString(salt),
        iterations = iterations,
        failureDelayMillis = 0,
        maxFailuresPerWindow = 1000,
        windowMillis = 900_000
    )

    private fun controller(
        budgetGuard: BudgetGuard = BudgetGuard(minIntervalMillis = 0, maxCallsPerDay = 1000, maxCallsPerMonth = 1000)
    ) = AdvisorController(gate(), budgetGuard, MockAIProvider())

    private fun basicHeader(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    private fun emptyRequest() = PilotAnalysisRequest(
        generatedAt = "2026-08-17T12:00:00.000Z",
        periodLabel = "Весь период наблюдения (все данные, доступные системе сейчас)",
        orders = OrderMetrics(0, 0, 0, 0),
        proposals = ProposalMetrics(0, 0, 0, 0, 0, 0),
        assignments = AssignmentMetrics(0, 0, 0),
        drivers = DriverMetrics(0, 0, 0),
        reactionTime = ReactionTimeMetrics(null, null, 0),
        health = HealthMetrics(5, 5)
    )

    private fun realisticRequest() = emptyRequest().copy(
        orders = OrderMetrics(total = 4, completed = 3, cancelled = 1, open = 0),
        proposals = ProposalMetrics(total = 4, accepted = 3, declined = 0, lapsed = 1, withdrawn = 0, open = 0),
        drivers = DriverMetrics(total = 2, available = 1, withActivity = 2)
    )

    @Test
    fun `a missing Authorization header returns 401 with no body`() {
        val response = controller().analyze(null, realisticRequest())

        assertEquals(401, response.statusCode.value())
        assertNull(response.body)
    }

    @Test
    fun `an incorrect owner credential returns 401`() {
        val response = controller().analyze(basicHeader("owner", "wrong"), realisticRequest())

        assertEquals(401, response.statusCode.value())
    }

    @Test
    fun `a correct credential with real data returns outcome ok with a full result`() {
        val response = controller().analyze(basicHeader("owner", password), realisticRequest())

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals("ok", body.outcome)
        assertNull(body.message)
        val result = body.result!!
        assertTrue(result.status in setOf("ok", "attention", "critical"))
        assertEquals("mock", result.providerName)
    }

    @Test
    fun `zero orders yields outcome ok with result status unknown -- insufficient data is an answer, not a failure`() {
        val response = controller().analyze(basicHeader("owner", password), emptyRequest())

        val body = response.body!!
        assertEquals("ok", body.outcome)
        assertEquals("unknown", body.result!!.status)
    }

    @Test
    fun `a second call inside the rate-limit window returns outcome budget_exceeded, not an error`() {
        val budgetGuard = BudgetGuard(minIntervalMillis = 60_000, maxCallsPerDay = 1000, maxCallsPerMonth = 1000)
        val c = controller(budgetGuard)
        c.analyze(basicHeader("owner", password), realisticRequest())

        val second = c.analyze(basicHeader("owner", password), realisticRequest())

        assertEquals(200, second.statusCode.value())
        assertEquals("budget_exceeded", second.body!!.outcome)
        assertNull(second.body!!.result)
        assertTrue(second.body!!.message!!.isNotBlank())
    }

    @Test
    fun `an unauthenticated call never consumes rate-limit budget`() {
        val budgetGuard = BudgetGuard(minIntervalMillis = 60_000, maxCallsPerDay = 1000, maxCallsPerMonth = 1000)
        val c = controller(budgetGuard)
        c.analyze(null, realisticRequest()) // rejected at auth, before budget is touched

        val authenticated = c.analyze(basicHeader("owner", password), realisticRequest())

        assertEquals("ok", authenticated.body!!.outcome)
    }

    @Test
    fun `the response never contains the configured password hash, salt, or plain password, in any field`() {
        val response = controller().analyze(basicHeader("owner", password), realisticRequest())
        val serialized = mapper.writeValueAsString(response.body)

        assertFalse(serialized.contains(password))
        assertFalse(serialized.contains(Base64.getEncoder().encodeToString(passwordHash)))
        assertFalse(serialized.contains(Base64.getEncoder().encodeToString(salt)))
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
