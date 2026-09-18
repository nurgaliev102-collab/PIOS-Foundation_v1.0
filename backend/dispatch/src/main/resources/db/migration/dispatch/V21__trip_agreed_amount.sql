-- D-06 (Settlement as Evidence): the agreed amount becomes a property of
-- Trip, captured once at Trip creation from the already-accepted Proposal,
-- and immutable thereafter. Additive, nullable column -- no backfill:
-- every Trip row that existed before this migration gets NULL and stays
-- NULL (D-06 Decision item 7). Proposal.stated_price is unaffected and
-- remains the historical record of what was stated; this column is the
-- authoritative agreed amount for a specific Trip.
ALTER TABLE trips ADD COLUMN agreed_amount TEXT;
