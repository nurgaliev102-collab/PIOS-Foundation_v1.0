# PIOS Taxi Code Audit + Core Slice 01 — Claude Code Task

**Status:** Ready for execution in the actual PIOS Taxi repository
**Purpose:** establish the real production-code boundary before implementing PIOS Core
**Production impact:** Read-only audit first; no production deployment and no production behavior change permitted by this task.

## 1. Why this task exists

The PIOS Foundation repository contains the approved product/data contracts, but it is not the full production Taxi codebase. The actual Taxi repository is the source of truth for the live implementation.

Therefore do not implement PIOS Core from assumptions taken from Foundation documentation. First inspect the real Taxi repository in VS Code and produce a code-level audit. Only after the audit is complete may Slice 01 be implemented.

The governing architectural rule is:

> PIOS Core is built next to Taxi, not by rewriting Taxi into PIOS.

Taxi remains the first vertical/application. PIOS Core must become reusable platform capability.

## 2. Mandatory safety rules

1. Do not touch production database data.
2. Do not run integration tests against any production-named database.
3. Before any test that can write to PostgreSQL, prove the database name/host is an isolated QA/test database.
4. Do not modify live Taxi routing, Order, Proposal, Assignment, Trip, Payment, or existing Connection behavior in Slice 01.
5. Do not merge or replace the existing Taxi Connection/Circle-of-Trust model based on documentation alone.
6. Do not introduce a second hidden relationship graph without documenting the mapping/boundary.
7. Do not add an LLM provider or paid AI dependency for Slice 01.
8. Do not perform a broad refactor, module rename, package migration, or architecture rewrite.
9. Every write path introduced by Slice 01 must be idempotent.
10. If a required boundary is unclear, stop implementation of that part and document the ambiguity rather than guessing.

## 3. Phase A — identify and audit the real Taxi repository

Run a read-only repository inspection from the actual PIOS Taxi workspace.

Record:

- repository name and current branch;
- commit SHA;
- build system and runtime versions;
- backend modules;
- frontend applications/packages;
- PostgreSQL databases/configuration;
- RabbitMQ/outbox/event infrastructure;
- deployment configuration;
- test configuration;
- QA configuration;
- production configuration.

At minimum inspect:

- `settings.gradle.kts` / equivalent module registry;
- `network-management`;
- `passenger-experience`;
- `dispatch`;
- `order-management`;
- `driver-management`;
- `identity`;
- all existing `Connection` entities/tables/services/repositories;
- Circle of Trust;
- Primary Driver;
- invitation/driver-code flow;
- Order → Proposal → Assignment → Trip;
- payment/transaction code if present;
- database/test configuration;
- CI workflows and deployment scripts.

Search globally for at least:

`Connection`, `CircleOfTrust`, `Primary`, `Invitation`, `Proposal`, `Assignment`, `Trip`, `Order`, `relationship`, `trusted`, `isPrimary`, `PostgreSQL`, `spring.datasource`, `DATABASE_URL`, `RabbitMQ`, `outbox`.

## 4. Required semantic diff

Produce a table comparing the Foundation contracts against the actual code.

### A. Identity

- actual user identity;
- actual driver/passenger identity;
- stable person identifier if one exists;
- phone-number identity handling;
- whether one person can hold multiple roles.

### B. Existing Taxi Connection

Determine exactly:

- owner/subject semantics;
- who creates it;
- who can remove/change it;
- trust semantics;
- whether it is bidirectional or contextual;
- whether it represents history or an active relationship;
- whether it has Primary Driver semantics;
- invitation provenance;
- database schema;
- API surface;
- consumers.

### C. PIOS Core Relationship

Compare against the canonical contract:

`connection/existence + trust recognition + optional vertical preference + invitation provenance + derived interaction history`.

Do not force equivalence. Classify each field/concept as:

- SAME SEMANTICS;
- DIFFERENT SEMANTICS;
- DERIVED ONLY;
- MISSING;
- CONFLICT.

### D. Primary Driver

Verify that Primary Driver is Taxi-specific and passenger-controlled. Confirm whether the implementation enforces:

`isPrimary => isTrusted`

and whether only one primary exists per passenger.

### E. First Refusal

Verify the current routing behavior and whether the implementation matches:

`Order → Relationship Resolution → RoutingInstruction → First Refusal → Proposal → Assignment → Trip`.

Do not implement First Refusal in Slice 01.

## 5. Database safety audit

This is a release blocker.

Identify every integration-test datasource and answer:

1. What exact database does each test profile use?
2. Can a default test command reach production?
3. Can a test create/update/delete production rows?
4. Is there a dedicated QA database?
5. Are QA ports/credentials isolated?
6. Is production configuration impossible to load accidentally during tests?

