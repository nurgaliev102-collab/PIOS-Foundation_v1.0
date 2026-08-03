-- ADR-043 Decision 4 (Owner Control Center — Observation Boundary):
-- additive, nullable timestamps alongside existing state, never replacing
-- it. Every proposal row that exists before this migration has NULL in
-- both columns permanently -- the event feed built on them is complete
-- only from deployment forward (ADR-043's own disclosed limitation).
-- Precedent: V6__add_optional_order_destination.sql,
-- V7__add_passenger_name_and_created_at.sql (order-management).
ALTER TABLE proposals ADD COLUMN created_at TIMESTAMPTZ NULL;
ALTER TABLE proposals ADD COLUMN responded_at TIMESTAMPTZ NULL;
