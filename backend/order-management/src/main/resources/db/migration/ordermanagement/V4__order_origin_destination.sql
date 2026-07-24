-- Adds Order's own origin and destination (Sprint FND-006: Minimal Order
-- Model) -- closing the gap between DOMAIN_MODEL.md Section 4's
-- already-ratified invariant ("An order ... originates from exactly one
-- source") and this table's previous shape, which recorded neither.
--
-- Both columns are NOT NULL: an Order is not considered a valid order at
-- all without them (see domain/Order.kt's own KDoc). The DEFAULT below
-- exists only to satisfy this constraint for any row already present
-- from earlier development/test use of this schema -- no code in this
-- module ever relies on the default; every real INSERT
-- (PostgreSQLOrderRepository.save) always supplies both values
-- explicitly.
ALTER TABLE orders
    ADD COLUMN origin TEXT NOT NULL DEFAULT 'unknown',
    ADD COLUMN destination TEXT NOT NULL DEFAULT 'unknown';

ALTER TABLE orders ALTER COLUMN origin DROP DEFAULT;
ALTER TABLE orders ALTER COLUMN destination DROP DEFAULT;
