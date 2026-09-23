-- ADR-082 C-2 implementation: durable SMS outbox (identity_sms_outbox) and
-- AES-256-GCM OTP ciphertext column on phone_verification_challenges.
--
-- Decision Lock I.1: maxAttempts default raised to 10; stored per-row, no
-- schema change required (max_attempts column already exists in V5).
--
-- Decision Lock I.3: provider_message_id BIGINT on outbox rows captures the
-- SMS Aero accepted-message ID whose exact semantic is "provider accepted for
-- routing", NOT "delivered".
--
-- Decision Lock I.5: PROCESSING + lease_until enable concurrent-safe relay
-- claim and expired-lease recovery without an advisory lock.
--
-- AES-256-GCM: otp_ciphertext and otp_nonce are added to
-- phone_verification_challenges. The relay decrypts the ciphertext to obtain
-- the OTP plaintext it must send to SMS Aero. Both columns are nulled
-- atomically with the PENDING→SENT transition (Decision Lock item 11).
-- Plaintext is never written to any column (Decision Lock item 2).

-- AES-256-GCM ciphertext and nonce stored beside the existing PBKDF2 hash.
-- NULL after a successful SENT transition (ciphertext destruction, item 11).
-- Also NULL if SMS Aero definitively rejected (FAILED) -- no point keeping
-- it; the challenge is unusable regardless.
ALTER TABLE phone_verification_challenges ADD COLUMN otp_ciphertext TEXT;
ALTER TABLE phone_verification_challenges ADD COLUMN otp_nonce       TEXT;

-- Durable SMS outbox: one row per OTP issuance attempt.
-- Lifecycle: PENDING → PROCESSING (relay claims it) → SENT | FAILED | UNKNOWN.
-- PROCESSING rows whose lease_until has passed are reclaimable by a relay
-- restart (expired-lease recovery, Decision Lock I.5).
--
-- challenge_id references the challenge whose OTP the relay must deliver.
-- phone is NOT duplicated here (Decision Lock item 10): the relay reads
-- phone_verification_challenges.phone to obtain it.
CREATE TABLE identity_sms_outbox (
    id                  BIGSERIAL PRIMARY KEY,
    challenge_id        TEXT        NOT NULL
                            REFERENCES phone_verification_challenges (id),
    status              TEXT        NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','UNKNOWN')),
    -- Non-NULL only while status = 'PROCESSING'. Relay sets it to
    -- now() + lease_duration when claiming; on crash the row is reclaimable
    -- once this instant has passed.
    lease_until         TIMESTAMPTZ,
    -- A random, per-claim fence. Every terminal/retry transition must match
    -- this value, so a worker whose lease expired cannot overwrite a newer
    -- claimant's result.
    claim_token         TEXT,
    -- Durable retry availability. PENDING rows are claimable only at or
    -- after this time; it is never an in-memory timer.
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Set to the SMS Aero data[0].id on SENT transition.
    -- Semantic: "SMS Aero accepted for routing" (NOT delivered).
    provider_message_id BIGINT,
    -- Set exactly when PROCESSING transitions to SENT. Provider acceptance
    -- is not carrier delivery.
    sent_at             TIMESTAMPTZ,
    -- Populated on FAILED/UNKNOWN transitions for ops/audit queries.
    failure_reason      TEXT,
    -- Incremented each time the relay retries an UNKNOWN row.
    retry_count         INTEGER     NOT NULL DEFAULT 0,
    -- Wall-clock of first insertion and last status update.
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Relay's primary claim query: find PENDING rows or PROCESSING rows with an
-- expired lease, ordered FIFO, skipping locked rows held by another relay.
CREATE INDEX ix_identity_sms_outbox_pending_claimable
    ON identity_sms_outbox (next_attempt_at ASC, created_at ASC)
    WHERE status = 'PENDING';

CREATE INDEX ix_identity_sms_outbox_processing_lease
    ON identity_sms_outbox (lease_until ASC, created_at ASC)
    WHERE status = 'PROCESSING';

-- Retention cleanup queries: find old SENT / FAILED / UNKNOWN rows.
CREATE INDEX ix_identity_sms_outbox_status_updated
    ON identity_sms_outbox (status, updated_at);
