-- Minimal In-Ride Messaging MVP (Product Cycle): a new, additive table
-- alongside existing state, never replacing it -- no other table's schema
-- changes. Scoped to proposal_id (not order_reference): a message belongs
-- to the specific passenger<->driver pairing a Proposal already is, not
-- to the order in the abstract (see ProposalMessage.kt's own KDoc for why)
-- -- this is what makes "невозможность перепутать сообщения разных
-- заказов" hold even across two different proposals for the same order
-- (a decline followed by a fresh proposal to a different driver).
--
-- No foreign key to `proposals.id`: this schema's own existing convention
-- (assignments.order_reference/driver_reference are plain text, not FKs)
-- already established that Dispatch's own tables reference each other by
-- plain identifier only, never a database-level constraint.
--
-- sender_role stored as text, mirroring assignments.status/proposals.status's
-- own convention -- evolving MessageSenderRole stays a plain data change,
-- never a schema migration.
CREATE TABLE proposal_messages (
    id TEXT PRIMARY KEY,
    proposal_id TEXT NOT NULL,
    sender_role TEXT NOT NULL,
    body TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL
);

-- Every real read ([ProposalMessageRepository.findByProposal]) filters by
-- proposal_id and orders by sent_at -- indexed together so that query
-- never needs a full table scan as message volume grows.
CREATE INDEX idx_proposal_messages_proposal_id_sent_at ON proposal_messages (proposal_id, sent_at);
