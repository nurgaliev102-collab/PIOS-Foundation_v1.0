-- Billing's own PostgreSQL schema (ADR-074 Part 2). Persists exactly the
-- Subscription logical entity this module owns -- driver_id, status,
-- current_period_end, is_test, and timestamps -- and nothing belonging to
-- any other domain (no shared database, no cross-domain table, no foreign
-- key into driver-management's own schema: driver_id is a plain reference,
-- never a FK, per ADR-005/ADR-019).
--
-- driver_id is the primary key: exactly one current subscription record
-- per driver (ADR-074 Part 2). No row is ever written for a driver whose
-- status is FREE -- the absence of a row for a driver_id IS FREE, by
-- construction. status never holds 'FREE' (enforced in the domain layer,
-- Subscription's own constructor guard).
CREATE TABLE subscriptions (
    driver_id TEXT PRIMARY KEY,
    status TEXT NOT NULL,
    current_period_end TIMESTAMPTZ,
    is_test BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
