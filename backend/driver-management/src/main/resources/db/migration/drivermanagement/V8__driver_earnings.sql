-- ADR-065 (Driver Earnings from Self-Stated Prices): adds the two derived
-- earnings figures to driver_milestones -- total_stated_earnings (the sum
-- of digits-only stated prices, ADR-065 Decision item 4's own confirmed
-- parsing rule) and unpriced_rides_count (completed rides whose stated
-- price did not parse). Both are derived, read-model state -- never the
-- source of truth for a ride's own stated price (Dispatch's own
-- proposals.stated_price remains that, ADR-065 Decision item 3) -- exactly
-- the same "can be dropped and rebuilt" property V6's own header comment
-- already establishes for this table's other counts.
--
-- A new migration, not an edit to V6/V7, per this codebase's own
-- forward-only Flyway convention (V7's own header comment).

ALTER TABLE driver_milestones ADD COLUMN total_stated_earnings BIGINT NOT NULL DEFAULT 0;
ALTER TABLE driver_milestones ADD COLUMN unpriced_rides_count INT NOT NULL DEFAULT 0;

-- One row per completed ride this module has seen an AssignmentCompleted
-- for, keyed by that event's own globally unique eventId (the same key
-- driver_management_processed_events already uses to guard idempotency).
-- Carries both the verbatim stated price string (ADR-042's own "opaque,
-- unvalidated" value, forwarded unchanged by Dispatch) and the parsed
-- amount (ADR-065 Decision item 4's digits-only rule) -- so the parsing
-- rule can be revised later by rebuilding driver_milestones' two totals
-- above from this table alone, with no Dispatch replay (ADR-065
-- Consequences, "Positive").
CREATE TABLE driver_ride_stated_prices (
    event_id TEXT PRIMARY KEY REFERENCES driver_management_processed_events(event_id),
    driver_id TEXT NOT NULL REFERENCES drivers(id),
    stated_price_raw TEXT,
    stated_price_parsed BIGINT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
