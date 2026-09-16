package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DispatchRequestApplicationService
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.FallbackDispatchApplicationService
import com.pios.dispatch.application.FirstRefusalApplicationService
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.ProposalStatus
import com.pios.ordermanagement.api.OrderSubmissionController
import com.pios.ordermanagement.api.SessionTokenVerifier
import com.pios.ordermanagement.api.SubmitOrderRequest
import com.pios.ordermanagement.application.NoOpTransactionRunner
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OrderSubmissionRequestHandler
import com.pios.ordermanagement.application.OutboxBacklog
import com.pios.ordermanagement.application.OutboxRecord
import com.pios.ordermanagement.application.OutboxRepository
import com.pios.ordermanagement.application.OutboxRelay
import com.pios.ordermanagement.persistence.InMemoryOrderRepository
import com.pios.ordermanagement.persistence.RabbitMQEventPublisher
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ADR-070 (Channel 1 — Discovery Matching), Part 1: proves the one path
 * this ADR found architecturally complete but never actually exercised by
 * a real caller — a passenger with **no primary driver and no trusted
 * connection** submits an order through Order Management's own real,
 * production HTTP-layer entry point (`OrderSubmissionController`, the same
 * constructor-wired, no-Spring-context convention
 * [com.pios.ordermanagement.api.OrderSubmissionControllerTest] already
 * establishes for "real HTTP/service layer" in this codebase), with
 * `explicitDriverIntent` **left at its own default** (`false` —
 * `SubmitOrderRequest.kt` line ~75), and that submission alone — through
 * the real domain event, the real outbox, and the real RabbitMQ broker —
 * reaches Dispatch's own real, unmodified `OrderSubmittedFirstRefusalListener`
 * -> [FirstRefusalApplicationService] (`NoPrimaryDriver`) ->
 * [FallbackDispatchApplicationService] (Tier 3, open marketplace — no
 * `TrustedDriverRepository` is wired here, so Tier 1 is structurally
 * skipped, isolating exactly the stranger-matching case ADR-070 Part 1
 * makes reachable) and produces a real [com.pios.dispatch.domain.Proposal].
 *
 * Why this is new (ADR-070 Context, "no existing test exercises this exact
 * 'real caller, real default flag' shape"): [RepeatClientLoopIntegrationTest]
 * already proves First Refusal -> Fallback Dispatch reacts correctly to a
 * real `OrderSubmitted` message, but that message is hand-built by the
 * test-only [OrderSubmittedMessagePublisher] with `explicitDriverIntent`
 * passed as a literal test argument, never produced by Order Management's
 * own real request-handling code. Every *real* frontend caller today
 * (`RideRequest.tsx`) always sends `explicitDriverIntent: true` (ADR-070
 * Context, Block 2) — a shape [OrderSubmittedFirstRefusalConsumerIntegrationTest]'s
 * own "Test C" already covers via the same hand-built publisher. Neither
 * existing test starts from [OrderSubmissionController] itself with the
 * field simply omitted, which is exactly what ADR-070 Part 1's new
 * driverless entry point (`RideRequest.tsx`'s own `/request` extension)
 * actually sends.
 *
 * Cross-module wiring mirrors [MvpVerticalSliceScenarioTest]'s own
 * precedent (`InMemoryOrderRepository`, a module-crossing test-scoped
 * dependency already declared in `dispatch/build.gradle.kts` for exactly
 * this purpose) for Order Management's own repository, since this test's
 * claim is about the real request -> domain -> event -> broker -> consumer
 * chain, not about Order Management's own PostgreSQL persistence (already
 * proven elsewhere, e.g. [com.pios.ordermanagement.persistence.OrderOutboxTransactionTest]).
 * [InMemoryOutboxRepository] (below) is the one small, real (non-no-op)
 * implementation this test needs beyond what already exists cross-module:
 * a functioning, in-memory [OutboxRepository] so the real
 * [OutboxRelay] — Order Management's own actual relay class, not a
 * substitute — has something real to find and publish. Dispatch's own
 * side (listener, First Refusal, Fallback Dispatch, Proposal
 * persistence) is 100% real and unmodified, against the real, isolated
 * `pios_dispatch_test` PostgreSQL database and the real, isolated
 * `pios-test` RabbitMQ vhost — the same infrastructure
 * [RepeatClientLoopIntegrationTest] already uses.
 */
class ChannelOneDiscoveryRealOrderSubmissionIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))

    // --- Order Management side: the real controller/handler/service/domain,
    // publishing through the real outbox relay and the real RabbitMQ
    // publisher onto the real, shared `order-management.events` exchange. ---

    private val orderRepository = InMemoryOrderRepository()
    private val outboxRepository = InMemoryOutboxRepository()
    private val orderLifecycleApplicationService =
        OrderLifecycleApplicationService(orderRepository, outboxRepository, NoOpTransactionRunner)
    private val orderSubmissionRequestHandler = OrderSubmissionRequestHandler(orderLifecycleApplicationService)
    private val orderManagementSecret = Base64.getEncoder().encodeToString("channel-1-real-submission-test-secret".toByteArray())
    private val orderManagementSessionTokenVerifier = SessionTokenVerifier(secretBase64 = orderManagementSecret)
    private val orderSubmissionController = OrderSubmissionController(orderSubmissionRequestHandler, orderManagementSessionTokenVerifier)
    private val outboxRelay = OutboxRelay(outboxRepository, RabbitMQEventPublisher(RabbitTemplate(RabbitMQTestConnection.connectionFactory)))

    // --- Dispatch side: real, unmodified production classes. Deliberately
    // no TrustedDriverRepository -- FallbackDispatchApplicationService's
    // own nullable default (`= null`) then skips Tier 1 structurally, so a
    // real Proposal here can only have come from Tier 3 (open marketplace),
    // isolating exactly the stranger-matching case ADR-070 Part 1 makes
    // reachable. ---

    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val driverAvailabilityRepository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(dataSource))
    private val primaryDriverRepository = PostgreSQLPrimaryDriverRepository(JdbcTemplate(dataSource))
    private val dispatchRequests = PostgreSQLDispatchRequestRepository(JdbcTemplate(dataSource))
    private val proposalApplicationService = ProposalApplicationService(proposalRepository, transactionRunner, driverAvailabilityRepository, dispatchRequests)
    private val firstRefusalApplicationService = FirstRefusalApplicationService(primaryDriverRepository, proposalApplicationService, driverAvailabilityRepository)
    private val fallbackDispatchApplicationService = FallbackDispatchApplicationService(driverAvailabilityRepository, proposalApplicationService)
    private val dispatchRequestApplicationService = DispatchRequestApplicationService(
        dispatchRequests, proposalRepository, proposalApplicationService,
        firstRefusalApplicationService, fallbackDispatchApplicationService,
        PostgreSQLOutboxRepository(JdbcTemplate(dataSource)), transactionRunner, ObjectMapper()
    )
    private val firstRefusalListener = OrderSubmittedFirstRefusalListener(dispatchRequestApplicationService, ObjectMapper())
    private val firstRefusalHarness = OrderSubmittedFirstRefusalTestListenerHarness(RabbitMQTestConnection.connectionFactory, firstRefusalListener)

    @AfterTest
    fun stopHarness() {
        firstRefusalHarness.stop()
    }

    @Test
    fun `a real driverless submission, with explicitDriverIntent left at its default, reaches Fallback Dispatch and produces a real Proposal`() {
        val passengerReference = "channel-1-passenger-${UUID.randomUUID()}"
        val strangerDriver = DriverReference("channel-1-stranger-driver-${UUID.randomUUID()}")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(strangerDriver, available = true, isTest = false))
        // No PrimaryDriverRepository row for [passengerReference] -- no
        // primary. No TrustedDriverRepository is even wired into
        // [fallbackDispatchApplicationService] -- no trusted connection can
        // be consulted at all. This is ADR-070 Part 1's own precondition:
        // "a passenger with no driver relationship."

        // The real submission: Order Management's own real controller, the
        // real request DTO, with explicitDriverIntent simply omitted --
        // exactly what ADR-070 Part 1's driverless entry point sends, and
        // exactly what no existing test sends through this exact class.
        val request = SubmitOrderRequest(passengerReference = passengerReference)
        val response = orderSubmissionController.submitOrder(request, authorization = passengerToken(passengerReference))
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = assertNotNull(response.body).orderId
        assertTrue(orderId.isNotBlank())

        // The real outbox relay, the real RabbitMQ publisher -- the same
        // classes production wiring uses, publishing the real
        // `OrderSubmitted` envelope (`explicitDriverIntent: false`, since
        // that is what SubmitOrderRequest actually defaulted to) onto the
        // real, shared `order-management.events` exchange.
        val relayed = outboxRelay.relay()
        assertEquals(1, relayed.size)
        assertEquals("OrderSubmitted", relayed.single().eventType)

        // Dispatch's own real, unmodified consumer chain reacts: First
        // Refusal sees NoPrimaryDriver (no PrimaryDriverRepository row was
        // ever seeded for [passengerReference]), so the only way a
        // Proposal can exist for this order at all is Fallback Dispatch's
        // Tier 3 (open marketplace) -- Tier 1 is structurally unreachable
        // here (no TrustedDriverRepository is even wired into
        // [fallbackDispatchApplicationService]).
        val proposal = awaitUntilNotNull { proposalRepository.findByOrder(OrderReference(orderId)).firstOrNull() }

        assertNotNull(proposal)
        assertEquals(ProposalStatus.OPEN, proposal.status)
        assertEquals(1, proposalRepository.findByOrder(OrderReference(orderId)).size)
        // Not asserted: that [proposal.driver] is exactly [strangerDriver].
        // `driver_availability` is a real, shared PostgreSQL table this
        // test does not exclusively own -- other tests in the same suite
        // leave their own available-driver rows behind, and Tier 3's own
        // fairness rule (ADR-068: longest-idle-first) can genuinely prefer
        // one of those over a driver this test just upserted (whose
        // `updated_at` is, by construction, the most recent of all). This
        // mirrors [RepeatClientLoopIntegrationTest]'s own second test,
        // which accepts the identical constraint and asserts a structural
        // fact instead of a specific winner. What *is* asserted, and is
        // sufficient to prove ADR-070 Part 1's own claim: the matched
        // driver is a real, currently-available driver Dispatch actually
        // holds a record for -- i.e., a genuine Tier 3 result, not a
        // fabricated or stale one.
        val matchedDriverRecord = driverAvailabilityRepository.findByDriverReference(proposal.driver)
        assertNotNull(matchedDriverRecord)
        assertTrue(matchedDriverRecord.available)
    }

    // --- Token minting (mirrors OrderSubmissionControllerTest's own exact convention) ---

    private val objectMapper = ObjectMapper()

    private fun issueToken(sub: String, ttlSeconds: Long = 3600): String {
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", sub)
        payloadNode.putNull("drv")
        payloadNode.put("exp", Instant.now().plusSeconds(ttlSeconds).epochSecond)
        val encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payloadNode))
        val secretBytes = Base64.getDecoder().decode(orderManagementSecret)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val encodedSignature = base64UrlEncode(mac.doFinal(encodedPayload.toByteArray(Charsets.UTF_8)))
        return "$encodedPayload.$encodedSignature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun passengerToken(passengerReference: String): String = "Bearer " + issueToken(sub = passengerReference)
}

