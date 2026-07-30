package com.pios.identity.persistence

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.AssociateDriverCommand
import com.pios.identity.application.CreateIdentityApplicationService
import com.pios.identity.application.CreateIdentityCommand
import com.pios.identity.application.IdentityNotFoundException
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.domain.IdentityId
import org.springframework.jdbc.core.JdbcTemplate
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

    private fun freshRepository() = PostgreSQLIdentityRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `a driver registers, the app reopens, and the same identity plus driver are restored`() {
        // Register: Identity created, then a driver profile associated —
        // the exact two-step sequence DriverHome.tsx's own onboarding
        // performs (ADR-039).
        val registrationRepository = freshRepository()
        val created = CreateIdentityApplicationService(registrationRepository).handle(CreateIdentityCommand(null))
        AssociateDriverApplicationService(registrationRepository)
            .handle(AssociateDriverCommand(created.id.value, "postgres-identity-lifecycle-driver-1"))

        // Reopen (device restart): a brand new repository/handler pair,
        // backed by nothing but the database itself.
        val reopened = RetrieveIdentityHandler(freshRepository()).handle(created.id)

        assertEquals(created.id, reopened.id)
        assertEquals("postgres-identity-lifecycle-driver-1", reopened.driverId)
    }

    @Test
    fun `retrieving an identity id that was never registered fails the same way after a fresh reopen`() {
        assertFailsWith<IdentityNotFoundException> {
            RetrieveIdentityHandler(freshRepository()).handle(IdentityId("postgres-identity-lifecycle-never-registered"))
        }
    }

    @Test
    fun `re-registering a driver association on reopen overwrites the previous one, not duplicates it`() {
        val repository = freshRepository()
        val created = CreateIdentityApplicationService(repository).handle(CreateIdentityCommand("+79991234567"))
        AssociateDriverApplicationService(repository)
            .handle(AssociateDriverCommand(created.id.value, "postgres-identity-lifecycle-driver-2a"))

        // A later reopen associating a different driver id (e.g. this
        // device re-ran onboarding after losing its own local pointer,
        // per BackendIdentityProvider's 404-recovery path) -- the backend
        // itself does not forbid this; it simply reflects the latest call.
        AssociateDriverApplicationService(freshRepository())
            .handle(AssociateDriverCommand(created.id.value, "postgres-identity-lifecycle-driver-2b"))

        val reopened = RetrieveIdentityHandler(freshRepository()).handle(created.id)
        assertEquals("postgres-identity-lifecycle-driver-2b", reopened.driverId)
        assertEquals("+79991234567", reopened.phone?.value)
    }
}
