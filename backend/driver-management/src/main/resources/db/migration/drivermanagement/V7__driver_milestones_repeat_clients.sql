-- Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
-- Section 2.1's own disclosed gap, now closed): repeat-client tracking.
-- `AssignmentCompleted` alone never named the passenger; this correlates
-- it locally against Order Management's own `OrderSubmitted` (already
-- publishing `payload.passengerReference` for exactly this kind of
-- cross-module correlation, per that event's own KDoc -- Dispatch's own
-- First Refusal feature already reads it the same way, no new contract).
--
-- A new migration, not an edit to V6, per this codebase's own forward-only
-- Flyway convention (V5 added is_test by ALTER, never edited V1).

ALTER TABLE driver_milestones ADD COLUMN repeat_clients_count INT NOT NULL DEFAULT 0;

-- One row per order this module has seen submitted, purely to answer "which
-- passenger was this order for" once a later AssignmentCompleted for the
-- same order_id arrives. Not a copy of Order Management's own Order
-- aggregate -- carries nothing but the one opaque reference this
-- correlation needs.
CREATE TABLE driver_management_order_passengers (
    order_id TEXT PRIMARY KEY,
    passenger_reference TEXT NOT NULL
);

-- One row per (driver, passenger) pair that has completed at least one
-- ride together. ride_count reaching 2 is the moment this pair becomes a
-- "repeat client" -- driver_milestones.repeat_clients_count is incremented
-- exactly once, at that transition, not recomputed by scanning this table.
CREATE TABLE driver_client_rides (
    driver_id TEXT NOT NULL REFERENCES drivers(id),
    passenger_reference TEXT NOT NULL,
    ride_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (driver_id, passenger_reference)
);
