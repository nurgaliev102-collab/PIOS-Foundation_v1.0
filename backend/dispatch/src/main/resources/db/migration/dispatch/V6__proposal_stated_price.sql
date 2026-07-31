-- Adds the price a driver states when accepting a Proposal (ADR-042,
-- Stated Ride Price Minimal Model, Decision Revised R2/R5). Nullable, no
-- DEFAULT, following this project's own established precedent for exactly
-- this kind of additive column (V5__assignment_ride_lifecycle.sql's own
-- comment, itself following order-management's
-- `V6__add_optional_order_destination.sql`): any row already present
-- satisfies the new column definition without one, and it is never set
-- except by a driver's own accept action. Stored as plain TEXT -- an
-- opaque, unvalidated, driver-stated fact, not a typed monetary amount
-- (ADR-042 R5, Open Question 2 remains open).
ALTER TABLE proposals
    ADD COLUMN stated_price TEXT NULL;
