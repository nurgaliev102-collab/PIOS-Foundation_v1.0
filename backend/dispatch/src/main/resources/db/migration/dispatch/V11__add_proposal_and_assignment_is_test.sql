-- Test/production data separation (Owner Control Center audit,
-- 2026-08-17): additive booleans on both aggregates this module owns,
-- mirroring V6__proposal_stated_price.sql's own precedent -- plain
-- columns, no new domain concept.
--
-- DEFAULT FALSE means every existing row (including every historical
-- test/sprint proposal and assignment already in these tables) becomes
-- `is_test = false` on migration -- this migration does not attempt to
-- guess which existing rows were test data; it only stops NEW rows from
-- being ambiguous going forward.
--
-- `proposals.is_test` is the caller's own explicit declaration at
-- creation (Proposal.propose, via POST /v1/proposals). `assignments.is_test`
-- is never independently supplied by a caller through the normal flow --
-- it is derived, in-process, from the Proposal it was created from
-- (ProposalAssignmentOrchestrationService), the same same-module
-- derivation Assignment.order/Assignment.driver already receive.
ALTER TABLE proposals ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE assignments ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT FALSE;
