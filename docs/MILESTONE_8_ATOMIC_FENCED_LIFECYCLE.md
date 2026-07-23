# Milestone 8 — Atomic Fenced Lifecycle

## Constitutional boundary

A new dispatch generation is valid only if the following durable facts become visible together:

1. the order's monotonic fence token advances;
2. the proposal is bound to that fence token;
3. the Decision Record is persisted;
4. the `ProposalOffered` Event Ledger record is persisted.

These writes execute inside one `BEGIN IMMEDIATE` SQLite transaction. A crash before commit must expose none of them.

## Required properties

- No burned fence generation after a failed proposal creation.
- No Decision Record without a corresponding durable `ProposalOffered`.
- No `ProposalOffered` without a corresponding fence binding.
- Retry after rollback reuses the next legitimate generation without a gap.
- Event hash-chain remains valid after injected rollback.
- Assignment ownership remains protected by the Milestone 6 concurrency boundary.
- Stale commands remain rejected by the Milestone 7 fence checks.

## Failure injection

Two deterministic failpoints prove rollback behavior:

- `after_fence_before_event`
- `after_event_before_commit`

Both must leave `order_fence`, `proposal_fence`, `decision_record`, and `event_ledger` unchanged.
