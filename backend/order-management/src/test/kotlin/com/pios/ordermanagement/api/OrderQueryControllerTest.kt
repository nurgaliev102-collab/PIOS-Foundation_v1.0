package com.pios.ordermanagement.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.RetrieveOrdersHandler
import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Constructs [OrderQueryController] directly, with a real
 * [RetrieveOrdersHandler], no Spring MVC context -- mirroring this
 * project's own constructor-based testing convention.
 *
 * ADR-060 (Order Query Authorization): `listOrders` used to return every
 * order to any caller, unauthenticated. This suite proves the P0 fix --
 * every mode of the access matrix, and specifically the IDOR/BOLA cases
 * ADR-060's own "What implementation must verify" names: passenger A
 * cannot read passenger B's orders, a passenger-only token cannot use
 * `?ids=`, and no header returns nothing.
 *
 * Token minting mirrors `passenger-experience`'s own
 * `ConnectionControllerTest`: this module has no build-time dependency on
 * `identity` and never calls it (ADR-055 Decision 1), so this test mints
 * tokens itself, in the exact format `SessionTokenIssuer` mints and this
 * module's own [SessionTokenVerifier] checks.
 */
class OrderQueryControllerTest {

    private val secret = Base64.getEncoder().encodeToString("order-query-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)

    private val ownerSalt = "order-query-owner-salt".toByteArray()
    private val ownerIterations = 1000
    private val ownerPassword = "owner-password"
    private val ownerCredentialGate = OwnerCredentialGate(
        configuredUsername = "owner",
        configuredPasswordHash = Base64.getEncoder().encodeToString(deriveKey(ownerPassword, ownerSalt, ownerIterations)),
        configuredPasswordSalt = Base64.getEncoder().encodeToString(ownerSalt),
        iterations = ownerIterations,
        failureDelayMillis = 0,
        maxFailuresPerWindow = 1000,
        windowMillis = 900_000
    )

    private val repository = InMemoryOrderRepository()
    private val handler = RetrieveOrdersHandler(repository)
    private val controller = OrderQueryController(handler, ownerCredentialGate, sessionTokenVerifier)

    // --- Token minting test helper (see class KDoc) ---

    private val objectMapper = ObjectMapper()

    private fun issueToken(sub: String, drv: String? = null, ttlSeconds: Long = 3600): String {
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", sub)
        if (drv == null) payloadNode.putNull("drv") else payloadNode.put("drv", drv)
        payloadNode.put("exp", Instant.now().plusSeconds(ttlSeconds).epochSecond)
        val encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payloadNode))
        val secretBytes = Base64.getDecoder().decode(secret)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val encodedSignature = base64UrlEncode(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
        return "$encodedPayload.$encodedSignature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun bearer(token: String): String = "Bearer $token"

    private fun basicHeader(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    // Passenger A and B, each with their own real order.
    private val passengerAId = "passenger-a"
    private val passengerBId = "passenger-b"
    private val passengerAToken = bearer(issueToken(sub = passengerAId))
    private val passengerBToken = bearer(issueToken(sub = passengerBId))
    private val driverToken = bearer(issueToken(sub = "driver-identity", drv = "driver-1"))
    private val otherDriverToken = bearer(issueToken(sub = "other-driver-identity", drv = "driver-2"))

    private fun seedOrder(origin: String): Order {
        val submitted = Order.submit(OrderOrigin(origin))
        repository.save(submitted.order)
        return submitted.order
    }

    // --- No credential at all: 401 on every mode (the vulnerability this ADR closes) ---

    @Test
    fun `no Authorization header with no parameter returns 401, not the full list`() {
        seedOrder(passengerAId)

        val response = controller.listOrders(null, null, null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(response.body)
    }

    @Test
    fun `no Authorization header with passengerReference returns 401`() {
        val response = controller.listOrders(null, passengerAId, null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `no Authorization header with ids returns 401`() {
        val order = seedOrder(passengerAId)

        val response = controller.listOrders(null, null, order.id.value)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `a malformed Bearer token returns 401`() {
        val response = controller.listOrders("Bearer not-a-real-token", null, null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `an expired Bearer token returns 401`() {
        val expired = bearer(issueToken(sub = passengerAId, ttlSeconds = -3600))

        val response = controller.listOrders(expired, passengerAId, null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `a Bearer token with a tampered signature returns 401`() {
        val real = bearer(issueToken(sub = passengerAId))
        val tampered = real.dropLast(2) + "xx"

        val response = controller.listOrders(tampered, passengerAId, null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `a bad Basic credential returns 401`() {
        val response = controller.listOrders(basicHeader("owner", "wrong-password"), null, null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    // --- IDOR -- passenger A must never receive passenger B's orders ---

    @Test
    fun `passenger A requesting passenger B's passengerReference is forbidden`() {
        seedOrder(passengerBId)

        val response = controller.listOrders(passengerAToken, passengerBId, null)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNull(response.body)
    }

    @Test
    fun `a passenger reading their own passengerReference receives only their own orders`() {
        val ownOrder = seedOrder(passengerAId)
        seedOrder(passengerBId)

        val response = controller.listOrders(passengerAToken, passengerAId, null)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(1, body.size)
        assertEquals(ownOrder.id.value, body.first().id)
    }

    // --- BOLA -- a passenger-only token must not use `?ids=` at all ---

    @Test
    fun `a passenger token (drv null) using ids is forbidden`() {
        val order = seedOrder(passengerAId)

        val response = controller.listOrders(passengerAToken, null, order.id.value)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    // --- Any Bearer with no parameter at all: forbidden, not the full list ---

    @Test
    fun `a valid passenger Bearer with no parameter is forbidden`() {
        val response = controller.listOrders(passengerAToken, null, null)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `a valid driver Bearer with no parameter is forbidden`() {
        val response = controller.listOrders(driverToken, null, null)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    // --- Driver mode: only the subset of named ids that exist, never another driver's unrequested orders ---

    @Test
    fun `a driver receives only the subset of requested ids that exist`() {
        val ownOrder = seedOrder(passengerAId)
        seedOrder(passengerBId) // never named in `ids` -- must not appear

        val response = controller.listOrders(driverToken, null, "${ownOrder.id.value},not-a-real-id")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(1, body.size)
        assertEquals(ownOrder.id.value, body.first().id)
    }

    @Test
    fun `an unknown id in ids is simply absent, not a 404`() {
        val response = controller.listOrders(driverToken, null, "not-a-real-id")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }

    @Test
    fun `either driver's token may fetch a named order -- ids grants no per-order ownership check (ADR-060 Decision 3, disclosed)`() {
        val order = seedOrder(passengerAId)

        val response = controller.listOrders(otherDriverToken, null, order.id.value)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.size)
    }

    // --- Shape guards: 400, checked after authentication ---

    @Test
    fun `both passengerReference and ids present returns 400`() {
        val response = controller.listOrders(driverToken, passengerAId, "some-id")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `ids containing a blank element returns 400`() {
        val order = seedOrder(passengerAId)

        val response = controller.listOrders(driverToken, null, "${order.id.value},")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `ids with more than 100 elements returns 400`() {
        val tooMany = (1..101).joinToString(",") { "id-$it" }

        val response = controller.listOrders(driverToken, null, tooMany)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- Owner mode (Basic): full list, but never mixed with a filter parameter ---

    @Test
    fun `a valid owner credential with no parameter returns every order`() {
        seedOrder(passengerAId)
        seedOrder(passengerBId)

        val response = controller.listOrders(basicHeader("owner", ownerPassword), null, null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2, response.body?.size)
    }

    @Test
    fun `a valid owner credential with passengerReference is a malformed request, not filtered owner access`() {
        val response = controller.listOrders(basicHeader("owner", ownerPassword), passengerAId, null)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a valid owner credential with ids is a malformed request`() {
        val order = seedOrder(passengerAId)

        val response = controller.listOrders(basicHeader("owner", ownerPassword), null, order.id.value)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- Regression: response shape is unchanged (ADR-060 Answer 3 -- row-level filtering only) ---

    @Test
    fun `a returned order still carries every existing field, unchanged shape`() {
        val submitted = Order.submit(
            origin = OrderOrigin(passengerAId),
            destination = "Аэропорт Уфа",
            passengerName = "Аня",
            pickupAddress = "Агидель",
            requestedPickupAt = Instant.parse("2026-08-25T06:30:00Z")
        )
        repository.save(submitted.order)

        val response = controller.listOrders(passengerAToken, passengerAId, null)

        val body = assertNotNull(response.body)
        val order = body.first()
        assertEquals(submitted.order.id.value, order.id)
        assertEquals("SUBMITTED", order.status)
        assertEquals(passengerAId, order.origin)
        assertEquals("Аэропорт Уфа", order.destination)
        assertEquals("Аня", order.passengerName)
        assertEquals("Агидель", order.pickupAddress)
        assertTrue(order.requestedPickupAt?.startsWith("2026-08-25T06:30:00") == true)
    }
}
