package com.pios.dispatch.persistence

import com.pios.dispatch.application.DeclineProposalCommand
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.FirstRefusalApplicationService
import com.pios.dispatch.application.FirstRefusalOutcome
import com.pios.dispatch.application.PrimaryDriverRecord
import com.pios.dispatch.application.PrimaryDriverRepository
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 15C (First Refusal Contract Completion and Concurrency Safety),
 * Tests 5, 6, 8. Proves, against a real PostgreSQL database and real
 * concurrent threads (not sequential calls, not mocked timing), that
 * `proposals_one_open_per_order` (V14) actually closes the gap Task 15B's
 * own audit found: `Proposal.propose`'s in-memory `existingProposals`
 * check alone cannot prevent two genuinely concurrent transactions from
 * both inserting an `OPEN` row for the same order.
 *
 * A [CountDownLatch] releases both worker threads at (as close to) the
 * same instant as the JVM allows -- this does not *guarantee* the two
 * transactions' own `SELECT`s overlap on every run (a true race is
 * inherently non-deterministic), but the assertions below hold
 * regardless of whether they actually did: if they raced, the database
 * constraint is what prevents a second row; if they serialized instead,
 * `Proposal.propose`'s own in-memory check already would have. Either
 * way, the test is not flaky -- only its own diagnostic value (whether it
 * actually exercised the constraint on a given run) varies.
 */
class ProposalConcurrencyTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val primaryDriverRepository: PrimaryDriverRepository = PostgreSQLPrimaryDriverRepository(JdbcTemplate(dataSource))
    private val driverAvailabilityRepository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val proposalApplicationService =
        ProposalApplicationService(proposalRepository, transactionRunner, driverAvailabilityRepository)
    private val firstRefusalApplicationService =
        FirstRefusalApplicationService(primaryDriverRepository, proposalApplicationService, driverAvailabilityRepository)

    /** Runs [first] and [second] concurrently, released by a shared latch, and waits for both to finish. */
    private fun runConcurrently(first: () -> Unit, second: () -> Unit) {
        val executor = Executors.newFixedThreadPool(2)
        val startLatch = CountDownLatch(1)
        try {
            val futures = listOf(first, second).map { task ->
                executor.submit {
                    startLatch.await()
                    task()
                }
            }
            startLatch.countDown()
            futures.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `Test 6 -- two concurrent automatic First Refusal attempts for the same order produce exactly one OPEN proposal`() {
        val order = OrderReference("concurrency-order-${UUID.randomUUID()}")
        val passenger = PassengerReference("concurrency-passenger-${UUID.randomUUID()}")
        val primaryDriver = DriverReference("concurrency-driver-${UUID.randomUUID()}")
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))

        val outcomes = Collections.synchronizedList(mutableListOf<FirstRefusalOutcome>())

        runConcurrently(
            { outcomes.add(firstRefusalApplicationService.attempt(order, passenger)) },
            { outcomes.add(firstRefusalApplicationService.attempt(order, passenger)) }
        )

        assertEquals(2, outcomes.size)
        assertEquals(1, outcomes.count { it is FirstRefusalOutcome.Proposed }, "exactly one attempt must have created a Proposal")
        assertEquals(1, outcomes.count { it == FirstRefusalOutcome.AlreadyAttempted }, "the other must observe AlreadyAttempted, never an uncaught exception")
        assertEquals(1, proposalRepository.findByOrder(order).size, "exactly one OPEN proposal must exist for this order")
        assertEquals(ProposalStatus.OPEN, proposalRepository.findByOrder(order).single().status)
    }

    @Test
    fun `Test 5 -- concurrent explicit vs automatic attempt for the same order never produces two proposals`() {
        // Represents today's actual, unprotected scenario (Task 15B's own
        // finding): the automatic path has no explicit-intent flag to
        // consult (explicitDriverIntentDeclared defaults to false), racing
        // a genuinely concurrent explicit ProposalApplicationService.handle
        // call for a *different* driver -- exactly the shape RideRequest.tsx's
        // own direct-propose flow and a future automatic OrderSubmitted
        // consumer would produce if raced against each other.
        val order = OrderReference("concurrency-order-${UUID.randomUUID()}")
        val passenger = PassengerReference("concurrency-passenger-${UUID.randomUUID()}")
        val primaryDriver = DriverReference("concurrency-primary-${UUID.randomUUID()}")
        val explicitDriver = DriverReference("concurrency-explicit-${UUID.randomUUID()}")
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(explicitDriver, available = true, isTest = false))

        var automaticOutcome: FirstRefusalOutcome? = null
        var explicitFailed = false

        runConcurrently(
            { automaticOutcome = firstRefusalApplicationService.attempt(order, passenger) },
            {
                try {
                    proposalApplicationService.handle(ProposeDriverCommand(order, explicitDriver))
                } catch (ex: IllegalStateException) {
                    explicitFailed = true
                } catch (ex: org.springframework.dao.DataIntegrityViolationException) {
                    explicitFailed = true
                }
            }
        )

        // The core guarantee under genuine concurrency, disclosed honestly
        // (Task 15B Section 8): exactly one proposal survives, for
        // *whichever* driver won -- this test does not claim explicit
        // always wins here, since no flag was declared (see the
        // deterministic counterpart test below for the case where it is).
        val proposals = proposalRepository.findByOrder(order)
        assertEquals(1, proposals.size, "exactly one proposal must exist, regardless of which side won the race")
        assertEquals(ProposalStatus.OPEN, proposals.single().status)
        // Exactly one side won -- neither both, nor neither. The loser
        // failed cleanly (a caught exception, never an uncaught one
        // reaching this test), which is what makes the single surviving
        // proposal above possible at all.
        val automaticWon = automaticOutcome is FirstRefusalOutcome.Proposed
        val explicitWon = !explicitFailed
        assertTrue(automaticWon != explicitWon, "exactly one of the two concurrent attempts must have won, never both and never neither")
    }

    @Test
    fun `Test 5 (deterministic) -- with explicit intent declared, the automatic attempt never competes at all`() {
        // Unlike the test above, this is the actual Task 15C guarantee:
        // when explicitDriverIntentDeclared is true (the flag Order.submit
        // records atomically, Task 15C Parts 2-3), there is no race to
        // resolve -- attempt() never reads PrimaryDriverRepository and
        // never calls ProposalApplicationService.handle at all.
        val order = OrderReference("concurrency-order-${UUID.randomUUID()}")
        val passenger = PassengerReference("concurrency-passenger-${UUID.randomUUID()}")
        val primaryDriver = DriverReference("concurrency-primary-${UUID.randomUUID()}")
        val explicitDriver = DriverReference("concurrency-explicit-${UUID.randomUUID()}")
        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(explicitDriver, available = true, isTest = false))

        var automaticOutcome: FirstRefusalOutcome? = null

        runConcurrently(
            {
                automaticOutcome = firstRefusalApplicationService.attempt(
                    order, passenger, explicitDriverIntentDeclared = true
                )
            },
            { proposalApplicationService.handle(ProposeDriverCommand(order, explicitDriver)) }
        )

        assertEquals(FirstRefusalOutcome.ExplicitDriverIntentDeclared, automaticOutcome)
        val proposals = proposalRepository.findByOrder(order)
        assertEquals(1, proposals.size)
        assertEquals(explicitDriver, proposals.single().driver, "the explicit driver's own proposal must be the only one, deterministically")
    }

    @Test
    fun `Test 8 -- after a proposal resolves, a new proposal for the same order can still be created`() {
        val order = OrderReference("lifecycle-order-${UUID.randomUUID()}")
        val firstDriver = DriverReference("lifecycle-driver-1-${UUID.randomUUID()}")
        val secondDriver = DriverReference("lifecycle-driver-2-${UUID.randomUUID()}")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(firstDriver, available = true, isTest = false))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(secondDriver, available = true, isTest = false))

        val created = proposalApplicationService.handle(ProposeDriverCommand(order, firstDriver))
        proposalApplicationService.declineProposal(DeclineProposalCommand(created.proposal.id))

        // The partial index only restricts OPEN rows -- a DECLINED proposal
        // does not block a fresh one for the same order (the ordinary
        // fallback/re-proposal flow, unaffected by V14).
        val second = proposalApplicationService.handle(ProposeDriverCommand(order, secondDriver))

        assertEquals(ProposalStatus.OPEN, second.proposal.status)
        assertEquals(2, proposalRepository.findByOrder(order).size)
        val statuses = proposalRepository.findByOrder(order).map { it.status }.toSet()
        assertEquals(setOf(ProposalStatus.DECLINED, ProposalStatus.OPEN), statuses)
    }

    @Test
    fun `Test 8 -- the isolated test database has no pre-existing duplicate OPEN proposals for any order`() {
        // Re-verifies Task 15C Part 4's own pre-migration investigation
        // remains true after the migration is live -- if this ever fails,
        // the constraint itself would already have made it impossible to
        // create new duplicates, so a failure here would point at
        // residual data older than V14, not a regression in this task.
        val duplicates = JdbcTemplate(dataSource).queryForList(
            "SELECT order_reference, count(*) AS c FROM proposals WHERE status = 'OPEN' GROUP BY order_reference HAVING count(*) > 1"
        )
        assertEquals(emptyList<Map<String, Any>>(), duplicates)
    }
}
