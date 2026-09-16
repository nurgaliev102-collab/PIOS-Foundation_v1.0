-- Historical builds allowed more than one identity to claim the same
-- driver. Keep the oldest claim and revoke every ambiguous later claim
-- before enforcing the invariant. Revocation (NULL) is safer than silently
-- moving control of a driver profile to the newest claimant.
WITH ranked_claims AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY driver_id
               ORDER BY created_at ASC, id ASC
           ) AS claim_rank
    FROM identities
    WHERE driver_id IS NOT NULL
)
UPDATE identities AS identity
SET driver_id = NULL
FROM ranked_claims
WHERE identity.id = ranked_claims.id
  AND ranked_claims.claim_rank > 1;

-- PostgreSQL permits multiple NULL values, so passenger-only identities
-- and revoked ambiguous claims remain unaffected by the unique index.
CREATE UNIQUE INDEX ux_identities_driver_id
    ON identities (driver_id)
    WHERE driver_id IS NOT NULL;
