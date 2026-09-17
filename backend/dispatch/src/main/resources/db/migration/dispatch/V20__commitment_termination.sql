CREATE TABLE dispatch_order_guards (
    order_reference TEXT PRIMARY KEY
);

ALTER TABLE trips
    ADD COLUMN termination_request_id TEXT,
    ADD COLUMN termination_initiator TEXT,
    ADD COLUMN termination_reason_code TEXT,
    ADD COLUMN termination_note TEXT,
    ADD COLUMN terminated_at TIMESTAMPTZ;

ALTER TABLE trips ADD CONSTRAINT trips_termination_fact_check CHECK (
    (status <> 'TERMINATED' AND termination_request_id IS NULL AND termination_initiator IS NULL
        AND termination_reason_code IS NULL AND termination_note IS NULL AND terminated_at IS NULL)
    OR
    (status = 'TERMINATED' AND termination_request_id IS NOT NULL AND termination_initiator IS NOT NULL
        AND termination_reason_code IS NOT NULL AND terminated_at IS NOT NULL AND completed_at IS NULL
        AND length(termination_note) <= 2000 AND (
            (termination_initiator = 'PASSENGER' AND termination_reason_code IN
                ('PLANS_CHANGED', 'FOUND_ANOTHER_DRIVER', 'DRIVER_UNRESPONSIVE', 'CANNOT_CONTINUE', 'OTHER'))
            OR (termination_initiator = 'DRIVER' AND termination_reason_code IN
                ('CANNOT_FULFILL', 'PASSENGER_UNRESPONSIVE', 'PASSENGER_NO_SHOW', 'TRIP_CONDITIONS_CHANGED', 'OTHER'))
            OR (termination_initiator = 'SYSTEM' AND termination_reason_code = 'SYSTEM_TIMEOUT')
        ))
);

CREATE UNIQUE INDEX trips_termination_request_id_unique
    ON trips (termination_request_id) WHERE termination_request_id IS NOT NULL;
CREATE INDEX trips_order_reference_idx ON trips (order_reference);
CREATE INDEX assignments_order_reference_idx ON assignments (order_reference);

CREATE TABLE dispatch_termination_requests (
    request_id TEXT PRIMARY KEY,
    order_reference TEXT NOT NULL,
    assignment_id TEXT,
    initiator TEXT NOT NULL,
    reason_code TEXT,
    note TEXT,
    outcome TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (outcome IN ('TERMINATED', 'NO_COMMITMENT', 'REASON_REQUIRED', 'ALREADY_COMPLETED', 'ALREADY_TERMINATED')),
    CHECK (length(note) <= 2000),
    CHECK (
        (initiator = 'PASSENGER' AND (reason_code IS NULL OR reason_code IN
            ('PLANS_CHANGED', 'FOUND_ANOTHER_DRIVER', 'DRIVER_UNRESPONSIVE', 'CANNOT_CONTINUE', 'OTHER')))
        OR (initiator = 'DRIVER' AND reason_code IN
            ('CANNOT_FULFILL', 'PASSENGER_UNRESPONSIVE', 'PASSENGER_NO_SHOW', 'TRIP_CONDITIONS_CHANGED', 'OTHER'))
        OR (initiator = 'SYSTEM' AND reason_code = 'SYSTEM_TIMEOUT')
    ),
    CHECK (reason_code IS NOT NULL OR note IS NULL)
);
CREATE INDEX dispatch_termination_requests_order_idx ON dispatch_termination_requests (order_reference);

CREATE FUNCTION prevent_trip_terminal_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status IN ('COMPLETED', 'TERMINATED') AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Terminal Trip is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trips_terminal_immutable BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION prevent_trip_terminal_rewrite();
