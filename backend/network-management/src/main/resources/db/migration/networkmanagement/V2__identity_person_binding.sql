-- D-12: legacy Persons remain unbound and quarantined. No heuristic backfill.
ALTER TABLE persons ADD COLUMN identity_id TEXT;
ALTER TABLE persons ADD COLUMN identity_bound_at TIMESTAMPTZ;

ALTER TABLE persons
    ADD CONSTRAINT ck_persons_identity_binding_pair
    CHECK ((identity_id IS NULL) = (identity_bound_at IS NULL));

ALTER TABLE persons
    ADD CONSTRAINT ck_persons_identity_id_not_blank
    CHECK (identity_id IS NULL OR btrim(identity_id) <> '');

CREATE UNIQUE INDEX ux_persons_identity_id
    ON persons (identity_id)
    WHERE identity_id IS NOT NULL;

-- No ordinary update, including a repository upsert, may transfer ownership.
CREATE FUNCTION prevent_person_identity_rebind() RETURNS trigger AS $$
BEGIN
    IF NEW.identity_id IS DISTINCT FROM OLD.identity_id
       OR NEW.identity_bound_at IS DISTINCT FROM OLD.identity_bound_at THEN
        RAISE EXCEPTION 'person identity binding is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_person_identity_immutable
    BEFORE UPDATE ON persons
    FOR EACH ROW EXECUTE FUNCTION prevent_person_identity_rebind();
