package com.pios.ordermanagement.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OrderSubmissionRequestHandler
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Constructs [OrderSubmissionController] directly, with a real
 * [OrderSubmissionRequestHandler]/[OrderLifecycleApplicationService], no
 * Spring MVC context -- mirroring this project's own constructor-based
 * testing convention (Tranche 2: Passenger Experience REST Transport).
 *
 * Order Provenance / Authentication Remediation (P0,
 * `docs/PIOS_DATA_FLOW_CODE_AUDIT.md` Section 5): `submitOrder` now
 * requires an `Authorization` header (see [OrderSubmissionController]'s own
 * KDoc). Token minting mirrors [OrderCancellationControllerTest]'s own
 * already-established pattern exactly (Task 25) -- this module has no
 * build-time dependency on `identity` and never calls it (ADR-055 Decision
 * 1), so a token is minted locally, in the exact format `SessionTokenIssuer`
 * mints and this module's own [SessionTokenVerifier] checks.
 */
class OrderSubmissionControllerTest {

    private val repository = InMemoryOrderRepository()
    private val handler = OrderSubmissionRequestHandler(OrderLifecycleApplicationService(repository))
    private val secret = Base64.getEncoder().encodeToString("order-submission-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val controller = OrderSubmissionController(handler, sessionTokenVerifier)

    // --- Token minting test helper (mirrors OrderCancellationControllerTest's own) ---

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

    /** A valid session token naming [passengerReference] as its own `sub` -- what `RideRequest.tsx` now sends on submit. */
    private fun passengerToken(passengerReference: String): String = "Bearer " + issueToken(sub = passengerReference)

    @Test
    fun `a valid request returns 201 with a new order id`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-1"), authorization = passengerToken("passenger-1"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertTrue(assertNotNull(response.body).orderId.isNotBlank())
    }

    // --- Destination (Sprint 3B: MVR Pilot Enablement -- Optional Destination) ---

