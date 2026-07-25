-- Sprint 7B: Personal Network Flow MVP. Passenger Experience's own
-- PostgreSQL schema (ADR-005/ADR-009: own database, no shared tables).
-- Records that a passenger reached PIOS through a specific driver's own
-- invitation link. UNIQUE(driver_id, passenger_reference) makes opening
-- the same link twice idempotent at the storage level, not just in
-- application code -- the same "explicit existence-check-before-save"
-- caution CreateDriverApplicationService already established, applied
-- here as a hard constraint since a link can legitimately be opened more
-- than once by the same passenger.
CREATE TABLE connections (
    id TEXT PRIMARY KEY,
    driver_id TEXT NOT NULL,
    passenger_reference TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (driver_id, passenger_reference)
);
