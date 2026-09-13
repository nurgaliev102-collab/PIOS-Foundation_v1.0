package com.pios.dispatch.domain

import java.time.Instant
import java.util.UUID

/**
 * Who sent a given [ProposalMessage] — the two, and only two, participants
 * a [Proposal] itself already recognizes ([Proposal.passengerReference],
 * [Proposal.driver]). Never a caller-supplied claim: every real sender is
 * derived at the API boundary from which identity the caller's own Bearer
 * token names (`ProposalMessageController`'s own KDoc), the same
 * discipline every other Proposal action already relies on.
 */
enum class MessageSenderRole { PASSENGER, DRIVER }

/**
 * A single short message exchanged between the passenger and driver of one
 * specific [Proposal] (Minimal In-Ride Messaging MVP; Product Cycle).
 *
 * Deliberately scoped to a [Proposal], not an [OrderReference] directly:
 * "коммуникация принадлежит конкретной поездке, а не платформе в целом" —
 * a [Proposal] already is the specific, already-authorized pairing between
 * one order and one driver (mirrors [Proposal]'s own KDoc: "a single
 * proposed pairing between an order and a driver"), and it persists,
 * unchanged in identity, for the entire rest of a ride's life — its own
 * [ProposalStatus] freezes at `ACCEPTED` once a ride begins; ride progress
 * from that point on is tracked separately, by [Trip] (not [Assignment] —
 * see [com.pios.dispatch.application.ProposalMessagingApplicationService]'s
 * own KDoc for why). Keying by
 * [proposalId] rather than a fresh order-level thread means a later,
 * different proposal for the same order (after a decline, to a different
 * driver) never inherits an earlier, unrelated conversation — exactly the
 * "невозможность перепутать сообщения разных заказов" requirement, applied
 * one level more precisely than the order itself.
 *
 * No lifecycle of its own: unlike [Proposal]/[Assignment], a message is
 * never edited, withdrawn, or transitioned once sent — it is a plain,
 * immutable fact, closer in shape to a domain event than a mutable
 * aggregate. Its own public constructor (unlike [Proposal]'s/[Order]'s
 * deliberately private ones) reflects exactly that: reconstructing a
 * persisted message from a database row re-runs the same two `require`
 * checks a fresh [send] already satisfied, never bypassing anything —
 * there is no later mutation a private constructor would need to protect
 * against replaying incorrectly, so no persistence-layer reflection is
 * needed anywhere for this class (contrast
 * [com.pios.dispatch.persistence.PostgreSQLProposalRepository]'s own
 * KDoc on exactly that need for [Proposal]).
 *
 * Gating on *when* a message may be sent — which [ProposalStatus]/
 * [TripStatus] combination still permits it, and what happens after
 * `COMPLETED` — is not this class's own concern (this class only knows how
 * to represent one already-decided-valid message); see
 * [com.pios.dispatch.application.ProposalMessagingApplicationService.isMessagingOpen]
 * for that decision.
 */
data class ProposalMessage(
    val id: ProposalMessageId,
    val proposalId: ProposalId,
    val senderRole: MessageSenderRole,
    val body: String,
    val sentAt: Instant
) {
    init {
        require(body.isNotBlank()) { "body must not be blank" }
        require(body.length <= MAX_BODY_LENGTH) { "body must be at most $MAX_BODY_LENGTH characters" }
    }

    companion object {
        /**
         * The one bound this MVP enforces on a message's own [body] — short,
         * per this cycle's own explicit "короткое сообщение" requirement, and
         * enforced here (not only trusted from whichever frontend screen
         * happens to call [send]) for the same reason
         * [com.pios.ordermanagement.domain.Order.MAX_NOTES_LENGTH] is enforced
         * server-side rather than only by a textarea's own `maxLength`.
         */
        const val MAX_BODY_LENGTH = 300

        /**
         * Sends a new message on [proposalId], per the sender's own act.
         * [body] is trimmed before the length/blank checks run, mirroring
         * [com.pios.ordermanagement.domain.Order.submit]'s own treatment of
         * passenger-supplied free text — whitespace alone is never a message.
         *
         * [at] defaults to the current time; overridable so
         * [com.pios.dispatch.persistence.PostgreSQLProposalMessageRepository]
         * can replay a previously persisted `sent_at` during reconstruction
         * instead of the moment of the read, mirroring [Proposal.accept]'s
         * own already-established `at` parameter precedent (ADR-043).
         */
        fun send(
            proposalId: ProposalId,
            senderRole: MessageSenderRole,
            body: String,
            at: Instant = Instant.now()
        ): ProposalMessage =
            ProposalMessage(
                id = ProposalMessageId(UUID.randomUUID().toString()),
                proposalId = proposalId,
                senderRole = senderRole,
                body = body.trim(),
                sentAt = at
            )
    }
}
