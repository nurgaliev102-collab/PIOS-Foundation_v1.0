# ADR-050: Platform Readiness Model — Running, Operational, Verified

## Status

**Draft — awaiting Product Owner ratification.**

Authorized in principle by two Product Owner instructions of 2026-08-06: the
O-1 resolution (*«После этого обязательно выполняется проверка Health… это
становится обязательным архитектурным правилом Platform Operations
Center»*), and the follow-up instruction to rework the mechanism into a
three-tier state model with terminology that does not collide with an
existing business concept, before this ADR was allowed to be written. Both
are executed here. Neither instruction ratifies this document's text by
itself — that is this ADR's own status line, not a claim of prior approval.

**Basis.** This ADR formalizes the conclusion of
`docs/PLATFORM_READINESS_MECHANISM_RESEARCH.md` (a research report, not
itself an ADR) — specifically its Section 8, written after the Product
Owner's rework instruction. Read that document for the comparative analysis
this ADR only summarizes.

**Relationship to ADR-046, ADR-047, ADR-049.** This ADR is built **on top
of** all three. It amends none of them. It resolves a gap those three left
open (F-1, `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md`
Part 1): ADR-046 Decision 3 and ADR-047 Decision 5 correctly forbid
`platform-ops` from calling any domain module's business or Owner-facing
API — this ADR does not reopen that boundary. It authorizes exactly one new,
narrow, non-Owner, non-business surface that sits outside it, per module.

## Context

### The gap this closes

F-1 established, and this document's own author verified directly in code,
that `platform-ops` has no legal way to know whether a module is genuinely
working, not merely running:

- `GET /v1/health/<module>` is gated by `OwnerCredentialGate`, which stores
  only an irreversible PBKDF2 hash and salt of the owner's password
  (`OwnerCredentialGate.kt` lines 65–67, 128–130) — nothing in the system can
  recover the plaintext from it, so `platform-ops` calling this endpoint
  would require a **second, independently held copy of the owner's actual
  password**, which contradicts ADR-047 Decision 5 (*"the agent has no
  client for those APIs at all"*) and ADR-046 Decision 3 (*"the agent never
  asks a domain module anything"*) by name.
- No equivalent mechanism already exists elsewhere in the project.
  `PLATFORM_READINESS_MECHANISM_RESEARCH.md` Sections 1–2 record an
  exhaustive check: `spring-boot-starter-actuator` is absent from every
  `build.gradle.kts` in `backend/`; `ADR-012-Observability-Principles.md`
  explicitly defers "specific tools, formats, or infrastructure" to a future
  decision and is itself unratified (**Proposed**); all five
  `HealthController.kt` files are structurally identical — one method, one
  route, always gated by the owner credential, with no second, lighter
  endpoint anywhere.

### The Product Owner's O-1 resolution, and what it actually requires

O-1's resolution requires two things simultaneously: the convergence
operation itself is idempotent (a target already in its desired state is a
no-op, reported as success — ADR-049 Decision 2's own semantics, unchanged
here), and a Health check runs after it, mandatorily. The Product Owner's own
fourth constraint on this ADR — *"позволяет Platform Operations Center
определить, что сервис действительно готов к работе, а не просто открыл
TCP-порт"* — rules out treating "the port is open" as sufficient evidence of
that check having passed. A single boolean fact bundling "process exists"
and "actually works" would not let anyone honestly tell those two things
apart on screen, which is the same failure mode ADR-046 Decision 4 already
named for a different pair of facts (process running vs. Owner-visible
health).

### The naming trap, checked rather than assumed

The Product Owner's own first draft of this model used **Availability** for
its weakest tier. Checked directly: `Availability` is not merely a similar
word — it is the literal name of an existing domain class,
`backend/driver-management/src/main/kotlin/com/pios/drivermanagement/domain/Availability.kt`
(`AVAILABLE`/`UNAVAILABLE`), already published as `DriverAvailabilityChanged`.
Using it again here for a technical, non-domain fact would make "availability"
mean two unrelated things in the same repository. Checked separately:
**Healthy**, the strongest tier's original name, collides not with a business
concept but with an existing *technical* one — ADR-044's own
`/v1/health/<module>` / `HealthResponse.status`. `grep` across
`docs/DOMAIN_MODEL.md` confirms `Operational` and `Verified` are used nowhere
as a named state; both are clean.

## Decision

### 1. Platform readiness is a three-tier, strictly-ordered model: **Running → Operational → Verified**

Each tier is evidence of a different, independently meaningful fact, and
each is strictly stronger than the one before it. None is a synonym for
Owner-facing "health" (ADR-044) or for the domain concept "availability"
(Driver Management) — this is a deliberate, checked naming choice (Context,
above), not an unexamined default.

| Tier | What it proves | Source | New code in the five modules | Crosses ADR-046 D3 / ADR-047 D5 |
| --- | --- | --- | --- | --- |
| **1. Running** | The process exists and the operating system does not consider it dead | Windows Service Control Manager, via the read surface ADR-046 Decision 2 already defines | None | No |
| **2. Operational** | The module's own web server has actually bound its port — stronger than "the OS process exists," weaker than "it works" | A plain TCP connect, from `platform-ops`, to the module's already-open port | None | No |
| **3. Verified** | The module has actively confirmed its own internal dependency (today: database reachability) | A new, dedicated, non-Owner endpoint (Decision 2, below) | Yes — one small new controller per module | No — a new surface built outside the boundary those ADRs protect, not a hole in it |

Tiers 1 and 2 require no change to any of the five modules — both already
follow entirely from what ADR-046 and the Sprint 1 plan already designed.
Only tier 3 is new work, and it was already disclosed as the real cost of
this ADR's approach back in
`PLATFORM_READINESS_MECHANISM_RESEARCH.md` Section 6.

### 2. Tier 3 is implemented as one new endpoint per module, structurally separate from the Owner health endpoint

- **Route:** `GET /v1/internal/verification`, on each of the five modules.
  Deliberately neither `/ready` nor `/health` — both names are already
  spoken for elsewhere in this repository (Context, above) and reusing
  either would recreate the exact confusion this ADR exists to prevent.
- **Response:** `{"verified": true|false}` and nothing else. No module name,
  no timestamp, no database label, no business field of any kind — a
  narrower body than the existing `HealthResponse`, by design, since this
  surface answers a narrower question.
- **Check performed:** the same check each module's own `HealthController`
  already performs internally (`SELECT 1` through the existing
  `JdbcTemplate`, per module) — this ADR does not invent new verification
  logic, it exposes the logic that already exists, through a new path.
- **Implementation shape:** a new, separate controller class per module
  (e.g. `InternalVerificationController.kt`), not an added branch inside the
  existing `HealthController.kt`. The already-shipped, already-tested owner
  path is not touched by this ADR at all — a new class carries strictly
  lower risk to what already works than editing the existing one.
- **No credential.** Neither the owner's password nor `platform-ops`'s own
  operations credential (ADR-047 Decision 1) gates this endpoint. This is
  today's conclusion of the rule Decision 6 states explicitly — read that
  Decision before relying on, or changing, this one. Decision 6 exists
  precisely so this bullet is never the last word on the question.

### 3. Tier 3 — Verified — is the mandatory bar for O-1's post-action check

A start (or restart) operation is not reported as successful on the strength
of tier 1 or tier 2 alone. Per the Product Owner's own fourth constraint,
"the port opened" is explicitly insufficient evidence. The convergence
engine's mandatory readiness step (O-1; `ADR-049` Decision 2's own
convergence mechanics, unchanged) polls tier 3 for each affected module
target after it starts, within the operation's own timeout, and the
operation's reported outcome names the tier actually reached — "started, not
yet verified" is a real, distinct, honestly reportable outcome, not
collapsed into a single success/failure bit.

