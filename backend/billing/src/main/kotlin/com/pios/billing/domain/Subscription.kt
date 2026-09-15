package com.pios.billing.domain

import java.time.Instant

/**
 * The Subscription aggregate (ADR-074 Part 2), within Billing's exclusive
 * ownership. Represents exactly one current subscription record for a
 * driver -- a plain reference ([driverId]), never a copy of
 * `driver-management`'s own Driver data.
 *
 * **Permitted transitions, and nothing else** (ADR-074 Part 2's own
 * table):
 *
 * | From | To |
 * |---|---|
 * | `FREE` (no record) | `TRIAL` |
 * | `FREE` (no record) | `ACTIVE` |
 * | `TRIAL` | `ACTIVE` |
 * | `TRIAL` | `EXPIRED` |
 * | `ACTIVE` | `EXPIRED` |
 * | `EXPIRED` | `ACTIVE` |
 *
 * `FREE` is never a state this class holds -- the constructor guard below
 * enforces it. A driver with no record at all is `FREE` by the absence of
 * any [Subscription] instance in storage, not by this class representing
 * that state (see [SubscriptionStatus.FREE]'s own KDoc). [start] is the
 * only way to bring an instance of this class into existence, standing in
 * for the two `FREE -> ...` rows in the table above; [recordPeriod] is the
 * only way to change an already-existing instance, standing in for the
 * remaining four. No other transition is permitted -- [recordPeriod]
 * throws [IllegalStateException] for anything not in that remaining set
 * (`TRIAL -> TRIAL`, `ACTIVE -> TRIAL`, `EXPIRED -> TRIAL`,
 * `EXPIRED -> EXPIRED`, and so on), mapped by the one write endpoint
 * (`com.pios.billing.api.SubscriptionController`) to HTTP 409, the same
 * "state conflict" status this codebase already uses for an illegal domain
 * transition elsewhere (e.g. `com.pios.dispatch.api.ProposalController`).
 *
 * [isTest] is supplied once, by the caller that creates the record via
 * [start] -- exactly as `Driver.isTest` and `Order.isTest` are supplied by
 * their own callers (ADR-069's sourcing pattern, reused by ADR-074 Part
 * 2). It is immutable for the lifetime of this aggregate: [recordPeriod]
 * never changes it, mirroring `Driver.isTest`'s own "set once, at
 * creation, and never changes afterward" discipline. It is never fetched
 * from `driver-management` -- that would be a copy of another module's
 * data, which the reference-not-ownership rule (ADR-005/ADR-019) forbids.
 *
 * No domain event is raised. ADR-074 Part 4/Part 3 both require this:
 * Billing consumes no event, publishes no event, and no other module
 * reads its state except through the one owner-gated write endpoint and
 * the one driver-scoped read endpoint -- neither is a subscriber to a
 * domain event this aggregate would need to produce.
 */
class Subscription private constructor(
    val driverId: DriverReference,
    status: SubscriptionStatus,
    currentPeriodEnd: Instant?,
    val isTest: Boolean,
    val createdAt: Instant,
    updatedAt: Instant
) {
    init {
        require(status != SubscriptionStatus.FREE) {
            "A Subscription record can never hold FREE -- FREE is the absence of a record, not a stored value"
        }
    }

    var status: SubscriptionStatus = status
        private set

    var currentPeriodEnd: Instant? = currentPeriodEnd
        private set

    var updatedAt: Instant = updatedAt
        private set

    /**
     * Records a new period against this already-existing subscription,
     * transitioning [status] to [targetStatus] and replacing
     * [currentPeriodEnd] with the newly recorded one. Throws
     * [IllegalStateException] if the transition from the current [status]
     * to [targetStatus] is not one of the four `TRIAL`/`ACTIVE`/`EXPIRED`
     * transitions this class's own KDoc table permits -- this is a
     * state-conflict error, not an input-validation one, which is why it
     * is [IllegalStateException] rather than [IllegalArgumentException]
     * (mirrors `Proposal`'s own convention in `dispatch`).
     */
    fun recordPeriod(targetStatus: SubscriptionStatus, currentPeriodEnd: Instant?, now: Instant) {
        check(isPermittedContinuation(status, targetStatus)) {
            "Subscription transition from $status to $targetStatus is not permitted"
        }
        status = targetStatus
        this.currentPeriodEnd = currentPeriodEnd
        updatedAt = now
    }

    companion object {
        /**
         * Brings a new [Subscription] into existence for a driver who
         * currently has no record at all -- the conceptual `FREE` state.
         * [targetStatus] must be [SubscriptionStatus.TRIAL] or
         * [SubscriptionStatus.ACTIVE]; anything else (including
         * [SubscriptionStatus.FREE] itself, or [SubscriptionStatus.EXPIRED])
         * throws [IllegalArgumentException], since starting a brand-new
         * record already expired is not a transition this table permits.
         */
        fun start(
            driverId: DriverReference,
            targetStatus: SubscriptionStatus,
            currentPeriodEnd: Instant?,
            isTest: Boolean,
            now: Instant
        ): Subscription {
            require(targetStatus == SubscriptionStatus.TRIAL || targetStatus == SubscriptionStatus.ACTIVE) {
                "A driver with no subscription record (FREE) may only transition to TRIAL or ACTIVE, not $targetStatus"
            }
            return Subscription(driverId, targetStatus, currentPeriodEnd, isTest, now, now)
        }

        /**
         * Reconstructs an already-persisted [Subscription] exactly as
         * stored, for a repository adapter's own use only (e.g.
         * `com.pios.billing.persistence.PostgreSQLSubscriptionRepository`).
         * Bypasses [start]'s own `FREE`-origin guard because a persisted
         * row, by definition, already exists and is never itself `FREE`
         * (the aggregate's own constructor guard still applies).
         */
        fun reconstruct(
            driverId: DriverReference,
            status: SubscriptionStatus,
            currentPeriodEnd: Instant?,
            isTest: Boolean,
            createdAt: Instant,
            updatedAt: Instant
        ): Subscription = Subscription(driverId, status, currentPeriodEnd, isTest, createdAt, updatedAt)

        private val PERMITTED_CONTINUATIONS: Set<Pair<SubscriptionStatus, SubscriptionStatus>> = setOf(
            SubscriptionStatus.TRIAL to SubscriptionStatus.ACTIVE,
            SubscriptionStatus.TRIAL to SubscriptionStatus.EXPIRED,
            SubscriptionStatus.ACTIVE to SubscriptionStatus.EXPIRED,
            SubscriptionStatus.EXPIRED to SubscriptionStatus.ACTIVE
        )

        private fun isPermittedContinuation(from: SubscriptionStatus, to: SubscriptionStatus): Boolean =
            (from to to) in PERMITTED_CONTINUATIONS
    }
}
