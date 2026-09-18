-- D-07 (Handoff Protocol): the executing driver becomes a fact distinct
-- from Trip's own committing driver (`driver_reference`, unchanged).
-- Additive, nullable column -- no backfill of existing rows: reconstruction
-- (PostgreSQLTripRepository.reconstruct) already falls back to
-- driver_reference when this column is NULL, which is the only value any
-- pre-D-07 Trip could ever have had (Handoff did not exist), so the
-- fallback is exact, not an approximation -- the same no-backfill
-- discipline D-06 already established for trips.agreed_amount.
ALTER TABLE trips ADD COLUMN executing_driver_reference TEXT;

-- The Handoff aggregate itself (D-07). One row per proposed Handoff;
-- historical (REFUSED/WITHDRAWN) rows are never deleted or overwritten --
-- each Handoff attempt is its own permanent audit record.
CREATE TABLE handoffs (
    id                           TEXT PRIMARY KEY,
    assignment_id                TEXT NOT NULL REFERENCES assignments (id),
    order_reference               TEXT NOT NULL,
    original_driver_reference     TEXT NOT NULL,
    substitute_driver_reference   TEXT NOT NULL,
    passenger_reference           TEXT,
    status                       TEXT NOT NULL,
    proposed_at                  TIMESTAMPTZ NOT NULL,
    substitute_accepted_at       TIMESTAMPTZ,
    resolved_at                  TIMESTAMPTZ,
    is_test                      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX handoffs_assignment_id_idx ON handoffs (assignment_id);

-- D-07 Invariant: at most one non-terminal (PROPOSED / SUBSTITUTE_ACCEPTED)
-- Handoff per Assignment at a time -- the persistent backstop for
-- Handoff.propose's own in-memory check, the same two-layer pattern
-- trips.assignment_id's own UNIQUE constraint already established for
-- Trip.create (V12__trips.sql).
CREATE UNIQUE INDEX handoffs_one_active_per_assignment
    ON handoffs (assignment_id)
    WHERE status IN ('PROPOSED', 'SUBSTITUTE_ACCEPTED');
