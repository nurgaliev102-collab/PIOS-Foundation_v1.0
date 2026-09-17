CREATE TABLE order_cancellation_requests (
    request_id TEXT PRIMARY KEY,
    order_id TEXT NOT NULL REFERENCES orders (id),
    reason_code TEXT,
    note TEXT,
    outcome TEXT NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    CHECK (reason_code IS NULL OR reason_code IN
        ('PLANS_CHANGED', 'FOUND_ANOTHER_DRIVER', 'DRIVER_UNRESPONSIVE', 'CANNOT_CONTINUE', 'OTHER')),
    CHECK (length(note) <= 2000),
    CHECK (outcome IN ('PENDING', 'TERMINATED', 'NO_COMMITMENT', 'REASON_REQUIRED',
        'ALREADY_COMPLETED', 'ALREADY_TERMINATED')),
    CHECK ((outcome = 'PENDING' AND resolved_at IS NULL) OR (outcome <> 'PENDING' AND resolved_at IS NOT NULL))
);
CREATE UNIQUE INDEX order_cancellation_one_pending_per_order
    ON order_cancellation_requests (order_id) WHERE outcome = 'PENDING';
CREATE INDEX order_cancellation_requests_order_idx ON order_cancellation_requests (order_id);

CREATE TABLE order_terminations (
    order_id TEXT PRIMARY KEY REFERENCES orders (id),
    request_id TEXT NOT NULL UNIQUE,
    assignment_id TEXT NOT NULL,
    driver_id TEXT NOT NULL,
    initiator TEXT NOT NULL,
    reason_code TEXT NOT NULL,
    note TEXT,
    terminated_at TIMESTAMPTZ NOT NULL,
    CHECK (length(note) <= 2000),
    CHECK (
        (initiator = 'PASSENGER' AND reason_code IN
            ('PLANS_CHANGED', 'FOUND_ANOTHER_DRIVER', 'DRIVER_UNRESPONSIVE', 'CANNOT_CONTINUE', 'OTHER'))
        OR (initiator = 'DRIVER' AND reason_code IN
            ('CANNOT_FULFILL', 'PASSENGER_UNRESPONSIVE', 'PASSENGER_NO_SHOW', 'TRIP_CONDITIONS_CHANGED', 'OTHER'))
        OR (initiator = 'SYSTEM' AND reason_code = 'SYSTEM_TIMEOUT')
    )
);
