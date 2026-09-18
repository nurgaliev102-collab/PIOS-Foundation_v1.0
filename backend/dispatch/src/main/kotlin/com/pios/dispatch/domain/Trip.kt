package com.pios.dispatch.domain

import java.time.Instant
import java.util.UUID

/**
 * The Trip aggregate (ADR-063: Trip as a New Dispatch-Owned Aggregate,
 * Distinct from Assignment), within Dispatch's exclusive ownership,
 * following the same module-isolation rules already governing
 * [Assignment] (ADR-002, ADR-005, ADR-019).
 *
 * Represents the actual service execution following a driver's
 * connection to an order — distinct from [Assignment], which represents
 * only the match itself. Order = passenger's request (Order Management,
 * unaffected by this aggregate); Assignment = the match (this module,
 * unaffected in its own scope by this aggregate); Trip = the execution
 * (this aggregate).
 *
 * ## Creation trigger (corrected, Task 11A)
 *
 * A Trip is created from [assignment], the [Assignment] it originated
 * from, at the exact moment [Assignment.create] succeeds — i.e., on the
 * production of [OrderAssigned], never on a read of [Assignment.status]
 * and never on [AssignmentAccepted] (that event is not published by any
 * live production path today; see `ADR-063`'s own Status section for the
 * full call-graph evidence). [create] is therefore invoked in-process,
 * from the same application-layer code that already creates the
 * [Assignment] ([com.pios.dispatch.application.DispatchAssignmentApplicationService]),
 * never as a reaction to a separately-consumed event.
 *
 * ## Backward compatibility (Task 11's own explicit scope)
 *
 * This aggregate's own [ARRIVED]/[IN_PROGRESS]/[COMPLETED] lifecycle
 * (via [arrive]/[start]/[complete] below) exists alongside, not instead
 * of, [Assignment]'s own identical ride-progress methods — neither is
 * wired to the other, and neither is wired into any existing REST
 * endpoint by this task. `ADR-063`'s own target design (Assignment's
 * ride-progress methods eventually removed, replaced entirely by Trip's)
 * remains the intended end state; this task deliberately stops short of
 * it, per its own "CURRENT Assignment behavior + NEW Trip foundation
 * must coexist" requirement.
 *
 * Invariant: only Dispatch may create or change a Trip — upheld
 * structurally, the same way as [Assignment]: a private constructor
 * makes [create] the only means by which a Trip comes into existence.
 *
 * Invariant: at most one Trip exists per Assignment. [create] enforces
 * this against an optional, caller-supplied existing Trip — mirroring
 * [Assignment.create]'s own `existingAssignments` check exactly, adapted
 * to Trip's own one-to-one (not one-to-many) relationship with its
 * originating Assignment. A database-level `UNIQUE` constraint on
 * `trips.assignment_id` (V12 migration) is the second, persistent half
 * of this guarantee — surviving process restart and closing the
 * check-then-create race this in-memory check alone cannot.
 *
 * [isTest] is derived from the originating [Assignment]'s own [isTest],
 * the same same-module derivation [order]/[driver] already receive —
 * never independently supplied, mirroring [Assignment.isTest]'s own
 * convention exactly.
 *
 * ## Agreed amount (D-06, Settlement as Evidence)
 *
 * [agreedAmount] is the amount agreed for *this* Trip, captured exactly
 * once — at [create] — and never again. It has no setter, is not
 * recomputed by [arrive]/[start]/[complete]/[terminate], and this class
 * never reads a [com.pios.dispatch.domain.Proposal] to determine or
 * revise it. The caller of [create] supplies it already known (the
 * application layer's own [com.pios.dispatch.domain.Proposal.statedPrice]
 * at the moment the just-accepted Proposal produced this Trip, in the
 * same transaction) — `null` when no Proposal precedes this Trip (the
 * manual-assignment path) or for any Trip created before this field
 * existed (no backfill, per D-06 Decision item 7).
 * [com.pios.dispatch.domain.Proposal.statedPrice] itself remains a
 * historical record of what was stated, on the Proposal aggregate; it is
 * no longer the source of truth for what a specific Trip's own agreed
 * amount is — this field is.
 *
 * ## Executing driver (D-07, Handoff Protocol)
 *
 * [driver] is the original committing driver — set once at [create],
 * exactly like [agreedAmount], and never rewritten by anything in this
 * class, including [assignExecutingDriver]. It answers "who originally
 * accepted this commitment," permanently (D-07 invariant: the original
 * committer remains historically identifiable).
 *
 * [executingDriver] answers the separate question "who actually executes
 * (or executed) this ride" — it defaults to [driver] and is changed,
 * exactly once per Handoff, only by [assignExecutingDriver], called only
 * by [com.pios.dispatch.application.HandoffApplicationService.consent]
 * at the moment a Handoff's passenger consent is recorded (the one and
 * only constitutive transition D-07 defines). Every ride-progress event
 * ([arrive]/[start]/[complete]) reports [executingDriver], not [driver],
 * as its own `driverId` — before any Handoff the two are identical, so
 * this changes nothing for the overwhelming majority of Trips; after a
 * consented Handoff, it is what makes `AssignmentCompleted`/`TripCompleted`
 * correctly attribute execution (and, downstream, Driver Management's own
 * `completedRidesCount`/`totalStatedEarnings`) to the driver who actually
 * drove, per D-07/D-08(b)'s own locked attribution split — without
 * changing either event's own field names, shape, or `eventVersion`.
 */
