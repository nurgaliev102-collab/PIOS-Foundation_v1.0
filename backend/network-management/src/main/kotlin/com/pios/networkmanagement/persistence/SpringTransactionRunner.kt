package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionOperations

/**
 * The real, PostgreSQL-transaction-backed [TransactionRunner], mirroring
 * `com.pios.drivermanagement.persistence.SpringTransactionRunner` exactly
 * (see that class's own KDoc for the `as T` cast's own reasoning).
 */
@Component
class SpringTransactionRunner(
    private val transactionOperations: TransactionOperations
) : TransactionRunner {
    @Suppress("UNCHECKED_CAST")
    override fun <T> run(action: () -> T): T = transactionOperations.execute { action() } as T
}
