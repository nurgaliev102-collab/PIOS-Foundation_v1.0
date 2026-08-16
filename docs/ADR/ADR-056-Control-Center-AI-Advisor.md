# ADR-056: Control Center AI Advisor — Architecture and Provider Boundary

## Status

**Proposed.** Authored per direct Product Owner architectural instruction,
2026-08-14, mirroring `docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`, itself
ratified the same day. That instruction specified the component chain
(`Control Center → AI Service → AIProvider → GigaChatProvider`), the
provider (GigaChat, model `GigaChat-3-Ultra`), and the non-negotiable
boundary (`Browser NEVER → GigaChat`) verbatim; this ADR records the
architecture that satisfies those instructions and the reasoning behind the
decisions the instruction left open. Per `.claude/CLAUDE.md`'s workflow,
architecture decisions of this scope normally route through the project
architect before implementation begins — this document is written to be
reviewed against that process, not to bypass it. No code is introduced by
this ADR (see "Files Changed").

## Context

### What Owner Control Center is today, and what it is not

ADR-043 established the observation boundary; ADR-044 established owner
authentication. Both remain in force, unedited, unamended. Owner Control
Center is, today, a pure frontend: `frontend/src/pages/OwnerControlCenter/`
calls the five pilot modules' existing `GET` endpoints directly
(`healthPoll.ts`, `todayData.ts`) and holds no backend of its own — there is
no `owner-control-center` entry in `backend/settings.gradle.kts`, and none is
introduced here either.

`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` Section 7 requires the AI
advisor to reach PIOS's domain modules exclusively through `GET`, with no
credential capable of a write. That requirement is structural, not a
description of current caution — it is the one property every decision below
is checked against.

### The platform-ops precedent: a component that is deliberately not a bounded-context module

ADR-046 Decision 1 (Draft, awaiting ratification, but already the
operative precedent for the one non-domain backend component PIOS has ever
built) states `platform-ops` is *"Not a PIOS module"* — it owns no domain
data, is not listed in `backend/settings.gradle.kts` alongside
`driver-management`/`passenger-experience`/`order-management`/`dispatch`/
`network-management`/`identity`, and is deployed as its own independent
Windows service (`windows-services/platform-ops/pios-platform-ops.xml`)
outside the domain modules' own build.

This is the closest existing precedent for the AI advisor, and Decision 1
below follows it deliberately: like `platform-ops`, the AI advisor owns no
domain aggregate, introduces no event, and is not itself a bounded context
under `MODULE_STRUCTURE.md` Section 3. Unlike `platform-ops` (which exists
to *act* on the platform, gated by the still-draft ADR-047 authorization
model), the AI advisor is forbidden from acting at all — it is structurally
closer to `platform-ops`'s deployment shape than to its authority model.

### `MODULE_STRUCTURE.md` already names, and has not scaffolded, the closest domain concept

Section 3's Analytics Module is described as *"responsible for producing
insight into platform and ecosystem behavior... Retrieve Insight...
[which] never owns the underlying information it derives insight from"* —
a strikingly close conceptual match to what an AI advisor does. It has never
been scaffolded (absent from `settings.gradle.kts`) and no ADR authorizes
its buildout. This ADR does **not** invoke, activate, or scaffold the
Analytics Module: doing so would ratify a bounded context beyond what
`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` asked for, and
`MODULE_STRUCTURE.md` Section 8 requires that ratification to happen through
its own decision, not as a side effect of this one. It is recorded here so a
future reader who notices the resemblance finds the connection already
named rather than rediscovers it.

### Why this cannot be five replicated endpoints, unlike CORS and health

