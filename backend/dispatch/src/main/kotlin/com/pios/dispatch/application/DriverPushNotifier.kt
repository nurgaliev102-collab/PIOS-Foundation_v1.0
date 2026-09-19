package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.ProposalId

/**
 * The port [ProposalApplicationService] calls to trigger driver Web Push
 * (ADR-083, D-10) -- exactly two methods, deliberately: "the port's
 * two-method signature makes this a compile-level boundary, not just a
 * convention" (ADR-083 Part 4). No third method may be added without a new
 * architecture decision; nothing else in this module may call this
 * interface at all -- decline, decline-price, lapse, withdraw, and every
 * Assignment/Trip transition are all out of scope (ADR-083 Constraints 3, 8).
 *
 * [WebPushDriverPushNotifier][com.pios.dispatch.persistence.WebPushDriverPushNotifier]
 * is the real, Web-Push-backed implementation; [NoOpDriverPushNotifier] is
 * the constructor default on [ProposalApplicationService].
 */
interface DriverPushNotifier {
    /** N1 -- an `OPEN` [com.pios.dispatch.domain.Proposal] was just created for [driver]. */
    fun offerCreated(proposalId: ProposalId, driver: DriverReference)

    /** N2 -- [driver]'s `Proposal` just transitioned `PRICE_PROPOSED -> ACCEPTED` via [com.pios.dispatch.domain.Proposal.confirmPrice]. Never fired for [com.pios.dispatch.domain.Proposal.accept]'s own `OPEN -> ACCEPTED` transition. */
    fun priceConfirmed(proposalId: ProposalId, driver: DriverReference)
}
