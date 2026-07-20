package com.pios.dispatch.application

/**
 * A [TransactionRunner] that runs [action] with no real transactional
 * boundary at all. See [TransactionRunner]'s own KDoc for when this is
 * used instead of the real, Spring-backed implementation.
 */
object NoOpTransactionRunner : TransactionRunner {
    override fun <T> run(action: () -> T): T = action()
}
