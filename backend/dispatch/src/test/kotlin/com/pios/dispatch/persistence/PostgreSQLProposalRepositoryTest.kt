package com.pios.dispatch.persistence

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
