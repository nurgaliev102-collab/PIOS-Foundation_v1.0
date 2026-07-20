package com.pios.dispatch.application

/**
 * The transactional boundary [DriverAvailabilityProjectionApplicationService]
 * uses to record an event's idempotency marker and apply its local
 * availability effect together, atomically (ADR-032) -- [run] either
 * completes both writes or neither. No storage technology or
 * transaction-management framework is named here:
 * [com.pios.dispatch.persistence.SpringTransactionRunner] is the real,
 * PostgreSQL-transaction-backed implementation Spring wires in;
 * [NoOpTransactionRunner] is the default for callers that don't need real
 * transactional behavior (for example, tests exercising unrelated logic).
 */
interface TransactionRunner {
    fun <T> run(action: () -> T): T
}
