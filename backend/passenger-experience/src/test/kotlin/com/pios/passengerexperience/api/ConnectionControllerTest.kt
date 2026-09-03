package com.pios.passengerexperience.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.passengerexperience.application.CreateConnectionApplicationService
import com.pios.passengerexperience.application.RemoveConnectionApplicationService
import com.pios.passengerexperience.application.RetrieveConnectionHandler
import com.pios.passengerexperience.application.RetrieveConnectionsForDriverHandler
import com.pios.passengerexperience.application.RetrieveConnectionsForPassengerHandler
import com.pios.passengerexperience.application.SetPrimaryConnectionApplicationService
import com.pios.passengerexperience.persistence.InMemoryConnectionRepository
import com.pios.passengerexperience.persistence.InMemoryPrimaryConnectionRepository
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Constructs [ConnectionController] directly, with real, in-memory
 * -backed application services -- no Spring MVC context, mirroring
 * `com.pios.drivermanagement.api.DriverControllerTest`'s own convention.
 *
 * [primaryConnectionRepository] is wired into [connectionRepository]'s own
 * `onConnectionDeleted` callback so the in-memory doubles replicate the
 * real `primary_connections` table's `ON DELETE CASCADE` -- see
 * [InMemoryConnectionRepository]'s own KDoc (ADR-054, Circle of Trust).
 *
 * Every request now carries a `Bearer` token minted by [issueToken] --
 * this module has no build-time dependency on `identity` and never calls
 * it (ADR-055 Decision 1), so this test mints tokens itself, using the
 * exact same format `com.pios.identity.application.SessionTokenIssuer`
 * mints and this module's own [SessionTokenVerifier] checks. The
 * pre-ADR-055 (ADR-054) test scenarios are preserved unweakened: each one
 * now authenticates as the passenger/driver it was already exercising,
 * rather than being deleted or its assertions loosened.
 */
class ConnectionControllerTest {
    private val secret = Base64.getEncoder().encodeToString("connection-controller-test-secret".toByteArray())
    private val primaryConnectionRepository = InMemoryPrimaryConnectionRepository()
    private val repository = InMemoryConnectionRepository(
        onConnectionDeleted = primaryConnectionRepository::removeIfPointingAt
    )
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val controller = ConnectionController(
        CreateConnectionApplicationService(repository),
        RetrieveConnectionsForDriverHandler(repository),
        RetrieveConnectionsForPassengerHandler(repository, primaryConnectionRepository),
        SetPrimaryConnectionApplicationService(repository, primaryConnectionRepository),
        RemoveConnectionApplicationService(repository, primaryConnectionRepository),
        RetrieveConnectionHandler(repository),
        sessionTokenVerifier
    )

    // --- Token minting test helper (see class KDoc: replicates
    // SessionTokenIssuer's exact format, this module has no dependency on
    // identity's own class) ---

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

    // Passenger tokens: sub == the passengerReference string these tests
    // already used throughout (ADR-054's own fixture names).
    private val reginaToken = bearer(issueToken(sub = "regina"))
    private val aigulToken = bearer(issueToken(sub = "aigul"))

    // Driver tokens: drv == the driverId string; sub is an arbitrary
    // identity id, never checked by this module (it does not own drivers).
    private val arturToken = bearer(issueToken(sub = "artur-identity", drv = "artur"))
    private val mansurToken = bearer(issueToken(sub = "mansur-identity", drv = "mansur"))

    @Test
    fun `creating a connection for the first time returns 201`() {
        val response = controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("regina", assertNotNull(response.body).passengerReference)
    }

    @Test
    fun `opening the same link a second time returns 200, not 409`() {
        controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken)

        val response = controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `creating a connection with a blank driverId returns 400`() {
        val response = controller.createConnection(CreateConnectionRequest("", "regina"), reginaToken)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `getting connections for a driver returns every connected passenger`() {
        controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken)

        val response = controller.getConnectionsForDriver("artur", arturToken)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(listOf("regina"), body.map { it.passengerReference })
    }

    @Test
    fun `getting connections for a driver with none returns an empty list`() {
        val response = controller.getConnectionsForDriver("nobody", bearer(issueToken(sub = "nobody-identity", drv = "nobody")))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }

    @Test
    fun `each connection carries when it was created, so a driver can tell today's from earlier`() {
        controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken)

        val response = controller.getConnectionsForDriver("artur", arturToken)

