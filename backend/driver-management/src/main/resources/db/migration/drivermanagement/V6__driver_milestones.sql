-- Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
-- idempotency ledger for consumed AssignmentCompleted events, mirroring
-- order_management_processed_events / driver_availability_processed_events
-- exactly -- an event-type-agnostic table keyed on the globally unique
-- eventId a RabbitMQ envelope always carries.
CREATE TABLE driver_management_processed_events (
    event_id TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per driver with at least one completed ride. completed_rides_count
-- and current_streak_weeks are both derived, read-model state -- never the
-- source of truth for a ride actually having happened (Dispatch's own
-- Assignment/Trip aggregates remain that); this table can be dropped and
-- rebuilt from Dispatch's own event history without losing anything Driver
-- Management itself owns.
CREATE TABLE driver_milestones (
    driver_id TEXT PRIMARY KEY REFERENCES drivers(id),
    completed_rides_count BIGINT NOT NULL DEFAULT 0,
    current_streak_weeks INT NOT NULL DEFAULT 0,
    last_completed_at TIMESTAMPTZ
);