If any test can write to a production-named database, classify it as **CRITICAL** and do not run destructive/integration tests until isolation is fixed.

## 6. Phase B — architecture decision for Slice 01

After the audit, select exactly one physical implementation boundary for the first Core slice.

Preferred direction:

- a reusable Core module/package/service boundary;
- no direct ownership of Taxi dispatch state;
- no duplicate Taxi routing logic;
- no direct mutation of existing Taxi Connection unless an explicit semantic mapping proves it safe;
- clear ownership of Person/Profile/Relationship/Capability data.

The audit must state:

- where Core code will physically live;
- which database/schema it owns;
- which module owns each canonical entity;
- how Taxi may consume Core later;
- what must remain Taxi-owned.

## 7. Slice 01 implementation target

Slice 01 is **Person Context / My People foundation**, not First Refusal and not AI dispatch.

The first code slice should establish the smallest real capability needed for:

> A user can recognize a person from their existing contact context and explicitly create/maintain a PIOS relationship without corrupting Taxi's existing relationship model.

Minimum target concepts:

- ContactRecord;
- Person identity/reference;
- Person Match Candidate where needed;
- Profile projection sufficient for user recognition;
- Relationship recognition/action;
- provenance;
- confidence where inference is involved;
- idempotent persistence;
- explicit user confirmation for relationship creation.

Do not infer profession/capability merely from a name. Capability extraction belongs to Slice 02.

## 8. Required API/product boundary for Slice 01

Define only the minimum endpoints/use cases actually justified by the audited codebase. Expected conceptual operations:

- inspect/import a contact candidate;
- resolve candidate to an existing PIOS Person when evidence is sufficient;
- explicitly recognize/connect a Person;
- retrieve a user's recognized people;
- retrieve person context needed for recognition.

The exact endpoint names must follow the actual Taxi repository conventions after audit.

## 9. Idempotency and concurrency

At minimum prove:

- repeated contact ingestion does not duplicate ContactRecord;
- repeated relationship-recognition command does not create duplicate Relationship rows;
- concurrent recognition requests converge to one relationship;
- retries do not duplicate events/outbox records;
- user cannot accidentally create two logically identical relationships through duplicate HTTP requests.

## 10. AI boundary

Slice 01 must not depend on an LLM.

Inference may be deterministic and evidence-based. If later AI is used to enrich context, its output must be represented as derived/inferred data with provenance and confidence and must never overwrite source facts silently.

## 11. Tests required

Before declaring Slice 01 complete:

### Unit

- identity normalization;
- contact deduplication;
- provenance/confidence rules;
- relationship recognition invariants;
- idempotency keys/commands.

### Integration

- isolated QA PostgreSQL only;
- create/read relationship;
- duplicate request;
- concurrent request;
- retry behavior;
- authorization/ownership boundary.

### Regression

Run the existing Taxi regression suite unchanged and demonstrate that existing:

- order creation;
- proposal lifecycle;
- assignment;
- trip lifecycle;
- Circle of Trust;
- Primary Driver;
- invitation flow

remain green.

## 12. Definition of Done

Slice 01 is NOT done merely because code compiles.

It is done only when all are true:

1. Actual Taxi repository and commit are documented.
2. Existing Connection semantics are documented from code.
3. Core-vs-Taxi semantic diff is complete.
4. Physical Core ownership boundary is documented.
5. Test database isolation is proven.
6. No production behavior was changed unintentionally.
7. Slice 01 code is isolated in a feature branch.
8. Unit/integration/regression tests pass against QA/test infrastructure.
9. Idempotency and concurrency behavior is proven.
10. No LLM/provider dependency was introduced.
11. A concise implementation report lists files changed, migrations, endpoints, tests, and remaining risks.

## 13. Deliverables

Claude Code must return, in order:

### Deliverable 1 — CODE AUDIT

A concise report containing:

- repository/branch/SHA;
- actual module map;
- actual Connection model;
- Primary Driver model;
- First Refusal status;
- database topology;
- test/production isolation result;
- semantic diff;
- recommended Core physical boundary;
- blockers.

### Deliverable 2 — IMPLEMENTATION PLAN

Exact files/packages/modules to add or modify, migrations, APIs, tests, and rollback strategy.

### Deliverable 3 — SLICE 01 CODE

Only after Deliverables 1 and 2 are internally consistent.

### Deliverable 4 — QA REPORT

Commands executed, database used, test results, regression results, changed files, and proof that production behavior/data were not touched.

## 14. Final architectural rule

Do not optimize for the fastest code change. Optimize for preserving the future PIOS graph.

The intended long-term chain is:

`Person → Relationship → Capability → Need → Opportunity → Action → Transaction → History → Repeat Business → AI Intelligence`.

Taxi is the first application of this graph, not the owner of the graph.
