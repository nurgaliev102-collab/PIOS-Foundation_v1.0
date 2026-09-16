-- Drivers registered after V11 but before initial availability was emitted
-- at creation have no Dispatch projection until their first status toggle.
-- Replay the current fact through the existing outbox once on upgrade.
-- No driver's availability changes. Dispatch's last-updated fairness
-- tie-break is refreshed once for these rows, as it was during V11.
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
