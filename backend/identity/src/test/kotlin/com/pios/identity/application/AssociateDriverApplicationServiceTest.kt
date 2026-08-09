package com.pios.identity.application

import com.pios.identity.api.SessionTokenVerifier
import com.pios.identity.persistence.InMemoryIdentityRepository
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AssociateDriverApplicationServiceTest {
    private val secret = Base64.getEncoder().encodeToString("associate-driver-test-secret-32by".toByteArray())
    private val repository = InMemoryIdentityRepository()
    private val createService = CreateIdentityApplicationService(repository)
    private val sessionTokenIssuer = SessionTokenIssuer(secretBase64 = secret, ttlSeconds = 2_592_000)
    private val service = AssociateDriverApplicationService(repository, sessionTokenIssuer)

    @Test
    fun `associating a driver updates and persists the identity`() {
        val identity = createService.handle(CreateIdentityCommand(null))
        assertNull(identity.driverId)

        val outcome = service.handle(AssociateDriverCommand(identity.id.value, "ILDAR001"))

        assertEquals("ILDAR001", outcome.identity.driverId)
        assertEquals("ILDAR001", repository.findById(identity.id)?.driverId)
    }

    @Test
    fun `associating a driver issues a fresh session token carrying the new driverId (ADR-055 Decision 6 addendum)`() {
        val identity = createService.handle(CreateIdentityCommand(null))

        val outcome = service.handle(AssociateDriverCommand(identity.id.value, "ILDAR001"))

        assertNotNull(outcome.token)
        val verifier = SessionTokenVerifier(secretBase64 = secret)
        val verified = assertNotNull(verifier.verify("Bearer ${outcome.token}"))
        assertEquals(identity.id.value, verified.sub)
        assertEquals("ILDAR001", verified.drv)
    }

    @Test
    fun `associating a driver for an unknown identity fails`() {
        assertFailsWith<IdentityNotFoundException> {
            service.handle(AssociateDriverCommand("unknown-identity", "ILDAR001"))
        }
    }

    @Test
    fun `associating a blank driver id fails`() {
        val identity = createService.handle(CreateIdentityCommand(null))

        assertFailsWith<IllegalArgumentException> {
            service.handle(AssociateDriverCommand(identity.id.value, ""))
        }
    }
}
