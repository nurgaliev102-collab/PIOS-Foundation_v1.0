package com.pios.dispatch.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.Proposal
import com.pios.dispatch.domain.ProposalStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class RouteSubmittedOrderCommand(
    val orderId: String,
    val passengerReference: String,
    val isTest: Boolean,
    val explicitDriverIntent: Boolean,
    val requestedDriverId: String?,
    val requestedPickupAt: Instant?,
    val submittedAt: Instant
)

/**
 * Persists the routing obligation before attempting an offer. The scheduled
 * retry uses the same row and decision path, so an availability-projection
 * race cannot silently consume the only chance to route an order.
 */
@Service
class DispatchRequestApplicationService(
    private val requests: DispatchRequestRepository,
    private val proposals: ProposalRepository,
    private val proposalService: ProposalApplicationService,
    private val firstRefusal: FirstRefusalApplicationService,
    private val fallback: FallbackDispatchApplicationService,
    private val outbox: OutboxRepository,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(DispatchRequestApplicationService::class.java)

    fun routeSubmitted(command: RouteSubmittedOrderCommand) {
        transactionRunner.run {
            val record = DispatchRequestRecord(
                orderId = command.orderId,
                passengerReference = command.passengerReference,
                isTest = command.isTest,
                explicitDriverIntent = command.explicitDriverIntent,
                requestedDriverId = command.requestedDriverId,
                requestedPickupAt = command.requestedPickupAt,
                submittedAt = command.submittedAt,
                expiresAt = command.submittedAt.plus(ROUTING_WINDOW),
                nextAttemptAt = clock.instant()
            )
            requests.insertIfAbsent(record)
            attemptLocked(command.orderId)
        }
    }

    fun retryDue(): Int {
        val dueIds: List<String> = requests.findDueOrderIds(clock.instant(), RETRY_BATCH_SIZE)
        dueIds.forEach { orderId ->
            try {
                transactionRunner.run { attemptLocked(orderId) }
            } catch (error: Exception) {
                logger.error("Dispatch retry failed for order {}; will retry next sweep", orderId, error)
            }
        }
        return dueIds.size
    }

    private fun attemptLocked(orderId: String) {
        val record: DispatchRequestRecord = requests.findForUpdate(orderId) ?: return
        if (record.state != DispatchRequestState.PENDING) return
        val order: OrderReference = OrderReference(orderId)
        val orderProposals: List<Proposal> = proposals.findByOrder(order)
        // A manual offer or an earlier event delivery wins. In particular,
        // never declare an order unfulfilled while a live or positively
        // resolved proposal exists. Narrowed from "any proposal exists"
        // (ADR-078, Dispatch Recovery After Proposal Decline or Lapse,
        // Decision A requirement 3): a DECLINED/LAPSED proposal no longer
        // counts, so a row this method itself just reopened from OFFERED
        // back to PENDING (DispatchRequestRepository.reopenIfOffered) is
        // not immediately re-marked OFFERED by that same now-resolved
        // proposal on this very sweep.
        if (hasUnresolvedOrAcceptedProposal(orderProposals)) {
            requests.markOffered(orderId)
            return
        }
        val now: Instant = clock.instant()
        if (!now.isBefore(record.expiresAt)) {
            requests.markUnfulfilled(orderId)
            outbox.save(createExhaustedEvent(orderId, now))
            return
        }
        attemptOffer(record, order, orderProposals)
        // Re-read after attemptOffer, then apply the identical narrowed
        // predicate -- a DECLINED/LAPSED proposal already present before
        // this attempt (the reopened row's own refusal) must not be
        // mistaken for a fresh offer just made; only a new OPEN (or
        // otherwise unresolved/positive) proposal means this attempt
        // actually produced an offer.
        if (hasUnresolvedOrAcceptedProposal(proposals.findByOrder(order))) {
            requests.markOffered(orderId)
        } else {
            requests.scheduleRetry(orderId, now.plus(RETRY_INTERVAL))
        }
    }

    private fun hasUnresolvedOrAcceptedProposal(orderProposals: List<Proposal>): Boolean =
        orderProposals.any {
            it.status == ProposalStatus.OPEN ||
                it.status == ProposalStatus.PRICE_PROPOSED ||
                it.status == ProposalStatus.ACCEPTED
        }

    private fun attemptOffer(record: DispatchRequestRecord, order: OrderReference, orderProposals: List<Proposal>) {
        val passenger: PassengerReference = PassengerReference(record.passengerReference)
        // ADR-078 Decision B: every driver who has already declined or
        // lapsed on this order is excluded from a resumed attempt, across
        // all three branches below.
        val excludeDrivers: Set<DriverReference> = orderProposals
            .filter { it.status == ProposalStatus.DECLINED || it.status == ProposalStatus.LAPSED }
            .map { it.driver }
            .toSet()
        if (record.requestedDriverId != null) {
            val namedDriver = DriverReference(record.requestedDriverId)
            if (namedDriver in excludeDrivers) {
                // PIOS does not substitute a random driver for the one a
                // client came to (Proposal.kt's ratified principle,
                // ADR-078 Decision B): an order that named a driver who
                // has already refused it falls through to UNFULFILLED at
                // window expiry, it is never retried with a substitute.
                return
            }
            try {
                proposalService.handle(
                    ProposeDriverCommand(
                        order = order,
                        driver = namedDriver,
                        isTest = record.isTest,
                        passengerReference = passenger,
                        requestedPickupAt = record.requestedPickupAt
                    )
                )
            } catch (error: IllegalStateException) {
                logger.info("Named driver is not yet eligible for order {}", record.orderId)
            }
            return
        }
        // Versions 1/2 could declare a selected driver without carrying its
        // ID. Never replace that choice with an unrelated network driver;
        // close the unrouteable request explicitly when its window expires.
        if (record.explicitDriverIntent) return
        val first: FirstRefusalOutcome = firstRefusal.attempt(
            order = order,
            passengerReference = passenger,
            isTest = record.isTest,
            requestedPickupAt = record.requestedPickupAt,
            excludeDrivers = excludeDrivers
        )
        if (first is FirstRefusalOutcome.NoPrimaryDriver || first is FirstRefusalOutcome.PrimaryDriverIneligible) {
            fallback.attempt(order = order, passengerReference = passenger, isTest = record.isTest, excludeDrivers = excludeDrivers)
        }
    }

    private fun createExhaustedEvent(orderId: String, occurredAt: Instant): OutboxRecord {
        val payload: String = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to "DispatchExhausted",
                "eventVersion" to 1,
                "occurredAt" to occurredAt.toString(),
                "payload" to mapOf("orderId" to orderId, "reason" to "NO_OFFER_WITHIN_WINDOW")
            )
        )
        return OutboxRecord(
            aggregateId = orderId,
            eventType = "DispatchExhausted",
            routingKey = "dispatch.exhausted",
            payload = payload
        )
    }

    companion object {
        private val ROUTING_WINDOW: Duration = Duration.ofMinutes(2)
        private val RETRY_INTERVAL: Duration = Duration.ofSeconds(5)
        private const val RETRY_BATCH_SIZE: Int = 100
    }
}
