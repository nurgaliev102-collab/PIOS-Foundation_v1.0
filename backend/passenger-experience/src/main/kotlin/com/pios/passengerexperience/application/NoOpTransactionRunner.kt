package com.pios.passengerexperience.application

/**
 * A [TransactionRunner] with no real transactional boundary -- the
 * default for tests and for any caller that does not need one, mirroring
 * `com.pios.drivermanagement.application.NoOpTransactionRunner` exactly.
 */
object NoOpTransactionRunner : TransactionRunner {
    override fun <T> run(action: () -> T): T = action()
}
