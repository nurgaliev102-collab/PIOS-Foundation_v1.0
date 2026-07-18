package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionOperations

/**
 * The real, PostgreSQL-transaction-backed [TransactionRunner] (ADR-032).
 * Delegates to Spring's [TransactionOperations] — the only place in this
 * module that names a transaction-management framework type; [TransactionRunner]
 * itself, and everything in the `application` package, remain unaware of it.
 */
@Component
class SpringTransactionRunner(
    private val transactionOperations: TransactionOperations
) : TransactionRunner {
    override fun <T> run(action: () -> T): T = transactionOperations.execute { action() }!!
}
