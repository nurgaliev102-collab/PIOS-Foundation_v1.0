-- Network Management's own PostgreSQL schema (Sprint 7A: PIOS Network
-- Foundation; ADR-037). Persists exactly the four logical entities this
-- module owns -- Person, PersonProfile, Connection, Invitation -- and
-- nothing belonging to any other domain (no shared database, no
-- cross-domain tables, no foreign key onto another module's schema:
-- driver_id/passenger_reference-style columns elsewhere in this schema are
-- always plain references, per ADR-037's own "reference, not ownership"
-- rule).

CREATE TABLE persons (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE person_profiles (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES persons (id),
    type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE connections (
    id TEXT PRIMARY KEY,
    from_person_id TEXT NOT NULL REFERENCES persons (id),
    to_person_id TEXT NOT NULL REFERENCES persons (id),
    type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE invitations (
    id TEXT PRIMARY KEY,
    creator_person_id TEXT NOT NULL REFERENCES persons (id),
    code TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
