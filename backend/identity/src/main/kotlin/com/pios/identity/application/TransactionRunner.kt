package com.pios.identity.application

/**
 * The transactional-boundary port for this module (mirrors every other
 * module's own `TransactionRunner`, e.g.
 * `com.pios.networkmanagement.application.TransactionRunner`) — a narrow,
 * technology-free interface application services depend on instead of any
 * concrete transaction API.
 */
interface TransactionRunner {
    fun <T> run(block: () -> T): T
}

/**
 * Default used wherever no real [TransactionRunner] is wired (in-memory
 * repositories, unit tests) — runs [block] with no transactional guarantee
 * at all, same convention as every other module's own `NoOpTransactionRunner`.
 */
object NoOpTransactionRunner : TransactionRunner {
    override fun <T> run(block: () -> T): T = block()
}
