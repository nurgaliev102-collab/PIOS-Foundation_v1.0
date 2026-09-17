package com.pios.ordermanagement.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.CompleteOrderCommand
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OrderCancellationCoordinationService
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.domain.OrderStatus
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import com.pios.ordermanagement.persistence.InMemoryOrderCancellationRequestRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Constructs [OrderCancellationController] directly, with a real
 * [OrderLifecycleApplicationService], no Spring MVC context — mirroring
 * [OrderSubmissionControllerTest]'s own constructor-based testing
 * convention (P0-2 Tier 1; ADR-053, Proposal Resolution on Order
 * Cancellation — Accepted).
 *
 * Task 25 (Orders Cancellation & Driver Availability Security
 * Remediation): `cancelOrder` now requires an `Authorization` header (see
 * [OrderCancellationController]'s own KDoc). Token minting mirrors
 * `com.pios.dispatch.api.ProposalControllerTest`'s own already-established
 * pattern exactly (Task 21) -- this module has no build-time dependency on
 * `identity` and never calls it (ADR-055 Decision 1), so a token is minted
 * locally, in the exact format `SessionTokenIssuer` mints and this
 * module's own [SessionTokenVerifier] checks.
 */
class OrderCancellationControllerTest {

    private val repository = InMemoryOrderRepository()
    private val orderLifecycleApplicationService = OrderLifecycleApplicationService(repository)
    private val secret = Base64.getEncoder().encodeToString("order-cancellation-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val requests = InMemoryOrderCancellationRequestRepository()
    private val coordination = OrderCancellationCoordinationService(
        repository, requests, orderLifecycleApplicationService,
        com.pios.ordermanagement.application.NoOpOutboxRepository,
        com.pios.ordermanagement.application.NoOpTransactionRunner,
        ObjectMapper()
    )
    private val controller = OrderCancellationController(coordination, repository, sessionTokenVerifier)

    // --- Token minting test helper (mirrors ProposalControllerTest's own) ---

    private val objectMapper = ObjectMapper()

    private fun issueToken(sub: String, ttlSeconds: Long = 3600): String {
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", sub)
        payloadNode.putNull("drv")
        payloadNode.put("exp", Instant.now().plusSeconds(ttlSeconds).epochSecond)
        val encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payloadNode))
        val secretBytes = Base64.getDecoder().decode(secret)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val encodedSignature = base64UrlEncode(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
        return "$encodedPayload.$encodedSignature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** A valid session token naming [passengerReference] as its own `sub` -- what `RideRequest.tsx` now sends on cancel. */
    private fun passengerToken(passengerReference: String): String = "Bearer " + issueToken(sub = passengerReference)

    private fun submittedOrderId(origin: String = "passenger-1"): String =
        orderLifecycleApplicationService.submitOrder(SubmitOrderCommand(OrderOrigin(origin))).order.id.value

    @Test
    fun `cancelling a submitted order returns 202 pending Dispatch confirmation`() {
        val orderId = submittedOrderId()

        val response = controller.cancelOrder(orderId, authorization = passengerToken("passenger-1"))

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals(orderId, response.body?.orderId)
        assertEquals("PENDING", response.body?.status)
    }

    @Test
    fun `cancelling a submitted order does not prematurely persist CANCELLED`() {
        val orderId = submittedOrderId()

        controller.cancelOrder(orderId, authorization = passengerToken("passenger-1"))

        assertEquals(OrderStatus.SUBMITTED, repository.findById(OrderId(orderId))?.status)
    }

    @Test
    fun `cancelling an unknown order returns 404`() {
        val response = controller.cancelOrder("never-submitted", authorization = passengerToken("passenger-x"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `cancelling an already-cancelled order returns 409`() {
        val orderId = submittedOrderId()
        controller.cancelOrder(orderId, authorization = passengerToken("passenger-1"))

        val response = controller.cancelOrder(orderId, authorization = passengerToken("passenger-1"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `cancelling an already-completed order returns 409, never overwriting COMPLETED`() {
        val orderId = submittedOrderId()
        orderLifecycleApplicationService.completeOrder(CompleteOrderCommand(OrderId(orderId)))

        val response = controller.cancelOrder(orderId, authorization = passengerToken("passenger-1"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(OrderStatus.COMPLETED, repository.findById(OrderId(orderId))?.status)
    }

    // --- Task 25: Orders Cancellation & Driver Availability Security Remediation ---

    @Test
    fun `cancel -- no Authorization header is rejected before any lookup`() {
        val orderId = submittedOrderId()

        val response = controller.cancelOrder(orderId)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(OrderStatus.SUBMITTED, repository.findById(OrderId(orderId))?.status)
    }

    @Test
    fun `cancel -- a malformed Authorization header is rejected exactly like a missing one`() {
        val orderId = submittedOrderId()

        val response = controller.cancelOrder(orderId, authorization = "not-a-real-scheme")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `cancel -- the owning passenger's own token succeeds`() {
        val orderId = submittedOrderId(origin = "passenger-owner")

        val response = controller.cancelOrder(orderId, authorization = passengerToken("passenger-owner"))

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals("PENDING", response.body?.status)
    }

    @Test
    fun `cancel -- a different passenger's token is rejected -- IDOR`() {
        val orderId = submittedOrderId(origin = "passenger-victim")

        val response = controller.cancelOrder(orderId, authorization = passengerToken("passenger-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(OrderStatus.SUBMITTED, repository.findById(OrderId(orderId))?.status)
    }

    @Test
    fun `cancel -- an unknown order id with a valid token still returns 404, not 403`() {
        val response = controller.cancelOrder("never-submitted-sec", authorization = passengerToken("passenger-does-not-matter"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
