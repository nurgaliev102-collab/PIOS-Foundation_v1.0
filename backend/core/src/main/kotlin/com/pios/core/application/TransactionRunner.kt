package com.pios.core.application

/**
 * The transactional boundary [ParticipantHistoryProjectionApplicationService]
 * uses to record an event's idempotency marker and write its history
 * effect together, atomically (ADR-032 / ADR-067 Idempotency) — [run]
 * either completes both writes or neither.
 *
 * No storage technology or transaction-management framework is named
 * here: [com.pios.core.persistence.SpringTransactionRunner] is the real,
 * PostgreSQL-transaction-backed implementation Spring wires in;
 * [NoOpTransactionRunner] is the default for callers that don't need real
 * transactional behaviour (constructor-based unit tests). Mirrors
 * dispatch's own `TransactionRunner` exactly.
 */
interface TransactionRunner {
    fun <T> run(action: () -> T): T
}
