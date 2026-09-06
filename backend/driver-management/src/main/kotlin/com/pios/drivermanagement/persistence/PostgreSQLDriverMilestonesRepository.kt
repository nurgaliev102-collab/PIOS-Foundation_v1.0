package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverMilestonesRepository
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.domain.DriverMilestones
import com.pios.drivermanagement.domain.RideStreakCalculator
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * PostgreSQL-backed adapter for [DriverMilestonesRepository]. Plain JDBC,
 * no ORM, matching every other repository in this module.
 *
 * [recordCompletedRide] reads this driver's current row first (or treats a
 * missing row as streak `0`/no prior ride), computes the new streak via
 * [RideStreakCalculator] against that prior state, then upserts. This
 * read-then-write is not itself atomic against a second, concurrent
 * `AssignmentCompleted` for the *same* driver — acceptable here because
 * [com.pios.drivermanagement.persistence.RabbitMQListenerContainerConfiguration]'s
 * default container concurrency processes this queue's messages
 * sequentially, so two events for one driver are never actually handled at
 * the same instant in practice; there is no explicit row lock beyond that.
 */
@Repository
class PostgreSQLDriverMilestonesRepository(
    private val jdbcTemplate: JdbcTemplate
) : DriverMilestonesRepository {

    override fun recordCompletedRide(driverId: DriverId, completedAt: Instant) {
        val existing = findByDriverId(driverId)
        val newStreak = RideStreakCalculator.nextStreak(
            previousStreak = existing?.currentStreakWeeks ?: 0,
            lastCompletedAt = existing?.lastCompletedAt,
            newRideAt = completedAt
        )
        jdbcTemplate.update(
            """
            INSERT INTO driver_milestones (driver_id, completed_rides_count, current_streak_weeks, last_completed_at)
            VALUES (?, 1, ?, ?)
            ON CONFLICT (driver_id) DO UPDATE SET
                completed_rides_count = driver_milestones.completed_rides_count + 1,
                current_streak_weeks = EXCLUDED.current_streak_weeks,
                last_completed_at = EXCLUDED.last_completed_at
            """.trimIndent(),
            driverId.value,
            newStreak,
            Timestamp.from(completedAt)
        )
    }

    override fun incrementRepeatClientsCount(driverId: DriverId) {
        jdbcTemplate.update(
            "UPDATE driver_milestones SET repeat_clients_count = repeat_clients_count + 1 WHERE driver_id = ?",
            driverId.value
        )
    }

    override fun findByDriverId(driverId: DriverId): DriverMilestones? {
        val rows = jdbcTemplate.query(
            "SELECT driver_id, completed_rides_count, current_streak_weeks, last_completed_at, repeat_clients_count FROM driver_milestones WHERE driver_id = ?",
            { rs, _ ->
                DriverMilestones(
                    driverId = DriverId(rs.getString("driver_id")),
                    completedRidesCount = rs.getLong("completed_rides_count"),
                    currentStreakWeeks = rs.getInt("current_streak_weeks"),
                    lastCompletedAt = rs.getTimestamp("last_completed_at")?.toInstant(),
                    repeatClientsCount = rs.getInt("repeat_clients_count")
                )
            },
            driverId.value
        )
        return rows.firstOrNull()
    }
}
