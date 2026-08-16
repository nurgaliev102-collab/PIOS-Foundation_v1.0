-- Adds the whole number of minutes a driver states it will take them to
-- reach the passenger, given when accepting a Proposal (ADR-057, Driver
-- Stated Time to Pickup). Nullable, no DEFAULT, following this project's own
-- established precedent for exactly this kind of additive column
-- (V6__proposal_stated_price.sql's own comment): any row already present
-- satisfies the new column definition without one, and it is never set
-- except by a driver's own accept action. A typed INTEGER, unlike
-- stated_price's opaque TEXT -- ADR-057 Decision item 3 records why no
-- analogous unratified dimension (currency, units, precision) applies here.
ALTER TABLE proposals
    ADD COLUMN stated_eta_minutes INTEGER NULL;
