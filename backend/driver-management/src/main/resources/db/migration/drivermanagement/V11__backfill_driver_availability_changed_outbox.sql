-- ADR-069 (Test/Real Segregation in Fallback Driver Selection), Part 4.2's
-- own named implementation constraint: "Backfill = owner-side replay, not a
-- cross-module read. Driver Management re-publishes DriverAvailabilityChanged
-- (carrying isTest) once for every driver it holds, through its existing
-- outbox/relay (ADR-032), as an operator-triggered one-off." This migration
-- is that replay.
--
-- Chosen mechanism, mirroring ADR-068's own
-- V4__backfill_connection_established_outbox.sql (Passenger Experience)
-- exactly, for the identical three reasons: no new one-time-job mechanism
-- (Flyway's own "runs exactly once, ever" guarantee is the completion
-- marker), zero new Dispatch-side ingestion path (the replayed events
-- travel through DriverAvailabilityChangedListener exactly like a live
-- event), and environment-uniform (every environment that runs this
-- module's migrations gets the backfill automatically).
--
-- Fresh eventIds, not redelivery of anything: each row gets a freshly
-- generated eventId (gen_random_uuid()) -- this is the implementation
-- constraint ADR-069 Part 4.2 calls out explicitly: "replayed events must
-- carry fresh eventIds, or driver_availability_processed_events's
-- primary-key ledger will correctly discard every one of them as a
-- redelivery and the backfill will silently do nothing." Reusing the
-- original eventId would make this backfill a no-op.
--
-- Republishes each driver's CURRENT availability value -- it does not
-- fabricate an availability change, and no driver's `available` flag flips
-- as a side effect of this backfill (ADR-069 Part 4.2). `updated_at` on
-- Dispatch's own `driver_availability` row is refreshed by its own
-- `upsert`, which resets the longest-idle tie-break platform-wide once --
-- an accepted, stated side effect (ADR-069 Consequences), affecting
-- tie-break order only, never eligibility.
--
-- occurredAt is sourced from now() (this backfill's own run time), not any
-- historical timestamp on `drivers` -- unlike ConnectionEstablished's own
-- backfill (where connections.created_at is the fact's true historical
-- moment), "this driver is currently in this availability/isTest state" is
-- a fact whose truth is anchored to the moment this replay runs, not to
-- this driver's original registration time.
--
-- Payload shape matches DriverAvailabilityApplicationService's own
-- envelopeFor/outboxRecordFor exactly -- eventId, eventType, eventVersion,
-- occurredAt, and a payload object carrying driverId, availability, and
-- (ADR-069) isTest.
INSERT INTO driver_management_outbox (aggregate_id, event_type, routing_key, payload)
SELECT
    d.id,
    'DriverAvailabilityChanged',
    'driver.availability.changed',
    jsonb_build_object(
        'eventId', gen_random_uuid()::text,
        'eventType', 'DriverAvailabilityChanged',
        'eventVersion', 1,
        'occurredAt', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        'payload', jsonb_build_object(
            'driverId', d.id,
            'availability', d.availability,
            'isTest', d.is_test
        )
    )::text
FROM drivers d;
