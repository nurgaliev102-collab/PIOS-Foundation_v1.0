-- Adds the passenger's own requested pickup instant, for a pre-booked ride
-- (ADR-058, Scheduled Pickup Time). Nullable, no DEFAULT, following V8's own
-- precedent for `pickup_address`: any row already present satisfies the new
-- column definition without one -- `null` means "as soon as possible", the
-- behaviour every existing row already has, unchanged. Stored as
-- TIMESTAMPTZ, the same shape `created_at` already uses (ADR-058 Decision
-- item 4) -- not a naive local-time string, and no timezone column.
ALTER TABLE orders
    ADD COLUMN requested_pickup_at TIMESTAMPTZ NULL;
