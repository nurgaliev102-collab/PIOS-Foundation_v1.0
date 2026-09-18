-- D-08 (Handoff Observation Foundation,
-- docs/PIOS_D08_HANDOFF_OBSERVATION_IMPLEMENTATION_SPEC.md Section 3):
-- purely additive indexes. No new table, no new column, no change to
-- any existing row or to D-07's own schema semantics -- observation
-- reads the already-permanent `handoffs` table (V22) and the existing
-- `assignments`/`dispatch_outbox` tables exactly as they already are.
--
-- Without this index, "every Handoff a given committing driver has ever
-- proposed" (HandoffRepository.findByOriginalDriver) is a full scan of
-- `handoffs` -- the same reasoning V22's own `handoffs_assignment_id_idx`
-- and V20's `assignments_order_reference_idx` already established for
-- their own equally-necessary lookups.
CREATE INDEX handoffs_original_driver_reference_idx ON handoffs (original_driver_reference);

-- Same reasoning for "every Assignment a given committing driver has
-- taken on" (AssignmentRepository.findByDriver), observation's own
-- denominator (spec Section 6). `assignments` has carried no index on
-- driver_reference since V1 -- this is the first query to need one.
CREATE INDEX assignments_driver_reference_idx ON assignments (driver_reference);
