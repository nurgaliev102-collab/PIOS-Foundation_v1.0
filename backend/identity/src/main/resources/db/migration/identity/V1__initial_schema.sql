-- Identity's own PostgreSQL schema (ADR-038). Persists exactly the one
-- capability this module owns today -- Identity -- and nothing else.
-- Credential, Device, VerificationChallenge, and Session are deliberately
-- NOT given tables here: they exist only as unpersisted domain shapes
-- (see backend/identity/.../domain/) until a future, separately-authorized
-- ADR actually implements one of them. An empty table for a concept
-- nothing writes to yet would commit this schema to a shape before it's
-- needed.

CREATE TABLE identities (
    id TEXT PRIMARY KEY,
    phone TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
