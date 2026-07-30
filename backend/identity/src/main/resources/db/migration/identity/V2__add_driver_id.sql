-- ADR-039: Identity's first real cross-module reference — a plain string
-- pointing at a driver-management-owned DriverId, never a foreign key
-- (driver-management's database is a separate database entirely; no
-- cross-database constraint is possible or intended, per ADR-005/ADR-009).
-- Additive only: existing rows keep driver_id = NULL, unaffected.

ALTER TABLE identities ADD COLUMN driver_id TEXT;
