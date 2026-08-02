-- Adds Order's own optional pickup address (Sprint H5: Entrepreneur
-- Working Cycle Integrity). Nullable, no DEFAULT, following V6/V7's own
-- precedent for `destination`/`passenger_name`/`created_at`: any row
-- already present satisfies the new column definition without one, and
-- the value is never caller-required -- `passenger-experience`'s
-- RestClientOrderSubmissionClient still sends only `passengerReference`
-- and must keep working unmodified (ADR-026's independent-deployability
-- guarantee).
ALTER TABLE orders
    ADD COLUMN pickup_address TEXT NULL;
