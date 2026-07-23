# Milestone 13 — Bidirectional Recovery Consistency

## Constitutional rule

Recovery admission must prove durable evidence consistency in both directions.

Milestone 12 established forward validation from projection tables to canonical Event Ledger evidence. Milestone 13 closes the inverse gap: canonical lifecycle evidence must not exist without the corresponding durable projection/fence/terminal/assignment evidence required by the dispatch model.

A process must fail closed when either side of a required durable correspondence is missing or contradictory. No automatic repair is permitted.

## Required correspondences

1. Every `ProposalOffered` event must have exactly one matching `proposal_fence` row with the same `proposal_id`, `order_id`, and `fence_token`.
2. Every fenced order must have exactly one `order_fence` whose `current_token` equals the maximum durable proposal generation for that order.
3. Every canonical terminal event (`ProposalCommitted`, `ProposalDeclined`, `ProposalLapsed`) must have exactly one matching `proposal_terminal` marker of the corresponding type.
4. Every `assignment_guard` must correspond to exactly one `ProposalCommitted` event and a `proposal_terminal = COMMITTED` marker for the same proposal/order/driver.
5. Every committed event must have exactly one matching assignment guard; declined/lapsed proposals must have none.
6. Contradictory or orphan durable evidence blocks construction of `FencedDispatchService` before dispatch capability is exposed.

## Required proofs

- orphan `ProposalOffered` event without `proposal_fence` blocks admission;
- orphan canonical terminal event without `proposal_terminal` blocks admission;
- orphan `assignment_guard` without committed evidence blocks admission;
- assignment guard whose order/driver/proposal disagrees with committed evidence blocks admission;
- valid multi-generation redispatch state remains admitted;
- valid committed, declined, and lapsed restart behavior remains unchanged;
- existing deterministic constitutional suite remains green;
- 10,000-order simulation remains green.

## Non-goals

- no automatic repair or reconciliation;
- no deletion or rewriting of contradictory evidence;
- no weakening of Milestone 12 admission checks;
- no change to dispatch fairness policy.
