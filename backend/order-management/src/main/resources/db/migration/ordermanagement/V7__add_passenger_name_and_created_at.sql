-- Adds Order's own optional passenger name and submission time (first-pilot
-- feedback: a driver's order card showed neither). Both nullable, no
-- DEFAULT, following V6's own precedent for `destination`: any row already
-- present satisfies the new column definitions without one, and neither
-- value is ever caller-required.
ALTER TABLE orders
    ADD COLUMN passenger_name TEXT NULL,
    ADD COLUMN created_at TIMESTAMPTZ NULL;
