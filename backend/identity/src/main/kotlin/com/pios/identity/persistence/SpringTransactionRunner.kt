package com.pios.identity.persistence

import com.pios.identity.application.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionOperations

/**
 * The real, PostgreSQL-transaction-backed [TransactionRunner], mirroring
 * `com.pios.networkmanagement.persistence.SpringTransactionRunner` exactly.
 */
@Component
class SpringTransactionRunner(
    private val transactionOperations: TransactionOperations
) : TransactionRunner {
    @Suppress("UNCHECKED_CAST")
    override fun <T> run(block: () -> T): T = transactionOperations.execute { block() } as T
}
