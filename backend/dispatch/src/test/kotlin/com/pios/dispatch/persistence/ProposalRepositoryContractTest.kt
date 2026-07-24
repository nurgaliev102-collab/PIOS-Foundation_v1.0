package com.pios.dispatch.persistence

import com.pios.dispatch.application.ProposalRepository
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
 * A shared behavioral contract every [ProposalRepository] implementation
 * must satisfy, run identically against [InMemoryProposalRepository] and
 * [PostgreSQLProposalRepository], mirroring
 * [AssignmentRepositoryContractTest] exactly.
 */
abstract class ProposalRepositoryContractTest {

    abstract fun createRepository(): ProposalRepository

    @Test
    fun `a saved proposal can be found by its id with its status intact`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-1"), DriverReference("contract-test-driver-1"))

        repository.save(created.proposal)

        assertEquals(ProposalStatus.OPEN, repository.findById(created.proposal.id)?.status)
    }

    @Test
    fun `an id that was never saved returns null rather than throwing`() {
        val repository = createRepository()

        assertNull(repository.findById(ProposalId("proposal-repository-contract-test-never-saved")))
    }

    @Test
    fun `saving the same proposal id again overwrites its previously persisted status`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-2"), DriverReference("contract-test-driver-2"))
        repository.save(created.proposal)

        created.proposal.accept()
        repository.save(created.proposal)

        assertEquals(ProposalStatus.ACCEPTED, repository.findById(created.proposal.id)?.status)
    }

    @Test
    fun `findByOrder returns only proposals for that order`() {
        val repository = createRepository()
        val order = OrderReference("contract-test-findbyorder-order")
        val matching = Proposal.propose(order, DriverReference("contract-test-findbyorder-driver-1"))
        val other = Proposal.propose(OrderReference("contract-test-findbyorder-other-order"), DriverReference("contract-test-findbyorder-driver-2"))
        repository.save(matching.proposal)
        repository.save(other.proposal)

        val found = repository.findByOrder(order)

        assertTrue(found.any { it.id == matching.proposal.id })
        assertTrue(found.none { it.id == other.proposal.id })
    }

    @Test
    fun `findByOrder returns an empty list when the order has no proposal`() {
        val repository = createRepository()

        assertEquals(emptyList(), repository.findByOrder(OrderReference("contract-test-findbyorder-never-proposed")))
    }

    @Test
    fun `a declined proposal can be found by its id with its status intact`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-3"), DriverReference("contract-test-driver-3"))
        created.proposal.decline()

        repository.save(created.proposal)

        assertEquals(ProposalStatus.DECLINED, repository.findById(created.proposal.id)?.status)
    }

    @Test
    fun `a lapsed proposal can be found by its id with its status intact`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-4"), DriverReference("contract-test-driver-4"))
        created.proposal.lapse()

        repository.save(created.proposal)

        assertEquals(ProposalStatus.LAPSED, repository.findById(created.proposal.id)?.status)
    }

    @Test
    fun `findByDriver returns only proposals for that driver`() {
        val repository = createRepository()
        val driver = DriverReference("contract-test-findbydriver-driver")
        val matching = Proposal.propose(OrderReference("contract-test-findbydriver-order-1"), driver)
        val other = Proposal.propose(OrderReference("contract-test-findbydriver-order-2"), DriverReference("contract-test-findbydriver-other-driver"))
        repository.save(matching.proposal)
        repository.save(other.proposal)

        val found = repository.findByDriver(driver)

        assertTrue(found.any { it.id == matching.proposal.id })
        assertTrue(found.none { it.id == other.proposal.id })
    }

    @Test
    fun `findByDriver returns an empty list when the driver has no proposal`() {
        val repository = createRepository()

        assertEquals(emptyList(), repository.findByDriver(DriverReference("contract-test-findbydriver-never-proposed")))
    }
}

class InMemoryProposalRepositoryContractTest : ProposalRepositoryContractTest() {
    override fun createRepository(): ProposalRepository = InMemoryProposalRepository()
}

class PostgreSQLProposalRepositoryContractTest : ProposalRepositoryContractTest() {
    override fun createRepository(): ProposalRepository =
        PostgreSQLProposalRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
}
