package com.pios.dispatch.persistence

import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.HandoffApplicationService
import com.pios.dispatch.application.HandoffObservationQueryService
import com.pios.dispatch.application.ProposeHandoffCommand
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-08 (Handoff Observation Foundation,
 * `docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md` §12,
 * repository/PostgreSQL layer). Real database, real
 * [DispatchAssignmentApplicationService]/`PostgreSQLOutboxRepository`
 * stack -- proves [PostgreSQLHandoffObservationRepository]'s own SQL
 * (the `dispatch_outbox`/`OrderAssigned` join this class's own KDoc
 * documents) produces the same results the in-memory reference
 * ([com.pios.dispatch.application.HandoffObservationQueryServiceTest])
 * already proved correct, against real, durable data.
 */
class HandoffObservationPostgreSQLTest {
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val jdbc = JdbcTemplate(dataSource)
    private val transactions = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val guard = PostgreSQLOrderGuard(jdbc)
    private val assignments = PostgreSQLAssignmentRepository(jdbc)
    private val trips = PostgreSQLTripRepository(jdbc)
    private val proposals = PostgreSQLProposalRepository(jdbc)
    private val handoffs = PostgreSQLHandoffRepository(jdbc)
    private val dispatchRequests = PostgreSQLDispatchRequestRepository(jdbc)
    private val outbox = PostgreSQLOutboxRepository(jdbc)
    private val driverAvailability = PostgreSQLDriverAvailabilityRepository(jdbc)
    private val mapper = com.fasterxml.jackson.databind.ObjectMapper()

    private val assignmentService = DispatchAssignmentApplicationService(
        assignments, outbox, transactions, mapper, trips, guard, dispatchRequests
    )
    private val handoffService = HandoffApplicationService(
        assignments, trips, handoffs, proposals, driverAvailability, guard, transactions
    )
    private val observationRepository = PostgreSQLHandoffObservationRepository(jdbc)
    private val service = HandoffObservationQueryService(observationRepository, windowWeeks = 4)

    private fun order(): OrderReference = OrderReference(UUID.randomUUID().toString())
    private fun driver(): DriverReference = DriverReference("driver-obs-${UUID.randomUUID()}")

    private fun seedAssignment(committingDriver: DriverReference): String {
        val created = assignmentService.handle(AssignOrderCommand(order(), committingDriver))
        return created.assignment.id.value
    }

    private fun backdateAssignmentCreation(assignmentId: String, at: Instant) {
        jdbc.update(
            "UPDATE dispatch_outbox SET created_at = ? WHERE aggregate_id = ? AND event_type = 'OrderAssigned'",
            Timestamp.from(at),
            assignmentId
        )
    }

    @Test
    fun `a driver never seen in this database reports insufficientData against real Postgres`() {
        val result = service.observe(driver())

        assertEquals(0, result.facts.totalCommitments)
        assertEquals(true, result.insufficientData)
        assertNull(result.rate)
    }

    @Test
    fun `a real committed Handoff counts toward the numerator, read back from real Postgres`() {
        val committingDriver = driver()
        val substitute = driver()
        val assignmentId = seedAssignment(committingDriver)
        driverAvailability.upsert(DriverAvailabilityRecord(substitute, available = true))
        val created = handoffService.propose(ProposeHandoffCommand(com.pios.dispatch.domain.AssignmentId(assignmentId), substitute.driverId))
        handoffService.acceptSubstitute(com.pios.dispatch.application.AcceptHandoffSubstituteCommand(created.handoff.id))
        handoffService.consent(com.pios.dispatch.application.ConsentHandoffCommand(created.handoff.id))

        val result = service.observe(committingDriver)

        assertEquals(1, result.facts.totalCommitments)
        assertEquals(1, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.committed)
        assertEquals(1.0, result.rate)
    }

    @Test
    fun `a real declined-before-acceptance Handoff does not count, read back from real Postgres`() {
        val committingDriver = driver()
        val substitute = driver()
        val assignmentId = seedAssignment(committingDriver)
        driverAvailability.upsert(DriverAvailabilityRecord(substitute, available = true))
        val created = handoffService.propose(ProposeHandoffCommand(com.pios.dispatch.domain.AssignmentId(assignmentId), substitute.driverId))
        handoffService.refuse(com.pios.dispatch.application.RefuseHandoffCommand(created.handoff.id))

        val result = service.observe(committingDriver)

        assertEquals(1, result.facts.totalCommitments)
        assertEquals(0, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1, result.facts.declinedBySubstitute)
    }

    @Test
    fun `window boundary against real Postgres -- an Assignment backdated before windowStart is excluded`() {
        val committingDriver = driver()
        val assignmentId = seedAssignment(committingDriver)
        val now = Instant.now()
        backdateAssignmentCreation(assignmentId, now.minusSeconds(60L * 24 * 3600))

        val result = service.observe(committingDriver, at = now)

        assertEquals(0, result.facts.totalCommitments)
        assertEquals(true, result.insufficientData)
    }

    @Test
    fun `window boundary against real Postgres -- an Assignment backdated just inside the window is included`() {
        val committingDriver = driver()
        val assignmentId = seedAssignment(committingDriver)
        val now = Instant.now()
        backdateAssignmentCreation(assignmentId, now.minusSeconds(27L * 24 * 3600))

        val result = service.observe(committingDriver, at = now)

        assertEquals(1, result.facts.totalCommitments)
    }

    @Test
    fun `observation never enforces -- a high computed rate still returns a normal, successful result`() {
        // Spec Section 12, item 14: observation-before-cap is expressed as
        // the total absence of any enforcement code path. This is the
        // concrete, testable proof for that absence: a driver whose entire
        // window is Handoffs-with-acceptance (the highest rate the metric
        // can express) still gets back an ordinary, successful result --
        // never a thrown exception, never any kind of denial.
        val committingDriver = driver()
        repeat(3) {
            val assignmentId = seedAssignment(committingDriver)
            val substitute = driver()
            driverAvailability.upsert(DriverAvailabilityRecord(substitute, available = true))
            val created = handoffService.propose(ProposeHandoffCommand(com.pios.dispatch.domain.AssignmentId(assignmentId), substitute.driverId))
            handoffService.acceptSubstitute(com.pios.dispatch.application.AcceptHandoffSubstituteCommand(created.handoff.id))
        }

        val result = service.observe(committingDriver)

        assertEquals(3, result.facts.totalCommitments)
        assertEquals(3, result.facts.handoffsWithSubstituteAcceptance)
        assertEquals(1.0, result.rate)
        assertTrue(result.facts.distinctSubstitutesUsed == 3)
    }
}
