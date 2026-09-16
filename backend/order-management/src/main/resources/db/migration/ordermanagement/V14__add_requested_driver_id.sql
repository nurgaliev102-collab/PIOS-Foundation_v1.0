-- The passenger's explicit driver choice is immutable order intent. It is
-- a cross-module reference, never a foreign key, because Driver Management
-- owns the referenced aggregate in a separate database.
ALTER TABLE orders ADD COLUMN requested_driver_id TEXT;

ALTER TABLE orders ADD CONSTRAINT ck_orders_requested_driver_intent
    CHECK (requested_driver_id IS NULL OR explicit_driver_intent = TRUE);
