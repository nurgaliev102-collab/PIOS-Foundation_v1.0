package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionOperations

/**
 * The real, PostgreSQL-transaction-backed [TransactionRunner], mirroring
 * `com.pios.drivermanagement.persistence.SpringTransactionRunner` exactly.
 */
@Component
class SpringTransactionRunner(
    private val transactionOperations: TransactionOperations
) : TransactionRunner {
    @Suppress("UNCHECKED_CAST")
    override fun <T> run(action: () -> T): T = transactionOperations.execute { action() } as T
}