### 4. Idempotency is unaffected, and is itself tier-aware

O-1's idempotency requirement (a target already in its target state performs
no action) is unchanged from `ADR-049` Decision 2. This ADR adds precision
to what "already in its target state" means for the **start** action
specifically: reaching it requires tier 3, not merely tier 1 — a module that
is `Running` and `Operational` but not yet `Verified` is still mid-startup,
not idle-and-already-satisfied, and a start request against it waits for or
re-polls verification rather than either restarting it or declaring success
early.

### 5. The status screen reports the tier reached, not a single flag

Design §2.2's status screen displays, per target, the highest tier currently
confirmed — consistent with, and an extension of, ADR-046 Decision 4's own
rule that process-state and Owner-visible health "must not be merged." A
target can now be shown as `Running` (SCM only), `Operational` (port
answers), or `Verified` (internal check passed) as three honestly distinct
states rather than one blended judgment call.

### 6. The rule underlying Decision 2's "no credential" — stated so it can be tested independently of today's facts, not silently inherited

This section exists because a trust-model review of this ADR (2026-08-06)
found that Decision 2, as originally written, stated a *conclusion*
("no credential") without stating the *rule* that conclusion follows from —
which left three questions unanswerable from the text alone: whether future
external ingress forces rewriting this ADR, whether a second Operations
Center for an unrelated platform could reuse this decision, and whether "no
credential" is itself a principle or an artifact of today's deployment. This
section is the fix, in four parts.

**Principle.** An endpoint on this surface may remain uncredentialed if and
only if everything it discloses is no more informative to an outside
observer than an unauthenticated TCP connect to the same port already is.
"No credential" is never itself the architectural commitment — this rule is.
Decision 2 is this rule's current, checkable conclusion, not a standing
exception to authentication that future work inherits by default or extends
by analogy.

**Criterion.** A response body satisfies the Principle only if it contains:
zero business fields (nothing about an order, a driver, a passenger, money,
or any domain fact any module owns — ADR-046 Decision 3 unchanged); zero
operational secrets (no configuration value, no internal address, no stack
trace, no identifier reusable elsewhere); and zero information that
measurably helps an attacker beyond "a process is listening on this port" —
which an unauthenticated network scan already reveals for free, on this
network, today. `{"verified": true|false}` passes this test as of this ADR.
It stops passing the moment either qualifier in this paragraph stops being
true of the actual, shipped response — not when someone judges the change to
be minor.

