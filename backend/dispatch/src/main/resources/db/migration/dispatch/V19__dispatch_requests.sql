-- V20: a durable routing record for an OrderSubmitted event. A CANCELLED row can
-- be written before the submitted event arrives (the two events have
-- separate RabbitMQ queues), preventing a late offer for a cancelled order.
CREATE TABLE dispatch_requests (
    order_id TEXT PRIMARY KEY,
    passenger_reference TEXT,
    is_test BOOLEAN,
    explicit_driver_intent BOOLEAN,
    requested_driver_id TEXT,
    requested_pickup_at TIMESTAMPTZ,
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'OFFERED', 'UNFULFILLED', 'CANCELLED')),
    submitted_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT dispatch_requests_active_payload CHECK (
        state = 'CANCELLED' OR (
            passenger_reference IS NOT NULL AND
            is_test IS NOT NULL AND
            explicit_driver_intent IS NOT NULL AND
            submitted_at IS NOT NULL AND
            expires_at IS NOT NULL
        )
    ),
    CONSTRAINT dispatch_requests_due_only_when_pending CHECK (
        (state = 'PENDING' AND next_attempt_at IS NOT NULL) OR
        (state <> 'PENDING' AND next_attempt_at IS NULL)
    )
);

CREATE INDEX dispatch_requests_due_idx
    ON dispatch_requests (next_attempt_at, order_id)
    WHERE state = 'PENDING';
