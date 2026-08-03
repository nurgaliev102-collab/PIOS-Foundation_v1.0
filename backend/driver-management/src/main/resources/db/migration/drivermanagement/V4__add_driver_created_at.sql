-- ADR-043 Decision 4 (Owner Control Center — Observation Boundary): an
-- additive, nullable timestamp alongside existing state, never replacing
-- it. Every row that exists before this migration -- including Артур's own
-- registration -- has NULL here permanently; the event feed built on this
-- column is complete only from deployment forward (ADR-043's own disclosed
-- limitation). Precedent: V6__add_optional_order_destination.sql,
-- V7__add_passenger_name_and_created_at.sql (order-management).
ALTER TABLE drivers ADD COLUMN created_at TIMESTAMPTZ NULL;
