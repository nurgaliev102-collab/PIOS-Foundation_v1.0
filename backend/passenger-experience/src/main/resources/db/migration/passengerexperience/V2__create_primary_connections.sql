-- Sprint "My Business + Circle of Trust" (ADR-054: Circle of Trust --
-- Passenger-Experience-Owned Primary Driver Relationship). Adds the
-- passenger-chosen "primary" designation on top of the existing
-- many-to-many connections table, as a separate table rather than a
-- column -- see ADR-054 Part 2 for why: the single-primary invariant
-- becomes a PRIMARY KEY, the composite FK makes "the primary must belong
-- to this passenger's own circle" structural, and ON DELETE CASCADE makes
-- removing a connection correct by construction with no application-layer
-- cleanup step to be forgotten.
ALTER TABLE connections
    ADD CONSTRAINT connections_passenger_reference_id_key UNIQUE (passenger_reference, id);

CREATE TABLE primary_connections (
    passenger_reference TEXT PRIMARY KEY,
    connection_id       TEXT NOT NULL,
    designated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT primary_connections_connection_fkey
        FOREIGN KEY (passenger_reference, connection_id)
        REFERENCES connections (passenger_reference, id)
        ON DELETE CASCADE
);
