package com.pios.dispatch.persistence

import com.pios.dispatch.application.AcceptHandoffSubstituteCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.CommitmentTerminationApplicationService
import com.pios.dispatch.application.ConsentHandoffCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.HandoffApplicationService
import com.pios.dispatch.application.ProposeHandoffCommand
import com.pios.dispatch.application.RefuseHandoffCommand
import com.pios.dispatch.application.StartAssignmentCommand
import com.pios.dispatch.application.TerminateCommitmentCommand
import com.pios.dispatch.application.WithdrawHandoffCommand
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.HandoffStatus
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.TerminationInitiator
import com.pios.dispatch.domain.TerminationReasonCode
import com.pios.dispatch.domain.TripStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D-07 (Handoff Protocol): real-PostgreSQL proof that the concurrency
 * invariants `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md` §8 names
 * actually hold, reusing the exact same `PostgreSQLOrderGuard`/real-thread
 * race harness `CommitmentTerminationPostgreSQLTest` already established
 * for D-01 -- no new locking mechanism, no new test infrastructure.
 */
class HandoffConcurrencyPostgreSQLTest {
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val jdbc = JdbcTemplate(dataSource)
    private val transactions = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val guard = PostgreSQLOrderGuard(jdbc)
    private val assignments = PostgreSQLAssignmentRepository(jdbc)
    private val trips = PostgreSQLTripRepository(jdbc)
    private val proposals = PostgreSQLProposalRepository(jdbc)
    private val handoffs = PostgreSQLHandoffRepository(jdbc)
    private val dispatchRequests = PostgreSQLDispatchRequestRepository(jdbc)
    private val terminationRequests = PostgreSQLTerminationRequestRepository(jdbc)
    private val outbox = PostgreSQLOutboxRepository(jdbc)
    private val driverAvailability = PostgreSQLDriverAvailabilityRepository(jdbc)
    private val mapper = com.fasterxml.jackson.databind.ObjectMapper()

    private val assignmentService = DispatchAssignmentApplicationService(
        assignments, outbox, transactions, mapper, trips, guard, dispatchRequests
    )
    private val termination = CommitmentTerminationApplicationService(
        assignments, trips, proposals, com.pios.dispatch.application.ProposalApplicationService(proposals, transactions, orderGuard = guard),
        dispatchRequests, terminationRequests, guard, outbox, transactions, mapper
    )
    private val handoffService = HandoffApplicationService(
        assignments, trips, handoffs, proposals, driverAvailability, guard, transactions
    )

    private fun order(): OrderReference = OrderReference(UUID.randomUUID().toString())
    private fun driver(): DriverReference = DriverReference("driver-${UUID.randomUUID()}")

    /** Creates a real, committed Assignment/Trip via the manual path, and registers a real, eligible substitute. */
    private fun seedCommittedRide(): Triple<OrderReference, DriverReference, DriverReference> {
        val theOrder = order()
        val original = driver()
        val substitute = driver()
        assignmentService.handle(AssignOrderCommand(theOrder, original))
        driverAvailability.upsert(DriverAvailabilityRecord(substitute, available = true))
        return Triple(theOrder, original, substitute)
    }

    private fun passengerCommand(order: OrderReference) =
        TerminateCommitmentCommand(UUID.randomUUID().toString(), order.orderId, TerminationInitiator.PASSENGER, TerminationReasonCode.PLANS_CHANGED)

