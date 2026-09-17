-- Server-side read model for "Мой бизнес" (driver CRM view of their
-- passengers), per docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md Part 5's
-- "safe to implement autonomously" read-model half of the Relationship
-- stage. `driver_client_rides` (V7) already carries `ride_count` per
-- (driver, passenger) pair, but nothing recorded *when* the most recent
-- ride happened -- the frontend had to derive that itself from proposals/
-- assignments already loaded in the browser
-- (`DriverHome.tsx`'s `clientRideStatsByReference`). This column lets the
-- backend answer "when was this passenger's last ride with this driver"
-- directly.
--
-- A new migration, not an edit to V7, per this codebase's own forward-only
-- Flyway convention. Nullable and unbackfilled: existing rows (rides
-- recorded before this column existed) simply have no known last-ride
-- timestamp -- same no-backfill precedent as ADR-065's own
-- `total_stated_earnings`/`unpriced_rides_count` columns.

ALTER TABLE driver_client_rides ADD COLUMN last_ride_at TIMESTAMPTZ;
