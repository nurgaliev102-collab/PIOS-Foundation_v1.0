-- PIOS Group and Long-Distance Rides Roadmap, Stage 1: a minimal
-- owned-by-driver vehicle record. Additive, nullable columns alongside
-- existing state, never replacing it. Every driver row that exists before
-- this migration has NULL in all five columns permanently -- a driver
-- simply has not declared a vehicle yet, the same meaning NULL already
-- carries for a driver who has never set a display_name.
-- Precedent: V3__add_optional_display_name.sql (this same table).
ALTER TABLE drivers ADD COLUMN vehicle_make TEXT NULL;
ALTER TABLE drivers ADD COLUMN vehicle_model TEXT NULL;
ALTER TABLE drivers ADD COLUMN vehicle_color TEXT NULL;
ALTER TABLE drivers ADD COLUMN vehicle_plate_number TEXT NULL;
ALTER TABLE drivers ADD COLUMN vehicle_seat_count INTEGER NULL;
