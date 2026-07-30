package com.pios.identity.api

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.CreateIdentityApplicationService
import com.pios.identity.application.CreateIdentityCommand
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.persistence.InMemoryIdentityRepository
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Constructs [IdentityController] directly, with real, in-memory-backed
 * application services -- no Spring MVC context, mirroring
 * `com.pios.networkmanagement.api.PersonControllerTest`'s own convention.
 */
class IdentityControllerTest {
    private val repository = InMemoryIdentityRepository()
    private val createService = CreateIdentityApplicationService(repository)
    private val associateDriverService = AssociateDriverApplicationService(repository)
    private val controller = IdentityController(createService, RetrieveIdentityHandler(repository), associateDriverService)

    @Test
    fun `creating an identity returns 201 with its id and phone`() {
        val response = controller.createIdentity(CreateIdentityRequest("+79991234567"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("+79991234567", body.phone)
        assertNotNull(body.id)
    }

    @Test
    fun `creating an identity without a phone returns 201 with a null phone`() {
        val response = controller.createIdentity(CreateIdentityRequest(null))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull(assertNotNull(response.body).phone)
    }

    @Test
    fun `creating an identity with a malformed phone returns 400`() {
        val response = controller.createIdentity(CreateIdentityRequest("not-a-phone"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `getting a known identity returns 200`() {
        val created = createService.handle(CreateIdentityCommand("+79991234567"))

        val response = controller.getIdentity(created.id.value)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("+79991234567", assertNotNull(response.body).phone)
    }

    @Test
    fun `getting an unknown identity returns 404`() {
        val response = controller.getIdentity("unknown-identity")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `associating a driver returns 200 with the driver id attached`() {
        val created = createService.handle(CreateIdentityCommand(null))

        val response = controller.associateDriver(created.id.value, AssociateDriverRequest("ILDAR001"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ILDAR001", assertNotNull(response.body).driverId)
    }

    @Test
    fun `associating a driver with an unknown identity returns 404`() {
        val response = controller.associateDriver("unknown-identity", AssociateDriverRequest("ILDAR001"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `associating a blank driver id returns 400`() {
        val created = createService.handle(CreateIdentityCommand(null))

        val response = controller.associateDriver(created.id.value, AssociateDriverRequest(""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a retrieved identity reflects its associated driver`() {
        val created = createService.handle(CreateIdentityCommand(null))
        controller.associateDriver(created.id.value, AssociateDriverRequest("ILDAR001"))

        val response = controller.getIdentity(created.id.value)

        assertEquals("ILDAR001", assertNotNull(response.body).driverId)
    }
}
