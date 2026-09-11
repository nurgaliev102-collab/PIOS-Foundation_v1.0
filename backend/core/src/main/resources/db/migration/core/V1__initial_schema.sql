-- PIOS Core's own PostgreSQL schema (ADR-067 Data Model Scope; ADR-025;
-- PERSISTENCE_ARCHITECTURE.md Section 3). The MINIMUM Slice 01 projection
-- and nothing more: a participant record, a history-event record, and a
-- processed-event ledger. No mutable relationship entity, no trust level,
-- no capability / need / opportunity, no payment record, no stored
-- "insight", and no denormalised copy of any Order / Driver / Proposal /
-- Assignment / Trip / Connection beyond a bare correlation reference.
--
-- Located under db/migration/core (not the shared db/migration default),
-- matching spring.flyway.locations in application.yml, so this module's
-- migration never collides on a shared test classpath with another
-- module's identically-versioned file -- the same convention every
-- existing module follows.
--
-- No cross-database foreign key of any kind (ADR-005 / ADR-009): every
-- reference here is a plain TEXT value, never an FK to another database.
-- Status/kind values are plain TEXT (no native enum, no CHECK) so
-- evolving the closed HistoryEventKind vocabulary stays a data change,
-- not a schema migration -- the same choice dispatch's own schema makes.

-- A participant record. Keyed on participant_reference (ADR-067
-- Participant Reference: the value flowing through the system as the
-- session token `sub`, Order.origin, Proposal.passengerReference, and
-- passenger-experience.Connection.passenger_reference -- deliberately NOT
-- named person_id). Created lazily, on the first event mentioning that
-- reference. No name, no phone, no address, no profile -- Core copies no
-- personal data from any module.
--
-- NOTE (ADR-067): participant_reference is a working reference for Slice
-- 01, NOT a declaration that identityId is the canonical universal Person
-- ID of PIOS. Driver-sourced facts key on the driver's own id and
-- passenger-sourced facts on the identityId; Slice 01 does not reconcile
-- the two id spaces (Explicit Non-Decisions).
CREATE TABLE participants (
    participant_reference TEXT PRIMARY KEY,
    first_seen_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A history-event record: one row per consumed, accepted event.
-- participant_reference is the participant this fact concerns; kind is a
-- value from Core's own closed vocabulary (com.pios.core.domain.HistoryEventKind);
-- occurred_at is taken from the event envelope's own `occurredAt`, never
-- invented; order_reference / driver_reference are bare correlation
-- references (plain strings) sufficient to group events -- never a copy
-- of the referenced aggregate. source_event_id is the envelope `eventId`
-- this row was derived from (audit trail; not the idempotency key -- that
-- is processed_events below).
CREATE TABLE participant_history_events (
    id                    BIGSERIAL PRIMARY KEY,
    participant_reference  TEXT        NOT NULL,
    kind                  TEXT        NOT NULL,
    occurred_at           TIMESTAMPTZ NOT NULL,
    order_reference       TEXT,
    driver_reference      TEXT,
    source_event_id       TEXT        NOT NULL,
    recorded_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The read path this projection exists to serve: "everything that
-- happened for one participant, in order".
CREATE INDEX idx_history_participant_time
    ON participant_history_events (participant_reference, occurred_at, id);

-- Correlation lookup for OrderCompleted / OrderCancelled, whose payload
-- carries only `orderId`: find which participant a prior OrderSubmitted
-- attributed that order to. If no such prior row exists (Core deployed
-- after the submit, or the submit was never seen), the event is recorded
-- as processed and NO history row is written -- Core never fetches the
-- participant from order-management (ADR-067 Write Boundary / Input
-- Events "Rule").
CREATE INDEX idx_history_order_reference
    ON participant_history_events (order_reference)
    WHERE order_reference IS NOT NULL;

-- The idempotency ledger. RabbitMQ is at-least-once (ADR-029 / ADR-031):
-- the same eventId may be delivered more than once. The ledger insert and
-- the read-model write happen in one pios_core transaction; a redelivered
-- eventId finds its row already present and applies no second effect.
-- Mirrors dispatch's own order_cancelled_processed_events /
-- driver_availability_processed_events pattern exactly.
CREATE TABLE processed_events (
    event_id     TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
