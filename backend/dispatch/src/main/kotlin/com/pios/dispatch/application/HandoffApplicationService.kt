package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.Handoff
import com.pios.dispatch.domain.HandoffCommitted
import com.pios.dispatch.domain.HandoffCreated
import com.pios.dispatch.domain.HandoffRefused
import com.pios.dispatch.domain.HandoffSubstituteAccepted
import com.pios.dispatch.domain.HandoffWithdrawn
import com.pios.dispatch.domain.ProposalStatus
import com.pios.dispatch.domain.TripStatus
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for D-07's five Handoff commands
 * (`docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md` §7). This service
 * sequences each command into [Handoff]'s own behavior and, at [consent]
 * only, into the connected [Trip][com.pios.dispatch.domain.Trip]'s own
 * [com.pios.dispatch.domain.Trip.assignExecutingDriver] — it does not
 * decide anything itself (APPLICATION_ARCHITECTURE.md Section 2), the
 * same "Domain Decides Business Meaning" discipline every other
 * application service in this module already follows.
 *
 * ## Authorization is the caller's own responsibility (§11 of the spec)
 *
 * None of the five methods below re-verifies "is this caller allowed to
 * do this" against a request-supplied identity, because none of the
 * commands carries one (see `HandoffCommands.kt`'s own KDoc). This
 * mirrors [AssignmentTerminationController.terminate] and
 * `AssignmentController.arrive/start/complete` exactly: the controller
 * loads the aggregate, compares the verified session against its own
 * field, and only then calls this service. Every method here still
 * re-reads its own aggregate *inside* the order lock before acting —
 * that re-read is a concurrency safeguard (state may have changed since
 * the controller's own pre-lock read), not an authorization check.
 *
 * ## Locking discipline (D-01's own, reused unmodified)
 *
 * Every method acquires [orderGuard]'s lock on the Handoff's own [order],
 * exactly the same `orderId`-scoped lock
 * [CommitmentTerminationApplicationService.terminate] and
 * [DispatchAssignmentApplicationService.completeAssignment] already
 * share — so a Handoff resolution and a D-01 termination (or a Trip
 * ride-progress transition) can never race each other unobserved
 * (`docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md` §8). No new lock
 * primitive is introduced.
 *
 * ## Eligibility (D-07 invariant #34)
 *
 * [propose] rejects a substitute [DriverReference] that
 * [driverAvailabilityRepository] has no local projection record for —
 * the same "is this driver known to Dispatch at all" check
 * [ProposalApplicationService.handle] already performs for an ordinary
 * Proposal, reused here for the identical reason: it is a verification
 * that the substitute is a real, registered driver, never a PIOS-side
 * *selection* of one (D-07 invariant #35 — [driverAvailabilityRepository]
 * is read, never written, by this class). Defaults to `null`, the same
 * default [ProposalApplicationService]'s own equivalent field uses, so a
 * test double with no interest in eligibility continues to compile and
 * behave exactly as before this check's addition; Spring's real wiring
 * always supplies the real
 * [com.pios.dispatch.persistence.PostgreSQLDriverAvailabilityRepository]
 * bean regardless.
 */
@Service
class HandoffApplicationService(
    private val assignmentRepository: AssignmentRepository,
    private val tripRepository: TripRepository,
    private val handoffRepository: HandoffRepository,
    private val proposalRepository: ProposalRepository,
    private val driverAvailabilityRepository: DriverAvailabilityRepository? = null,
    private val orderGuard: OrderGuard = NoOpOrderGuard,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {

    /**
     * Proposes a Handoff for [command.assignmentId], naming
     * [command.substituteDriverId] as the substitute. Throws
     * [AssignmentNotFoundException] if no such Assignment exists.
     * Propagates [Handoff.propose]'s own [IllegalStateException] (an
     * active Handoff already exists) or [IllegalArgumentException] (the
     * substitute equals the original driver) unchanged.
     */
    fun propose(command: ProposeHandoffCommand): HandoffCreated = transactionRunner.run {
        val assignment = assignmentRepository.findById(command.assignmentId)
            ?: throw AssignmentNotFoundException(command.assignmentId)
        orderGuard.lock(assignment.order)
        val trip = tripRepository.findByAssignmentId(assignment.id)
        checkNotNull(trip) { "Assignment ${assignment.id.value} has no connected Trip" }
        check(trip.status == TripStatus.CREATED || trip.status == TripStatus.ARRIVED) {
            "Trip ${trip.id.value} is not eligible for a Handoff from status ${trip.status}"
        }
        val substitute = DriverReference(command.substituteDriverId)
        if (driverAvailabilityRepository != null) {
            checkNotNull(driverAvailabilityRepository.findByDriverReference(substitute)) {
                "Substitute driver ${substitute.driverId} is not a registered driver"
            }
        }
        val passenger = proposalRepository.findByOrder(assignment.order)
            .firstOrNull { it.status == ProposalStatus.ACCEPTED }
            ?.passengerReference
        val existingActive = handoffRepository.findActiveByAssignmentId(assignment.id)
        val created = Handoff.propose(
            assignmentId = assignment.id,
            order = assignment.order,
            originalDriver = assignment.driver,
            substituteDriver = substitute,
            passenger = passenger,
            existingActiveHandoff = existingActive,
            isTest = assignment.isTest
        )
        handoffRepository.save(created.handoff)
        created
    }

    /**
     * The named substitute explicitly accepts [command.handoffId]. Throws
     * [HandoffNotFoundException] if none exists. Re-verifies the
     * connected Trip is still eligible (§8, race #10) — a Handoff can
     * technically still read as `PROPOSED` while its own Trip has since
     * left `CREATED`/`ARRIVED` through an unrelated path.
     */
    fun acceptSubstitute(command: AcceptHandoffSubstituteCommand): HandoffSubstituteAccepted = transactionRunner.run {
        val handoff = handoffRepository.findById(command.handoffId)
            ?: throw HandoffNotFoundException(command.handoffId)
        orderGuard.lock(handoff.order)
        val locked = handoffRepository.findById(command.handoffId)
            ?: throw HandoffNotFoundException(command.handoffId)
        val trip = tripRepository.findByAssignmentId(locked.assignmentId)
        checkNotNull(trip) { "Assignment ${locked.assignmentId.value} has no connected Trip" }
        check(trip.status == TripStatus.CREATED || trip.status == TripStatus.ARRIVED) {
            "Trip ${trip.id.value} is no longer eligible for this Handoff (status ${trip.status})"
        }
        val event = locked.acceptSubstitute()
        handoffRepository.save(locked)
        event
    }

    /**
     * The passenger's own constitutive consent to [command.handoffId] —
     * the one and only transition that changes the connected Trip's own
     * executing driver. Performs the atomic revalidation D-07 requires
     * (invariant #33, spec §7/§8): under the same order lock this method
     * already holds, it re-confirms the Handoff is still
     * `SUBSTITUTE_ACCEPTED` (via [Handoff.commit]'s own `check`) and the
     * Trip is still `CREATED`/`ARRIVED` — not merely whatever was true
     * when the caller's own screen last polled.
     *
     * `Trip.agreedAmount` and the connected `Connection`/relationship
     * record are never read or written by this method (D-07 invariants
     * #4/#7) — the only write beyond the Handoff's own state is
     * [com.pios.dispatch.domain.Trip.assignExecutingDriver].
     */
    fun consent(command: ConsentHandoffCommand): HandoffCommitted = transactionRunner.run {
        val handoff = handoffRepository.findById(command.handoffId)
            ?: throw HandoffNotFoundException(command.handoffId)
        orderGuard.lock(handoff.order)
        val locked = handoffRepository.findById(command.handoffId)
            ?: throw HandoffNotFoundException(command.handoffId)
        val trip = tripRepository.findByAssignmentId(locked.assignmentId)
        checkNotNull(trip) { "Assignment ${locked.assignmentId.value} has no connected Trip" }
        check(trip.status == TripStatus.CREATED || trip.status == TripStatus.ARRIVED) {
            "Trip ${trip.id.value} is no longer eligible for this Handoff (status ${trip.status})"
        }
        val event = locked.commit()
        handoffRepository.save(locked)
        trip.assignExecutingDriver(locked.substituteDriver)
        tripRepository.save(trip)
        event
    }

    /**
     * The passenger declines [command.handoffId]. Never touches the
     * Assignment/Trip in any way (D-07 invariant: refusal cancels only
     * the proposal).
     */
    fun refuse(command: RefuseHandoffCommand): HandoffRefused = transactionRunner.run {
        val handoff = handoffRepository.findById(command.handoffId)
            ?: throw HandoffNotFoundException(command.handoffId)
        orderGuard.lock(handoff.order)
        val locked = handoffRepository.findById(command.handoffId)
            ?: throw HandoffNotFoundException(command.handoffId)
        val event = locked.refuse()
        handoffRepository.save(locked)
        event
    }

    /**
     * The original driver retracts [command.handoffId]. Propagates
     * [Handoff.withdraw]'s own [IllegalStateException] unchanged if the
     * Handoff is already `COMMITTED` — D-07's own unconditional
     * "cannot withdraw after passenger consent" rule, enforced by the
     * domain method itself, not by this service re-deriving it; a
     * withdrawal that loses the order-lock race to a consent that has
     * already committed fails here for exactly that reason (§8, race #9).
     */
    fun withdraw(command: WithdrawHandoffCommand): HandoffWithdrawn = transactionRunner.run {
        val handoff = handoffRepository.findById(command.handoffId)
            ?: throw HandoffNotFoundException(command.handoffId)
        orderGuard.lock(handoff.order)
        val locked = handoffRepository.findById(command.handoffId)
            ?: throw HandoffNotFoundException(command.handoffId)
        val event = locked.withdraw()
        handoffRepository.save(locked)
        event
    }
}
