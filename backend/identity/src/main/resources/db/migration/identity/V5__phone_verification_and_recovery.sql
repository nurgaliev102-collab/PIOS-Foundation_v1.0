-- ADR-082 (D-03: Phone-Verified Identity Recovery). Additive only; no
-- existing migration is edited.

-- D-03.2: every existing row is UNVERIFIED by construction. No backfill,
-- no inference from any other column -- NULL means unverified, and stays
-- NULL for every row that existed before this migration.
ALTER TABLE identities ADD COLUMN phone_verified_at TIMESTAMPTZ;

-- D-03.3: session-generation counter, carried as the additive `sgen`
-- token claim (mirrors `gst`'s own additive-claim precedent, ADR-075).
-- Bumped by exactly one on a successful recovery.
ALTER TABLE identities ADD COLUMN session_generation INTEGER NOT NULL DEFAULT 0;

-- Per-(identity, purpose) lock, taken first inside
-- PhoneVerificationChallengeIssuer's own transaction, before superseding or
-- inserting a challenge row -- the same guard-row pattern ADR-080's own
-- dispatch_order_guards already established. Without this, two concurrent
-- first-ever requests for the same (identity, purpose) can both find
-- nothing to supersede and both attempt to insert, violating the partial
-- unique index below (found by a real two-thread PostgreSQL test, not
-- assumed).
CREATE TABLE phone_verification_challenge_guards (
    guard_key TEXT PRIMARY KEY
);

-- D-03.7: durable, hashed, single-use, expiring, attempt-bounded OTP
-- challenges. Shared by RECOVERY and LEGACY_ENROLLMENT purposes so both
-- flows get the same security properties from one mechanism, not two.
-- code_hash/code_salt/iterations mirror identity_credentials' own shape
-- (V3) -- the OTP code is hashed with the same PasswordHasher this module
-- already uses for passwords; plaintext is never persisted.
CREATE TABLE phone_verification_challenges (
    id             TEXT PRIMARY KEY,
    identity_id    TEXT NOT NULL REFERENCES identities (id),
    phone          TEXT NOT NULL,
    purpose        TEXT NOT NULL CHECK (purpose IN ('RECOVERY', 'LEGACY_ENROLLMENT')),
    code_hash      TEXT NOT NULL,
    code_salt      TEXT NOT NULL,
    iterations     INTEGER NOT NULL,
    attempt_count  INTEGER NOT NULL DEFAULT 0,
    max_attempts   INTEGER NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    consumed_at    TIMESTAMPTZ,
    superseded_at  TIMESTAMPTZ
);

-- D-03.7 "duplicate/replay rejected", structurally: at most one currently
-- LIVE challenge (neither consumed nor superseded) per (identity, purpose)
-- -- the same structural-guarantee-over-application-discipline precedent
-- ADR-080's own dispatch_order_guards/termination-request constraints
-- already established for this codebase.
CREATE UNIQUE INDEX ux_phone_verification_challenges_live
    ON phone_verification_challenges (identity_id, purpose)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;

-- Supports the recovery/confirm and legacy-enrolment-confirm lookup path
-- (find the live challenge for an identity+purpose) even before the
-- partial unique index above would otherwise be consulted.
CREATE INDEX ix_phone_verification_challenges_identity_purpose
    ON phone_verification_challenges (identity_id, purpose);
