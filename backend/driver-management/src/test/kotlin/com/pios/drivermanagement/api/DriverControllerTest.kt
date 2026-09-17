package com.pios.drivermanagement.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.application.CreateDriverApplicationService
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler
import com.pios.drivermanagement.application.RetrieveDriverClientsHandler
import com.pios.drivermanagement.application.RetrieveDriverMilestonesHandler
import com.pios.drivermanagement.application.UpdateLongDistancePreferenceApplicationService
import com.pios.drivermanagement.application.UpdateVehicleApplicationService
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.persistence.InMemoryDriverClientsRepository
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
import kotlin.test.assertNull
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
    private val milestonesRepository = InMemoryDriverMilestonesRepository()
    private val milestonesHandler = RetrieveDriverMilestonesHandler(milestonesRepository)
    private val clientsRepository = InMemoryDriverClientsRepository()
    private val clientsHandler = RetrieveDriverClientsHandler(clientsRepository)
    private val updateVehicleService = UpdateVehicleApplicationService(repository)
    private val updateLongDistancePreferenceService = UpdateLongDistancePreferenceApplicationService(repository)
    private val secret = Base64.getEncoder().encodeToString("driver-controller-test-secret".toByteArray())
    private val sessionTokenVerifier = SessionTokenVerifier(secretBase64 = secret)
    private val controller = DriverController(
        handler,
        availabilityService,
        createDriverService,
        milestonesHandler,
        clientsHandler,
        updateVehicleService,
        updateLongDistancePreferenceService,
        sessionTokenVerifier,
        OwnerCredentialGate("", "", "", 1_000, 0, 1_000, 900_000),
        repository
    )

    // --- Token minting test helper (mirrors ProposalControllerTest's own) ---

    private val objectMapper = ObjectMapper()

    private fun issueToken(sub: String, drv: String? = null, ttlSeconds: Long = 3600, guest: Boolean = false): String {
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", sub)
        if (drv == null) payloadNode.putNull("drv") else payloadNode.put("drv", drv)
        payloadNode.put("gst", guest)
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

    private fun onboardingToken(identityId: String): String = "Bearer " + issueToken(sub = identityId)

    @Test
    fun `HTTP driver creation requires an authenticated identity`() {
        val response = controller.createDriver(CreateDriverRequest("anonymous-driver"), authorization = null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(repository.findById(DriverId("anonymous-driver")))
    }

    @Test
    fun `HTTP driver creation binds the driver id to the identity subject`() {
        val response = controller.createDriver(
            CreateDriverRequest("victim-driver"),
            authorization = onboardingToken("attacker-identity")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNull(repository.findById(DriverId("victim-driver")))
    }

    @Test
    fun `HTTP driver creation accepts the identity's own id`() {
        val response = controller.createDriver(
            CreateDriverRequest("new-owned-driver"),
            authorization = onboardingToken("new-owned-driver")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(repository.findById(DriverId("new-owned-driver")))
    }

    @Test
    fun `a guest identity cannot create a driver profile`() {
        val response = controller.createDriver(
            CreateDriverRequest("guest-driver"),
            authorization = "Bearer " + issueToken(sub = "guest-driver", guest = true)
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNull(repository.findById(DriverId("guest-driver")))
    }

    @Test
    fun `a regular identity cannot inject test data`() {
        val response = controller.createDriver(
            CreateDriverRequest("test-data-injection", isTest = true),
            authorization = onboardingToken("test-data-injection")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNull(repository.findById(DriverId("test-data-injection")))
    }

    @Test
    fun `HTTP driver enumeration requires owner authentication`() {
        val response = controller.listDrivers(authorization = null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

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

    // --- Vehicle (PIOS Group and Long-Distance Rides Roadmap, Stage 1) ---

    @Test
    fun `vehicle -- the driver's own token succeeds, and getDriver reflects it afterwards`() {
        controller.createDriver(CreateDriverRequest("driver-vehicle-1"))

        val response = controller.updateVehicle(
            "driver-vehicle-1",
            UpdateVehicleRequest(make = "Lada", model = "Vesta", color = "белый", plateNumber = "А123БВ102", seatCount = 4),
            authorization = driverToken("driver-vehicle-1")
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Lada", response.body?.vehicleMake)
        assertEquals(4, response.body?.vehicleSeatCount)
        val fetched = controller.getDriver("driver-vehicle-1")
        assertEquals("Vesta", fetched.body?.vehicleModel)
    }

    @Test
    fun `vehicle -- no Authorization header is rejected`() {
        controller.createDriver(CreateDriverRequest("driver-vehicle-anon"))

        val response = controller.updateVehicle("driver-vehicle-anon", UpdateVehicleRequest(make = "Lada"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `vehicle -- a different driver's token is rejected -- IDOR`() {
        controller.createDriver(CreateDriverRequest("driver-vehicle-victim"))

        val response = controller.updateVehicle(
            "driver-vehicle-victim",
            UpdateVehicleRequest(make = "Lada"),
            authorization = driverToken("driver-vehicle-attacker")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNull(controller.getDriver("driver-vehicle-victim").body?.vehicleMake)
    }

    @Test
    fun `vehicle -- a passenger-only token (drv null) is rejected`() {
        controller.createDriver(CreateDriverRequest("driver-vehicle-passenger-token"))

        val response = controller.updateVehicle(
            "driver-vehicle-passenger-token",
            UpdateVehicleRequest(make = "Lada"),
            authorization = "Bearer " + issueToken(sub = "some-passenger")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `vehicle -- an unknown driver id with a valid, matching token returns 404`() {
        val response = controller.updateVehicle(
            "driver-vehicle-unknown",
            UpdateVehicleRequest(make = "Lada"),
            authorization = driverToken("driver-vehicle-unknown")
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `vehicle -- an invalid seat count returns 400`() {
        controller.createDriver(CreateDriverRequest("driver-vehicle-bad-seats"))

        val response = controller.updateVehicle(
            "driver-vehicle-bad-seats",
            UpdateVehicleRequest(seatCount = 0),
            authorization = driverToken("driver-vehicle-bad-seats")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `vehicle -- getDriver and listDrivers surface it unauthenticated, alongside displayName`() {
        controller.createDriver(CreateDriverRequest("driver-vehicle-public"))
        controller.updateVehicle(
            "driver-vehicle-public",
            UpdateVehicleRequest(make = "Kia", plateNumber = "В456ГД102"),
            authorization = driverToken("driver-vehicle-public")
        )

        val getResponse = controller.getDriver("driver-vehicle-public")
        assertEquals("Kia", getResponse.body?.vehicleMake)
        val listResponse = controller.listDrivers()
        assertTrue(listResponse.body?.any { it.id == "driver-vehicle-public" && it.vehiclePlateNumber == "В456ГД102" } == true)
    }

    // --- Long-distance preference (PIOS Group and Long-Distance Rides Roadmap, Stage 3) ---

    @Test
    fun `long-distance preference -- the driver's own token succeeds, and getDriver reflects it afterwards`() {
        controller.createDriver(CreateDriverRequest("driver-ld-1"))

        val response = controller.updateLongDistancePreference(
            "driver-ld-1",
            UpdateLongDistancePreferenceRequest(accepts = true),
            authorization = driverToken("driver-ld-1")
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body?.acceptsLongDistanceTrips)
        val fetched = controller.getDriver("driver-ld-1")
        assertEquals(true, fetched.body?.acceptsLongDistanceTrips)
    }

    @Test
    fun `long-distance preference -- no Authorization header is rejected`() {
        controller.createDriver(CreateDriverRequest("driver-ld-anon"))

        val response = controller.updateLongDistancePreference("driver-ld-anon", UpdateLongDistancePreferenceRequest(accepts = true))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `long-distance preference -- a different driver's token is rejected -- IDOR`() {
        controller.createDriver(CreateDriverRequest("driver-ld-victim"))

        val response = controller.updateLongDistancePreference(
            "driver-ld-victim",
            UpdateLongDistancePreferenceRequest(accepts = true),
            authorization = driverToken("driver-ld-attacker")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(false, controller.getDriver("driver-ld-victim").body?.acceptsLongDistanceTrips)
    }

    @Test
    fun `long-distance preference -- a passenger-only token (drv null) is rejected`() {
        controller.createDriver(CreateDriverRequest("driver-ld-passenger-token"))

        val response = controller.updateLongDistancePreference(
            "driver-ld-passenger-token",
            UpdateLongDistancePreferenceRequest(accepts = true),
            authorization = "Bearer " + issueToken(sub = "some-passenger")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `long-distance preference -- an unknown driver id with a valid, matching token returns 404`() {
        val response = controller.updateLongDistancePreference(
            "driver-ld-unknown",
            UpdateLongDistancePreferenceRequest(accepts = true),
            authorization = driverToken("driver-ld-unknown")
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `long-distance preference -- getDriver and listDrivers surface it unauthenticated, alongside vehicle`() {
        controller.createDriver(CreateDriverRequest("driver-ld-public"))
        controller.updateLongDistancePreference(
            "driver-ld-public",
            UpdateLongDistancePreferenceRequest(accepts = true),
            authorization = driverToken("driver-ld-public")
        )

        val getResponse = controller.getDriver("driver-ld-public")
        assertEquals(true, getResponse.body?.acceptsLongDistanceTrips)
        val listResponse = controller.listDrivers()
        assertTrue(listResponse.body?.any { it.id == "driver-ld-public" && it.acceptsLongDistanceTrips } == true)
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

    // --- Growth Loops TZ v1, Phase 2: getMilestones ---

    @Test
    fun `getting milestones with no Authorization header is rejected`() {
        val response = controller.getMilestones("milestones-driver-1")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting milestones with a different driver's token is rejected -- IDOR`() {
        val response = controller.getMilestones("milestones-driver-victim", authorization = driverToken("milestones-driver-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `a brand-new driver with no completed rides returns zeroed milestones, including earnings`() {
        val response = controller.getMilestones("milestones-driver-new", authorization = driverToken("milestones-driver-new"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(0L, body.completedRidesCount)
        assertEquals(0L, body.totalStatedEarnings)
        assertEquals(0, body.unpricedRidesCount)
    }

    // --- ADR-065: Driver Earnings from Self-Stated Prices ---

    @Test
    fun `getting milestones for a driver with parsed and unpriced completed rides returns both totals`() {
        milestonesRepository.recordCompletedRide(DriverId("milestones-driver-earnings"), Instant.parse("2026-08-03T09:00:00Z"), statedPriceParsed = 350L)
        milestonesRepository.recordCompletedRide(DriverId("milestones-driver-earnings"), Instant.parse("2026-08-10T09:00:00Z"), statedPriceParsed = null)

        val response = controller.getMilestones("milestones-driver-earnings", authorization = driverToken("milestones-driver-earnings"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(2L, body.completedRidesCount)
        assertEquals(350L, body.totalStatedEarnings)
        assertEquals(1, body.unpricedRidesCount)
    }

    // --- ADR-073: Driver-to-Driver Referral -- Single-Hop Origin Fact ---

    @Test
    fun `creating a driver with a valid inviter persists and returns invitedByDriverId`() {
        controller.createDriver(CreateDriverRequest("adr073-inviter-1"))

        val response = controller.createDriver(CreateDriverRequest("adr073-invited-1", invitedByDriverId = "adr073-inviter-1"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("adr073-inviter-1", repository.findById(DriverId("adr073-invited-1"))?.invitedByDriverId)
    }

    @Test
    fun `creating a driver with no invitedByDriverId leaves it null, unaffected for every existing caller`() {
        val response = controller.createDriver(CreateDriverRequest("adr073-no-inviter"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull(repository.findById(DriverId("adr073-no-inviter"))?.invitedByDriverId)
    }

    @Test
    fun `creating a driver with a self-referencing invitedByDriverId degrades to null and still succeeds`() {
        val response = controller.createDriver(CreateDriverRequest("adr073-self", invitedByDriverId = "adr073-self"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull(repository.findById(DriverId("adr073-self"))?.invitedByDriverId)
    }

    @Test
    fun `creating a driver with a nonexistent invitedByDriverId degrades to null and still succeeds`() {
        val response = controller.createDriver(CreateDriverRequest("adr073-bad-inviter", invitedByDriverId = "no-such-driver"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull(repository.findById(DriverId("adr073-bad-inviter"))?.invitedByDriverId)
    }

    @Test
    fun `milestones exposes invitedDriversCount, real and isTest invitees alike, private to the inviter's own Bearer token`() {
        controller.createDriver(CreateDriverRequest("adr073-milestones-inviter"))
        controller.createDriver(CreateDriverRequest("adr073-milestones-real-1", invitedByDriverId = "adr073-milestones-inviter"))
        controller.createDriver(CreateDriverRequest("adr073-milestones-real-2", invitedByDriverId = "adr073-milestones-inviter"))
        controller.createDriver(CreateDriverRequest("adr073-milestones-test-1", isTest = true, invitedByDriverId = "adr073-milestones-inviter"))

        val response = controller.getMilestones("adr073-milestones-inviter", authorization = driverToken("adr073-milestones-inviter"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(3L, assertNotNull(response.body).invitedDriversCount)
    }

    @Test
    fun `a brand-new driver with nobody invited through their link returns invitedDriversCount zero`() {
        val response = controller.getMilestones("milestones-driver-new", authorization = driverToken("milestones-driver-new"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0L, assertNotNull(response.body).invitedDriversCount)
    }

    // --- Server-side "Мой бизнес" read model (docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md Part 5): getClients ---

    @Test
    fun `getting clients with no Authorization header is rejected`() {
        val response = controller.getClients("clients-driver-1")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getting clients with a different driver's token is rejected -- IDOR`() {
        val response = controller.getClients("clients-driver-victim", authorization = driverToken("clients-driver-attacker"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `a driver with zero rides for any passenger gets an empty clients list, not an error`() {
        val response = controller.getClients("clients-driver-empty", authorization = driverToken("clients-driver-empty"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body)
    }

    @Test
    fun `getClients returns ride count, last-ride timestamp, and the repeat flag for each passenger`() {
        clientsRepository.recordRideForClient(DriverId("clients-driver-a"), "passenger-x", Instant.parse("2026-08-03T09:00:00Z"))
        clientsRepository.recordRideForClient(DriverId("clients-driver-a"), "passenger-x", Instant.parse("2026-08-10T09:00:00Z"))
        clientsRepository.recordRideForClient(DriverId("clients-driver-a"), "passenger-y", Instant.parse("2026-08-05T09:00:00Z"))

        val response = controller.getClients("clients-driver-a", authorization = driverToken("clients-driver-a"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(2, body.size)
        val repeatEntry = body.first { it.passengerReference == "passenger-x" }
        assertEquals(2, repeatEntry.rideCount)
        assertEquals("2026-08-10T09:00:00Z", repeatEntry.lastRideAt)
        assertTrue(repeatEntry.isRepeat)
        val singleRideEntry = body.first { it.passengerReference == "passenger-y" }
        assertEquals(1, singleRideEntry.rideCount)
        assertEquals("2026-08-05T09:00:00Z", singleRideEntry.lastRideAt)
        assertEquals(false, singleRideEntry.isRepeat)
    }

    @Test
    fun `a passenger who rides with two different drivers stays scoped per driver in getClients`() {
        clientsRepository.recordRideForClient(DriverId("clients-driver-scope-a"), "passenger-shared", Instant.parse("2026-08-03T09:00:00Z"))
        clientsRepository.recordRideForClient(DriverId("clients-driver-scope-b"), "passenger-shared", Instant.parse("2026-08-03T09:00:00Z"))
        clientsRepository.recordRideForClient(DriverId("clients-driver-scope-b"), "passenger-shared", Instant.parse("2026-08-10T09:00:00Z"))

        val responseA = controller.getClients("clients-driver-scope-a", authorization = driverToken("clients-driver-scope-a"))
        val responseB = controller.getClients("clients-driver-scope-b", authorization = driverToken("clients-driver-scope-b"))

        val bodyA = assertNotNull(responseA.body)
        assertEquals(1, bodyA.single().rideCount)
        assertEquals(false, bodyA.single().isRepeat)
        val bodyB = assertNotNull(responseB.body)
        assertEquals(2, bodyB.single().rideCount)
        assertTrue(bodyB.single().isRepeat)
    }
}
