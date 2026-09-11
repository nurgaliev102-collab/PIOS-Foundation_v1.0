package com.pios.core.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.core.application.NoOpTransactionRunner
import com.pios.core.application.OrderSubmittedRecordCommand
import com.pios.core.application.ParticipantHistoryProjectionApplicationService
import com.pios.core.application.ParticipantHistoryQueryService
import com.pios.core.persistence.InMemoryParticipantHistoryRepository
import com.pios.core.persistence.InMemoryProcessedEventRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Constructor-based unit tests for [ParticipantHistoryController]: the
 * endpoint reads only Core's own projection, and its Bearer /
 * owner-Basic / deny authorization behaves as
 * `PIOS_CORE_SLICE_01_CONSTRAINTS.md` requires. No Spring MVC context —
 * the controller is called directly.
 */
class ParticipantHistoryControllerTest {

    private val secret = Base64.getEncoder().encodeToString("core-slice-01-test-secret-0123456789".toByteArray())

    private lateinit var history: InMemoryParticipantHistoryRepository
    private lateinit var controller: ParticipantHistoryController

    @BeforeTest
    fun setUp() {
        history = InMemoryParticipantHistoryRepository()
        val projection = ParticipantHistoryProjectionApplicationService(
            InMemoryProcessedEventRepository(), history, NoOpTransactionRunner
        )
        projection.handleOrderSubmitted(
            OrderSubmittedRecordCommand("e1", "identity-P", "order-1", Instant.parse("2026-09-10T10:00:00Z"))
        )
        controller = ParticipantHistoryController(
            ParticipantHistoryQueryService(history),
            SessionTokenVerifier(secret),
            OwnerCredentialGate("", "", "", 210000, 0L, 20, 900000L)
        )
    }

    private fun bearerFor(sub: String): String {
        val payload = ObjectMapper().createObjectNode().apply {
            put("sub", sub); putNull("drv"); put("exp", Instant.now().plusSeconds(3600).epochSecond)
        }
        val enc = Base64.getUrlEncoder().withoutPadding().encodeToString(ObjectMapper().writeValueAsBytes(payload))
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256")) }
        val sig = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(enc.toByteArray()))
        return "Bearer $enc.$sig"
    }

    @Test
    fun `a Bearer token whose sub matches the participant returns that participant's history`() {
        val response = controller.history("identity-P", bearerFor("identity-P"))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf("ORDER_SUBMITTED"), response.body!!.events.map { it.kind })
        assertEquals("identity-P", response.body!!.participantReference)
    }

    @Test
    fun `a Bearer token for a different sub is denied`() {
        assertEquals(HttpStatus.UNAUTHORIZED, controller.history("identity-P", bearerFor("identity-OTHER")).statusCode)
    }

    @Test
    fun `no credential is denied`() {
        assertEquals(HttpStatus.UNAUTHORIZED, controller.history("identity-P", null).statusCode)
    }

    @Test
    fun `an unknown participant with a valid matching token returns an empty history, not an error`() {
        val response = controller.history("identity-UNKNOWN", bearerFor("identity-UNKNOWN"))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body!!.events)
    }
}