        val body = assertNotNull(response.body)
        assertNotNull(body.single().createdAt)
    }

    @Test
    fun `getting connections for a passenger returns every connection with isPrimary false when none is set`() {
        controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken)
        controller.createConnection(CreateConnectionRequest("mansur", "regina"), reginaToken)

        val response = controller.getConnectionsForPassenger("regina", reginaToken)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(setOf("artur", "mansur"), body.map { it.driverId }.toSet())
        assertEquals(listOf(false, false), body.map { it.isPrimary })
    }

    @Test
    fun `setting a primary makes exactly that connection primary and every other one not`() {
        val first = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)
        val second = assertNotNull(controller.createConnection(CreateConnectionRequest("mansur", "regina"), reginaToken).body)

        val setResponse = controller.setPrimaryConnection(first.connectionId, reginaToken)

        assertEquals(HttpStatus.OK, setResponse.statusCode)
        assertEquals(true, assertNotNull(setResponse.body).isPrimary)

        val listed = assertNotNull(controller.getConnectionsForPassenger("regina", reginaToken).body)
        assertEquals(true, listed.single { it.connectionId == first.connectionId }.isPrimary)
        assertEquals(false, listed.single { it.connectionId == second.connectionId }.isPrimary)
    }

    @Test
    fun `setting a primary twice never leaves two primaries for the same passenger`() {
        val first = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)
        val second = assertNotNull(controller.createConnection(CreateConnectionRequest("mansur", "regina"), reginaToken).body)

        controller.setPrimaryConnection(first.connectionId, reginaToken)
        controller.setPrimaryConnection(second.connectionId, reginaToken)

        val listed = assertNotNull(controller.getConnectionsForPassenger("regina", reginaToken).body)
        assertEquals(1, listed.count { it.isPrimary })
        assertEquals(true, listed.single { it.connectionId == second.connectionId }.isPrimary)
    }

    @Test
    fun `setting a primary twice on the same connection still leaves exactly one primary`() {
        val first = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)

        controller.setPrimaryConnection(first.connectionId, reginaToken)
        controller.setPrimaryConnection(first.connectionId, reginaToken)

        val listed = assertNotNull(controller.getConnectionsForPassenger("regina", reginaToken).body)
        assertEquals(1, listed.count { it.isPrimary })
    }

    @Test
    fun `setting a primary for one passenger does not affect another passenger's own primary`() {
        val forRegina = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)
        val forAigul = assertNotNull(controller.createConnection(CreateConnectionRequest("mansur", "aigul"), aigulToken).body)

        controller.setPrimaryConnection(forRegina.connectionId, reginaToken)
        controller.setPrimaryConnection(forAigul.connectionId, aigulToken)

        val reginaListed = assertNotNull(controller.getConnectionsForPassenger("regina", reginaToken).body)
        val aigulListed = assertNotNull(controller.getConnectionsForPassenger("aigul", aigulToken).body)
        assertEquals(true, reginaListed.single { it.connectionId == forRegina.connectionId }.isPrimary)
        assertEquals(true, aigulListed.single { it.connectionId == forAigul.connectionId }.isPrimary)
    }

    @Test
    fun `setting a primary on a non-existent connectionId returns 404`() {
        val response = controller.setPrimaryConnection("no-such-connection", reginaToken)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `removing a connection means it no longer appears in that passenger's list`() {
        val first = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)
        controller.createConnection(CreateConnectionRequest("mansur", "regina"), reginaToken)

        val removeResponse = controller.removeConnection(first.connectionId, reginaToken)

        assertEquals(HttpStatus.NO_CONTENT, removeResponse.statusCode)
        val listed = assertNotNull(controller.getConnectionsForPassenger("regina", reginaToken).body)
        assertEquals(listOf("mansur"), listed.map { it.driverId })
    }

    @Test
    fun `removing an already-gone connectionId still returns 204`() {
        val response = controller.removeConnection("never-existed", reginaToken)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `removing the currently-primary connection also clears the primary designation`() {
        val first = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)
        controller.setPrimaryConnection(first.connectionId, reginaToken)

        controller.removeConnection(first.connectionId, reginaToken)

        val listed = assertNotNull(controller.getConnectionsForPassenger("regina", reginaToken).body)
        assertEquals(emptyList(), listed)
        // Re-add the same driver: if the cascade hadn't cleared the stale
        // primary designation, this fresh connection would still show up
        // as primary despite never having been designated -- this is the
        // assertion that would fail without ADR-054 Part 2's ON DELETE
        // CASCADE (or its in-memory replica).
        controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken)
        val relisted = assertNotNull(controller.getConnectionsForPassenger("regina", reginaToken).body)
        assertEquals(listOf(false), relisted.map { it.isPrimary })
    }

    @Test
    fun `getting connections for a driver is unaffected by the passenger-side surface`() {
        val created = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)
        controller.setPrimaryConnection(created.connectionId, reginaToken)

        val response = controller.getConnectionsForDriver("artur", arturToken)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(listOf("regina"), body.map { it.passengerReference })
    }

    // --- ADR-055: no/garbage token is 401 on all five endpoints ---

    @Test
    fun `creating a connection with no token returns 401`() {
        val response = controller.createConnection(CreateConnectionRequest("artur", "regina"), null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `creating a connection with a garbage token returns 401`() {
        val response = controller.createConnection(CreateConnectionRequest("artur", "regina"), bearer("garbage.garbage"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting connections for a passenger with no token returns 401`() {
        val response = controller.getConnectionsForPassenger("regina", null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting connections for a passenger with a garbage token returns 401`() {
        val response = controller.getConnectionsForPassenger("regina", bearer("garbage.garbage"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting connections for a driver with no token returns 401`() {
        val response = controller.getConnectionsForDriver("artur", null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting connections for a driver with a garbage token returns 401`() {
        val response = controller.getConnectionsForDriver("artur", bearer("garbage.garbage"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `setting a primary with no token returns 401`() {
        val first = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)

        val response = controller.setPrimaryConnection(first.connectionId, null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `setting a primary with a garbage token returns 401`() {
        val first = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)

        val response = controller.setPrimaryConnection(first.connectionId, bearer("garbage.garbage"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `removing a connection with no token returns 401`() {
        val first = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)

        val response = controller.removeConnection(first.connectionId, null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `removing a connection with a garbage token returns 401`() {
        val first = assertNotNull(controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken).body)

        val response = controller.removeConnection(first.connectionId, bearer("garbage.garbage"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    // --- ADR-055: passenger A cannot read or write passenger B's own
    // circle, on any of the five endpoints -- the core security property
    // this Sprint exists to establish. ---

    @Test
    fun `creating a connection with someone else's passengerReference returns 403, and nothing is created for the impersonated passenger`() {
        val response = controller.createConnection(CreateConnectionRequest("artur", "aigul"), reginaToken)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(emptyList(), controller.getConnectionsForPassenger("aigul", aigulToken).body)
    }

    @Test
    fun `reading another passenger's connections with your own valid token returns 403, not their data`() {
        controller.createConnection(CreateConnectionRequest("mansur", "aigul"), aigulToken)

        val response = controller.getConnectionsForPassenger("aigul", reginaToken)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `reading a driver's connections with a token for a different driver returns 403, not their data`() {
        controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken)

        val response = controller.getConnectionsForDriver("artur", mansurToken)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `reading a driver's connections with a passenger-only token (no drv claim) returns 403`() {
        controller.createConnection(CreateConnectionRequest("artur", "regina"), reginaToken)

        val response = controller.getConnectionsForDriver("artur", reginaToken)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `setting another passenger's connection as primary with your own valid token returns 404, and their primary is unaffected`() {
        val aigulsConnection = assertNotNull(controller.createConnection(CreateConnectionRequest("mansur", "aigul"), aigulToken).body)

        val response = controller.setPrimaryConnection(aigulsConnection.connectionId, reginaToken)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val aigulListed = assertNotNull(controller.getConnectionsForPassenger("aigul", aigulToken).body)
        assertEquals(false, aigulListed.single { it.connectionId == aigulsConnection.connectionId }.isPrimary)
    }

    @Test
    fun `deleting another passenger's connection with your own valid token returns 204 but deletes nothing`() {
        val aigulsConnection = assertNotNull(controller.createConnection(CreateConnectionRequest("mansur", "aigul"), aigulToken).body)

        val response = controller.removeConnection(aigulsConnection.connectionId, reginaToken)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val aigulListed = assertNotNull(controller.getConnectionsForPassenger("aigul", aigulToken).body)
        assertTrue(aigulListed.any { it.connectionId == aigulsConnection.connectionId })
    }
}
