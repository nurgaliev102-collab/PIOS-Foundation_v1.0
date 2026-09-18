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
    fun `a withdrawn proposal can be found by its id with its status intact (ADR-053)`() {
        // This is the specific regression this test exists to catch: the
        // Architectural Prerequisite ADR-053 named -- PostgreSQLProposalRepository.reconstruct()'s
        // own `when (status)` block is a statement, not an expression, so
        // Kotlin does not force it to handle WITHDRAWN. Without the
        // corresponding branch, this assertion would silently see OPEN
        // instead, with no compile error.
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-5"), DriverReference("contract-test-driver-5"))
        created.proposal.withdraw()

        repository.save(created.proposal)

        assertEquals(ProposalStatus.WITHDRAWN, repository.findById(created.proposal.id)?.status)
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

    // --- Stated price (ADR-042) ---

    @Test
    fun `a proposal accepted with a stated price can be found with that price intact`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-price-1"), DriverReference("contract-test-driver-price-1"))
        created.proposal.accept(statedPrice = "1200")

        repository.save(created.proposal)

        assertEquals("1200", repository.findById(created.proposal.id)?.statedPrice)
    }

    @Test
    fun `a proposal accepted without a stated price can be found with a null price`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-price-2"), DriverReference("contract-test-driver-price-2"))
        created.proposal.accept()

        repository.save(created.proposal)

        assertNull(repository.findById(created.proposal.id)?.statedPrice)
    }

    @Test
    fun `a declined proposal never has a stated price`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-price-3"), DriverReference("contract-test-driver-price-3"))
        created.proposal.decline()

        repository.save(created.proposal)

        assertNull(repository.findById(created.proposal.id)?.statedPrice)
    }

    // --- Stated eta (ADR-057) ---

    @Test
    fun `a proposal accepted with a stated eta can be found with that eta intact`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-eta-1"), DriverReference("contract-test-driver-eta-1"))
        created.proposal.accept(statedEtaMinutes = 7)

        repository.save(created.proposal)

        assertEquals(7, repository.findById(created.proposal.id)?.statedEtaMinutes)
    }

    @Test
    fun `a proposal accepted without a stated eta can be found with a null eta`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-eta-2"), DriverReference("contract-test-driver-eta-2"))
        created.proposal.accept()

        repository.save(created.proposal)

        assertNull(repository.findById(created.proposal.id)?.statedEtaMinutes)
    }

    @Test
    fun `a declined proposal never has a stated eta`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-eta-3"), DriverReference("contract-test-driver-eta-3"))
        created.proposal.decline()

        repository.save(created.proposal)

        assertNull(repository.findById(created.proposal.id)?.statedEtaMinutes)
    }

    // --- D-09.1 (Tier 1 Visible): viaTrustedFallback persists/reconstructs ---

    @Test
    fun `a proposal created with viaTrustedFallback true can be found with that fact intact`() {
        val repository = createRepository()
        val created = Proposal.propose(
            OrderReference("contract-test-order-tier1-1"),
            DriverReference("contract-test-driver-tier1-1"),
            viaTrustedFallback = true
        )

        repository.save(created.proposal)

        assertEquals(true, repository.findById(created.proposal.id)?.viaTrustedFallback)
    }

    @Test
    fun `a proposal created with no viaTrustedFallback argument can be found as false`() {
        val repository = createRepository()
        val created = Proposal.propose(OrderReference("contract-test-order-tier1-2"), DriverReference("contract-test-driver-tier1-2"))

        repository.save(created.proposal)

        assertEquals(false, repository.findById(created.proposal.id)?.viaTrustedFallback)
    }

    @Test
    fun `viaTrustedFallback survives reconstruction through an accepted, then re-saved, proposal`() {
        val repository = createRepository()
        val created = Proposal.propose(
            OrderReference("contract-test-order-tier1-3"),
            DriverReference("contract-test-driver-tier1-3"),
            viaTrustedFallback = true
        )
        repository.save(created.proposal)

        created.proposal.accept()
        repository.save(created.proposal)

        assertEquals(true, repository.findById(created.proposal.id)?.viaTrustedFallback)
        assertEquals(ProposalStatus.ACCEPTED, repository.findById(created.proposal.id)?.status)
    }
}

class InMemoryProposalRepositoryContractTest : ProposalRepositoryContractTest() {
    override fun createRepository(): ProposalRepository = InMemoryProposalRepository()
}

class PostgreSQLProposalRepositoryContractTest : ProposalRepositoryContractTest() {
    override fun createRepository(): ProposalRepository =
        PostgreSQLProposalRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
}