**Consequences.** Two independent things can invalidate Decision 2's
conclusion without touching Decisions 1, 3, 4 or 5 — the three-tier model
itself is indifferent to network topology and to any one response shape:

- *The network trust boundary changing* (an ADR-048 concern: external
  ingress, a new gateway, a routed hostname reachable outside the LAN) does
  not, by itself, require rewriting this ADR. It requires ADR-048 or its
  successor to independently re-apply this section's Criterion to the *new*
  exposure context and record its own conclusion. This ADR's Principle and
  Criterion are the fixed yardstick that future decision is measured
  against, not something that decision reopens or restates.
- *A future field added* to `/v1/internal/verification`'s response, or to an
  equivalent endpoint on a future target, that fails the Criterion
  invalidates Decision 2 for that endpoint specifically. This **is** a
  change to this ADR, not a silent implementation detail — see the Change
  Rule below.
- *A second Operations Center, for a platform other than PIOS*, may reuse
  the Principle and the Criterion as a method. It may not reuse Decision 2's
  conclusion by inheritance: the Criterion must be re-applied against that
  platform's own domain vocabulary and its own threat model, exactly as this
  ADR applied it to PIOS's — a different platform's "process is listening on
  this port" is not guaranteed to be as unremarkable as it is here.

**Change rule.** Before any field is added to a tier-3 response body, and
before a tier-3 endpoint is exposed through any network path other than the
one active when this ADR was ratified, the change's author must re-run the
Criterion against the resulting response or exposure **in writing**, and
record one of two outcomes: "still passes, Decision 2 unchanged" or "fails,
Decision 2 no longer holds for this endpoint and must be amended." A field
or a route added without that written check is a defect in the change, not
a judgment call an implementer is free to make silently. This obligation
travels with the Principle, not with today's specific conclusion — it does
not expire merely because Decision 2's current answer happens to still be
"no credential" when someone next touches this code.

### 7. What this ADR does not decide

- **Targets without a tier-3 signal.** `frontend`, `cloudflared`,
  PostgreSQL and RabbitMQ have no tier-3 endpoint under this ADR — it
  defines the mechanism for the five PIOS modules only. Whether and how a
  tier-3-equivalent check is meaningful for the other targets is not
  addressed here and is not required for Sprint 1's five capabilities.
- **"Verified, then silently broke."** Exactly the limitation
  `ADR-049` Decision 1 already disclosed for crash recovery: a module that
  passes tier 3 once and later becomes internally broken (a stalled outbox,
  an exhausted pool) is not re-detected by this mechanism outside of another
  explicit start/restart action. No periodic re-verification loop is
  introduced by this ADR — that would be the "alive but broken" automation
  question `ADR-049` already named as undecided and out of scope (no rule
  for it exists; inventing one here would repeat the same mistake).

## Consequences

### Positive

- Closes F-1 without touching, weakening, or routing around ADR-046,
  ADR-047 or the Owner-facing credential model in any way.
- Gives the status screen three honestly distinct, individually meaningful
  states instead of one conflated boolean — directly serving
  `PLATFORM_OPERATIONS_CENTER_VISION_V1.md` §4.5's own "honesty over
  reassurance" principle.
- Reuses verification logic that already exists and is already exercised by
  each module's own tests, rather than inventing new checks.
- Tiers 1 and 2 are free — no code, no risk to any shipped module — so the
  model degrades gracefully even before tier 3 ships.

### Negative

- Adds one new controller class and one new route to five already-shipped,
  already-tested modules — real Sprint 1 scope, already disclosed in
  `PLATFORM_READINESS_MECHANISM_RESEARCH.md` Section 6 and now confirmed
  as non-optional by Decision 3.
- The unauthenticated tier-3 endpoint (Decision 2) is a conclusion, not a
  standing exception — Decision 6 names the Principle and Criterion it must
  keep satisfying, and the Change Rule that applies the moment either the
  response body or its exposure changes. Nothing about it is safe to assume
  indefinitely by default.
- Does not, and is not intended to, detect a module that degrades after
  being verified — that gap is named, not solved, consistent with how
  ADR-049 already treats the same class of gap elsewhere.

## References

- `PLATFORM_READINESS_MECHANISM_RESEARCH.md` — full comparative analysis and
  the terminology check this ADR's Decision 1 and Context summarize.
- `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` — finding
  F-1, which this ADR resolves.
- `ADR-046-Platform-Operations-Component-Boundary.md` — Decisions 2, 3, 4.
- `ADR-047-Destructive-Action-Authorization.md` — Decisions 1, 5.
- `ADR-049-Platform-Recovery-and-Change-Safety.md` — Decisions 1, 2.
- `ADR-044-Owner-Authentication-Mechanism.md` — the credential model this ADR
  deliberately does not touch or reuse.
