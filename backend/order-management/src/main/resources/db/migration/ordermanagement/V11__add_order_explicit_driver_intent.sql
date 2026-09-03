-- Adds Order's own explicit-driver-intent flag (Task 15C: First Refusal
-- Contract Completion and Concurrency Safety) -- see domain/Order.kt's own
-- KDoc for the full atomicity argument. Recorded once, at submission, and
-- never changed afterward.
--
-- NOT NULL DEFAULT FALSE, and the default is kept (not dropped, unlike
-- V4's own transitional 'unknown' placeholder): FALSE is a genuinely
-- correct, permanent value for this column, not a placeholder -- an order
-- with no known explicit driver intent is exactly what "no value supplied"
-- should mean, for every row that predates this column and for every
-- future INSERT that does not explicitly set it.
ALTER TABLE orders
    ADD COLUMN explicit_driver_intent BOOLEAN NOT NULL DEFAULT FALSE;
