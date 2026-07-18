-- Dispatch's own PostgreSQL schema (ADR-025; PERSISTENCE_ARCHITECTURE.md
-- Section 3). Persists exactly the Assignment logical entity this module
-- owns — id, the order and driver it connects (by reference only, never a
-- copy of Order Management's or Driver Management's own information;
-- PERSISTENCE_ARCHITECTURE.md Section 5), and status — and nothing
-- belonging to any other domain (no shared database, no cross-domain
-- tables). Status is stored as text rather than a native enum type to
-- keep evolution of AssignmentStatus (DOMAIN_MODEL.md Section 6) a plain
-- data change, not a schema migration.
--
-- Located under db/migration/dispatch (not the shared db/migration
-- default) so this module's migration never collides, on a shared test
-- classpath, with another module's identically-versioned migration file
-- — the same fix already applied between Order and Driver Management.
CREATE TABLE assignments (
    id TEXT PRIMARY KEY,
    order_reference TEXT NOT NULL,
    driver_reference TEXT NOT NULL,
    status TEXT NOT NULL
);
