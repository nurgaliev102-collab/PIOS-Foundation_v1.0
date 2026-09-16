package com.pios.dispatch.persistence

import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.FallbackDispatchApplicationService
import com.pios.dispatch.application.PrimaryDriverRecord
import com.pios.dispatch.application.PrimaryDriverRepository
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposalLapseApplicationService
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * FR-003A (Fallback Dispatch) after a primary driver's own lapse -- the
 * real chain [ProposalLapseApplicationService]'s own "FR-003A after a
 * primary driver's own lapse" KDoc describes, proven against the real,
 * isolated `pios_dispatch_test` PostgreSQL database (not in-memory fakes,
 * unlike [com.pios.dispatch.application.ProposalLapseApplicationServiceTest]'s
 * own coverage of the identical decision logic): a real
 * [ProposalApplicationService.handle] creates a Proposal for the
 * passenger's real primary driver, [ProposalLapseApplicationService.lapseStaleProposals]
 * finds and lapses it once its age exceeds the configured timeout, and a
 * real [FallbackDispatchApplicationService.attempt] then proposes to a
 * real, currently-available driver -- end to end, no mocks, over real
 * JDBC.
 */
class FallbackDispatchAfterLapseIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val primaryDriverRepository: PrimaryDriverRepository = PostgreSQLPrimaryDriverRepository(JdbcTemplate(dataSource))
    private val driverAvailabilityRepository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val proposalApplicationService =
        ProposalApplicationService(proposalRepository, transactionRunner, driverAvailabilityRepository)
    private val fallbackDispatchApplicationService =
        FallbackDispatchApplicationService(driverAvailabilityRepository, proposalApplicationService)
    private val lapseApplicationService = ProposalLapseApplicationService(
        proposalRepository,
        proposalApplicationService,
        primaryDriverRepository,
        fallbackDispatchApplicationService,
        timeoutMinutes = 5
    )

    private val cleanupDriverIds = mutableListOf<String>()

    @AfterTest
    fun cleanupDriverAvailability() {
        val jdbcTemplate = JdbcTemplate(dataSource)
        cleanupDriverIds.forEach { jdbcTemplate.update("DELETE FROM driver_availability WHERE driver_reference = ?", it) }
    }

    /**
     * Forced far into the past, randomized (never a shared literal --
     * mirrors [PostgreSQLDriverAvailabilityRepositoryTest]'s own
     * `farPastTimestamp` exactly): makes [driver] deterministically the
     * "longest idle available" candidate regardless of any other
     * available-driver row this shared `pios_dispatch_test` database may
     * already hold from other tests.
     */
    private fun markAvailableSinceFarPast(driver: DriverReference) {
        // ADR-069 Part 3: `attempt`'s own default isTest is `false`, and
        // every call in this file relies on that default, so the fallback
        // candidate itself must be an explicitly real driver.
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(driver, available = true, isTest = false))
        JdbcTemplate(dataSource).update(
            "UPDATE driver_availability SET updated_at = ? WHERE driver_reference = ?",
            java.sql.Timestamp.from(Instant.parse("2000-01-01T00:00:00Z").minusSeconds((0..3_000_000_000L).random())),
            driver.driverId
        )
        cleanupDriverIds.add(driver.driverId)
    }

    @Test
    fun `a real primary-driver proposal that lapses produces a real Fallback Dispatch proposal for the longest-idle available driver`() {
        val order = OrderReference("order-${UUID.randomUUID()}")
        val passenger = PassengerReference("passenger-${UUID.randomUUID()}")
        val primaryDriver = DriverReference("driver-primary-${UUID.randomUUID()}")
        val fallbackDriver = DriverReference("driver-fallback-${UUID.randomUUID()}")

        primaryDriverRepository.upsert(PrimaryDriverRecord(passenger, primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))
        markAvailableSinceFarPast(fallbackDriver)

        val primaryProposal = proposalApplicationService.handle(
            ProposeDriverCommand(order, primaryDriver, passengerReference = passenger)
        ).proposal
        assertEquals(ProposalStatus.OPEN, proposalRepository.findById(primaryProposal.id)?.status)

        // The primary driver never responds -- sweep well past the timeout.
        lapseApplicationService.lapseStaleProposals(now = Instant.now().plusSeconds(6 * 60))

        assertEquals(ProposalStatus.LAPSED, proposalRepository.findById(primaryProposal.id)?.status)
        val proposalsForOrder = proposalRepository.findByOrder(order)
        assertEquals(2, proposalsForOrder.size, "the lapsed primary proposal, plus a new real Fallback Dispatch proposal")
        val fallbackProposal = assertNotNull(proposalsForOrder.singleOrNull { it.id != primaryProposal.id })
        assertEquals(fallbackDriver, fallbackProposal.driver)
        assertEquals(ProposalStatus.OPEN, fallbackProposal.status)
        assertEquals(passenger, fallbackProposal.passengerReference)
    }

    @Test
    fun `a real Fallback Dispatch proposal that itself lapses is not retried`() {
        val order = OrderReference("order-${UUID.randomUUID()}")
        val passenger = PassengerReference("passenger-${UUID.randomUUID()}")
        val fallbackDriver = DriverReference("driver-fallback-${UUID.randomUUID()}")
        val wouldBeSecondFallback = DriverReference("driver-second-${UUID.randomUUID()}")
        markAvailableSinceFarPast(fallbackDriver)
        // No PrimaryDriverRecord for this passenger at all.

        val fallbackOutcome = fallbackDispatchApplicationService.attempt(order, passenger)
        val fallbackProposal = assertNotNull((fallbackOutcome as? com.pios.dispatch.application.FallbackDispatchOutcome.Proposed)?.proposal)
        assertEquals(fallbackDriver, fallbackProposal.driver)

        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(wouldBeSecondFallback, available = true, isTest = false))
        cleanupDriverIds.add(wouldBeSecondFallback.driverId)

        lapseApplicationService.lapseStaleProposals(now = Instant.now().plusSeconds(6 * 60))

        assertEquals(ProposalStatus.LAPSED, proposalRepository.findById(fallbackProposal.id)?.status)
        assertEquals(1, proposalRepository.findByOrder(order).size, "no second, retried Fallback Dispatch proposal must be created")
    }
}
