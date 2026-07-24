-- Adds Order's own optional destination (Sprint 3B: MVR Pilot Enablement
-- -- Optional Destination). Nullable, unlike V4's mandatory `destination`
-- column (dropped again by V5 in the same sprint): the one real existing
-- caller of POST /v1/orders (passenger-experience's
-- RestClientOrderSubmissionClient) never sends one and must keep working
-- unmodified, per ADR-026's independent-deployability guarantee. No
-- DEFAULT is needed -- unlike V4, this column is nullable, so any row
-- already present satisfies the new column definition without one.
--
-- V4 and V5 are not edited or deleted, per this project's own migration
-- discipline of never rewriting a migration already recorded in Flyway's
-- history.
ALTER TABLE orders
    ADD COLUMN destination TEXT NULL;
