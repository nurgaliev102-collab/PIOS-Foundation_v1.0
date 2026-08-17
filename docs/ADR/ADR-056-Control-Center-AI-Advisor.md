# ADR-056: AI Advisor for Owner Control Center

## Status

**Accepted — ratified by Product Owner, 2026-08-17.** Supersedes this
document's own 2026-08-14 draft in place
(`CLAUDE.md`, "Never Delete Documentation" — this is pre-ratification
iteration on a still-Proposed text, not an edit to an Accepted one; the
2026-08-14 draft's own reasoning is preserved below wherever this revision
did not find cause to change it, and every place this revision departs from
it is named explicitly, not silently).

**What changed since 2026-08-14, and why.** Two things happened between the
two drafts that this revision exists to account for:

1. **The frontend half of this feature was actually built** ("AI Analyst
   first technical stage," commit `af5f9dd`, 2026-08-17) — `pilotAnalytics.ts`
   (`PilotAnalyticsInput`, computed client-side from data the browser already
   legitimately reads), `aiProvider.ts` (`AIProvider` interface,
   `MockAIProvider`), `AIAnalystCard.tsx`. This is not hypothetical anymore;
   it is committed, tested (112/112), and deployed. This revision audits that
   real code against this ADR's own decisions, rather than designing in the
   abstract.
2. **A conflict with an already-drafted sibling ADR was found and had to be
   resolved, not merely disclosed.** The 2026-08-14 Decision 2 has
   `ai-advisor` itself calling `GET /v1/health/<module>` x5, `/v1/drivers`,
   `/v1/orders`, `/v1/proposals`, `/v1/assignments` — a backend component
   fanning out reads across all five domain modules to build one aggregated
   view. `ADR-043` Decision 1 forbids exactly this shape by name (quoted
   directly in `ADR-046` Decision 3, which built `platform-ops` specifically
   to avoid it: *"any correlation between platform state and business state
   happens in the browser... The agent neither knows nor asks"*). The
   2026-08-14 draft did not name this tension. This revision does, and
   resolves it — see Decision 1 below.

## Context

### What Owner Control Center is today, and what it is not

Unchanged from the 2026-08-14 draft: ADR-043 established the observation
boundary; ADR-044 established owner authentication. Both remain in force,
unedited. Owner Control Center is a pure frontend
(`frontend/src/pages/OwnerControlCenter/`) that calls the five pilot
modules' existing `GET` endpoints directly and holds no backend of its own.

**New fact this revision adds:** as of `af5f9dd`, Owner Control Center's own
frontend *already* computes `PilotAnalyticsInput` — a small, aggregated,
PII-free snapshot (order/proposal/assignment/driver counts, rates, reaction
times, module health) — using exactly the same `GET` calls `todayData.ts`
already made before this feature existed
(`frontend/src/pages/OwnerControlCenter/pilotAnalytics.ts`). This is not a
new capability; it is a new *aggregation of already-legitimate reads*,
performed where those reads were already authorized to happen: in the
browser, behind the same owner credential, over the same public endpoints.

`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` (ratified 2026-08-14, unedited
by this revision) Section 7 requires the AI advisor to reach PIOS's domain
modules exclusively through `GET`, with no write-capable credential. That
requirement is unchanged in force. What this revision changes is *whether
`ai-advisor` needs to reach PIOS's domain modules at all* — see Decision 1.

### The platform-ops precedent, re-examined rather than reused wholesale

The 2026-08-14 draft cited `ADR-046` Decision 1 (`platform-ops`: not a
bounded-context module, independently deployed, own Windows service) as
"the closest existing precedent" and used it to justify `ai-advisor`'s own
shape. Re-reading `ADR-046` in full for this revision surfaces a distinction
the earlier draft missed:

- `platform-ops` reports **only platform facts** — process state, log tails,
  backups — and `ADR-046` Decision 3 states, as a load-bearing design
  choice, that it **never asks a domain module anything** and never
  correlates platform state with business state itself. That correlation is
  pushed to the browser specifically *so that* `platform-ops` never becomes
  the cross-module aggregator `ADR-043` Decision 1 forbids.
- The 2026-08-14 `ai-advisor` design does the opposite: its entire purpose
  is to read and correlate business data across five modules.

**Conclusion this revision draws:** `platform-ops` is the right precedent
for *deployment shape* (independent process, no domain data of its own kind,
own Windows service, excluded from `backend/settings.gradle.kts`) and the
wrong precedent for *data access shape*. `ai-advisor` cannot copy
`platform-ops`'s "never ask a domain module" property and still do what
`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` asks of it (Section 5: "AI
получает контекст... `GET /v1/health/<module>`... `/v1/drivers`,
`/v1/orders`, `/v1/proposals`, `/v1/assignments`"). Decision 1 below is how
this revision closes that gap without either contradicting `ADR-043`
Decision 1 or asking that Product Decision to be reopened.

### `MODULE_STRUCTURE.md`'s Analytics Module — still not scaffolded, still not invoked

Unchanged from 2026-08-14: this ADR does not scaffold `MODULE_STRUCTURE.md`
Section 3's Analytics Module. Recorded here as a resemblance a future reader
should not mistake for activation.

## Problem

How does the owner get an AI-generated explanation of pilot state, backed by
a real external language model, without (a) any browser code path ever
reaching that model directly, (b) any new backend component becoming the
cross-module aggregator `ADR-043` Decision 1 forbids, and (c) any change to
the domain modules, their contracts, their database, or their existing
authentication?

## Goals

- The owner can trigger an AI-generated analysis of already-visible pilot
  data from `/owner`, behind the same login that already gates the rest of
  the console (ADR-044).
- The analysis is grounded only in real, already-computed numbers — never a
  fabricated fact about PIOS (`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`
  Section 6).
- Swapping the underlying provider (DeepSeek, Claude, a future third) is a
  configuration change plus a new class, never a rewrite of Owner Control
  Center or of the `ai-advisor` request handler (`AIProvider`, unchanged
  principle from 2026-08-14).
- The provider's API key exists in exactly one place, never reaches the
  browser, and is never logged.
- A provider outage, timeout, or quota error degrades to an honest "not
  available right now" — never a fabricated answer, never a crash of the
  rest of Owner Control Center.

## Non-goals (explicit, per this task's own scope)

- Not implementing `DeepSeekProvider` or `ClaudeProvider` — this ADR
  authorizes their eventual shape, not their code.
- Not adding any API key, secret, or `.env` entry.
- Not changing the passenger, driver, proposal, assignment, or order
  lifecycle, their contracts, or the database.
- Not giving the AI advisor any write capability, ever, at this stage —
  see "Read → Analyze → Report only" below.
- Not building free-form owner Q&A yet (`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`
  Section 4's first user story — "почему у водителя Артура сегодня три
  отменённых заказа подряд") — that needs raw, per-order/per-driver text in
  the prompt, which reopens the PII and prompt-injection questions Decision
  9 below defers deliberately. The MVP scope is the already-built
  "Проанализировать пилот" button: one aggregated snapshot in, one
  structured analysis out, no free text either direction.

## Decision

### 1. `ai-advisor` never reads any domain module directly. It receives an already-computed `PilotAnalyticsInput` from the browser instead.

**This is the one substantive departure from the 2026-08-14 draft**, and it
is the resolution to the `ADR-043` Decision 1 conflict named in Context
above.

```
Browser (Owner Control Center, already logged in — ADR-044)
   |  1. pilotAnalytics.ts (already built, af5f9dd) reads the same public
   |     GET endpoints Owner Control Center already reads for
   |     TodayCard/EventFeed — no new capability, no new authorization.
   |  2. POST /v1/advisor/analyze  { input: PilotAnalyticsInput }
   |     Authorization: Basic ... (the same owner credential, ADR-044,
   |     already sent to GET /v1/health by healthPoll.ts)
   v
ai-advisor (new, independent backend process)
   |  1. verifies the Authorization header (OwnerCredentialGate, replicated
   |     — see Decision 4)
   |  2. validates the request body's shape and size (Decision 8)
   |  3. builds a prompt from the numbers already in the request — reads no
   |     domain module, holds no domain module's base URL, has no network
   |     path to any of the five modules' ports at all
   |  4. calls AIProvider.analyze(input) -> interface, no caller-side
   |     knowledge of which provider is active
   v
DeepSeekProvider / ClaudeProvider (implements AIProvider, not built yet)
   |  holds its own provider API key, calls that provider's own HTTP API
   v
External AI provider (DeepSeek API / Claude API)
```

**Why this is correct, not merely convenient:**

- `ai-advisor` performs **no cross-module read of any kind**. It is not a
  "backend-for-frontend, gateway, or aggregation endpoint that fans out to
  other modules" (`ADR-043` Decision 1's own forbidden shape) because it
  fans out to *nothing* — the aggregation already happened, legitimately, in
  the browser, exactly where `ADR-046` Decision 3 already establishes that
  kind of correlation belongs.
- `ai-advisor` needs **zero configuration for any domain module's base URL**
  — it has no `pios.driver-management.base-url`-shaped property, unlike
  every domain module's own cross-module HTTP client configuration. Its
  network egress is exactly two destinations: the browser (inbound) and the
  configured AI provider (outbound). This is a smaller, more auditable
  surface than the 2026-08-14 draft's five-module read fan-out.
- **The trust question this raises — the browser could send fabricated
  numbers — is real but low-severity, and is named rather than solved.**
  There is exactly one legitimate caller (the owner, ADR-044 Decision 6:
  "one credential, one capability"), and the only consequence of a
  fabricated `PilotAnalyticsInput` is a misleading answer to the person who
  supplied the fabricated input — nothing downstream reads, stores, or acts
  on it (Decision 3, Read → Analyze → Report only). This is the same trust
  level `buildReport.ts` already extends to the browser's own computed
  `TodaySnapshot` today, not a new one.
- **What this does not change:** the owner still never talks to the AI
  provider directly (`Browser NEVER → DeepSeek/Claude`, verbatim from the
  2026-08-14 draft's own non-negotiable boundary — unchanged and, if
  anything, easier to enforce now that `ai-advisor` has no reason to hold
  any domain-module credential either).

### 2. `ai-advisor` is a new, independent, top-level deployable — not a bounded-context module, and not folded into `backend/`'s domain-module build.

Revised placement recommendation (the 2026-08-14 draft left this open):
**`ai-advisor/` as its own top-level Gradle project**, sibling to
`backend/` and `platform-ops/` — its own `settings.gradle.kts`, its own
`build.gradle.kts`, its own `gradlew`, its own Windows service
(`windows-services/ai-advisor/pios-ai-advisor.xml`), its own port (8091 —
8090 belongs to `platform-ops`, ADR-046 Decision 1). Kotlin/Spring Boot,
unchanged stack (ADR-022, ADR-023).

**Why top-level and not `backend/ai-advisor/`, and why not `platform-ops`'s
own reasoning verbatim:** `platform-ops` argues (`ADR-046` Decision 1.1) it
is *"infrastructure that operates PIOS"*, in the same category as
PostgreSQL and RabbitMQ — genuinely not part of the product. `ai-advisor` is
not that: it exists because a PIOS product feature (AI Analyst) needs it,
and it is visible to the owner as part of Owner Control Center. Placing it
under `backend/` would be the more literal fit *if* it were a domain
module — but it owns no aggregate, no table, no event, and is absent from
`MODULE_STRUCTURE.md` Section 3's list, exactly as `platform-ops` is. Its
placement question is therefore genuinely open between "product component,
non-domain" and "infrastructure, non-domain" — this revision resolves it as
**top-level**, matching the one precedent this codebase has already proven
end-to-end (build, deploy, WinSW service, restart procedure), rather than
inventing a third placement convention for a single component. Binding
properties, same as `ADR-046` Decision 1 for the reasons that transfer:

- Owns no domain aggregate, entity, or table. No migration.
- Absent from `backend/settings.gradle.kts`'s `include(...)` list.
- Not added to `MODULE_STRUCTURE.md` Section 3.
- Publishes and consumes no domain event; absent from the RabbitMQ topology.

**Reserved-name check (`ADR-046` Decision 1's own list):** `ai-advisor` does
not collide with `operations`/`observability`/`monitoring`/`reporting`/
`analytics` — the reserved names `MODULE_STRUCTURE.md` Section 8's gate
protects. No change needed here.

### 3. Read → Analyze → Report only. No write capability exists, at the type level, not only by convention.

Per this task's own explicit instruction. `ai-advisor`:

- Holds no `RestTemplate`/`WebClient` pointed at any domain module's `POST`,
  `PUT`, `PATCH`, or `DELETE` path — and, per Decision 1, holds no
  `RestTemplate`/`WebClient` pointed at any domain module at all, which is a
  strictly stronger guarantee than the 2026-08-14 draft's "never configured
  toward a write method."
- Its only outbound calls are to the configured AI provider's own chat/
  completion endpoint — never `dispatch`, `order-management`,
  `driver-management`, `passenger-experience`, or `identity`.
- `AIProvider.analyze` returns text/structured findings only. Its return
  type has no field, method, or side channel through which a caller could
  ask it to perform an action — there is no `AIProvider.act(...)`, and
  adding one is explicitly out of this ADR's scope (mirrors `ADR-046`
  Decision 7's own "what this ADR does not authorize" pattern): `AI →
  DELETE`, `AI → UPDATE`, `AI → ACCEPT`, `AI → CANCEL`, `AI → ASSIGN` are
  all structurally impossible, not merely unimplemented.

### 4. Owner authentication is replicated into this sixth process, unchanged in mechanism.

`ai-advisor` verifies `POST /v1/advisor/analyze`'s `Authorization` header
using the same Basic-auth check (constant-time comparison against
`pios.owner.password-hash`/`pios.owner.password-salt`, same PBKDF2
parameters) the five domain modules' own `OwnerCredentialGate` already
implement — a sixth replica, not a new mechanism, exactly as the 2026-08-14
draft already decided and as `ADR-044` Decision 3 already established the
precedent for. No second login, no session, no token.

**Disclosed cost, unchanged from every existing replica:** the owner
credential now exists in six configuration locations instead of five. This
is `ADR-044`'s own already-accepted debt, not a new one introduced here.

### 5. Request DTO — mirrors `PilotAnalyticsInput` field-for-field, no translation ambiguity.

```kotlin
data class PilotAnalysisRequest(
    val generatedAt: String,               // ISO-8601 instant, browser's own clock
    val periodLabel: String,
    val orders: OrderMetrics,               // total, completed, cancelled, open
    val proposals: ProposalMetrics,         // total, accepted, declined, lapsed, withdrawn, open
    val assignments: AssignmentMetrics,     // total, completed, inProgress
    val drivers: DriverMetrics,             // total, available, withActivity
    val reactionTime: ReactionTimeMetrics,  // averageMinutes, medianMinutes, sampleSize (all nullable except sampleSize)
    val health: HealthMetrics               // modulesUp, modulesTotal
)
```

Field names, types, and nullability match
`frontend/src/pages/OwnerControlCenter/pilotAnalytics.ts`'s own
`PilotAnalyticsInput` exactly — chosen deliberately so the mapping is
mechanical, not interpretive, and so a future schema check (Decision 8) can
validate structurally rather than semantically.

### 6. Response DTO — an explicit outcome, never an implicit one.

```kotlin
data class PilotAnalysisResponse(
    val outcome: String,   // "ok" | "provider_unavailable" | "budget_exceeded" | "invalid_input"
    val result: PilotAnalysisResultDto?,  // present only when outcome == "ok"
    val message: String?   // present when outcome != "ok"; honest, non-technical, per PRODUCT_DECISION_CONTROL_CENTER_AI.md Section 12
)

data class PilotAnalysisResultDto(
    val status: String,           // "ok" | "attention" | "critical" | "unknown"
    val summary: String,
    val keyFindings: List<String>,
    val risks: List<String>,
    val recommendations: List<String>,
    val metrics: PilotAnalysisMetricsDto,  // acceptanceRate/completionRate/cancellationRate, each nullable
    val generatedAt: String,
    val providerName: String
)
```

Mirrors `frontend/src/pages/OwnerControlCenter/aiProvider.ts`'s own
`PilotAnalysisResult` exactly — `AIAnalystCard.tsx` already renders this
shape today against `MockAIProvider`'s output; pointing it at a real
`outcome == "ok"` response requires no UI change.

**How Owner Control Center already distinguishes "AI недоступен" from
"данных недостаточно" — this was solved by the Mock stage already, not left
open:**

- `outcome != "ok"` (network failure, provider timeout, 401/402/429/5xx from
  the provider, budget exceeded) → `AIAnalystCard.tsx`'s existing `'error'`
  state ("Не удалось выполнить анализ. Попробуйте ещё раз.") — this is "AI
  is not answering right now."
- `outcome == "ok"` with `result.status == "unknown"` → the existing
  `'ready'` state, rendering `MockAIProvider`'s own "Недостаточно данных для
  анализа" summary — this is "AI answered, and the honest answer is that
  there is nothing to analyze yet." A real provider inherits this
  distinction for free: `ai-advisor` still computes `orders.total == 0`
  itself (or forwards that determination) before ever prompting the model,
  so "no data" is decided by code, never by asking the model to notice it
  was given nothing.

### 7. Provider abstraction — `AIProvider` is the same interface the Mock stage already defined, now with a real implementation requirement.

```kotlin
interface AIProvider {
    val name: String
    fun analyze(input: PilotAnalysisRequest): AIProviderOutcome
}

sealed interface AIProviderOutcome {
    data class Success(val result: PilotAnalysisResultDto) : AIProviderOutcome
    data class Failure(val reason: FailureReason, val providerStatusCode: Int?) : AIProviderOutcome
}

enum class FailureReason { UNAVAILABLE, RATE_LIMITED, QUOTA_EXCEEDED, AUTH_FAILED, TIMEOUT, MALFORMED_RESPONSE }
```

"Never throws, returns outcomes as values" — the same pattern
`healthPoll.ts`'s `fetchModuleHealth` already established for the frontend,
now on the backend side too (2026-08-14 draft's Decision 6, unchanged).
`providerStatusCode` exists for logging only (Decision 11) — it is never
forwarded to the browser as anything more specific than
`PilotAnalysisResponse.outcome`, so the owner is never shown a raw HTTP
status from a system they do not operate.

### 8. Cost and abuse controls — sized to match `PilotAnalyticsInput`'s own already-bounded shape.

- **Prompt size is structurally bounded, not policed.** `PilotAnalyticsInput`
  carries counts, rates, and durations only — no per-order or per-driver
  text, no list that grows with pilot volume (unlike, say, a raw event
  feed). The prompt `ai-advisor` builds from it is therefore `O(1)` in size
  regardless of how large the pilot cohort grows. This is a design property
  already true today, not a control to add later.
- **Request body size limit.** `ai-advisor` still rejects (`invalid_input`
  outcome, HTTP 400) any request body exceeding a small fixed ceiling (e.g.
  8 KB — generous for the DTO in Decision 5, tight enough that no malformed
  or malicious oversized body reaches prompt-building). Defense in depth,
  not a control this DTO is expected to ever approach.
- **Provider call timeout.** A fixed timeout (recommended starting value:
  30s — longer than `FAN_OUT_REQUEST_TIMEOUT_MS`'s 10s, since a completion
  call is not a simple `GET`) on the outbound call to the provider. On
  expiry: `AIProviderOutcome.Failure(TIMEOUT)`, mapped to
  `outcome: "provider_unavailable"`. Configurable (`pios.ai-advisor.<provider>.timeout-ms`).
- **No automatic retry, anywhere in this component.** A failed or
  timed-out provider call returns failure immediately. The owner's own
  button click is the only retry mechanism (this task's own explicit
  requirement — "отсутствие автоматических повторов, которые могут создать
  неконтролируемые расходы"). This also means `ai-advisor` never needs
  idempotency-key logic: every call is already a fresh, owner-initiated
  action, not a system-retried one.
- **Rate limit / minimum interval between calls.** `ai-advisor` enforces a
  minimum interval between successive `POST /v1/advisor/analyze` calls
  (recommended starting value: 10s — comfortably above normal double-click
  timing, comfortably below "the owner genuinely wants to re-check").
  Enforced the same way `OwnerCredentialGate`'s own failure-window rate
  limiter already works: an in-memory, per-process, rolling counter —
  resets on restart, single-instance-only, the same disclosed limitation
  `ADR-044` Decision 8 already accepts for that gate. A call inside the
  window returns `outcome: "budget_exceeded"` with a message naming the
  wait, not a silent drop.
- **Daily/monthly budget guard.** An in-memory counter of successful calls
  (`AIProviderOutcome.Success` only — a failed call that never reached the
  provider should not count against budget) compared against a configured
  ceiling (`pios.ai-advisor.budget.max-calls-per-day`,
  `pios.ai-advisor.budget.max-calls-per-month`). Exceeding either returns
  `outcome: "budget_exceeded"` before any provider call is attempted — the
  guard is checked *before* Decision 1 step 3 (prompt building), not after,
  so a budget-exceeded request costs nothing at all, not even a wasted
  provider call. Same in-memory, resets-on-restart limitation as the rate
  limiter above — acceptable at pilot scale (a handful of owner-initiated
  clicks per day), named rather than hidden.
- **No streaming for this MVP.** Decision 9 (streaming) explains why a
  single blocking request/response is sufficient and simpler to bound in
  cost and latency than a streamed one.

### 9. Provider comparison — technical only, no pricing claim.

Compared strictly on integration shape, per this task's own instruction not
to select by unconfirmed cost:

| Dimension | DeepSeek API | Claude API (Anthropic) |
| --- | --- | --- |
| Auth | `Authorization: Bearer <key>` | `x-api-key: <key>` + `anthropic-version` header |
| Endpoint shape | OpenAI-compatible: `POST /v1/chat/completions` | Anthropic-native: `POST /v1/messages` |
| Request shape | `messages: [{role, content}]`, `model`, `temperature`, etc. — OpenAI-compatible | `messages: [{role, content}]` with `system` as a separate top-level field, not a message role |
| Response shape | `choices[0].message.content`, `usage.{prompt,completion,total}_tokens` | `content[0].text` (content is a block array), `usage.{input,output}_tokens` |
| Structured/JSON output | `response_format: {type: "json_object"}` (OpenAI-compatible convention) | Tool-use / forced tool-choice with a JSON schema, or plain-text instruction |
| Streaming | Supported (SSE, `stream: true`) | Supported (SSE) |
| Error shape | HTTP status + JSON error body (401/402/429/5xx observed directly this session) | HTTP status + JSON error body (`type`, `message`) |
| Timeout handling | Client-side only — no server-declared budget in the response | Client-side only, same shape |
| Streaming needed for this MVP? | **No** — Decision 8 already bounds prompt/response size small enough that a blocking call is simpler and easier to cost-bound. Streaming would complicate the budget guard (a call could be cancelled mid-stream, complicating "did this count against budget") for no user-visible benefit at this response length. | Same reasoning, same answer: no. |
| Swappable behind `AIProvider` without a frontend change? | **Yes** — `DeepSeekProvider` implements `AIProvider`, translates `PilotAnalysisRequest` into DeepSeek's own chat-completion shape internally, translates the response back into `PilotAnalysisResultDto` internally. No caller sees DeepSeek's own wire shape. | **Yes**, symmetric to DeepSeek's own row — `ClaudeProvider` does the equivalent translation for Anthropic's `/v1/messages` shape. |

**Structured output is the one integration detail that matters most for
this MVP**, since `PilotAnalysisResultDto` has a fixed shape
(`status`/`summary`/`keyFindings`/`risks`/`recommendations`) the provider's
own free-text answer must be coerced into. Both providers support a path to
this (DeepSeek's `response_format`, Claude's forced tool-use) — neither
requires prompt-parsing heuristics to extract structure from prose, which
this ADR should require of whichever `*Provider` is built first: the
provider's own structured-output mechanism, not regex over free text.

**This section does not choose a provider.** The already-observed DeepSeek
`402 Payment Required` (this session, 2026-08-17, on a real key) is an
account/billing fact, not a technical one, and is explicitly excluded from
this comparison per this task's own instruction.

### 10. Failure modes — every one is a returned value, never a fabricated answer.

| Condition | `AIProviderOutcome` | `PilotAnalysisResponse.outcome` | Owner sees |
| --- | --- | --- | --- |
| Provider unreachable / DNS / TLS failure | `Failure(UNAVAILABLE, null)` | `provider_unavailable` | "Не удалось выполнить анализ. Попробуйте ещё раз." (existing `AIAnalystCard.tsx` `'error'` state) |
| Provider call exceeds timeout | `Failure(TIMEOUT, null)` | `provider_unavailable` | same as above |
| Provider 401 (bad/expired key) | `Failure(AUTH_FAILED, 401)` | `provider_unavailable` | same as above — the owner is never told "the API key is wrong," which would invite them to go looking for it |
| Provider 402 (no balance) | `Failure(QUOTA_EXCEEDED, 402)` | `provider_unavailable` | same as above |
| Provider 429 (rate limited) | `Failure(RATE_LIMITED, 429)` | `provider_unavailable` | same as above |
| Provider 5xx | `Failure(UNAVAILABLE, 5xx)` | `provider_unavailable` | same as above |
| Provider responds, but not in the expected structured shape | `Failure(MALFORMED_RESPONSE, 200)` | `provider_unavailable` | same as above — a malformed answer is treated as no answer, never partially trusted |
| `ai-advisor`'s own rate limit / budget guard triggered | *(provider never called)* | `budget_exceeded` | a distinct message naming the guard, so the owner is not left thinking the provider itself failed |
| Malformed/oversized request body | *(provider never called)* | `invalid_input` | generic error — this should not normally happen from `AIAnalystCard.tsx`'s own well-typed request, and signals a client/backend version mismatch if it does |
| `orders.total == 0` in the input | provider is called or not, per Decision 6 | `ok`, `result.status == "unknown"` | "Недостаточно данных для анализа" — an answer, not a failure |

**Every row degrades gracefully: the rest of Owner Control Center (health,
counters, event feed) is untouched by any of them**, exactly as
`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` Section 12 requires and as
`healthPoll.ts`'s own partial-failure tolerance already models for the rest
of the console.

### 11. Observability — log file only, no database.

- `ai-advisor` logs each analysis request (timestamp, `outcome`, provider
  name, latency, `providerStatusCode` where present) to its own rolling log
  file, following the `../logs/<module>.log` convention (T-1,
  `ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md`) — same
  choice the 2026-08-14 draft already made (Decision 8), unchanged.
- **The log records the request's numeric metrics and the response's
  findings/risks/recommendations text, never the provider API key, in any
  form, at any log level.**
- **No database.** This revision makes explicit what the 2026-08-14 draft
  left unstated: `ai-advisor` has no PostgreSQL schema, no migration, no
  table — the same property `ADR-046` Decision 1 states for `platform-ops`,
  for the same reason (it owns no domain data, and persisting analysis
  *history* — trend-over-time, "was last week's cancellation rate higher" —
  is a genuinely new capability nobody has asked for and this task's own
  instruction forbids new migrations for). A rolling log file already
  satisfies the audit requirement (`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`
  Section 13) without one.

### 12. Threat model

| Threat | Mitigation | Residual risk, disclosed |
| --- | --- | --- |
| **Provider API key leakage** | Key lives only in `ai-advisor`'s own deployment configuration (registry `Environment`, same mechanism as `pios.owner.password-hash` — `ADR-044` Decision 2's precedent). Never in frontend code, bundle, or any `VITE_*` variable — structurally absent from the frontend build, not merely undocumented there. Never logged (Decision 11). Never returned in any `ai-advisor` HTTP response body. | If the machine itself (registry, or the `ai-advisor` process's own memory) is compromised, the key is exposed — the same residual risk `pios.owner.password-hash` already carries and that no application-layer control can close. |
| **Prompt injection via order/event text** | **Structurally prevented at this MVP's scope**: `PilotAnalyticsInput`/`PilotAnalysisRequest` carries no free-text field from any passenger, driver, or order record — no `passengerName`, no `pickupAddress`, no event description reaches the prompt at all (see Non-goals). There is nothing in the request a passenger or driver could set to inject instructions into. | **Becomes a real, unmitigated risk the moment free-form owner Q&A (Non-goals, deferred) or any raw event text is added to a future prompt** — a passenger's own `passengerName` field, for instance, already flows unsanitized into `todayData.ts`'s `EventFeed` text today. That future work must treat any such text as untrusted data, not instruction (e.g. explicit prompt structure separating "context data" from "instructions," per whichever provider's own recommended pattern) — named here as a prerequisite for that later ADR, not solved by this one. |
| **PII leakage to the provider** | Same structural fact as above: the request contains no name, phone, address, or free text of any kind — only counts, rates, and durations. Nothing to redact because nothing sensitive is collected in the first place. | None identified at this MVP's scope. Re-evaluate the moment any free-text field is added to the request shape. |
| **Excessive API usage / cost** | Decision 8 in full: bounded prompt size, per-call timeout, no auto-retry, minimum-interval rate limit, daily/monthly budget guard checked before any provider call. | The budget guard is in-memory and resets on `ai-advisor` restart — a restart mid-day resets the daily counter early. Acceptable at pilot scale (single owner, a handful of clicks/day); a persistent counter would need the database this ADR deliberately does not add. |
| **Repeated/duplicate requests (accidental double-click, or a stuck client retrying)** | No auto-retry in `ai-advisor` itself (Decision 8) plus the rate-limit minimum interval (Decision 8) — a double-click within the interval returns `budget_exceeded` for the second call rather than triggering a second provider call. `AIAnalystCard.tsx`'s own button is already `disabled` while `state === 'loading'` (existing frontend code, unchanged), which prevents the common case client-side too. | A client bypassing the disabled button (e.g. a direct API call, not through the UI) still hits the server-side rate limit — defense in depth already covers this, not merely the UI guard. |
| **Compromise of Owner Control Center's own credential** | Unchanged from `ADR-044`'s own threat model: a stolen owner credential can already read everything Owner Control Center reads. This ADR adds the ability for that stolen credential to also trigger AI analysis calls — bounded in cost by Decision 8's budget guard, and structurally incapable of writing anything (Decision 3). | The blast radius of a stolen credential grows from "read PIOS data" to "read PIOS data, plus consume up to the daily/monthly AI budget" — a real but bounded, disclosed increase, not an open-ended one. |
| **Spoofed/fabricated `PilotAnalyticsInput`** | Named explicitly in Decision 1: `ai-advisor` trusts the authenticated caller's own request body, the same trust level `buildReport.ts` already extends to browser-computed data. No independent re-verification against a live backend read. | The only party who can exploit this is the owner themselves, against their own analysis — no meaningful blast radius, since nothing downstream reads or acts on the result (Decision 3). Not mitigated further, deliberately, to avoid reintroducing the cross-module fan-out Decision 1 exists to avoid. |

## Provider abstraction — rollout plan

1. **This ADR, ratified.** No code.
2. **`ai-advisor` scaffolded**: empty Spring Boot app, `OwnerCredentialGate`
   replica, `POST /v1/advisor/analyze` wired to a `MockAIProvider` moved
   server-side (identical rules to today's frontend `MockAIProvider`, so
   behavior is unchanged for the owner while the transport moves) — proves
   the DTO contract and deployment shape before any real provider exists.
   `AIAnalystCard.tsx` is repointed from `getActiveAIProvider()` (local) to
   `POST /v1/advisor/analyze` (remote) — this is the one frontend change
   this whole ADR requires, and it is a call-site change, not a UI rewrite.
3. **One real `*Provider`** (whichever has a funded account first —
   Section 9 does not select) implemented and wired in behind a
   configuration flag, tested against `outcome != "ok"` paths first
   (unreachable, wrong key, timeout) before the happy path.
4. **Pilot use**, budget guard values tuned from real observed call volume
   (Decision 8's own starting values are defaults, not final).

## Alternatives Considered

Everything the 2026-08-14 draft already rejected (calling the provider
directly from the browser; five replicated endpoints; scaffolding the
Analytics Module now; a provider type leaking into the request handler) is
rejected here for the same reasons, unchanged, and not repeated in full.

**New alternative this revision considered and rejected:** *Keep the
2026-08-14 design (`ai-advisor` reads the five modules itself) and accept
the `ADR-043` Decision 1 conflict as a disclosed, deliberate exception.*
Rejected — `ADR-043` Decision 1 is a ratified constraint (`ADR-046` Decision
3 already built an entire component, `platform-ops`, specifically to avoid
violating it); creating a second exception to it for `ai-advisor` when
Decision 1 above shows the exception is unnecessary (the browser already
has the data) would be choosing complexity with no corresponding benefit.

## Pilot boundary

Per `docs/PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md`'s own governing principle
(validate before building further): this ADR's rollout plan Step 2
(`MockAIProvider`, server-side, no real provider) is sufficient to validate
the transport/contract/budget-guard machinery **before** any provider
credential is purchased or configured. Nothing about Steps 3–4 is required
for the pilot's own core loop (already validated end-to-end,
`af5f9dd`'s own predecessor sessions) to continue functioning — AI Analyst
remains an optional, owner-triggered surface the rest of Owner Control
Center does not depend on, at every stage of this rollout.

## Open questions

1. **Exact model name and provider selection** — deferred to whichever
   Product Owner decision funds a provider account (Section 9 deliberately
   does not choose).
2. **Whether `ai-advisor`'s hostname needs its own ingress**, mirroring
   `ADR-048`'s own treatment of `platform-ops` — `ai-advisor` is only ever
   called from the browser at `/owner`, which already reaches the frontend
   over the existing `pios.<domain>` path; unlike `platform-ops`,
   `ai-advisor` does not need to survive the frontend's own failure (if the
   frontend is down, there is no button to click). **Recommendation: route
   `POST /v1/advisor/analyze` through the existing `preview.proxy` table
   (a twelfth-ish row, alongside the five `/v1/health/<module>` rows
   `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Variant A already added) rather
   than a separate hostname** — `ADR-048` Decision 1's own argument against
   routing *control* through `vite preview` does not apply here, since
   `ai-advisor` is not asked to restart anything, including `vite preview`
   itself. This is this revision's own view, not a ratified decision — an
   Architect should confirm before Step 2 of the rollout plan.
3. **Whether a second provider (fallback on failure) is ever worth
   building** — Section 9's own comparison stays silent on this by design;
   recommendation is **no**, for v1: it doubles credential/cost surface for
   a pilot-scale, owner-initiated, human-retryable feature where Decision
   10's honest-failure behavior already has no bad outcome for the owner to
   avoid.
4. **Exact request-size and timeout constants** (Decision 8's 8 KB / 30s /
   10s starting values) — recommended defaults, not load-tested; confirm or
   revise once Step 2 of the rollout plan is live and real latency is
   observed.

## Traceability

| Subject | Source |
| --- | --- |
| Product-level role, boundary, and constraints this ADR implements | `docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md`, all sections |
| Owner Control Center's current, unamended scope | ADR-043, ADR-044 |
| The fan-out prohibition this revision resolves | ADR-043 Decision 1; ADR-046 Decision 3 |
| Deployment-shape precedent (independent process, no domain data, own service) | ADR-046 Decision 1 |
| Why platform-ops's *data-access* shape does not transfer | ADR-046 Decision 3 (own reasoning re-read for this revision) |
| Reserved module names this component does not collide with | ADR-046 Decision 1 |
| Ingress precedent considered for the open question above | ADR-048 Decisions 1–2 |
| Owner credential replication precedent | ADR-044 Decisions 2–3, 8 (rate limiter) |
| "Never throws, returns outcomes as values" pattern, backend side | frontend/src/pages/OwnerControlCenter/healthPoll.ts, `fetchModuleHealth` |
| The already-built frontend half this ADR audits against | `frontend/src/pages/OwnerControlCenter/{pilotAnalytics,aiProvider,AIAnalystCard}.{ts,tsx}`, commit `af5f9dd` |
| Pilot-validation-before-build principle | docs/PRODUCT_DECISION_MVP_PILOT_BOUNDARY.md |
| Backend technology (Kotlin/Spring Boot), unchanged | ADR-022, ADR-023 |

## Files Changed

This ADR only, revised in place (superseding its own 2026-08-14 text, not
deleting it — the file's own git history preserves that version).
`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` is read and cited, not edited —
nothing in this revision changes that document's own business-level
decisions, only the technical architecture that satisfies them. No source
file, build file, `.env`, or Git configuration is touched. `ai-advisor` is
not scaffolded by this document.
