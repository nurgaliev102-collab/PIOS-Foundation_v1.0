package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.persistence.PostgreSQLProposalRepository
import com.pios.dispatch.persistence.PostgreSQLTestDatabase
import com.pios.dispatch.persistence.awaitUntilNotNull
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.MapPropertySource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test-only, scheduling-only Spring context registering the real
 * [ProposalRepository], [ProposalApplicationService],
 * [ProposalLapseApplicationService], and [ProposalLapseScheduler] beans --
 * deliberately not the full `DispatchApplication`. Mirrors
 * `TestOutboxRelaySchedulerConfiguration` exactly (`OutboxRelaySchedulerTest.kt`).
 * No RabbitMQ bean is registered: unlike Outbox events, Proposal events are
 * not published externally (`ProposalApplicationService`'s own KDoc), so
 * lapsing needs only Dispatch's own database.
 */
@Configuration
@EnableScheduling
class TestProposalLapseSchedulerConfiguration {

    @Bean
    fun proposalRepository(): ProposalRepository =
        PostgreSQLProposalRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Bean
    fun proposalApplicationService(proposalRepository: ProposalRepository): ProposalApplicationService =
        ProposalApplicationService(proposalRepository)

    @Bean
    fun proposalLapseApplicationService(
        proposalRepository: ProposalRepository,
        proposalApplicationService: ProposalApplicationService
    ): ProposalLapseApplicationService =
        // timeout-minutes: 0 so any already-created proposal is immediately
        // stale -- proves the trigger and the age comparison, not the
        // (separately, ADR-051/052-deferred) numeric value.
        ProposalLapseApplicationService(proposalRepository, proposalApplicationService, timeoutMinutes = 0)

    @Bean
    fun proposalLapseScheduler(proposalLapseApplicationService: ProposalLapseApplicationService): ProposalLapseScheduler =
        ProposalLapseScheduler(proposalLapseApplicationService)
}

/**
 * Proves [ProposalLapseScheduler] actually triggers
 * [ProposalLapseApplicationService.lapseStaleProposals] automatically, on
 * its own configured schedule (P0-3; ADR-051, ADR-052 -- both Accepted).
 * This test never calls the sweep itself. Mirrors `OutboxRelaySchedulerTest`
 * exactly, with the tick interval overridden to 200ms via an explicit
 * property source so the test does not wait on the 30s production default
 * (`application.yml`'s own `pios.proposal.lapse.fixed-delay-ms`, itself a
 * configuration default, not decided by ADR-051/052).
 */
class ProposalLapseSchedulerTest {

    @Test
    fun `an open proposal is automatically lapsed by the scheduled trigger, without the test calling the sweep itself`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource("test-proposal-lapse-overrides", mapOf("pios.proposal.lapse.fixed-delay-ms" to "200"))
        )
        context.register(TestProposalLapseSchedulerConfiguration::class.java)
        context.refresh()

        try {
            val proposalRepository = context.getBean(ProposalRepository::class.java)
            val proposalApplicationService = context.getBean(ProposalApplicationService::class.java)
            val order = OrderReference("order-${System.nanoTime()}")
            val driver = DriverReference("driver-${System.nanoTime()}")

            val proposal = proposalApplicationService.handle(ProposeDriverCommand(order, driver)).proposal
            assertEquals(ProposalStatus.OPEN, proposalRepository.findById(proposal.id)?.status)

            val lapsed = awaitUntilNotNull(maxAttempts = 30, perAttemptDelayMillis = 200) {
                proposalRepository.findById(proposal.id)?.takeIf { it.status == ProposalStatus.LAPSED }
            }

            assertEquals(ProposalStatus.LAPSED, lapsed?.status)
        } finally {
            context.close()
        }
    }
}
