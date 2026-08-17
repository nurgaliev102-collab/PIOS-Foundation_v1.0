package com.pios.dispatch.persistence

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the save/load lifecycle described by [ProposalRepository]
 * against a real PostgreSQL database, not merely in memory — mirroring
 * [PostgreSQLAssignmentRepositoryTest] exactly.
 */
class PostgreSQLProposalRepositoryTest {

    private val repository = PostgreSQLProposalRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `a proposal saved to PostgreSQL can be loaded back with its identity, references, and status preserved`() {
        val created = Proposal.propose(OrderReference("postgres-order-1"), DriverReference("postgres-driver-1"))

        repository.save(created.proposal)
        val loaded = repository.findById(created.proposal.id)

        assertEquals(created.proposal.id, loaded?.id)
        assertEquals(OrderReference("postgres-order-1"), loaded?.order)
        assertEquals(DriverReference("postgres-driver-1"), loaded?.driver)
        assertEquals(ProposalStatus.OPEN, loaded?.status)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(ProposalId("postgres-repository-test-never-saved")))
    }

    @Test
    fun `saving again after acceptance overwrites the previously persisted row's status`() {
        val created = Proposal.propose(OrderReference("postgres-order-2"), DriverReference("postgres-driver-2"))
        repository.save(created.proposal)

        created.proposal.accept()
        repository.save(created.proposal)

        assertEquals(ProposalStatus.ACCEPTED, repository.findById(created.proposal.id)?.status)
    }

    @Test
    fun `findByOrder returns a proposal saved to PostgreSQL for that order`() {
        val order = OrderReference("postgres-findbyorder-order-1")
        val created = Proposal.propose(order, DriverReference("postgres-findbyorder-driver-1"))

        repository.save(created.proposal)

        assertTrue(repository.findByOrder(order).any { it.id == created.proposal.id })
    }

    // --- Stated price (ADR-042) ---

    /**
     * The trap this test exists to catch: `save`'s upsert is
     * `INSERT ... ON CONFLICT (id) DO UPDATE SET ...`. A row is first
     * inserted here at propose time (no price yet), then updated at accept
     * time (when the price is set) -- the second [save] call therefore
     * always takes the `DO UPDATE` path, exactly like a real accept
     * request. Reading back through a **freshly constructed**
     * [PostgreSQLProposalRepository] -- not the [repository] field used to
     * write it -- rules out any possibility that the value merely survived
     * in the writing object's own memory rather than actually being
     * persisted to the `stated_price` column.
     */
    @Test
    fun `a stated price set on a second save (accept, after the row already exists) is readable from a fresh repository instance`() {
        val order = OrderReference("postgres-order-price-1")
        val driver = DriverReference("postgres-driver-price-1")
        val created = Proposal.propose(order, driver)
        repository.save(created.proposal)

        created.proposal.accept(statedPrice = "999")
        repository.save(created.proposal)

        val freshRepository = PostgreSQLProposalRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
        val reloaded = freshRepository.findById(created.proposal.id)

        assertEquals("999", reloaded?.statedPrice)
        assertEquals(ProposalStatus.ACCEPTED, reloaded?.status)
    }

    // --- ADR-043: createdAt/respondedAt reconstruction ---

    @Test
    fun `createdAt round-trips through PostgreSQL, and respondedAt stays null while still OPEN`() {
        val created = Proposal.propose(OrderReference("postgres-order-timestamps-1"), DriverReference("postgres-driver-timestamps-1"))

        repository.save(created.proposal)
        val reloaded = repository.findById(created.proposal.id)

        assertNotNull(reloaded?.createdAt)
        assertNull(reloaded?.respondedAt)
    }

    @Test
    fun `an accepted proposal reloaded from a fresh repository instance preserves its own real respondedAt`() {
        val created = Proposal.propose(OrderReference("postgres-order-timestamps-2"), DriverReference("postgres-driver-timestamps-2"))
        repository.save(created.proposal)
        val respondedAt = Instant.parse("2026-08-02T20:15:00Z")

        created.proposal.accept(statedPrice = "500", at = respondedAt)
        repository.save(created.proposal)

        val freshRepository = PostgreSQLProposalRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
        val reloaded = freshRepository.findById(created.proposal.id)

        assertEquals(respondedAt, reloaded?.respondedAt)
        assertEquals(ProposalStatus.ACCEPTED, reloaded?.status)
    }

    // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---

    @Test
    fun `isTest true round-trips through PostgreSQL`() {
        val created = Proposal.propose(
            OrderReference("postgres-order-is-test-true"),
            DriverReference("postgres-driver-is-test-true"),
            isTest = true
        )

        repository.save(created.proposal)

        assertEquals(true, repository.findById(created.proposal.id)?.isTest)
    }

    @Test
    fun `a proposal saved without isTest loads back with isTest false`() {
        val created = Proposal.propose(OrderReference("postgres-order-is-test-default"), DriverReference("postgres-driver-is-test-default"))

        repository.save(created.proposal)

        assertEquals(false, repository.findById(created.proposal.id)?.isTest)
    }
}
