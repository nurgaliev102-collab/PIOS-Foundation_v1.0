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
        val identity = createService.handle(CreateIdentityCommand("+79991234567"))
        assertNull(identity.driverId)

        val outcome = service.handle(AssociateDriverCommand(identity.id.value, identity.id.value))

        assertEquals(identity.id.value, outcome.identity.driverId)
        assertEquals(identity.id.value, repository.findById(identity.id)?.driverId)
    }

    @Test
    fun `associating a driver issues a fresh session token carrying the new driverId (ADR-055 Decision 6 addendum)`() {
        val identity = createService.handle(CreateIdentityCommand("+79991234567"))

        val outcome = service.handle(AssociateDriverCommand(identity.id.value, identity.id.value))

        assertNotNull(outcome.token)
        val verifier = SessionTokenVerifier(secretBase64 = secret)
        val verified = assertNotNull(verifier.verify("Bearer ${outcome.token}"))
        assertEquals(identity.id.value, verified.sub)
        assertEquals(identity.id.value, verified.drv)
    }

    @Test
    fun `associating a driver for an unknown identity fails`() {
        assertFailsWith<IdentityNotFoundException> {
            service.handle(AssociateDriverCommand("unknown-identity", "ILDAR001"))
        }
    }

    @Test
    fun `associating a blank driver id fails`() {
        val identity = createService.handle(CreateIdentityCommand("+79991234567"))

        assertFailsWith<IllegalArgumentException> {
            service.handle(AssociateDriverCommand(identity.id.value, ""))
        }
    }

    @Test
    fun `an identity cannot claim a driver id owned outside its identity boundary`() {
        val identity = createService.handle(CreateIdentityCommand("+79991234567"))

        assertFailsWith<IllegalArgumentException> {
            service.handle(AssociateDriverCommand(identity.id.value, "somebody-elses-driver"))
        }
        assertNull(repository.findById(identity.id)?.driverId)
    }

    @Test
    fun `associating the same driver twice is idempotent`() {
        val identity = createService.handle(CreateIdentityCommand("+79991234567"))
        service.handle(AssociateDriverCommand(identity.id.value, identity.id.value))

        val outcome = service.handle(AssociateDriverCommand(identity.id.value, identity.id.value))

        assertEquals(identity.id.value, outcome.identity.driverId)
    }

    @Test
    fun `a guest identity cannot be associated with a driver`() {
        val identity = createService.handle(CreateIdentityCommand(null))

        assertFailsWith<IllegalArgumentException> {
            service.handle(AssociateDriverCommand(identity.id.value, identity.id.value))
        }
    }
}
