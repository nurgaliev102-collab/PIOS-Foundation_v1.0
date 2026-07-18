package com.pios.ordermanagement.application

/**
 * The transactional boundary [OrderLifecycleApplicationService] uses to
 * save an [com.pios.ordermanagement.domain.Order] and its [OutboxRecord]
 * together, atomically (ADR-032) — [run] either completes both writes or
 * neither. No storage technology or transaction-management framework is
 * named here: [com.pios.ordermanagement.persistence.SpringTransactionRunner]
 * is the real, PostgreSQL-transaction-backed implementation Spring wires
 * in; [NoOpTransactionRunner] is the default for callers that don't need
 * real transactional behavior (for example, tests exercising only order
 * lifecycle logic, unrelated to the outbox).
 */
interface TransactionRunner {
    fun <T> run(action: () -> T): T
}
