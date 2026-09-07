-- PIOS Group and Long-Distance Rides Roadmap, Stage 2 (group orders):
-- additive, nullable column alongside existing state, never replacing it.
-- Every order row that exists before this migration has NULL here --
-- unchanged from today's own implicit assumption of one passenger, not a
-- default PIOS is inventing retroactively.
-- Precedent: V8__add_order_pickup_address.sql (this same table).
ALTER TABLE orders ADD COLUMN passenger_count INTEGER NULL;
