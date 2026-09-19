package com.pios.dispatch.persistence

import com.pios.dispatch.application.DriverPushSubscription
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposeDriverCommand
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Proves ADR-083 (D-10) Part 8's transaction-boundary requirement against a
 * real PostgreSQL database and a real [SpringTransactionRunner] --
 * mirroring [ProposalDeclineReopensDispatchRequestTransactionTest]'s own
 * established rollback-proof pattern exactly.
 *
 * [ProposalApplicationService.handle] is exercised from *inside* an
 * already-open outer [transactionRunner] block, the same nesting shape
 * [com.pios.dispatch.application.DispatchRequestApplicationService.routeSubmitted]
 * produces in real production traffic (its own `transactionRunner.run {
 * ... attemptOffer(...) -> proposalService.handle(...) }`, `DispatchRequestApplicationService.kt:46-63`,
 * `123-171`) -- reproduced directly here, without constructing the full
 * service and its many collaborators, since [SpringTransactionRunner]'s own
 * `TransactionTemplate` joins an already-open physical transaction under
 * Spring's default `REQUIRED` propagation regardless of which application
 * service opened it.
 *
 * [WebPushDriverPushNotifier]'s own after-commit hook is what this proves:
 * the push send must never happen merely because the *inner*
 * `transactionRunner.run` block (inside [ProposalApplicationService.handle])
 * returned -- only once the *outermost* transaction actually commits.
 */
class DriverPushAfterCommitTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val immediateExecutor = Executor { it.run() }

    @Test
    fun `push fires only after the outermost transaction commits, not merely after the inner handle() call returns`() {
        val subscriptionRepository = InMemoryDriverPushSubscriptionRepository()
        val driver = DriverReference("tx-nest-driver-${UUID.randomUUID()}")
        subscriptionRepository.upsert(DriverPushSubscription("endpoint-nest-${UUID.randomUUID()}", driver, "p256dh", "auth"))
        val sentCount = mutableListOf<Unit>()
        val notifier = WebPushDriverPushNotifier(
            subscriptionRepository, immediateExecutor, "pub", "priv", "mailto:test@example.com",
            sender = { _, _ -> sentCount.add(Unit); 201 }
        )
        val proposalApplicationService = ProposalApplicationService(proposalRepository, transactionRunner, driverPushNotifier = notifier)
        val order = OrderReference("tx-nest-order-${UUID.randomUUID()}")

        var sentBeforeOuterTransactionFinished: Boolean? = null
        transactionRunner.run {
            // Nested call: handle() opens its own transactionRunner.run,
            // which joins this already-open physical transaction rather
            // than committing on its own.
            proposalApplicationService.handle(ProposeDriverCommand(order, driver))
            // Still inside the one physical transaction -- the after-commit
            // hook must not have fired yet.
            sentBeforeOuterTransactionFinished = sentCount.isNotEmpty()
        }

        assertEquals(false, sentBeforeOuterTransactionFinished)
        assertEquals(1, sentCount.size)
    }

    @Test
    fun `a rollback of the outermost transaction after handle() succeeded results in zero push`() {
        val subscriptionRepository = InMemoryDriverPushSubscriptionRepository()
        val driver = DriverReference("tx-nest-rollback-driver-${UUID.randomUUID()}")
        subscriptionRepository.upsert(DriverPushSubscription("endpoint-nest-rollback-${UUID.randomUUID()}", driver, "p256dh", "auth"))
        val sentCount = mutableListOf<Unit>()
        val notifier = WebPushDriverPushNotifier(
            subscriptionRepository, immediateExecutor, "pub", "priv", "mailto:test@example.com",
            sender = { _, _ -> sentCount.add(Unit); 201 }
        )
        val proposalApplicationService = ProposalApplicationService(proposalRepository, transactionRunner, driverPushNotifier = notifier)
        val order = OrderReference("tx-nest-rollback-order-${UUID.randomUUID()}")

        assertFailsWith<RuntimeException> {
            transactionRunner.run {
                proposalApplicationService.handle(ProposeDriverCommand(order, driver))
                throw RuntimeException("simulated failure after handle(), before the outer transaction commits")
            }
        }

        assertTrue(sentCount.isEmpty())
        assertTrue(proposalRepository.findByOrder(order).isEmpty())
    }
}
