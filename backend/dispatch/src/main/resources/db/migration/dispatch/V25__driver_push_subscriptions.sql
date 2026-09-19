CREATE TABLE driver_push_subscriptions (
    endpoint         TEXT PRIMARY KEY,
    driver_reference TEXT NOT NULL,
    p256dh           TEXT NOT NULL,
    auth             TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX driver_push_subscriptions_driver_reference_idx
    ON driver_push_subscriptions (driver_reference);
