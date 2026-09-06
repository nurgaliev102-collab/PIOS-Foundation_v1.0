package com.pios.drivermanagement.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.application.CreateDriverApplicationService
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler
import com.pios.drivermanagement.application.RetrieveDriverMilestonesHandler
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.InMemoryDriverMilestonesRepository
import com.pios.drivermanagement.persistence.InMemoryDriverRepository
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
 * Constructs [DriverController] directly, with a real
 * [RetrieveDriverAvailabilityHandler]/[DriverAvailabilityApplicationService]/
 * [CreateDriverApplicationService], no Spring MVC context -- mirroring
 * this project's own constructor-based testing convention (Tranche 2:
 * Passenger Experience REST Transport).
 *
 * Task 25 (Orders Cancellation & Driver Availability Security
 * Remediation): `declareAvailability` now requires an `Authorization`
 * header (see [DriverController]'s own KDoc). Token minting mirrors
 * `com.pios.dispatch.api.ProposalControllerTest`'s own already-established
 * pattern exactly (Task 21) -- this module has no build-time dependency on
 * `identity` and never calls it (ADR-055 Decision 1), so a token is minted
 * locally, in the exact format `SessionTokenIssuer` mints and this
 * module's own new [SessionTokenVerifier] checks. `createDriver`/
 * `getDriver`/`listDrivers` are untouched by Task 25 (see that task's own
 * scope) -- every test exercising them below is unchanged from before this
 * task.
 */
class DriverControllerTest {

    private val repository = InMemoryDriverRepository()
    private val handler = RetrieveDriverAvailabilityHandler(repository)
    private val availabilityService = DriverAvailabilityApplicationService(repository)
    private val createDriverService = CreateDriverApplicationService(repository)
    private val milestonesHandler = RetrieveDriverMilestonesHandler(InMemoryDriverMilestonesRepository())
    private val secret = Base64.getEncoder().encodeToString("driver-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val controller = DriverController(handler, availabilityService, createDriverService, milestonesHandler, sessionTokenVerifier)

    // --- Token minting test helper (mirrors ProposalControllerTest's own) ---

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

    /** A valid session token naming [driverId] as its own `drv` -- what `DriverHome.tsx` now sends on toggleAvailability. */
    private fun driverToken(driverId: String): String = "Bearer " + issueToken(sub = "$driverId-identity", drv = driverId)

    @Test
    fun `creating a driver returns 201 with the new driver's id and default availability`() {
        val response = controller.createDriver(CreateDriverRequest("new-driver-1"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("new-driver-1", body.id)
        assertEquals("UNAVAILABLE", body.availability)
        assertEquals(Availability.UNAVAILABLE, repository.findById(DriverId("new-driver-1"))?.availability)
    }

    @Test
    fun `creating a driver stamps registeredAt, retrievable afterwards`() {
        val response = controller.createDriver(CreateDriverRequest("new-driver-registered-at"))

        assertNotNull(assertNotNull(response.body).registeredAt)
        assertNotNull(repository.findById(DriverId("new-driver-registered-at"))?.createdAt)

        val getResponse = controller.getDriver("new-driver-registered-at")
        assertNotNull(assertNotNull(getResponse.body).registeredAt)
    }

    @Test
    fun `creating a driver for an id that already exists returns 409`() {
        repository.save(Driver(DriverId("existing-driver"), Availability.AVAILABLE))

        val response = controller.createDriver(CreateDriverRequest("existing-driver"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(Availability.AVAILABLE, repository.findById(DriverId("existing-driver"))?.availability)
    }

    @Test
    fun `creating a driver with a blank id returns 400`() {
        val response = controller.createDriver(CreateDriverRequest(""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `creating a driver with a displayName returns 201 with that displayName, and it is retrievable`() {
        val response = controller.createDriver(CreateDriverRequest("driver-with-name", "Артур"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("Артур", assertNotNull(response.body).displayName)
        assertEquals("Артур", repository.findById(DriverId("driver-with-name"))?.displayName)

        val getResponse = controller.getDriver("driver-with-name")
        assertEquals("Артур", assertNotNull(getResponse.body).displayName)
    }

    @Test
    fun `a known driver id returns 200 with id and availability`() {
        repository.save(Driver(DriverId("driver-1"), Availability.AVAILABLE))

        val response = controller.getDriver("driver-1")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("driver-1", body.id)
        assertEquals("AVAILABLE", body.availability)
    }

    @Test
    fun `an unknown driver id returns 404`() {
        val response = controller.getDriver("unknown")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `a blank driver id returns 400`() {
        val response = controller.getDriver("")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listing drivers returns every saved driver`() {
        repository.save(Driver(DriverId("list-1"), Availability.AVAILABLE))
        repository.save(Driver(DriverId("list-2"), Availability.UNAVAILABLE))

        val response = controller.listDrivers()

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertTrue(body.any { it.id == "list-1" && it.availability == "AVAILABLE" })
        assertTrue(body.any { it.id == "list-2" && it.availability == "UNAVAILABLE" })
    }

    @Test
    fun `listing drivers when none exist returns an empty list`() {
        val response = controller.listDrivers()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }

    @Test
    fun `declaring availability for a known driver returns 200 with the new availability`() {
        repository.save(Driver(DriverId("driver-2"), Availability.UNAVAILABLE))

        val response = controller.declareAvailability("driver-2", DeclareAvailabilityRequest("AVAILABLE"), authorization = driverToken("driver-2"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("driver-2", body.id)
        assertEquals("AVAILABLE", body.availability)
        assertEquals(Availability.AVAILABLE, repository.findById(DriverId("driver-2"))?.availability)
    }

    @Test
    fun `declaring availability for an unknown driver returns 404`() {
        val response = controller.declareAvailability("unknown", DeclareAvailabilityRequest("AVAILABLE"), authorization = driverToken("unknown"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `declaring an unrecognized availability value returns 400`() {
        repository.save(Driver(DriverId("driver-3"), Availability.UNAVAILABLE))

        val response = controller.declareAvailability("driver-3", DeclareAvailabilityRequest("BUSY"), authorization = driverToken("driver-3"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // --- Task 25: Orders Cancellation & Driver Availability Security Remediation ---

    @Test
    fun `availability -- no Authorization header is rejected`() {
        repository.save(Driver(DriverId("driver-sec-1"), Availability.UNAVAILABLE))

        val response = controller.declareAvailability("driver-sec-1", DeclareAvailabilityRequest("AVAILABLE"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(Availability.UNAVAILABLE, repository.findById(DriverId("driver-sec-1"))?.availability)
    }

    @Test
    fun `availability -- a malformed Authorization header is rejected exactly like a missing one`() {
        repository.save(Driver(DriverId("driver-sec-2"), Availability.UNAVAILABLE))

        val response = controller.declareAvailability("driver-sec-2", DeclareAvailabilityRequest("AVAILABLE"), authorization = "not-a-real-scheme")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `availability -- the driver's own token succeeds`() {
        repository.save(Driver(DriverId("driver-sec-own"), Availability.UNAVAILABLE))

        val response = controller.declareAvailability("driver-sec-own", DeclareAvailabilityRequest("AVAILABLE"), authorization = driverToken("driver-sec-own"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("AVAILABLE", response.body?.availability)
    }

    @Test
    fun `availability -- a different driver's token is rejected -- IDOR`() {
        repository.save(Driver(DriverId("driver-sec-victim"), Availability.UNAVAILABLE))

        val response = controller.declareAvailability("driver-sec-victim", DeclareAvailabilityRequest("AVAILABLE"), authorization = driverToken("driver-sec-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(Availability.UNAVAILABLE, repository.findById(DriverId("driver-sec-victim"))?.availability)
    }

    @Test
    fun `availability -- a passenger-only token (drv null) is rejected, even for a real driver`() {
        repository.save(Driver(DriverId("driver-sec-3"), Availability.UNAVAILABLE))

        val response = controller.declareAvailability(
            "driver-sec-3",
            DeclareAvailabilityRequest("AVAILABLE"),
            authorization = "Bearer " + issueToken(sub = "some-passenger")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `availability -- an unknown driver id with a valid, matching token still returns 404`() {
        val response = controller.declareAvailability(
            "driver-sec-unknown",
            DeclareAvailabilityRequest("AVAILABLE"),
            authorization = driverToken("driver-sec-unknown")
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---

    @Test
    fun `creating a driver with isTest true persists and returns isTest true`() {
        val response = controller.createDriver(CreateDriverRequest("e2e-driver-1", isTest = true))

        assertEquals(true, assertNotNull(response.body).isTest)
        assertEquals(true, repository.findById(DriverId("e2e-driver-1"))?.isTest)
    }

    @Test
    fun `creating a driver without isTest defaults to isTest false -- a real driver is never marked test by omission`() {
        val response = controller.createDriver(CreateDriverRequest("real-driver-1"))

        assertEquals(false, assertNotNull(response.body).isTest)
        assertEquals(false, repository.findById(DriverId("real-driver-1"))?.isTest)
    }

    @Test
    fun `listing drivers surfaces each driver's own isTest, unaffected by any other driver's`() {
        repository.save(Driver(DriverId("mix-real"), isTest = false))
        repository.save(Driver(DriverId("mix-test"), isTest = true))

        val response = controller.listDrivers()

        val body = assertNotNull(response.body)
        assertEquals(false, body.first { it.id == "mix-real" }.isTest)
        assertEquals(true, body.first { it.id == "mix-test" }.isTest)
    }
}
