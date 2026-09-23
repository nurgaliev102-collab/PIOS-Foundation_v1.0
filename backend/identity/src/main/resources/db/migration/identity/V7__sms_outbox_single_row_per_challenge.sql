-- C-2 hardening: a challenge is issued once and retries reuse the same
-- durable outbox row.  Refuse migration when historical data violates that
-- invariant rather than silently deleting audit records.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM identity_sms_outbox
        GROUP BY challenge_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'identity_sms_outbox contains multiple rows for one challenge; resolve before applying V7';
    END IF;
END $$;

ALTER TABLE identity_sms_outbox
    ADD CONSTRAINT uq_identity_sms_outbox_challenge_id UNIQUE (challenge_id);
