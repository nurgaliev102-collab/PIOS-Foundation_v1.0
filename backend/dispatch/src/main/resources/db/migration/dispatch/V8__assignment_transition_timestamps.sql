-- ADR-043 Decision 4 (Owner Control Center — Observation Boundary):
-- additive, nullable timestamps alongside existing state -- V5's own
-- `status_changed_at` is kept, not replaced (binding constraint of that
-- Decision), since it records only the latest transition, never a
-- history. Every assignment row that exists before this migration has
-- NULL in all three columns permanently -- the event feed built on them
-- is complete only from deployment forward (ADR-043's own disclosed
-- limitation).
ALTER TABLE assignments ADD COLUMN arrived_at TIMESTAMPTZ NULL;
ALTER TABLE assignments ADD COLUMN started_at TIMESTAMPTZ NULL;
ALTER TABLE assignments ADD COLUMN completed_at TIMESTAMPTZ NULL;
