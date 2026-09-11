package com.pios.core.persistence

import com.pios.core.application.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionOperations

/**
 * The real, PostgreSQL-transaction-backed [TransactionRunner] (ADR-032).
 * Delegates to Spring's [TransactionOperations] — the only place in this
 * module that names a transaction-management framework type;
 * [TransactionRunner] itself, and everything in the `application`
 * package, remain unaware of it. Mirrors dispatch's own
 * `SpringTransactionRunner` exactly.
 */
@Component
class SpringTransactionRunner(
    private val transactionOperations: TransactionOperations
) : TransactionRunner {
    // Spring's TransactionOperations.execute is @Nullable, so Kotlin sees
    // its result as T? regardless of what T itself is. `as T` is an
    // unchecked (erased) cast that lets a genuinely null result flow
    // through when T is nullable, while remaining exactly as safe as
    // before when T is not — execute never returns null unless action()
    // itself did, and a non-nullable-typed Kotlin lambda can never itself
    // evaluate to null. Same reasoning as dispatch's own copy.
    @Suppress("UNCHECKED_CAST")
    override fun <T> run(action: () -> T): T = transactionOperations.execute { action() } as T
}
