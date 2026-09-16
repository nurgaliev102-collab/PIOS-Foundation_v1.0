package com.pios.identity.persistence

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.AssociateDriverCommand
import com.pios.identity.application.CreateIdentityApplicationService
import com.pios.identity.application.CreateIdentityCommand
import com.pios.identity.application.IdentityNotFoundException
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.application.SessionTokenIssuer
import com.pios.identity.domain.IdentityId
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Sprint 2 (Identity MVP): proves the full "driver registers, closes the
 * app, reopens it" scenario end to end against the real PostgreSQL
 * database — not [com.pios.identity.persistence.InMemoryIdentityRepository],
 * which every existing Identity test before this one used exclusively.
 * Mirrors `com.pios.dispatch.persistence.PostgreSQLAssignmentLifecycleTest`'s
 * own shape and reasoning.
 *
 * Each "reopen" is a fresh [PostgreSQLIdentityRepository]/application-service
 * instance built on the same [PostgreSQLTestDatabase.dataSource] — nothing
 * is held in this test's own process memory across those calls, so a pass
 * here specifically rules out "it only works because the repository still
 * has it cached," the one thing an in-memory-only test can never prove.
 */
class PostgreSQLIdentityLifecycleTest {

    private val sessionTokenIssuer = SessionTokenIssuer(
        secretBase64 = Base64.getEncoder().encodeToString("postgres-identity-lifecycle-secret-32".toByteArray()),
        ttlSeconds = 2_592_000
    )

    private fun freshRepository() = PostgreSQLIdentityRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `a driver registers, the app reopens, and the same identity plus driver are restored`() {
        // Register: Identity created, then a driver profile associated —
        // the exact two-step sequence DriverHome.tsx's own onboarding
        // performs (ADR-039).
        val registrationRepository = freshRepository()
        val created = CreateIdentityApplicationService(registrationRepository).handle(CreateIdentityCommand(uniqueTestPhone()))
        AssociateDriverApplicationService(registrationRepository, sessionTokenIssuer)
            .handle(AssociateDriverCommand(created.id.value, created.id.value))

        // Reopen (device restart): a brand new repository/handler pair,
        // backed by nothing but the database itself.
        val reopened = RetrieveIdentityHandler(freshRepository()).handle(created.id)

        assertEquals(created.id, reopened.id)
        assertEquals(created.id.value, reopened.driverId)
    }

    @Test
    fun `retrieving an identity id that was never registered fails the same way after a fresh reopen`() {
        assertFailsWith<IdentityNotFoundException> {
            RetrieveIdentityHandler(freshRepository()).handle(IdentityId("postgres-identity-lifecycle-never-registered"))
        }
    }

    @Test
    fun `reopening cannot reassign an identity to a different driver`() {
        val repository = freshRepository()
        // ADR-055 added a unique index on `identities.phone`; this test's
        // own phone value must therefore be unique per run, not a shared
        // literal -- the same database persists across repeated runs of
        // this suite (no per-test rollback, per this class's own KDoc), so
        // a fixed literal would collide with a row a previous run already
        // committed. The specific phone value is otherwise incidental to
        // what this test actually proves (driver reassociation, not phone
        // uniqueness).
        val phone = uniqueTestPhone()
        val created = CreateIdentityApplicationService(repository).handle(CreateIdentityCommand(phone))
        AssociateDriverApplicationService(repository, sessionTokenIssuer)
            .handle(AssociateDriverCommand(created.id.value, created.id.value))

        assertFailsWith<IllegalArgumentException> {
            AssociateDriverApplicationService(freshRepository(), sessionTokenIssuer)
                .handle(AssociateDriverCommand(created.id.value, "somebody-elses-driver"))
        }

        val reopened = RetrieveIdentityHandler(freshRepository()).handle(created.id)
        assertEquals(created.id.value, reopened.driverId)
        assertEquals(phone, reopened.phone?.value)
    }

    private fun uniqueTestPhone(): String = "+7999" + System.nanoTime().toString().takeLast(7)
}