`WebCorsConfiguration` and each module's `HealthController` are replicated
five times because they are stateless, credential-free (CORS) or
single-value (health) checks with no shared secret and no aggregation across
modules (ADR-044 Decision 1's own reasoning for the identical shape). The AI
advisor does not fit that shape for two independent reasons: it holds one
secret (the GigaChat API key) that must exist in exactly one place, not five
independently-rotated copies (`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`
Section 11); and it must read across all five modules' data to answer one
question, which none of the five modules can do about the other four
without becoming a sixth kind of inter-module dependency ADR-009 (Domain
Isolation) does not allow.

## Decision

### 1. The AI advisor is a new, independent backend component — `ai-advisor` — not a bounded-context domain module.

Structured like `platform-ops`: its own Gradle build, its own deployable
artifact, its own Windows service, absent from
`backend/settings.gradle.kts`'s domain-module list, owning no table, no
migration, no aggregate, and consuming no domain event. It is not subject to
`MODULE_STRUCTURE.md` Section 8's bounded-context ratification because it is
not a bounded context: it owns no information any other module would need
to reference, exactly as `platform-ops` does not.

Placed under `backend/ai-advisor/` (a Kotlin/Spring Boot module, consistent
with ADR-022/ADR-023 — no new backend language is introduced) or as a
top-level project alongside `platform-ops/`, mirroring whichever of the two
existing precedents (`backend/*` vs. top-level `platform-ops/`) the
architect confirms during review; this ADR does not resolve that placement
question, since it is a repository-layout detail with no behavioral
consequence, not an architecture decision.

### 2. Component chain: `Control Center (browser) → ai-advisor (backend) → AIProvider → GigaChatProvider → GigaChat API`.

```
Browser (Owner Control Center)
   |  POST /v1/advisor/ask  (owner credential, ADR-044, same Authorization header)
   v
ai-advisor backend
   |  1. reads context: GET /v1/health/<module> x5, /v1/drivers,
   |     /v1/orders, /v1/proposals, /v1/assignments — the same
   |     unauthenticated read surface Control Center's own frontend
   |     already calls (ADR-043 Decision 5's boundary, unchanged)
   |  2. builds a prompt from that context + the owner's question
   |  3. calls AIProvider.ask(prompt) -> AIProvider is an interface,
   |     not a class reference to GigaChat
   v
GigaChatProvider (implements AIProvider)
   |  holds the GigaChat API key (Decision 5), calls GigaChat's own API
   v
GigaChat API (external, network egress from ai-advisor only)
```

**`Browser NEVER → GigaChat`, in force absolutely**: the browser holds no
GigaChat credential, makes no request whose destination is a GigaChat host,
and `ai-advisor` is the only component in this chain with network egress to
GigaChat. The only requests the browser makes are to `ai-advisor`'s own
`POST /v1/advisor/ask`, authenticated identically to every other Control
Center request today (`Authorization: Basic ...`, ADR-044 Decision 3) — no
second login, no second credential type is introduced.

### 3. `AIProvider` is an interface with exactly one operation surface, described here in prose (no code is written under this ADR).

Conceptually: `AIProvider.ask(context: PilotContext, question: String):
AdvisorAnswer`, where `PilotContext` is the read-only snapshot `ai-advisor`
assembled in step 1 above (health, driver/order/proposal/assignment data —
never the money figures `PRODUCT_DECISION_CONTROL_CENTER_AI.md` Section 6
forbids), and `AdvisorAnswer` is either a textual answer or an explicit
failure value (Decision 6). `GigaChatProvider` is the first, and for this
MVP the only, implementation. No caller of `AIProvider` — the request
handler that serves `POST /v1/advisor/ask` — references `GigaChatProvider`,
GigaChat's own request/response shape, or any GigaChat-specific
configuration key directly; all of that is confined to
`GigaChatProvider`'s own implementation.

This is the same shape ADR-044 itself used for its own configuration idiom
(Decision 2's `pios.*` Spring property convention,
`OrderManagementRestClientConfiguration`'s `@Value`-injected base URL): one
interface, one Spring-selected implementation, no caller-side knowledge of
which implementation is active.

### 4. `ai-advisor` never holds, requests, or forwards a write credential to any PIOS module.

It calls only the `GET` endpoints named in Decision 2, using no
`Authorization` header those endpoints do not already accept from an
anonymous caller today (ADR-043 Decision 5: those endpoints are already
public). Where `ai-advisor` itself requires a credential — verifying that
the browser's own request to `POST /v1/advisor/ask` carries a valid owner
credential — it performs the identical check ADR-044 Decision 3 already
defines (Basic auth, constant-time comparison against
`pios.owner.password-hash`/`pios.owner.password-salt`), replicated into this
sixth process the same way it is already replicated into the first five, not
invented anew.

`ai-advisor` is, by construction, structurally incapable of a write: no
`RestTemplate`/`WebClient` configuration in this component is ever pointed
at a `POST`/`PUT`/`PATCH`/`DELETE` path on any domain module. This is the
technical form of `docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` Section 7's
"read-only security boundary."

### 5. The GigaChat API key lives only in `ai-advisor`'s own deployment configuration, never in frontend code or environment.

Following the exact precedent `pios.owner.password-hash`/
`pios.owner.password-salt` already set (ADR-044 Decision 2): a
`pios.ai-advisor.gigachat.api-key`-shaped Spring property (final property
name confirmed at implementation time), read once at startup, never logged
(mirrors ADR-044 Decision 3's "the credential must never be logged"), never
returned in any HTTP response body `ai-advisor` produces, and absent from
every `VITE_*` environment variable the frontend's build can see — the
frontend has no build-time or runtime path to this value, structurally, not
by convention: it is never passed to `frontend/`'s own build in the first
place.

### 6. Failure is a returned value, never a fabricated answer.

`AIProvider.ask` returns an explicit failure outcome — GigaChat unreachable,
GigaChat error, context assembly incomplete — the same "never throws,
returns outcomes as values" shape `healthPoll.ts`'s `fetchModuleHealth`
already established for Control Center's own health polling. `ai-advisor`
never asks GigaChat to answer with incomplete context and never lets an
absent data source become a silent gap in the prompt — Section 12 of
`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` requires the owner be told what
was missing, not given an answer that quietly ignored it.

### 7. Model selection is a configuration value, not a compiled constant.

`GigaChat-3-Ultra` is the starting value of a `pios.ai-advisor.gigachat.model`
property (exact name confirmed at implementation time), read the same way
`pios.proposal.lapse.timeout-minutes` already is — changing it is a
configuration + restart operation on `ai-advisor` alone, touching no other
module, no frontend build, and no other ADR.

### 8. Logging/audit is new to this component, and is scoped narrowly.

`ai-advisor` logs each question and answer (never the GigaChat API key) to
its own rolling log file, following the same `../logs/<module>.log`
convention T-1 already established for the five pilot modules
(`ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md`). This is
a deliberate departure from ADR-044 Decision 6 ("no audit... because there
is exactly one subject and it performs no actions") — the AI advisor is the
first Control Center surface where the owner's single credential drives a
content-producing action (a question, an answer) rather than a passive
read, which is exactly the distinction Decision 6 draws as its own boundary
for when audit becomes warranted.

## Alternatives Considered

- **Call GigaChat directly from the browser.** Rejected outright — this is
  the one option `docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`'s "критическое
  ограничение" section forecloses explicitly (`API key AI никогда не
  попадает во frontend`; `браузер не обращается напрямую к AI provider`).
  No further architectural weighing applies; it is a stated constraint, not
  a traded-off option.
- **Embed the AI call inside each of the five existing modules**, mirroring
  `WebCorsConfiguration`/`HealthController`'s five-way replication. Rejected
  — see "Why this cannot be five replicated endpoints" above: a single
  secret replicated five times and a cross-module read no single module
  can perform without violating ADR-009.
- **Scaffold `MODULE_STRUCTURE.md`'s Analytics Module now, and place the
  advisor inside it.** Rejected for this ADR specifically, not rejected in
  principle: doing so ratifies a bounded context beyond what
  `docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` authorized, per
  `MODULE_STRUCTURE.md` Section 8's own requirement that a new module
  requires a decision "that extends or supersedes ADR-017 and ADR-018."
  Recorded in Context above as a resemblance worth a future reader's
  attention, not exercised here.
- **Call GigaChat's SDK/API type directly from the `POST /v1/advisor/ask`
  handler, with no `AIProvider` interface.** Rejected —
  `docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` Section 8 requires the
  abstraction explicitly, for the same reason ADR-044 avoided hand-rolling
  a second authentication idiom: a provider-specific type leaking into the
  request handler is exactly the coupling Section 14's future replacement
  requirement forbids.

## Consequences

### Positive

- `Browser NEVER → GigaChat` is enforced structurally, not by convention:
  the browser has no code path, no credential, and no network destination
  that reaches GigaChat.
- No existing ADR is amended. ADR-043, ADR-044's read-only/single-credential
  design, and ADR-009's domain isolation all remain exactly as ratified.
- The GigaChat API key exists in exactly one place, unlike the owner
  credential's five-way replication (ADR-044's own disclosed debt) — this
  design does not repeat that cost for a new secret.
- Replacing GigaChat later is, by construction, a new `AIProvider`
  implementation plus a configuration change — `docs/PRODUCT_DECISION_
  CONTROL_CENTER_AI.md` Section 14's requirement is satisfied by the shape
  of the interface, not by a separate migration plan.

### Negative, disclosed

- **A sixth backend process** now exists (`ai-advisor`), alongside five
  domain modules and `platform-ops` — one more thing to deploy, monitor, and
  keep patched, for a component that (unlike the five domain modules) is not
  independently useful without a live AI provider behind it.
- **`ai-advisor` becomes a second point of egress to the public internet**
  from this deployment (the first being `platform-ops`'s own control-plane
  ingress, ADR-048), which the "PIOS production must work in Russia without
  VPN" constraint (`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`, "Критическое
  ограничение") makes a live operational concern, not a hypothetical one —
  GigaChat's own reachability from the deployment environment is a
  precondition of this feature working, the same way TLS is a precondition
  ADR-044 Decision 9 names rather than assumes.
- **This is the first PIOS component whose correctness is not fully
  verifiable by PIOS's own tests** — a wrong or misleading AI answer is a
  product-quality failure mode this ADR does not solve, only bounds (Decision
  6: failure must be visible, not fabricated; the *content* of a non-failure
  answer is outside what any ADR can guarantee).
- **Audit log introduces the first owner-identifiable content record** in a
  system that has, by ADR-044 Decision 6, never recorded what its one user
  did. This is a deliberate, narrow exception (Decision 8), not a reversal
  of that decision's reasoning for the rest of Control Center.

## Traceability

| Subject | Source |
| --- | --- |
| Product-level role, boundary, and constraints this ADR implements | `docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`, all sections |
| Owner Control Center's current, unamended scope | ADR-043, ADR-044 |
| Precedent for a non-domain, independently-deployed backend component | ADR-046 Decision 1; `windows-services/platform-ops/pios-platform-ops.xml` |
| Bounded-context ratification requirement this ADR deliberately does not trigger | `MODULE_STRUCTURE.md` Section 8 |
| Analytics Module's conceptual resemblance, not activated here | `MODULE_STRUCTURE.md` Section 3 |
| Domain isolation, why no module reads across the other four for this purpose | ADR-009 |
| Configuration idiom reused (`pios.*` Spring properties) | ADR-044 Decision 2; `pios.order-management.base-url`; `pios.proposal.lapse.timeout-minutes` |
| "Never throws, returns outcomes as values" pattern reused | `frontend/src/pages/OwnerControlCenter/healthPoll.ts`, `fetchModuleHealth` |
| Owner credential check replicated into a sixth process | ADR-044 Decision 3 |
| Rolling log file convention reused | T-1, `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md` |
| Backend technology (Kotlin/Spring Boot), unchanged | ADR-022, ADR-023 |

## Files Changed

This ADR and `docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` only. No source
file, build file, `.env`, or Git configuration is touched by this document —
per the Product Owner's explicit instruction, no dependency is added, no API
key is created, and no `ai-advisor` module is scaffolded under this ADR.
Scaffolding is a separate, later implementation task once this ADR is
reviewed.
