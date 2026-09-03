-- Dispatch's own PostgreSQL schema for the Trip aggregate (ADR-063: Trip
-- as a New Dispatch-Owned Aggregate, Distinct from Assignment; Task 11,
-- Trip Domain Foundation). Persists exactly the Trip logical entity this
-- module owns -- id, the Assignment it originated from, the order and
-- driver it connects (by reference only, denormalized directly onto this
-- table, the same "reference, not ownership" pattern already established
-- for `assignments`/`proposals`), status, and its own ride-progress
-- timestamps -- mirroring the `assignments` table (V1__initial_schema.sql,
-- V5__assignment_ride_lifecycle.sql, V8__assignment_transition_timestamps.sql,
-- V11__add_proposal_and_assignment_is_test.sql) exactly. Status is stored
-- as plain TEXT for the same reason `assignments.status` is: evolution of
-- TripStatus stays a plain data change, not a schema migration.
--
-- `assignment_id` carries both a `UNIQUE` constraint and a `REFERENCES`
-- foreign key -- unlike `order_reference`/`driver_reference` (which
-- point at other modules' own databases and therefore cannot be foreign
-- keys, per ADR-005/ADR-009's own module-isolation rule), `assignments`
-- is a table this same module, this same database, already owns, so a
-- real foreign key here is the correct, stronger integrity guarantee
-- Task 11 itself asks for -- not a violation of that rule, an
-- application of it within the one boundary where it is actually
-- possible (mirroring network-management's own precedent of real
-- intra-module foreign keys, e.g. `person_profiles.person_id`). The
-- `UNIQUE` constraint is this migration's persistent half of "at most one
-- Trip per Assignment" -- `Trip.create`'s own in-memory check
-- (Trip.kt) is the first half, checked before this constraint would ever
-- be reached in the normal case; this constraint is what makes that
-- invariant survive a process restart and close the check-then-create
-- race the in-memory check alone cannot.
CREATE TABLE trips (
    id TEXT PRIMARY KEY,
    assignment_id TEXT NOT NULL UNIQUE REFERENCES assignments (id),
    order_reference TEXT NOT NULL,
    driver_reference TEXT NOT NULL,
    status TEXT NOT NULL,
    status_changed_at TIMESTAMPTZ NULL,
    arrived_at TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    is_test BOOLEAN NOT NULL DEFAULT FALSE
);
