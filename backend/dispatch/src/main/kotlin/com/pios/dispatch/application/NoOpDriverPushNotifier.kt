package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.ProposalId

/**
 * A [DriverPushNotifier] that does nothing. The constructor default on
 * [ProposalApplicationService] (ADR-083, D-10), mirroring
 * [NoOpEventPublisher]/[NoOpOrderGuard]'s own established convention
 * exactly, so every existing direct-construction call site and test of
 * this service continues to compile and behave unchanged.
 */
object NoOpDriverPushNotifier : DriverPushNotifier {
    override fun offerCreated(proposalId: ProposalId, driver: DriverReference) = Unit
    override fun priceConfirmed(proposalId: ProposalId, driver: DriverReference) = Unit
}
