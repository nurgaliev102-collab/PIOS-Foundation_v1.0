-- ADR-055 (Session Authentication and Password Credential), Decision 2.
-- Named V3, not V2, because V2__add_driver_id.sql (ADR-039) already
-- occupies that version in this module's own migration history; the SQL
-- body below is otherwise exactly what ADR-055 Decision 2 specifies.

CREATE UNIQUE INDEX ux_identities_phone ON identities (phone) WHERE phone IS NOT NULL;

CREATE TABLE identity_credentials (
    identity_id    TEXT PRIMARY KEY REFERENCES identities (id),
    password_hash  TEXT NOT NULL,        -- base64, PBKDF2-HMAC-SHA256
    password_salt  TEXT NOT NULL,        -- base64, generated per credential
    iterations     INTEGER NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
);