    @Test
    fun `a request without a destination still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-no-destination"),
            authorization = passengerToken("passenger-no-destination")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.destination)
    }

    @Test
    fun `a request with a destination persists it`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-with-destination", "Аэропорт"),
            authorization = passengerToken("passenger-with-destination")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals("Аэропорт", repository.findById(OrderId(orderId))?.destination)
    }

    // --- Passenger name (first-pilot feedback) ---

    @Test
    fun `a request with a passenger name persists it`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-named", "Аэропорт", "Мария"),
            authorization = passengerToken("passenger-named")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals("Мария", repository.findById(OrderId(orderId))?.passengerName)
    }

    @Test
    fun `a request without a passenger name still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-unnamed"),
            authorization = passengerToken("passenger-unnamed")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.passengerName)
    }

    // --- Pickup address (Sprint H5: Entrepreneur Working Cycle Integrity) ---

    @Test
    fun `a request with a pickup address persists it`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-with-pickup", pickupAddress = "ул. Ленина, 10"),
            authorization = passengerToken("passenger-with-pickup")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals("ул. Ленина, 10", repository.findById(OrderId(orderId))?.pickupAddress)
    }

    @Test
    fun `a request without a pickup address still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-no-pickup"),
            authorization = passengerToken("passenger-no-pickup")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.pickupAddress)
    }

    // --- Passenger count (PIOS Group and Long-Distance Rides Roadmap, Stage 2) ---

    @Test
    fun `a request with a passenger count persists it`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-group", passengerCount = 4),
            authorization = passengerToken("passenger-group")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals(4, repository.findById(OrderId(orderId))?.passengerCount)
    }

    @Test
    fun `a request without a passenger count still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-solo"),
            authorization = passengerToken("passenger-solo")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.passengerCount)
    }

    @Test
    fun `a request with a zero passenger count returns 400`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-bad-count", passengerCount = 0),
            authorization = passengerToken("passenger-bad-count")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- Notes (Product Cycle: Passenger Ride Requirements) ---

    @Test
    fun `a request with notes persists them`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-with-notes", notes = "Детское кресло, встретить у подъезда"),
            authorization = passengerToken("passenger-with-notes")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals("Детское кресло, встретить у подъезда", repository.findById(OrderId(orderId))?.notes)
    }

    @Test
    fun `a request without notes still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-no-notes"),
            authorization = passengerToken("passenger-no-notes")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.notes)
    }

    @Test
    fun `a request with notes at exactly the length limit persists them`() {
        val notes = "a".repeat(500)
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-notes-max-length", notes = notes),
            authorization = passengerToken("passenger-notes-max-length")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals(500, repository.findById(OrderId(orderId))?.notes?.length)
    }

    @Test
    fun `a request with notes longer than the length limit returns 400`() {
        val tooLong = "a".repeat(501)
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-notes-too-long", notes = tooLong),
            authorization = passengerToken("passenger-notes-too-long")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- Requested pickup time (ADR-058, Scheduled Pickup Time) ---

    @Test
    fun `a request with a requested pickup instant persists it`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-scheduled", requestedPickupAt = "2099-08-25T06:30:00Z"),
            authorization = passengerToken("passenger-scheduled")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertEquals(
            java.time.Instant.parse("2099-08-25T06:30:00Z"),
            repository.findById(OrderId(orderId))?.requestedPickupAt
        )
    }

    @Test
    fun `a request without a requested pickup instant still succeeds -- regression for the existing contract`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-not-scheduled"),
            authorization = passengerToken("passenger-not-scheduled")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertNull(repository.findById(OrderId(orderId))?.requestedPickupAt)
    }

    @Test
    fun `a request with a malformed requested pickup instant returns 400`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-bad-schedule", requestedPickupAt = "tomorrow at 6"),
            authorization = passengerToken("passenger-bad-schedule")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a requested pickup instant in the past returns 400 without saving an order`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-past-schedule", requestedPickupAt = "2000-01-01T00:00:00Z"),
            authorization = passengerToken("passenger-past-schedule")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(null, response.body)
    }

    // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---

    @Test
    fun `a request with isTest true persists an order marked isTest true`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-e2e", isTest = true),
            authorization = passengerToken("passenger-e2e")
        )

        val orderId = assertNotNull(response.body).orderId
        assertEquals(true, repository.findById(OrderId(orderId))?.isTest)
    }

    @Test
    fun `a request without isTest persists an order marked isTest false -- a real order is never marked test by omission`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-real"),
            authorization = passengerToken("passenger-real")
        )

        val orderId = assertNotNull(response.body).orderId
        assertEquals(false, repository.findById(OrderId(orderId))?.isTest)
    }

    // --- Order Provenance / Authentication Remediation (P0, `docs/PIOS_DATA_FLOW_CODE_AUDIT.md` Section 5) ---

    @Test
    fun `submit -- no Authorization header is rejected before any lookup, and no order is created`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-1"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `submit -- a malformed Authorization header is rejected exactly like a missing one`() {
        val response = controller.submitOrder(SubmitOrderRequest("passenger-1"), authorization = "not-a-real-scheme")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `submit -- a token naming a different passenger than the request body is rejected -- IDOR`() {
        val response = controller.submitOrder(
            SubmitOrderRequest("passenger-victim"),
            authorization = passengerToken("passenger-attacker")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(repository.findAll().isEmpty())
    }

    /**
     * Supersedes this class's own pre-remediation "a blank passenger
     * reference returns 400" test: a valid session token can never carry a
     * blank `sub` ([SessionTokenVerifier.verify] already rejects that,
     * unconditionally), so a blank `passengerReference` can never equal a
     * real token's own `sub` -- it now always falls into the same 403
     * mismatch path as any other wrong passenger, checked before
     * [SubmitOrderRequest.passengerReference]'s own domain validation is
     * ever reached. The domain-level 400 for a blank reference is not
     * removed (still real, still tested by
     * [OrderSubmissionRequestHandler]'s own unit tests) -- it is simply no
     * longer reachable through this now-authenticated endpoint, which is
     * the intended effect of the fix, not an accidental side effect.
     */
    @Test
    fun `submit -- a blank passenger reference is rejected as a token mismatch (403), not as 400 -- auth is checked first`() {
        val response = controller.submitOrder(
            SubmitOrderRequest(""),
            authorization = passengerToken("passenger-1")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(repository.findAll().isEmpty())
    }
}
