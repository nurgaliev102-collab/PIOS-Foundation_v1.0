# Milestone 12 — Recovery Admission Gate

## Constitutional rule

A process may become dispatch-capable only after durable recovery evidence has passed consistency validation.

If Event Ledger, proposal fencing, terminal lifecycle, assignment guard, or current generation contradict one another, construction of the dispatch service must fail closed. The process must not accept a new proposal, accept, decline, lapse, or redispatch decision from an unvalidated state.

## Admission sequence

1. Open durable ledger and recover base projections.
2. Ensure fencing schema exists.
3. Validate Event Ledger hash chain.
4. Validate every proposal fence against `ProposalOffered` evidence.
5. Validate each `order_fence.current_token` equals the maximum durable proposal generation.
6. Validate every terminal marker has exactly one matching canonical terminal event.
7. Validate `COMMITTED` iff an `assignment_guard` exists for the proposal.
8. Only then return a usable `FencedDispatchService` instance.

## Required proofs

- valid committed state survives restart and remains idempotent;
- valid declined/lapsed state survives restart;
- corrupt fence generation blocks process admission;
- terminal marker/event disagreement blocks process admission;
- committed terminal without assignment blocks process admission;
- the existing deterministic constitutional suite and 10,000-order simulation remain green.

No automatic repair is permitted in this milestone. Contradictory durable evidence is an explicit operational fault requiring diagnosis, not an invitation to guess authoritative state.