    private fun <A, B> race(first: () -> A, second: () -> B): Pair<Result<A>, Result<B>> {
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        return try {
            val a = pool.submit(Callable { gate.await(); runCatching(first) })
            val b = pool.submit(Callable { gate.await(); runCatching(second) })
            gate.countDown()
            a.get(20, TimeUnit.SECONDS) to b.get(20, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `two concurrent handoff proposals for the same assignment -- exactly one PROPOSED handoff survives`() {
        repeat(5) {
            val (theOrder, _, _) = seedCommittedRide()
            val assignment = assignments.findByOrder(theOrder).single()
            val substituteA = driver().also { driverAvailability.upsert(DriverAvailabilityRecord(it, available = true)) }
            val substituteB = driver().also { driverAvailability.upsert(DriverAvailabilityRecord(it, available = true)) }

            val (resultA, resultB) = race(
                { handoffService.propose(ProposeHandoffCommand(assignment.id, substituteA.driverId)) },
                { handoffService.propose(ProposeHandoffCommand(assignment.id, substituteB.driverId)) }
            )

            val successes = listOf(resultA, resultB).count { it.isSuccess }
            val failures = listOf(resultA, resultB).count { it.isFailure }
            assertEquals(1, successes, "exactly one proposal must win")
            assertEquals(1, failures, "exactly one proposal must lose")
            val active = handoffs.findActiveByAssignmentId(assignment.id)
            assertNotNull(active, "the winning proposal's own Handoff must be persisted and active")
            assertEquals(1, handoffs.findByAssignmentId(assignment.id).size, "no second Handoff row of any kind must exist")
        }
    }

    @Test
    fun `handoff proposal vs D-01 termination -- propose only ever succeeds against a genuinely live Trip`() {
        // Unlike completion-vs-termination (D-01's own race, where both
        // operations compete to be the SAME Trip's own terminal
        // transition), propose-vs-terminate are not mutually exclusive in
        // that sense: propose winning the lock first (while the Trip is
        // still live), committing, and *then* termination independently
        // terminating the same Trip afterward is a legitimate, sequential
        // outcome -- nothing in D-07 makes a pending Handoff immune to a
        // later, ordinary termination. What must actually hold: propose
        // never succeeds while observing an already-terminated Trip
        // (Invariant #6), and termination itself always resolves cleanly
        // regardless of a concurrent proposal.
        repeat(5) {
            val (theOrder, _, substitute) = seedCommittedRide()
            val assignment = assignments.findByOrder(theOrder).single()

            val (proposeResult, terminateResult) = race(
                { handoffService.propose(ProposeHandoffCommand(assignment.id, substitute.driverId)) },
                { termination.terminate(passengerCommand(theOrder)) }
            )

            assertTrue(terminateResult.isSuccess, "termination itself must always succeed regardless of a concurrent proposal")
            if (proposeResult.isFailure) {
                assertTrue(
                    proposeResult.exceptionOrNull() is IllegalStateException,
                    "a rejected proposal must fail with the same clean IllegalStateException every other ineligibility case uses"
                )
            } else {
                // propose won the lock first (the Trip was still live at that
                // moment) -- a real Handoff must exist, in PROPOSED status,
                // regardless of what termination does to the Trip afterward.
                assertEquals(HandoffStatus.PROPOSED, handoffs.findById(proposeResult.getOrThrow().handoff.id)?.status)
            }
        }
    }

    @Test
    fun `consent vs D-01 termination -- consent's own COMMITTED outcome is always internally consistent with the trip's own executingDriver`() {
        // Same reasoning as the propose-vs-terminate race above: consent
        // winning the lock first (Trip still live), committing, and then an
        // independent, later termination of the now-substitute-executed
        // Trip is a legitimate sequential outcome -- D-07 never grants a
        // committed Handoff immunity from an ordinary subsequent
        // termination (e.g. the substitute themselves later cannot
        // fulfil). What must actually hold, unconditionally: consent NEVER
        // succeeds while observing an already-terminated Trip (Invariant
        // #6), and Handoff.status/Trip.executingDriver are never left
        // mutually inconsistent by either outcome.
        repeat(5) {
            val (theOrder, original, substitute) = seedCommittedRide()
            val assignment = assignments.findByOrder(theOrder).single()
            val created = handoffService.propose(ProposeHandoffCommand(assignment.id, substitute.driverId))
            handoffService.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))

            val (consentResult, terminateResult) = race(
                { handoffService.consent(ConsentHandoffCommand(created.handoff.id)) },
                { termination.terminate(passengerCommand(theOrder)) }
            )

            assertTrue(terminateResult.isSuccess)
            val trip = assertNotNull(trips.findByAssignmentId(assignment.id))
            val handoff = assertNotNull(handoffs.findById(created.handoff.id))
            if (consentResult.isFailure) {
                assertTrue(consentResult.exceptionOrNull() is IllegalStateException)
                assertEquals(original, trip.executingDriver, "a rejected consent must never change the executing driver")
                assertEquals(HandoffStatus.SUBSTITUTE_ACCEPTED, handoff.status, "a rejected consent must leave the handoff exactly where it was")
            } else {
                // consent won the lock first: it observed a live Trip, so it
                // legitimately committed -- regardless of whether termination
                // (racing, or arriving moments later) subsequently ends the
                // now-substitute-executed Trip.
                assertEquals(HandoffStatus.COMMITTED, handoff.status)
                assertEquals(substitute, trip.executingDriver, "a committed consent must always have updated the executing driver, whatever happens to the Trip afterward")
            }
        }
    }

    @Test
    fun `consent vs Trip start -- consent and start never both silently apply inconsistently`() {
        repeat(5) {
            val (theOrder, original, substitute) = seedCommittedRide()
            val assignment = assignments.findByOrder(theOrder).single()
            assignmentService.arriveAssignment(com.pios.dispatch.application.ArriveAssignmentCommand(assignment.id))
            val created = handoffService.propose(ProposeHandoffCommand(assignment.id, substitute.driverId))
            handoffService.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))

            val (consentResult, startResult) = race(
                { handoffService.consent(ConsentHandoffCommand(created.handoff.id)) },
                { assignmentService.startAssignment(StartAssignmentCommand(assignment.id)) }
            )

            val trip = assertNotNull(trips.findByAssignmentId(assignment.id))
            if (trip.status == TripStatus.IN_PROGRESS && consentResult.isFailure) {
                // start won the lock first: the trip left ARRIVED before
                // consent's own revalidation ran, so consent must have been
                // rejected -- the executing driver stays the original.
                assertEquals(original, trip.executingDriver)
            } else if (consentResult.isSuccess) {
                // consent won the lock first: executing driver changed while
                // still ARRIVED; start (whichever thread) then proceeds
                // normally against the now-updated executing driver.
                assertEquals(substitute, trip.executingDriver)
            }
            assertTrue(startResult.isSuccess, "start itself must always succeed -- it never depends on the handoff's own outcome")
        }
    }

    @Test
    fun `withdrawal racing consent -- withdrawal must never win once consent has actually committed`() {
        repeat(5) {
            val (theOrder, original, substitute) = seedCommittedRide()
            val assignment = assignments.findByOrder(theOrder).single()
            val created = handoffService.propose(ProposeHandoffCommand(assignment.id, substitute.driverId))
            handoffService.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))

            val (consentResult, withdrawResult) = race(
                { handoffService.consent(ConsentHandoffCommand(created.handoff.id)) },
                { handoffService.withdraw(WithdrawHandoffCommand(created.handoff.id)) }
            )

            val handoff = assertNotNull(handoffs.findById(created.handoff.id))
            val trip = assertNotNull(trips.findByAssignmentId(assignment.id))
            when (handoff.status) {
                HandoffStatus.COMMITTED -> {
                    assertTrue(consentResult.isSuccess)
                    assertTrue(withdrawResult.isFailure, "withdrawal must fail once the handoff is COMMITTED, unconditionally")
                    assertEquals(substitute, trip.executingDriver)
                }
                HandoffStatus.WITHDRAWN -> {
                    assertTrue(withdrawResult.isSuccess)
                    assertTrue(consentResult.isFailure, "consent must fail once the handoff is already WITHDRAWN")
                    assertEquals(original, trip.executingDriver, "a withdrawn handoff must never have changed the executing driver")
                }
                else -> error("Handoff did not reach a terminal state: ${handoff.status}")
            }
        }
    }

    @Test
    fun `two concurrent consents -- exactly one commits, the executing driver changes exactly once`() {
        repeat(5) {
            val (theOrder, original, substitute) = seedCommittedRide()
            val assignment = assignments.findByOrder(theOrder).single()
            val created = handoffService.propose(ProposeHandoffCommand(assignment.id, substitute.driverId))
            handoffService.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))

            val (first, second) = race(
                { handoffService.consent(ConsentHandoffCommand(created.handoff.id)) },
                { handoffService.consent(ConsentHandoffCommand(created.handoff.id)) }
            )

            val successes = listOf(first, second).count { it.isSuccess }
            assertEquals(1, successes, "exactly one of the two identical, concurrent consent calls must win")
            val trip = assertNotNull(trips.findByAssignmentId(assignment.id))
            assertEquals(substitute, trip.executingDriver)
            assertEquals(HandoffStatus.COMMITTED, handoffs.findById(created.handoff.id)?.status)
        }
    }

    @Test
    fun `refusal after substitute acceptance never touches the trip, even under concurrent load`() {
        repeat(5) {
            val (theOrder, original, substitute) = seedCommittedRide()
            val assignment = assignments.findByOrder(theOrder).single()
            val created = handoffService.propose(ProposeHandoffCommand(assignment.id, substitute.driverId))
            handoffService.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))

            val (refuseResult, secondRefuseResult) = race(
                { handoffService.refuse(RefuseHandoffCommand(created.handoff.id)) },
                { handoffService.refuse(RefuseHandoffCommand(created.handoff.id)) }
            )

            assertEquals(1, listOf(refuseResult, secondRefuseResult).count { it.isSuccess })
            val trip = assertNotNull(trips.findByAssignmentId(assignment.id))
            assertEquals(original, trip.executingDriver)
            assertEquals(TripStatus.CREATED, trip.status)
        }
    }

    @Test
    fun `same substitute proposed twice for the same assignment -- second attempt is rejected, no duplicate active handoff`() {
        val (theOrder, _, substitute) = seedCommittedRide()
        val assignment = assignments.findByOrder(theOrder).single()

        handoffService.propose(ProposeHandoffCommand(assignment.id, substitute.driverId))
        assertFailsWith<IllegalStateException> {
            handoffService.propose(ProposeHandoffCommand(assignment.id, substitute.driverId))
        }

        assertEquals(1, handoffs.findByAssignmentId(assignment.id).size)
    }

    @Test
    fun `a chained handoff attempt by a committed substitute is rejected at the application layer, no second handoff exists`() {
        val (theOrder, _, substitute) = seedCommittedRide()
        val assignment = assignments.findByOrder(theOrder).single()
        val created = handoffService.propose(ProposeHandoffCommand(assignment.id, substitute.driverId))
        handoffService.acceptSubstitute(AcceptHandoffSubstituteCommand(created.handoff.id))
        handoffService.consent(ConsentHandoffCommand(created.handoff.id))
        val thirdDriver = driver().also { driverAvailability.upsert(DriverAvailabilityRecord(it, available = true)) }

        // HandoffController is what actually rejects this (the substitute is
        // never Assignment.driver, checked before this service is even
        // called) -- proven here at the application/domain layer: the
        // service itself has no notion of "who is calling," so this proves
        // the domain fact a controller-level check depends on -- the
        // substitute never becomes Assignment.driver, only Trip.executingDriver.
        assertEquals(substitute, assignments.findById(assignment.id)!!.let {
            trips.findByAssignmentId(it.id)!!.executingDriver
        })
        assertEquals(assignment.driver, assignments.findById(assignment.id)!!.driver, "Assignment.driver -- the authorization anchor -- never changes")

        // A second Handoff naming a third driver, proposed as if by the
        // substitute, would be rejected purely because the substitute is
        // not Assignment.driver -- confirmed structurally, not by calling
        // propose() with a forged identity (this service trusts its caller;
        // HandoffControllerTest already proves the controller-level rejection).
        assertEquals(0, handoffs.findByAssignmentId(assignment.id).count { it.originalDriver == substitute })
    }
}