class Trip private constructor(
    val id: TripId,
    val assignmentId: AssignmentId,
    val order: OrderReference,
    val driver: DriverReference,
    val agreedAmount: String?,
    val isTest: Boolean = false,
    executingDriver: DriverReference = driver
) {
    var status: TripStatus = TripStatus.CREATED
        private set

    var executingDriver: DriverReference = executingDriver
        private set

    /** The time of this trip's most recent status transition — `null` until the first one happens, mirroring [Assignment.statusChangedAt] exactly. */
    var statusChangedAt: Instant? = null
        private set

    /** The moment this trip reached [TripStatus.ARRIVED] — `null` until [arrive] is called. */
    var arrivedAt: Instant? = null
        private set

    /** The moment this trip reached [TripStatus.IN_PROGRESS] — `null` until [start] is called. */
    var startedAt: Instant? = null
        private set

    /** The moment this trip reached [TripStatus.COMPLETED] — `null` until [complete] is called. */
    var completedAt: Instant? = null
        private set

    var termination: Termination? = null
        private set

    /**
     * Records that the driver has reached the passenger. Only a
     * [TripStatus.CREATED] trip may arrive — unlike [Assignment.arrive],
     * which also accepts [AssignmentStatus.ACCEPTED] to accommodate that
     * aggregate's own documented gap (ADR-040 Decision item 2), Trip has
     * exactly one pre-arrival state, so no such accommodation is needed
     * here.
     */
    fun arrive(at: Instant = Instant.now()): TripArrived {
        check(status == TripStatus.CREATED) {
            "Trip ${id.value} cannot be marked arrived from status $status"
        }
        status = TripStatus.ARRIVED
        statusChangedAt = at
        arrivedAt = at
        return TripArrived(orderId = order, driverId = executingDriver)
    }

    /** Records that the ride itself has begun. Only an [TripStatus.ARRIVED] trip may start. */
    fun start(at: Instant = Instant.now()): TripStarted {
        check(status == TripStatus.ARRIVED) {
            "Trip ${id.value} cannot be started from status $status"
        }
        status = TripStatus.IN_PROGRESS
        statusChangedAt = at
        startedAt = at
        return TripStarted(orderId = order, driverId = executingDriver)
    }

    /** Records that the ride has finished. Only an [TripStatus.IN_PROGRESS] trip may complete. */
    fun complete(at: Instant = Instant.now()): TripCompleted {
        check(status == TripStatus.IN_PROGRESS) {
            "Trip ${id.value} cannot be completed from status $status"
        }
        status = TripStatus.COMPLETED
        statusChangedAt = at
        completedAt = at
        return TripCompleted(orderId = order, driverId = executingDriver)
    }

    fun terminate(fact: Termination) {
        check(status != TripStatus.COMPLETED && status != TripStatus.TERMINATED) {
            "Trip ${id.value} cannot be terminated from status $status"
        }
        status = TripStatus.TERMINATED
        statusChangedAt = fact.terminatedAt
        termination = fact
    }

    /**
     * Records that [driver] is now this Trip's own executing driver
     * (D-07, Handoff Protocol) — called only at the moment a Handoff's
     * passenger consent is recorded, inside the same transaction that
     * commits the Handoff itself. Never touches [driver] the field
     * above (the permanent, original committing driver) — see this
     * class's own "Executing driver" KDoc. Only reachable while the Trip
     * is still [TripStatus.CREATED] or [TripStatus.ARRIVED] — the same
     * window D-07 restricts Handoff to; a Trip already [TripStatus.IN_PROGRESS],
     * [TripStatus.COMPLETED], or [TripStatus.TERMINATED] rejects this,
     * so this method is itself one of the structural guards against a
     * Handoff resolving after the ride has moved on.
     */
    fun assignExecutingDriver(driver: DriverReference) {
        check(status == TripStatus.CREATED || status == TripStatus.ARRIVED) {
            "Trip ${id.value} cannot change its executing driver from status $status"
        }
        executingDriver = driver
    }

    companion object {
        /**
         * Creates a new Trip originating from [assignment]. Rejects the
         * creation if [existingTrip] is non-null (already one Trip for
         * this Assignment) — the caller is responsible for supplying it,
         * the same responsibility [Assignment.create]'s own caller has
         * for [Assignment.create]'s `existingAssignments`.
         *
         * [agreedAmount] (D-06) is captured here, once, as supplied by the
         * caller — this factory does not look it up itself, does not read
         * a Proposal, and does not compute it. `null` when the caller has
         * none (manual assignment, or a self-heal Trip created with no
         * Proposal in the picture at all — see
         * [com.pios.dispatch.application.DispatchAssignmentApplicationService.tripFor]).
         */
        fun create(
            assignment: Assignment,
            agreedAmount: String? = null,
            existingTrip: Trip? = null
        ): TripCreated {
            check(existingTrip == null) {
                "Assignment ${assignment.id.value} already has a Trip"
            }
            val trip = Trip(
                id = TripId(UUID.randomUUID().toString()),
                assignmentId = assignment.id,
                order = assignment.order,
                driver = assignment.driver,
                agreedAmount = agreedAmount,
                isTest = assignment.isTest
            )
            return TripCreated(trip)
        }
    }
}

/**
 * The result of creating a trip: the new [Trip]. Unlike [AssignmentCreated],
 * this carries no domain event — Trip's own creation is a purely
 * Dispatch-internal fact today (nothing outside this module, and nothing
 * else inside it, needs to react to "a Trip now exists"), so no event is
 * introduced for it, consistent with this task's own instruction not to
 * create an event merely because it appears conceptually useful.
 */
data class TripCreated(val trip: Trip)