/**
 * A small, genuinely functional (non-no-op) in-memory [OutboxRepository],
 * used only by [ChannelOneDiscoveryRealOrderSubmissionIntegrationTest] so
 * that Order Management's own real [OutboxRelay] has something real to
 * find and publish — see that test's own KDoc for why Order Management's
 * PostgreSQL-backed outbox is not needed to prove this specific claim.
 */
private class InMemoryOutboxRepository : OutboxRepository {
    private val records = mutableListOf<OutboxRecord>()
    private var nextId = 1L

    @Synchronized
    override fun save(record: OutboxRecord): OutboxRecord {
        val saved = record.copy(id = nextId++, createdAt = Instant.now())
        records += saved
        return saved
    }

    @Synchronized
    override fun findUnpublished(): List<OutboxRecord> = records.filter { it.publishedAt == null }

    @Synchronized
    override fun markPublished(id: Long) {
        val index = records.indexOfFirst { it.id == id }
        if (index >= 0) {
            records[index] = records[index].copy(publishedAt = Instant.now())
        }
    }

    @Synchronized
    override fun countUnpublished(): OutboxBacklog {
        val pending = records.count { it.publishedAt == null }
        return OutboxBacklog(pending = pending.toLong(), oldestPendingCreatedAt = records.firstOrNull { it.publishedAt == null }?.createdAt)
    }
}
