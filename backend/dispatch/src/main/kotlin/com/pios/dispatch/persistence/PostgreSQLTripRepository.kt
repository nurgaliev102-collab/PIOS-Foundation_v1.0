package com.pios.dispatch.persistence

import com.pios.dispatch.application.TripRepository
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripId
import com.pios.dispatch.domain.TripStatus
import com.pios.dispatch.domain.Termination
import com.pios.dispatch.domain.TerminationInitiator
import com.pios.dispatch.domain.TerminationReasonCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * The PostgreSQL-backed adapter for [TripRepository] (ADR-063; mirroring
 * [PostgreSQLAssignmentRepository]'s own conventions exactly — plain JDBC
 * via [JdbcTemplate], no ORM, no entity annotations). Persists exactly
 * the Trip logical entity this module owns, in its own database
 * (`pios_dispatch`) — no other module's information, no shared schema.
 *
 * ## Reconstruction and the private constructor
 *
 * [Trip]'s constructor is private, reachable only through [Trip.create],
 * which always starts a new trip at [TripStatus.CREATED]. Reconstructing
 * a persisted, possibly-further-progressed [Trip] therefore takes two
 * steps, both confined to this one file, mirroring
 * [PostgreSQLAssignmentRepository.reconstruct] exactly:
 *
 * 1. Reflectively invoke the private constructor with the persisted id,
 *    assignment id, order reference, driver reference, agreed amount
 *    (D-06; nullable, but still a required positional constructor
 *    parameter, so its declared type is looked up the same way as every
 *    other `String`-erased parameter here), isTest flag, and executing
 *    driver reference (D-07; positioned last, after `isTest`, matching
 *    [Trip]'s own primary constructor parameter order exactly — confirmed
 *    empirically against the compiled class to take unboxed
 *    `String`/`Boolean` parameters, the same value-class behavior already
 *    documented for [Assignment]). This always yields a trip in its
 *    initial [TripStatus.CREATED] state.
 * 2. Replay however many of [Trip.arrive]/[Trip.start]/[Trip.complete]
 *    are needed to reach the persisted status, each given its own real
 *    persisted timestamp (falling back to `now()` only when a persisted
 *    value happens to be `null`) — the same technique
 *    [PostgreSQLAssignmentRepository] already established.
 */
@Repository
class PostgreSQLTripRepository(
    private val jdbcTemplate: JdbcTemplate
) : TripRepository {

    override fun save(trip: Trip) {
        jdbcTemplate.update(
            """
            INSERT INTO trips (
                id, assignment_id, order_reference, driver_reference, status, status_changed_at,
                arrived_at, started_at, completed_at, is_test, termination_request_id,
                termination_initiator, termination_reason_code, termination_note, terminated_at,
                agreed_amount, executing_driver_reference
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                status_changed_at = EXCLUDED.status_changed_at,
                arrived_at = EXCLUDED.arrived_at,
                started_at = EXCLUDED.started_at,
                completed_at = EXCLUDED.completed_at,
                termination_request_id = EXCLUDED.termination_request_id,
                termination_initiator = EXCLUDED.termination_initiator,
                termination_reason_code = EXCLUDED.termination_reason_code,
                termination_note = EXCLUDED.termination_note,
                terminated_at = EXCLUDED.terminated_at,
                executing_driver_reference = EXCLUDED.executing_driver_reference
            """.trimIndent(),
            trip.id.value,
            trip.assignmentId.value,
            trip.order.orderId,
            trip.driver.driverId,
            trip.status.name,
            trip.statusChangedAt?.let { Timestamp.from(it) },
            trip.arrivedAt?.let { Timestamp.from(it) },
            trip.startedAt?.let { Timestamp.from(it) },
            trip.completedAt?.let { Timestamp.from(it) },
            trip.isTest,
            trip.termination?.requestId,
            trip.termination?.initiator?.name,
            trip.termination?.reasonCode?.name,
            trip.termination?.note,
            trip.termination?.terminatedAt?.let { Timestamp.from(it) },
            trip.agreedAmount,
            trip.executingDriver.driverId
        )
    }

    override fun findById(id: TripId): Trip? {
        val rows = jdbcTemplate.query(
            """
            SELECT id, assignment_id, order_reference, driver_reference, status, status_changed_at,
                   arrived_at, started_at, completed_at, is_test, termination_request_id,
                   termination_initiator, termination_reason_code, termination_note, terminated_at,
                   agreed_amount, executing_driver_reference
            FROM trips WHERE id = ?
            """.trimIndent(),
            { rs, _ -> reconstruct(rs) },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findByAssignmentId(assignmentId: AssignmentId): Trip? {
        val rows = jdbcTemplate.query(
            """
            SELECT id, assignment_id, order_reference, driver_reference, status, status_changed_at,
                   arrived_at, started_at, completed_at, is_test, termination_request_id,
                   termination_initiator, termination_reason_code, termination_note, terminated_at,
                   agreed_amount, executing_driver_reference
            FROM trips WHERE assignment_id = ?
            """.trimIndent(),
            { rs, _ -> reconstruct(rs) },
            assignmentId.value
        )
        return rows.firstOrNull()
    }

    private fun reconstruct(rs: ResultSet): Trip {
        val constructor = Trip::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Boolean::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        val driverReference = rs.getString("driver_reference")
        val trip = constructor.newInstance(
            rs.getString("id"),
            rs.getString("assignment_id"),
            rs.getString("order_reference"),
            driverReference,
            rs.getString("agreed_amount"),
            rs.getBoolean("is_test"),
            rs.getString("executing_driver_reference") ?: driverReference
        )
        val status = TripStatus.valueOf(rs.getString("status"))
        val arrivedAt: Instant = rs.getTimestamp("arrived_at")?.toInstant() ?: Instant.now()
        val startedAt: Instant = rs.getTimestamp("started_at")?.toInstant() ?: Instant.now()
        val completedAt: Instant = rs.getTimestamp("completed_at")?.toInstant() ?: Instant.now()
        when (status) {
            TripStatus.CREATED -> {}
            TripStatus.ARRIVED -> trip.arrive(arrivedAt)
            TripStatus.IN_PROGRESS -> {
                trip.arrive(arrivedAt)
                trip.start(startedAt)
            }
            TripStatus.COMPLETED -> {
                trip.arrive(arrivedAt)
                trip.start(startedAt)
                trip.complete(completedAt)
            }
            TripStatus.TERMINATED -> {
                if (rs.getTimestamp("started_at") != null) {
                    trip.arrive(arrivedAt)
                    trip.start(startedAt)
                } else if (rs.getTimestamp("arrived_at") != null) {
                    trip.arrive(arrivedAt)
                }
                trip.terminate(
                    Termination(
                        requestId = rs.getString("termination_request_id"),
                        initiator = TerminationInitiator.valueOf(rs.getString("termination_initiator")),
                        reasonCode = TerminationReasonCode.valueOf(rs.getString("termination_reason_code")),
                        terminatedAt = rs.getTimestamp("terminated_at").toInstant(),
                        note = rs.getString("termination_note")
                    )
                )
            }
        }
        return trip
    }
}
