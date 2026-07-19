package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.TransactionRunner
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
    // Not `!!`, unlike Order Management's identically-named class:
    // DriverAvailabilityApplicationService.handle legitimately returns
    // null (no availability change occurred), so T is instantiated as a
    // nullable type here -- asserting non-null on that result would throw
    // on every no-op declaration. Order Management never wraps a
    // nullable-returning action in transactionRunner.run, which is why
    // this difference has never surfaced there. Spring's
    // TransactionOperations.execute is @Nullable, so Kotlin sees its
    // result as T? regardless of what T itself is; `as T` is an unchecked
    // (erased) cast that lets a genuinely null result flow through when T
    // is nullable, while remaining exactly as safe as before when T is not
    // -- execute never actually returns null unless action() itself did.
    @Suppress("UNCHECKED_CAST")
    override fun <T> run(action: () -> T): T = transactionOperations.execute { action() } as T
}
