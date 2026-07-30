-- Adds Assignment's own status-change timestamp (ADR-040, Assignment Ride
-- Lifecycle). Nullable, no DEFAULT, following this project's own established
-- precedent for exactly this kind of additive column (order-management's
-- `V6__add_optional_order_destination.sql`, `V7__add_passenger_name_and_created_at.sql`):
-- any row already present satisfies the new column definition without one,
-- and it is never caller-supplied. `status` itself needs no migration --
-- it is stored as plain TEXT (V1's own comment already explains why), so
-- ARRIVED/IN_PROGRESS/COMPLETED are just new values in an existing column.
ALTER TABLE assignments
    ADD COLUMN status_changed_at TIMESTAMPTZ NULL;
