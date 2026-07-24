-- Dispatch's own PostgreSQL schema for the Proposal aggregate (ADR-035;
-- Implementation Design — Proposal Aggregate, Section 5). Persists
-- exactly the Proposal logical entity this module owns — id, the order
-- and driver it connects (by reference only, never a copy of Order
-- Management's or Driver Management's own information;
-- PERSISTENCE_ARCHITECTURE.md Section 5), and status — mirroring the
-- `assignments` table (V1__initial_schema.sql) exactly. Status is stored
-- as text rather than a native enum type for the same reason: evolution
-- of ProposalStatus stays a plain data change, not a schema migration.
CREATE TABLE proposals (
    id TEXT PRIMARY KEY,
    order_reference TEXT NOT NULL,
    driver_reference TEXT NOT NULL,
    status TEXT NOT NULL
);
