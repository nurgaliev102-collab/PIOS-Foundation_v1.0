# PIOS Taxi — Task 20: Proposal Security Remediation Audit

Status: **Read-only security audit. No code was changed. No production, RabbitMQ, or other live-infrastructure connection was made. No ADR was created.** This document closes Task 19 Part 4's own open item: it does not fix the Proposal caller-identity gap, it prepares the minimal, precedented plan to fix it.

## Method

Every claim below is sourced directly: `ProposalController.kt`, `SessionTokenVerifier.kt`, `OwnerCredentialGate.kt`, `Proposal.kt`, `ProposalLapseScheduler.kt`, `AssignmentController.kt`, and Passenger Experience's `ConnectionController.kt` were all read in full or in the relevant part for this task. Every frontend caller of `/v1/proposals` was located by a repository-wide search, not assumed.

## A. Current Attack Surface

| Endpoint | Auth today | Who can call it today |
|---|---|---|
| `POST /v1/proposals` (create) | **None** | Anyone reaching Dispatch's HTTP surface — confirmed publicly reachable via the already-deployed pilot tunnel + frontend proxy (Task 19 Part 4) |
| `POST /v1/proposals/{id}/accept` | **None** | Same — and can name *any* proposal id, not only ones belonging to the caller |
| `POST /v1/proposals/{id}/decline` | **None** | Same |
| `POST /v1/proposals/{id}/lapse` | **None** | Same — and (Section below) has **zero legitimate caller of any kind today**, HTTP or internal |
| `GET /v1/proposals?orderId=` | None (deliberate, ADR-060 Decision 4 — an enumeration sink, out of this task's scope) | Anyone |
| `GET /v1/proposals?driverId=` | **Already fixed** (ADR-060 Decision 4/Answer 2) — `Bearer` token with matching `drv`, or owner `Basic` | N/A — precedent, not a gap |

`ProposalController`'s own KDoc states plainly, and this audit confirms by reading the code: *"Neither the order nor the driver referenced is verified to exist."* Combined with no authentication at all on four of five write/read-sensitive paths, the practical attack surface today is: anyone on the public internet can create a proposal naming any order id and any driver id, accept or decline any proposal by id (including one they have no relationship to), and force-lapse any open proposal — with no log of who did it beyond an IP address, since no identity is ever captured.

## B. Required Authorization Matrix

| Endpoint | Actor who should be authorized | Identity already available for this check | Verification needed |
|---|---|---|---|
| `accept` | The specific driver named on the Proposal (`proposal.driver`) | `SessionTokenVerifier.verify(...).drv` | `verified.drv == proposal.driver.driverId` |
| `decline` | Same as `accept` | Same | Same |
| `lapse` | No real end-user actor at all — the only legitimate resolution mechanism is `ProposalLapseScheduler`, which calls `ProposalLapseApplicationService` **in-process**, never over HTTP (confirmed by reading `ProposalLapseScheduler.kt` — it holds no `RestTemplate`/HTTP client of any kind). The REST endpoint therefore exists for administrative/manual override use only. | `OwnerCredentialGate.verify(...)` | Owner `Basic` credential, full stop — no driver or passenger case to support |
| `create` | Two legitimate actor types today: (1) the passenger who owns the order the proposal is for (`RideRequest.tsx`'s own `attemptProposal`), (2) the owner/coordinator, on behalf of any order (`Coordinator.tsx`'s own `handleAssign`) | `SessionTokenVerifier.verify(...).sub` (any authenticated passenger) **or** `OwnerCredentialGate.verify(...)` | Minimal, achievable now: require one of the two to be present at all. **Not achievable without an architectural change:** verifying the authenticated passenger's own `sub` actually matches the *order's own* passenger — `Proposal` carries no `passengerReference` field today, and Dispatch does not query Order Management synchronously (ADR-005/009/019 — no server-to-server call on the write path). See Section C's own explicit disclosure of this residual gap. |

### What can be checked without any new authentication architecture

Everything in the "Verification needed" column above. **The two collaborators this fix needs — `SessionTokenVerifier` and `OwnerCredentialGate` — are already constructor-injected into `ProposalController` today** (`private val sessionTokenVerifier: SessionTokenVerifier, private val ownerCredentialGate: OwnerCredentialGate`, both used already, but only by `listProposalsForDriver`). No new Spring bean, no new configuration property, no new secret. This is the single strongest piece of evidence for Part G.

### What requires an architectural decision

Only the residual `create`-ownership gap named in the matrix above: fully verifying that the authenticated passenger creating a proposal actually owns the order it names. Two paths exist, neither authorized by this audit to choose: (a) add a `passengerReference` field to `Proposal` (a schema change, a new migration, a decision about whether this becomes a durable Dispatch-owned fact or stays transient) or (b) accept the weaker "any authenticated passenger, any order" guarantee as sufficient for the pilot's own risk tolerance. This is named, not resolved, here.

## C. Minimal Remediation

A single, additive change to `ProposalController.kt` — no domain, application-service, or repository change, no new migration, no new Spring bean:

1. **`acceptProposal`, `declineProposal`**: add a required `@RequestHeader("Authorization", required = false) authorization: String?` parameter (mirroring `listProposals`'s own existing parameter shape exactly). Before delegating to the existing service call, read the proposal (`proposalRepository.findById(id)` — already available, already used by `getProposal`), verify the Bearer token (`sessionTokenVerifier.verify(authorization)`), and require `verified.drv == proposal.driver.driverId` — 401 if the token does not verify at all, 403 if it verifies but names a different driver (or a passenger-only token, `drv == null`), 404 if the proposal itself does not exist (preserving the existing 404 contract, checked before the 403 so a non-existent id does not leak whether *some* proposal exists at that id via a differing status code — mirrors `ConnectionController`'s own precedent of checking existence and identity together).
2. **`lapseProposal`**: add the same `Authorization` header parameter; require `ownerCredentialGate.verify(authorization)` — 401 otherwise. No driver/passenger branch, since none has a legitimate reason to call this directly (Section B).
3. **`createProposal`**: add the same header parameter; require either `sessionTokenVerifier.verify(authorization) != null` (any authenticated passenger) or `ownerCredentialGate.verify(authorization)` — 401 if neither holds. This closes anonymous creation; it does not close the residual ownership gap named in Section B, which this remediation explicitly does not attempt to close.

**Disclosed, deliberate scope limit:** this remediation does not touch `AssignmentController`'s own `arrive`/`start`/`complete` endpoints, which this audit confirmed (by reading `AssignmentController.kt`) have the **identical** structural gap (no authorization at all). That is a real, directly analogous finding, but `Assignment` was explicitly out of this task's own scope ("Proposal API" only) — named here so it is not mistaken for something this remediation already covers, not silently left for a future task to rediscover from scratch.

### Direct answers to this task's own six specific questions

- **Нельзя ли принять Proposal от имени другого driver?** Today: yes, trivially. After the minimal fix: no — `verified.drv` must equal `proposal.driver.driverId`.
- **Нельзя ли decline чужой Proposal?** Same answer, same fix.
- **Нельзя ли создать Proposal с произвольным driverId?** This is not, and should not be, fully closed — naming *any* driver id on a new proposal is the product's own existing, legitimate design for both real callers (a passenger proposing any driver whose link they hold; a coordinator proposing any available driver) — there is no Circle-of-Trust membership check on `create` today, by design, and this remediation does not add one. What the minimal fix closes is *anonymous* creation, not driver-choice freedom.
- **Нельзя ли использовать известный proposalId для изменения чужого Proposal?** For `accept`/`decline`: closed by the driver-identity check. For `lapse`: closed by restricting the endpoint to the owner credential entirely, since no other caller has ever had a legitimate reason to call it.
- **Не ломает ли proposed fix Coordinator/manual assignment?** No. `Coordinator.tsx`'s own `handleAssign` needs exactly one addition — an `Authorization: Basic` header built from the credential the screen already holds in state, via the already-existing `toBasicAuthorizationHeader` helper (`ownerCredential.ts`) `OwnerControlCenter/todayData.ts` already uses for its own owner-gated calls. No behavior of the coordinator flow itself changes.
- **Не ломает ли Circle of Trust?** No. Circle of Trust lives entirely in Passenger Experience's `ConnectionController`, untouched by this remediation. The only touchpoint is that `RideRequest.tsx`'s own `attemptProposal` call would need to send the passenger's own already-held `identity.token` as a Bearer header — no Circle-of-Trust logic is read, written, or reasoned about differently.
- **Не ломает ли First Refusal?** No — and this is worth stating with the same care Task 16/17's own reports gave equivalent claims: `OrderSubmittedFirstRefusalListener` → `FirstRefusalApplicationService.attempt` → `ProposalApplicationService.handle(ProposeDriverCommand(...))` is an **entirely in-process Kotlin call chain that never goes through `ProposalController` or HTTP at all** (confirmed by reading the listener's own source this session and in Task 16/17's own prior work). Adding authorization to the `POST /v1/proposals` HTTP endpoint has zero effect on First Refusal's own automatic proposal creation, because First Refusal never calls that endpoint — it calls the same underlying application-service method directly.

## D. Changes Required By File (not implemented here)

- **`backend/dispatch/src/main/kotlin/com/pios/dispatch/api/ProposalController.kt`** — the only backend file requiring a change: three new `Authorization` header parameters and the checks described in Section C, on `acceptProposal`, `declineProposal`, `lapseProposal`, and `createProposal`.
- **`frontend/src/pages/DriverHome/DriverHome.tsx`** — `respondToProposal` (accept/decline): add `headers: { Authorization: `Bearer ${identity.token}` }` to the existing `request(...)` call. `identity` is already in scope in this function (used elsewhere in the same screen for `loadProposals`).
- **`frontend/src/pages/RideRequest/RideRequest.tsx`** — `attemptProposal`: add the same `Authorization: Bearer` header, using the `identity` this component already holds by the time this function can run (every call site is already past the `if (!identity)` early-return).
- **`frontend/src/pages/Coordinator/Coordinator.tsx`** — `handleAssign`: add `Authorization: toBasicAuthorizationHeader(credential)` (already imported pattern from `OwnerControlCenter/todayData.ts`'s own precedent; `credential` is already held in this screen's own state, gating the page itself).
- **No other file** — no domain, application-service, repository, migration, RabbitMQ topology, or test-infrastructure file requires a change for the minimal remediation. (Backend test files would gain new tests — Section E — but nothing existing requires modification.)

## E. Tests Required (not written here)

Mirroring `ConnectionControllerTest.kt`'s own already-established shape for the identical pattern:

- `accept`/`decline`, no `Authorization` header → 401.
- `accept`/`decline`, a validly-signed token for a *different* driver than the one named on the proposal → 403.
- `accept`/`decline`, a validly-signed token whose `drv` matches the proposal's own driver → 200, unchanged behavior from today.
- `accept`/`decline`, a passenger-only token (`drv == null`) → 403.
- `accept`/`decline`, a non-existent `proposalId` → 404, unchanged, regardless of the header.
- `lapse`, no `Authorization` or a non-owner credential → 401.
- `lapse`, valid owner `Basic` credential → 200, unchanged behavior.
- `create`, no `Authorization` header → 401.
- `create`, any validly-signed passenger token → 201, unchanged behavior (still names an arbitrary `driverId`, deliberately — Section C).
- `create`, valid owner `Basic` credential → 201, unchanged behavior (Coordinator's own path).
- A real, in-process test proving `FirstRefusalApplicationService.attempt` still creates a Proposal with no `Authorization` header anywhere in its own call path (i.e., a test that never touches HTTP at all, only the application-service layer) — this is the test that actually proves the "does not break First Refusal" claim in Section C, not merely an assertion in this report.
- A real, real-RabbitMQ/real-Postgres end-to-end test (mirroring `OrderSubmittedFirstRefusalConsumerIntegrationTest`'s own shape) confirming a full `OrderSubmitted` → listener → `FirstRefusalApplicationService` → Proposal path still produces a Proposal after this change, with zero code path through the now-authenticated controller.
- Frontend: `RideRequest.test.tsx`, `DriverHome.test.tsx`, `Coordinator.test.tsx` each gain one assertion confirming the new `Authorization` header is present on the relevant request body, mirroring this repository's own existing convention (e.g., Task 17's own `explicitDriverIntent` assertion pattern) — not a new testing style.

## F. Rollback Strategy

This remediation touches no schema and no migration — a straight code revert (redeploy the previous jar for Dispatch, and the previous frontend build) is sufficient and complete; nothing is left in an intermediate state, since no data is written or shaped differently by this change, only which callers are permitted to write it. This is a materially simpler rollback than Task 19 Part 3's own deployment plan, precisely because this fix adds no persistent state of any kind.

## G. Safe To Implement Without A New ADR

**Yes**, for the minimal remediation in Section C, with the residual gap named there explicitly excluded from that claim. Reasoning: both authorization primitives this fix needs (`SessionTokenVerifier`, `OwnerCredentialGate`) already exist, are already the ratified mechanism for this exact class of problem (ADR-055 for session tokens, ADR-044 for the owner credential), are already injected into this exact controller, and are already used, in this exact file, for the structurally identical `?driverId=` case (ADR-060 Decision 4). This remediation is a direct, same-shape replication of an already-accepted pattern (`ProposalController`'s own `listProposalsForDriver`, and independently, Passenger Experience's `ConnectionController`, which authorizes every one of its own five endpoints this same way) — not a new authorization model, not a new identity concept, not a new cross-module contract. The one piece that *would* need an architectural decision — full order-ownership verification for `create` — is named in Section B/C as explicitly out of this minimal remediation's own scope, not silently absorbed into a "no ADR needed" claim that would not actually cover it.

## H. Exact Implementation Task For The Next Step

**Task 21 — Proposal Caller-Identity Remediation (Implementation).** Scope: implement exactly Section C's four changes to `ProposalController.kt` and the three frontend files in Section D, add exactly the tests named in Section E (including the two First-Refusal-preserving tests, which are not optional — they are what makes the "does not break First Refusal" claim in this document verified rather than asserted), run the full Dispatch/Order Management/Passenger Experience/Frontend regression this task chain's own established convention already requires (isolated `*_test` databases, isolated `pios-test` RabbitMQ vhost, no production connection), and report focused + full-suite results with real numbers, not "PASS." Explicitly out of scope for that task, per this audit's own findings: `AssignmentController`'s identical sibling gap (a separate, future task); the `create`-ownership architectural gap (Section B/C — requires a Product Owner/architecture decision before any code addresses it); any change to `Proposal`'s own schema or the `passengerReference` question. That task should not proceed to production deployment on its own authority either — Task 19's own Deployment Gate (Part 3) already governs when Dispatch itself is deployed, and this fix should travel with that same, already-planned deployment, not trigger a separate one.
