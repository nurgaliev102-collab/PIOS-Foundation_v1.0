-- Product Cycle (Passenger Ride Requirements): additive, nullable column
-- alongside existing state, never replacing it. Every order row that
-- exists before this migration has NULL here -- unchanged from today's
-- own implicit assumption of "nothing stated," not a default PIOS is
-- inventing retroactively. Length is bounded at the application layer
-- (Order.MAX_NOTES_LENGTH = 500), not by a column constraint here --
-- following V8__add_order_pickup_address.sql's own precedent of a plain
-- TEXT column for passenger-supplied free text, with no DB-level CHECK.
ALTER TABLE orders ADD COLUMN notes TEXT NULL;
