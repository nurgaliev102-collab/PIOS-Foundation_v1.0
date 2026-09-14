-- ADR-068 (Relationship-Ordered Fallback Dispatch), Part 1's own named
-- implementation constraint: "every existing connections row predates
-- these events... Without a one-time, explicitly-designed backfill...
-- Tier 1 is empty for every existing passenger." This migration is that
-- backfill.
--
-- Chosen mechanism: a one-time Flyway migration that inserts one
-- synthetic ConnectionEstablished outbox record per pre-existing
-- `connections` row, reusing the exact, already-tested
-- outbox -> OutboxRelay -> RabbitMQ -> Dispatch-consumer pipeline the
-- live event path uses. This is the "replay from Passenger Experience"
-- option ADR-068 Part 1 names -- chosen over an "operator-run projection
-- seed" script for three reasons:
--   1. No new one-time-job mechanism is introduced. Flyway's own
--      "every migration runs exactly once, ever, per database" guarantee
--      already gives this exactly the property a bespoke backfill runner
--      would have to re-implement (a completion marker, idempotent
--      re-run safety).
--   2. It requires zero new Dispatch-side code. The backfilled events
--      travel through TrustedDriverEventListener/
--      TrustedDriverProjectionApplicationService exactly like any live
--      event -- there is no separate "backfill ingestion path" to build,
--      test, or ever get out of sync with the live one.
--   3. It is environment-uniform: every environment that runs this
--      module's migrations (a fresh clone, CI once it exists, the real
--      pilot database) gets the backfill automatically, with no separate
--      manual step an operator could forget.
--
-- Safety under redelivery/re-run: each row gets a freshly generated
-- eventId (gen_random_uuid()), so Dispatch's own
-- trusted_driver_processed_events ledger treats every backfilled event
-- exactly like a live one -- no special-casing. Independently,
-- trusted_driver_records' own composite primary key
-- (passenger_reference, driver_reference) plus this repository's
-- `ON CONFLICT ... DO NOTHING` insert make the *resulting projection
-- state* idempotent regardless: even if this exact migration were somehow
-- applied twice against the same data (Flyway itself prevents this, but
-- the property is independently true), the final trusted_driver_records
-- contents would be identical, not duplicated.
--
-- Payload shape matches CreateConnectionApplicationService's own
-- envelopeFor/outboxRecordFor exactly -- eventId, eventType, eventVersion,
-- occurredAt, and a payload object carrying only the two opaque
-- identifiers ADR-068 Part 1 authorizes. occurredAt is sourced from each
-- row's own connections.created_at, not the backfill's own run time --
-- the fact being reported ("this passenger trusted this driver") already
-- happened at that historical moment, not now.
INSERT INTO passenger_experience_outbox (aggregate_id, event_type, routing_key, payload)
SELECT
    c.passenger_reference,
    'ConnectionEstablished',
    'connection.established',
    jsonb_build_object(
        'eventId', gen_random_uuid()::text,
        'eventType', 'ConnectionEstablished',
        'eventVersion', 1,
        'occurredAt', to_char(c.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        'payload', jsonb_build_object(
            'passengerReference', c.passenger_reference,
            'driverId', c.driver_id
        )
    )::text
FROM connections c;
