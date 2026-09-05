# PIOS Current State Audit

**Date:** 2026-09-04. **Method:** read-only inspection — git, source, tests, docs, one already-committed prior self-audit (`PIOS_REALITY_AUDIT.md`, dated 2026-09-02, HEAD `b550ed4` at the time) cross-checked against the current HEAD (`b701c6c`, one commit later) and the current uncommitted working tree. Every claim below is either independently verified this session (marked as such) or explicitly attributed to the prior audit as inherited, not re-verified. Marked **UNCERTAIN** wherever evidence was insufficient. No file was modified, staged, committed, or deleted in the course of this audit except this report itself.

**Read this first — two facts change the shape of everything below:**
1. **A prior, already-committed, very thorough self-audit (`PIOS_REALITY_AUDIT.md`, repo root) already exists**, dated two days before this one. This audit does not repeat its work — it verifies which of its findings still hold, updates what has changed since, and adds what it could not have covered (the design-system work, a live AI Advisor environment-variable issue, and the current uncommitted working tree).
2. **The single most severe finding in this audit is operational, not architectural**: a real, already-written, already-tested authorization fix for a live IDOR-class gap on the dispatch/driver mutation endpoints exists **only in the uncommitted working tree** — not in any commit, and therefore, on the balance of evidence, not in whatever is currently deployed to the pilot. See Section 6, Finding F-1.

---

## 1. Executive Summary

- **Branch `pios-product-main`, HEAD `b701c6c`** ("complete first refusal runtime integration", 2026-09-03), up to date with `origin`. Working tree has 37 modified files + ~75 untracked entries, spanning **four unrelated bodies of work** mixed together (design-system migration, dispatch/driver-management/order-management auth remediation, AI Advisor Ollama/Qwen providers, Owner Control Center trend analytics) plus a pile of ~24 untracked docs from an unrelated earlier task thread (Cloudflare tunnel diagnostics).
- **The commit's own message self-discloses it is not V1-complete** and names three unresolved items; this audit independently confirms one is fixed (`explicitDriverIntent` is genuinely sent by `RideRequest.tsx`), one remains genuinely open (redelivery-after-resolution idempotency), and the third — the Proposal/Assignment caller-identity authorization gap — has a **written, tested fix sitting uncommitted**, which is the audit's top finding.
- **PIOS is a real, running, multi-module system**, not a scaffold — six Kotlin/Spring Boot backend modules, a React/TypeScript SPA, real Postgres persistence, real RabbitMQ outbox pipelines, a completed real pilot ride recorded in production data (per the prior audit, independently unverified here). Confirmed still true.
- **The prior audit's #1 critical risk — backend tests writing into production-named Postgres databases — is independently verified FIXED in the current codebase**: all five `PostgreSQLTestDatabase.kt` files now point at `_test`-suffixed database names (checked directly, this session). Whether those `_test` databases actually exist with correct schemas, and whether the full suite now passes cleanly, was **not** re-verified by actually running the backend suite — a deliberate decision, explained in Section 5.
- **Frontend: `npm run build` currently fails** (`tsc -b` error in `aiProvider.test.ts`, unrelated to design work — a type mismatch introduced by the uncommitted `pilotAnalytics.ts` change). `vitest` is clean: 228/228. `oxlint` is clean.
- **`ai-advisor` backend: 104 tests, 1 failed** — root-caused to a real environment condition on **this specific host**, not a code defect: the OS-level environment variable `PIOS_AI_ADVISOR_PROVIDER` is set (confirmed present, value not printed), which means the "safe by default" Mock-provider guarantee the checked-in `application.yml` documents **does not actually hold on this machine** — this host's real `ai-advisor` process, if started here, inherits that same environment variable.
- **Design system work is real and substantial** (contradicting the prior audit's own "zero CSS custom properties" claim, which predates this work): a working token system + 12 new components + 2 fully migrated screens (`PassengerLanding`, `RideRequest`) exist, uncommitted. Coverage is 14/29 CSS modules (48%); `DriverHome` — the driver's own working screen — remains unmigrated.
- **Security is uneven and, for one class of endpoint, currently exploitable if today's deployed pilot matches HEAD**: `dispatch`'s Proposal accept/decline, Assignment arrive/start/complete, and `driver-management`'s availability toggle have **no committed caller-identity check** — any caller who can reach these ports and knows/guesses an id can act as any driver. A correct, tested fix exists in the uncommitted working tree only.
- **`network-management` remains fully built, fully tested, and wired to nothing** except an internal test harness — unchanged from the prior audit.
- **Tailscale Funnel (`https://home-pc.tail385153.ts.net`) is confirmed working right now** (fresh `curl` this session: HTTP 200). Cloudflare Tunnel remains the previously, extensively diagnosed broken path (documented in `docs/PIOS_TAXI_TASK_31`–`44_*.md`); this audit did not re-touch or re-diagnose it, per this task's own explicit instruction.
- **Documentation volume is large (97+ files, 61+ ADRs) and visibly lags implementation** in specific, named ways — `README.md` self-contradicts on backend module count and omits `ai-advisor`/`platform-ops`/`network-management` entirely.
- **Repository hygiene has real, fixable problems**: a stray 12KB config backup file, generated knowledge-graph output, and rotated runtime logs are all untracked-but-present in the repo root, and a real, security-relevant infrastructure script (`windows-services/cloudflared/run-tunnel.ps1`) is, oddly, not tracked in git at all despite being referenced as if it were.

## 2. Repository / Git State

```
Branch:            pios-product-main (up to date with origin/pios-product-main)
HEAD:              b701c6c "feat(pios-taxi): complete first refusal runtime integration" (2026-09-03 15:58:41 +05:00)
Working tree:      not clean — 37 modified (unstaged), 0 staged, ~75 untracked
```

**A. Current branch:** `pios-product-main`.
**B. Current HEAD:** `b701c6c`. Its own commit message explicitly states: *"This does not represent completion of PIOS Taxi V1 as a product... Known, disclosed, unresolved items carried forward unchanged by this checkpoint: the pre-existing Proposal accept/decline/lapse caller-identity authorization gap; no real frontend caller yet sets `explicitDriverIntent=true`; the narrow redelivery-after-resolution idempotency gap."*
**C. Working tree contains unfinished work — yes, confirmed, four distinct strands:**

| Strand | Representative files | Committed anywhere? |
|---|---|---|
| Design-system migration | `frontend/src/index.css`, `frontend/src/styles/tokens.css` (untracked), 12 new component dirs (untracked), `PassengerLanding.*`, `RideRequest.*`, 6 new design docs (untracked) | No |
| Dispatch/driver-management/order-management auth remediation (Task 20–25) | `AssignmentController.kt`, `ProposalController.kt`, `DriverController.kt`, `OrderCancellationController.kt` + tests, `SessionTokenVerifier.kt` (new, untracked, driver-management) | No — see Finding F-1 |
| AI Advisor Ollama/Qwen providers + trend-context fix | `ai-advisor/**` (4 modified + 8 untracked files) | No |
| Owner Control Center trend analytics | `pilotAnalytics.ts`/`.test.ts` | No |

**D. Related to the recent PIOS Taxi design work:** the 6 modified + 19 untracked files in the "Design-system migration" row above — confirmed by reading every one of them this session (see Section 4).
**E. Related to Cloudflare/Tailscale/production deployment:** **none** of the currently uncommitted changes touch Cloudflare/Tailscale config directly. `windows-services/cloudflared/run-tunnel.ps1` is untracked but its own content (read this session) is unchanged deployment tooling, not a new change. `frontend/src/identity/BackendIdentityProvider.ts`'s diff switches `IDENTITY_BASE_URL` to same-origin routing "so the preview server (vite preview) can proxy `/v1/identities`" — this **is** deployment-adjacent (affects how the built frontend reaches the identity backend in the pilot's `vite preview` setup) though not Cloudflare/Tailscale-specific.
**F. Unrelated or potentially accidental:** the **~24 untracked `docs/PIOS_TAXI_TASK_17`–`44_*.md` files** (Cloudflare tunnel diagnostics, security audit reports from a separate task thread within this same overall session) and `docs/CLAUDE_CODE_ENVIRONMENT_AUDIT.md` — none relate to the design work, the auth remediation, or the AI Advisor work currently in the tree; a plain `git add -A && git commit` would sweep all of it into one commit alongside the other three strands.
**G. Generated/backup/log/temp artifacts that should NOT enter the repo** — confirmed present, all untracked:

| Path | What it is | Should be tracked? |
|---|---|---|
| `.claude/settings.json.graphify-bak` | 12,412-byte backup of a config file, dated Aug 16 | No — stray backup |
| `graphify-out/` | Generated knowledge-graph output (dated snapshot dirs, `graph.json`, `graph.html`, `cache/`) | No — build artifact |
| `logs/` | Real runtime logs (`ai-advisor.log` + a rotated `.gz`) | No — runtime output |
| `.claude/skills/` | Local Claude Code tooling config | Project-dependent, not application code |

**Nothing was staged or committed during this audit.**

## 3. What Is Actually Implemented

This section states only what changed relative to, or was not covered by, `PIOS_REALITY_AUDIT.md` (2026-09-02) — that document's own Sections 3–18 remain the fuller reference and are not restated wholesale here.

**Frontend.** Everything the prior audit described (routes, pages, component inventory) still holds structurally. **New since that audit, independently verified this session:** a real design-token system (`frontend/src/styles/tokens.css`) and 12 new reusable components, with `PassengerLanding` and `RideRequest` fully migrated onto them (Section 4). The prior audit's own claim — *"No design-token system: zero CSS custom properties in the entire frontend"* — is now **stale**, not because that audit was wrong, but because real work landed since it was written.

**Backend.** Structurally unchanged from the prior audit's six-module architecture, with two additions confirmed this session: (1) `dispatch` now owns a `Trip` aggregate and a `FirstRefusalApplicationService`/`PrimaryConnection` projection (per HEAD's own commit message — not independently re-derived from source in this pass, cited as committed fact); (2) the backend test/production database isolation gap that prior audit flagged as its single highest-severity live risk is **independently confirmed fixed** — all five `PostgreSQLTestDatabase.kt` files now target `_test`-suffixed database names (verified by direct `grep` this session, not inherited from the prior document).

**AI Advisor.** Structurally as the prior audit described (Mock default, DeepSeek/Ollama/Qwen as opt-in providers, fails closed on a blank key), **plus one materially new finding this session did not have available before**: this specific host's OS environment already carries `PIOS_AI_ADVISOR_PROVIDER` (and paired Qwen key/model variables), meaning the "Mock is always the safe default" claim, true of the checked-in config file, is **not true of this host's actual runtime environment**. See Finding F-4.

**Security.** Structurally as the prior audit described (uneven — some endpoints Bearer-gated with real ownership checks, others open), **plus the material update that the fix for the exact gap that audit flagged is now written and tested, sitting uncommitted**. See Finding F-1.

**Deployment.** No infrastructure was touched or changed by this audit. Tailscale Funnel confirmed reachable via a fresh, read-only `curl` this session (HTTP 200). Cloudflare Tunnel's status is unchanged from the extensive prior diagnostic work (`docs/PIOS_TAXI_TASK_31`–`44`) — not re-diagnosed here, per this task's explicit scope limit.

## 4. Design System Audit

### Requirement matrix

| Requirement | Documented | Implemented | Actually Used | Tested | Status |
|---|---|---|---|---|---|
| `tokens.css` defines full token set (`PIOS_DESIGN_SYSTEM.md` §3) | Yes | Yes (143 lines, matches spec) | Yes (imported by `index.css`) | N/A (CSS) | **COMPLETE** |
| Foundation components (`Button`, `Card`, `Text`, `Heading`, `Input`, `FormField`, `Divider`, `IconButton`, `LoadingState`, `ErrorState`, `StatusMessage`) | Yes (`PIOS_DESIGN_SYSTEM.md` §5) | Yes, all 11 exist | Yes, on 2 screens | Yes (`RideRequest.test.tsx`/`PassengerLanding.test.tsx` pass unmodified in logic) | **COMPLETE** |
| `DriverTrustIndicator` — no numeric/rating/rank prop | Yes (`PIOS_DESIGN_SYSTEM.md` §9) | Yes | Yes, 2 call sites | Yes | **COMPLETE** — independently verified by reading the component's own props: `name`, `availability?`, `isPrimary?`, `emphasis?`, `showAvatar?` only |
| Accent color (`--pios-color-accent`) ratified by Product Owner | Yes (`PIOS_DESIGN_SYSTEM.md` §16, DoD item 2) | No — kept as implementer default, explicitly flagged open in 3 documents | N/A | N/A | **MISSING** (honestly disclosed, not silently skipped) |
| `--pios-color-trust` ratified by Product Owner | Same as above | Same as above | N/A | N/A | **MISSING** (same disclosure) |
| Single shared `RideStatus` component, used identically by driver and passenger (`PIOS_TAXI_DESIGN_BRIEF.md` §13, criterion 3) | Yes | **No** — no such component exists | N/A | N/A | **CONTRADICTORY** — `RideRequest.tsx` renders status via `StatusMessage` + `rideStatusLabel()`/`rideStatusTone()`; `DriverHome.tsx` renders the equivalent fact via hand-written CSS classes (`.proposalOpen`/`.proposalAccepted`/`.proposalResolved`) with no shared component at all |
| `DriverHome` migrated onto tokens | Implied in-scope (brief §6, "Driver experience") | No | No | N/A | **MISSING** — 0 of `DriverHome.module.css`'s rules use `var(--pios-*)` |
| `Coordinator`/`OwnerControlCenter` migrated | Explicitly **out of scope** (brief §4: "internal operational tooling... explicitly outside the driver/passenger experience") | No | No | N/A | **N/A — correctly out of scope, not a gap** |
| `Sheet`, `Dialog`, `RideCard`, `LocationField`, `RouteSummary`, `FareSummary`, `AvailabilityStatus`, `RequestCard`, `ActiveRidePanel`, `BottomNavigation`, `Tabs`, `BackButton` | Named in `PIOS_DESIGN_SYSTEM.md` §5 | No | No | N/A | **MISSING**, explicitly deferred with a stated reason in `PIOS_DESIGN_IMPLEMENTATION_LOG.md` §2, not silently dropped |
| Accessibility (WCAG AA contrast, keyboard/focus verified) | Yes (`PIOS_DESIGN_SYSTEM.md` §16, DoD item 6) | UNCERTAIN | UNCERTAIN | Not verified | **UNCERTAIN** — no contrast-checking or screen-reader tool available this session |
| Touch targets ≥44px | Yes | Yes (`--pios-touch-target-min: 44px`, applied) | Yes | Verified via prior Playwright pass (`PIOS_DESIGN_IMPLEMENTATION_LOG.md`), not re-run this session | **COMPLETE** |

### Narrative findings (task Section 3 questions)

**A/B — genuinely reusable, actually used consistently:** the 11 foundation components, yes — each has one real responsibility and both consuming screens use them the same way (verified by reading both files in full). **C — old ad-hoc styling still exists:** yes, extensively — 15 of 29 `.module.css` files remain fully hardcoded-hex, including `DriverHome`, `DriverCard`, `QRCard`, `ActionButton`, `Header`, `Spinner`, `PasswordInput`, both onboarding overlays, `Coordinator`, `OwnerControlCenter`. **D — internally consistent:** within the migrated subset, yes; across the whole app, no — two visibly different visual languages coexist today. **E/F — mobile/desktop layouts:** both confirmed coherent for the 2 migrated screens via the implementation log's own documented, real Playwright screenshots (mobile 390×844, desktop 1440×900, and a 320px narrow-viewport pass) — not independently re-run this session. **G/H — typography/spacing:** consistent within the token system; the legacy 15 files use their own, separately-consistent-with-themselves pre-token palette (same de-facto hex values the token system itself was built from), so there is no visible clash between old and new screens side by side, only a lack of a single shared mechanism. **I — buttons/interactive elements:** `Button` (new) and `ActionButton` (legacy) are near-identical in shape (both pill, 44px, primary/secondary) but are two separate, non-unified implementations — a genuine duplicated-component finding. **J — loading/error/empty states:** consistent within each generation (legacy screens use `Spinner`+inline `role="alert"` paragraphs; migrated screens use `LoadingState`/`ErrorState`/`StatusMessage`) but not unified across the app. **K — accessibility defects:** none obviously found in the migrated components' own markup (real `<button>`/`<label>` elements, `aria-live` on transient feedback, visible focus rings per token) — **not independently verified against actual computed contrast values (UNCERTAIN)**. **L — duplicated/competing implementations:** two found and worth naming precisely — `Button` vs. `ActionButton` (Section above), and the missing shared `RideStatus` (the two independent status-rendering paths in `DriverHome.tsx` vs. `RideRequest.tsx`, both new — see matrix row above).

## 5. Test / Build Results

| # | Command | Result | Notes |
|---|---|---|---|
| 1 | `npx tsc -b --noEmit` (from `frontend/`) | **FAIL** | 1 error: `src/pages/OwnerControlCenter/aiProvider.test.ts(19,7): error TS2739` — `PilotAnalyticsInput` missing `currentDay`, `history`. Root cause: uncommitted `pilotAnalytics.ts` widened the interface without updating this one test fixture. Unrelated to design work — confirmed via `git diff HEAD -- pilotAnalytics.ts` (adds the two fields) and `git log -1 -- aiProvider.test.ts` (last touched by an earlier, unrelated commit). |
| 2 | `npm run build` (`tsc -b && vite build`) | **FAIL** | Blocked by the same error above; `vite build` is never reached. |
| 3 | `npx vitest run` (from `frontend/`) | **PASS** | 228/228 tests, 24/24 files, 48.87s |
| 4 | `npx oxlint` | **PASS** | 0 errors; 13 pre-existing warnings (`no-unsafe-optional-chaining` in 5 test files, 1 `react-hooks/exhaustive-deps` in `PassengerLanding.tsx:224`), all confirmed to predate the files' current diffs |
| 5 | `cd ai-advisor && ./gradlew.bat test --console=plain -q` | **FAIL** — 104 tests, **1 failed** | Failing test: `ProviderSelectionTest.with no provider property set, MockAIProvider is the only AIProvider bean`. **Root-caused, not merely observed**: `env \| grep PIOS_AI_ADVISOR` on this host confirms `PIOS_AI_ADVISOR_PROVIDER` is genuinely set in this OS user environment (value not printed), alongside `PIOS_AI_ADVISOR_QWEN_API_KEY`/`PIOS_AI_ADVISOR_QWEN_MODEL`. Spring Boot's relaxed environment-variable binding maps this to `pios.ai-advisor.provider`, which `ApplicationContextRunner` (used by this test) picks up from the real OS environment by default — so "no provider property set" is not actually true in this process's environment. **This is not a code regression** — the annotations (`@ConditionalOnProperty`) on `DeepSeekProvider`/`OllamaProvider`/`QwenProvider`/`MockAIProvider` were read directly and are all correctly written. It is a real environment condition with a real consequence — see Finding F-4. |
| 6 | Backend Gradle test suites (`dispatch`, `driver-management`, `order-management`, `passenger-experience`, `identity`, `network-management`) | **NOT RUN** | Deliberate. `PIOS_REALITY_AUDIT.md` §19 documents a real, disclosed incident two days ago: a routine `./gradlew build` run, launched for the same kind of "safe verification" this task authorizes, connected directly to production-named Postgres databases and wrote 76 real-looking rows before being caught — because at that time, test database URLs pointed at production database names. This session independently confirmed (Section 3) that the specific connection strings are now `_test`-suffixed in all five affected modules — a real, positive sign — but did **not** re-run the suite to confirm the `_test` databases actually exist with correct schemas end-to-end, given this task's own explicit "do not modify production infrastructure" instruction and the same incident's own additional lesson that a detached Gradle daemon can persist and reproduce unwanted state even after its launching shell is killed. Recommended as an early, careful follow-up (Section 12), not skipped indefinitely. |

## 6. Functional Findings

**F-1**
Severity: **CRITICAL** (operational, not a code defect — a shipped fix not yet shipped)
Area: Backend security / deployment gap
Evidence: `git diff HEAD -- backend/dispatch/.../AssignmentController.kt backend/dispatch/.../ProposalController.kt backend/driver-management/.../DriverController.kt` shows a real, complete `SessionTokenVerifier`-based Bearer-token authorization check added to `arrive`/`start`/`complete` (Assignment), `accept`/`decline` (Proposal), and `declareAvailability` (Driver) — all correctly ordered (401 before existence is revealed, then 404, then 403 on identity mismatch), all uncommitted. HEAD's own commit message (`b701c6c`) explicitly lists "the pre-existing Proposal accept/decline/lapse caller-identity authorization gap" as still open as of this commit.
Impact: If the currently deployed pilot was built from `git HEAD` (or any commit before this uncommitted diff), any caller who can reach `dispatch`'s and `driver-management`'s ports and knows or guesses a proposal/assignment/driver id can accept or decline another driver's proposal, advance or complete another driver's ride, or flip another driver's on/off-duty status — with no credential at all. This is exactly the "driver/passenger identity spoofing... proposal/assignment manipulation" class this task's own Section 9 asked to be checked for, and it is real, not speculative — confirmed both by this session's own diff reading and by `PIOS_REALITY_AUDIT.md` §7's independent, dated confirmation that this gap "has been true throughout the pilot to date."
Recommended action: Commit and deploy this already-written, already-diffed fix as the highest-priority, lowest-risk next action available (see Section 12) — no new code needs to be written.

**F-2**
Severity: HIGH
Area: Frontend build
Evidence: `npm run build` fails (Section 5, row 2) — `tsc -b` errors on `aiProvider.test.ts` against a `pilotAnalytics.ts` type change.
Impact: The entire working tree — including the finished design-system migration and the F-1 security fix — currently cannot be built into a deployable artifact via the documented command.
Recommended action: Fix the one test fixture (`aiProvider.test.ts`) to match `PilotAnalyticsInput`'s new optional fields, or revert the interface widening if it's not yet needed — a small, isolated, non-design, non-security change; not attempted in this audit per its own read-only scope.

**F-3**
Severity: MEDIUM
Area: Design system completeness / acceptance-criteria conformance
Evidence: `PIOS_TAXI_DESIGN_BRIEF.md` §13 criterion 3 requires "one single, shared visual status-state component used identically" by driver and passenger. No such component exists; `DriverHome.tsx` and `RideRequest.tsx` each independently render the same underlying fact.
Impact: A future status-wording or status-color change must be made in two places by hand, and the two can silently drift out of sync — already true today in a small way (different visual treatments for the same states).
Recommended action: Build the deferred `RideStatus` component and adopt it on both screens — natural next increment of the already-underway migration.

**F-4**
Severity: MEDIUM–HIGH
Area: AI Advisor / operational cost & data-privacy risk
Evidence: `PIOS_AI_ADVISOR_PROVIDER`, `PIOS_AI_ADVISOR_QWEN_API_KEY`, `PIOS_AI_ADVISOR_QWEN_MODEL` are all genuinely set in this host's OS environment (confirmed present via `env | grep`, values not disclosed here). `ai-advisor`'s own checked-in `application.yml` documents Mock as the always-safe default specifically so "the existing pilot never depends on a funded... account" — but Spring Boot's environment-variable binding means this host's real `ai-advisor` process, if and when actually running here, would pick up these variables the same way the test JVM just did.
Impact: The documented safety guarantee ("calling this module today costs nothing and hits no real external API" — `PIOS_REALITY_AUDIT.md` §5) holds only for the checked-in config file, not for this host's actual runtime environment. If the real `ai-advisor` service is (or has been) run on this host with these variables present, it is likely calling a real, paid external API (Qwen/DashScope) with real pilot data (aggregated counts/rates only, per `PilotAnalysisRequest.kt`'s own KDoc — no free text, no PII, confirmed by reading the DTO) rather than the Mock provider a casual reader of `application.yml` alone would assume is active.
Recommended action: Confirm with the Product Owner/operator whether this is intentional (a deliberate switch to real Qwen analysis) or an artifact of past manual testing left in the environment; if unintentional, unset it or make the intended provider an explicit, documented operational decision rather than an inherited shell variable.

**F-5**
Severity: LOW
Area: Frontend component architecture
Evidence: `Button` (new, tokens) and `ActionButton` (legacy, hardcoded hex) coexist with near-identical visual shape and no migration path recorded for the latter's remaining 6+ call sites.
Impact: Ongoing risk of a future screen picking the wrong one by habit, prolonging the two-visual-language state.
Recommended action: Note as a natural target once `DriverHome`'s own migration is scheduled (F-3's sibling); not urgent on its own.

## 7. Security Findings

**S-1** — see **F-1** above (Section 6); duplicated here per the report's own required structure, not a second, different finding.
Classification: **CRITICAL** if the deployed pilot matches HEAD; **already fixed but unshipped** in the working tree.

**S-2**
Severity: HIGH
Area: `network-management` module
Evidence: `PIOS_REALITY_AUDIT.md` §5/§7 (inherited, not independently re-verified this session): all 8 endpoints across `Person`/`PersonProfile`/`Connection`/`Invitation` have no authentication of any kind, no health endpoint, and no `OwnerCredentialGate` at all — the only backend module with zero auth infrastructure.
Impact: Mitigated in practice by the module not being wired into any real product screen (ADR-037) — but the process itself, if running and its port reachable, has zero access control. **Whether port 8085 is reachable from outside this machine (through the Tailscale Funnel proxy or otherwise) is UNCERTAIN** — not established either way this session.
Recommended action: Confirm port 8085's actual network exposure; if reachable externally, this is a live gap regardless of whether the frontend calls it. If not reachable, this is a smaller, "harden before ever wiring it in" item.

**S-3**
Severity: MEDIUM
Area: `driver-management`/`order-management` mutation endpoints (create driver, submit order, cancel order)
Evidence: `PIOS_REALITY_AUDIT.md` §7 (inherited): `POST /drivers`, `POST /orders`, `POST /orders/{id}/cancel` remain unauthenticated by design/audit, distinct from the F-1/S-1 gap (which covers accept/decline/arrive/start/complete/availability, all addressed by the uncommitted fix). `POST /drivers` specifically is a documented, accepted risk (pre-authentication, caller-generated unguessable UUID, same shape as `identity`'s own registration).
Impact: A caller who can reach `order-management`'s port can submit or cancel orders against any driver code without a credential.
Recommended action: Track as a known, disclosed scope boundary (consistent with the prior audit's own framing) rather than treat as newly discovered; prioritize below S-1/F-1.

**S-4**
Severity: INFO (positive finding)
Area: Secret handling
Evidence: `PIOS_OWNER_PASSWORD_SALT`, `PIOS_OWNER_PASSWORD_HASH`, `PIOS_OWNER_USERNAME`, `PIOS_AI_ADVISOR_QWEN_API_KEY` all confirmed present as real OS environment variables on this host (values never printed by this audit) rather than hardcoded in any checked-in file — consistent with `application.yml`'s own documented externalization discipline and `DeepSeekProvider.kt`'s own KDoc ("never logs the API key... never returns the API key"). No hardcoded credential was found in any file read this session.

**S-5**
Severity: INFO
Area: CORS
Evidence: Not independently re-verified this exact session (carried forward from earlier work in this same overall session, referenced but not re-read here): each backend module's own `WebCorsConfiguration` builds its allowed-origin list from `localhost:5173` plus the `PIOS_PILOT_FRONTEND_ORIGIN` environment variable — not a wildcard. Marked **UNCERTAIN** as "currently exactly this" since the file itself was not re-opened in this pass.

## 8. Deployment / Infrastructure Findings

| Item | Status | Evidence |
|---|---|---|
| Tailscale Funnel (`https://home-pc.tail385153.ts.net` → `127.0.0.1:4173`) | **Working** | Fresh `curl` this session: `HTTP 200`. Not modified. |
| Cloudflare Tunnel (`pios-pilot`, `piosapp.ru`) | **Broken, unchanged, not re-diagnosed** | Per this task's own explicit scope limit ("DO NOT change this configuration") and the extensive, already-complete prior diagnostic record (`docs/PIOS_TAXI_TASK_31`–`44_*.md`, all untracked in git — see Section 10) — last known state: QUIC/HTTP2 control-stream failures, root cause not conclusively resolved (leading, unproven hypothesis: local security-software network filtering). |
| `windows-services/cloudflared/run-tunnel.ps1` | **Present, correct content, but untracked in git** | Read this session — correctly avoids ever echoing the tunnel token, reads it from a gitignored `tunnel.token` file. Odd that the script itself (not the token) is untracked — see Git Hygiene, Section 10. |
| `docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md` | **Obsolete relative to actual working setup** | Documents a classic `cloudflared tunnel create` + local `config.yml` plan that, per this session's inherited context, was never actually completed on this host — the real, currently-working path is Tailscale Funnel, documented elsewhere. Should be marked superseded, not deleted (Constitution: "Never Delete Documentation"). |
| Backend test/production database isolation | **Fixed in code, not re-verified end-to-end** | See Section 3/5. |
| Docker / CI | **Does not exist** | Confirmed by `README.md`'s own statement and absence of any `Dockerfile`/`docker-compose.yml`/CI config — inherited from the prior audit, not independently re-searched this session. |

## 9. Documentation Findings

Inherited, still current (not independently re-verified line-by-line this session, but the underlying files were not touched by anything this audit found in the working tree):
- `README.md`'s opening paragraph claims "four independent... backend modules"; its own "Backend startup" section, four paragraphs later, lists **five** and never mentions `ai-advisor` (confirmed missing from both "Technology" and "Running Locally" this session — independently re-checked), `platform-ops`, or `network-management` anywhere.
- `project-brain/PIOS_CURRENT_STATE.md` (per the prior audit) claims no `frontend/` directory exists — flatly false.

New this session:
- `docs/PIOS_DESIGN_SYSTEM.md` and `docs/PIOS_TAXI_DESIGN_BRIEF.md`, unlike every `PRODUCT_DECISION_*.md` file in this repository, carry no explicit "Ratified by Product Owner, [date]" line — whether their scope was directly requested by the Product Owner or self-initiated within the implementation-engineer's own remit is **UNCERTAIN** from the documents alone and worth a direct confirmation.
- `docs/PIOS_DESIGN_IMPLEMENTATION_LOG.md` already self-documents the `aiProvider.test.ts` build failure as "pre-existing, unrelated" — consistent with, and independently confirmed by, this audit's own Section 5 finding; no contradiction found here, only confirmation that the log was honest at the time it was written.
- HEAD's own commit message (`b701c6c`) claims `explicitDriverIntent=true` is "not yet set by any real frontend caller" — this session's own `grep` shows `RideRequest.tsx` line 659 does send `explicitDriverIntent: true` on every order submission. Either this line predates the commit message's own claim (the message may describe an earlier state within the same multi-task checkpoint) or the claim is simply stale relative to what's actually in the tree today — **worth a direct clarification, not asserted as a contradiction of fact.**

## 10. Git Hygiene Findings

| Path | Classification | Recommended treatment |
|---|---|---|
| 12 new `frontend/src/components/*/` dirs, `frontend/src/styles/tokens.css`, 6 new `docs/*DESIGN*`/`*DECISION*.md` | Legitimate source/documentation | Commit as one logical "design system foundation + passenger migration" change |
| `backend/**/SessionTokenVerifier.kt`, `*PostgreSQLSecurityTest.kt` (driver-management, dispatch, order-management) | Legitimate source | Commit as one logical "auth remediation" change — highest priority, see F-1 |
| `ai-advisor/**/OllamaProvider.kt`, `QwenProvider.kt`, `*RestClientConfiguration.kt` + tests | Legitimate source | Commit as one logical "AI Advisor providers" change |
| `frontend/src/pages/OwnerControlCenter/pilotAnalytics.{ts,test.ts}` | Legitimate source, but currently breaks the build (F-2) | Fix before or as part of committing |
| `docs/PIOS_TAXI_TASK_17`–`44_*.md`, `docs/CLAUDE_CODE_ENVIRONMENT_AUDIT.md` (~24 files) | Legitimate documentation, but **unrelated to this repository's current uncommitted work** | Decide separately whether these belong in `docs/` at all in this branch, or are artifacts of a different task thread that should be relocated/reviewed on their own before any commit sweeps them in |
| `.claude/settings.json.graphify-bak` | Stray backup artifact | Should never be tracked — add to `.gitignore`, do not commit |
| `graphify-out/` | Generated knowledge-graph output | Should never be tracked — add to `.gitignore` |
| `logs/` (`ai-advisor.log` + rotated `.gz`) | Runtime log output | Should never be tracked — add to `.gitignore` |
| `windows-services/cloudflared/run-tunnel.ps1` | Legitimate, security-conscious infrastructure script — but **not tracked**, unusually, for a file this session's own inherited context treats as an established, referenced part of the deployment story | Should be tracked (it contains no secret — the token itself is correctly kept out via `tunnel.token`, already gitignored) |
| `.claude/skills/` | Claude Code tooling configuration | Project-scope tooling decision, not application code — outside this audit's authority to recommend either way |

No file was deleted, staged, or committed by this audit.

## 11. Top Pilot Risks

1. **[SECURITY]** F-1/S-1 — the Proposal/Assignment/availability authorization fix exists only uncommitted; if the deployed pilot doesn't already have it, any caller can manipulate any driver's rides right now.
2. **[TECHNICAL]** F-2 — the entire working tree, including the fix for risk #1, currently cannot be built (`npm run build` fails).
3. **[OPERATIONS]** F-4 — this host's real `ai-advisor` process may already be spending real money against a real external API contrary to the documented "safe by default" assumption, undetected unless someone checks the environment directly (as this audit did).
4. **[TECHNICAL]** Backend test/production database isolation — verified fixed in code, but not verified end-to-end by actually running the suite; a false sense of safety here would repeat the exact incident `PIOS_REALITY_AUDIT.md` §19 already disclosed once.
5. **[UX]** F-3 — no shared ride-status component between driver and passenger screens; a real, brief-violating inconsistency that will get harder to fix the longer both copies diverge independently.
6. **[PRODUCT]** `network-management` remains fully built, fully tested, and completely unused by the real product, alongside a separately-named, differently-scoped `Connection` concept in `passenger-experience` that the product actually uses — unresolved architectural ambiguity, unchanged since the prior audit.
7. **[DEPLOYMENT]** Cloudflare Tunnel remains broken with no conclusively identified root cause; Tailscale Funnel is the only confirmed-working public path, and it is a single point of failure with no documented fallback plan if it, too, stops working.
8. **[OPERATIONS]** `windows-services/cloudflared/run-tunnel.ps1` — real deployment tooling — is not tracked in git; if this machine's working copy were lost, this exact script would need to be recreated from memory/documentation rather than checked out.
9. **[UX/DESIGN]** `DriverHome` — the screen the driver actually uses every shift — remains on the pre-migration visual language while the passenger's own screens have already moved to the new one; a visible inconsistency for any pilot participant who sees both.
10. **[PRODUCT]** Documentation volume (97+ files) vs. documentation currency (`README.md` self-contradiction, stale `project-brain/` file) — a real onboarding/trust risk for the next person or AI session that reads the top-level docs at face value.

## 12. Recommended Next Step

**Commit and deploy the already-written Task 20–25 authorization fix (F-1/S-1) — and only that — as its own, isolated change, before anything else in this working tree.**

**Why this is the next step:** it is the one item in this entire audit that is (a) a live, currently-real risk to the running pilot, per both this audit's own diff reading and the prior audit's independent, dated confirmation of the same gap; (b) already fully written and internally consistent (correct 401→404→403 ordering, mirrors an already-proven pattern used elsewhere in the same codebase); (c) requires zero new design work, zero new architecture, and zero product decision — it is purely "ship what already exists." No other finding in this audit clears all three of these bars simultaneously.

**What problem it solves:** closes the one class of finding that lets an unauthenticated or wrongly-authenticated caller manipulate another driver's live ride state or availability — the most severe, concretely exploitable issue this audit found.

**What should NOT be worked on yet:**
- The design-system migration of `DriverHome` (F-3's sibling, Section 4) — real, but not urgent relative to F-1, and its own working tree is currently entangled with the same build failure (F-2) that must be resolved first regardless.
- `network-management`'s fate (deprecate vs. wire in) — a Product Owner decision, not urgent, unchanged since the prior audit.
- Any new AI Advisor provider work — F-4 needs a human decision about intent, not more code.
- Any Cloudflare Tunnel re-diagnosis — explicitly out of this task's scope, and not urgent given Tailscale Funnel is currently working.

**Definition of Done for this step:** see Section 13.

## 13. Definition of Done for the Next Step

1. `AssignmentController.kt`, `ProposalController.kt`, `DriverController.kt`, and the new `SessionTokenVerifier.kt` (driver-management) are committed, isolated from the other three uncommitted strands (design system, AI Advisor, pilot analytics) — verified by `git show --stat` on the resulting commit touching only these files plus their own tests.
2. `F-2` is resolved first or alongside (the `aiProvider.test.ts`/`PilotAnalyticsInput` mismatch) — or the F-1 commit is built and deployed via a path that does not depend on `npm run build` succeeding, if the two are deployed independently. Either way, `npm run build` succeeds from a clean checkout of the resulting state before deployment is considered complete.
3. The affected backend module(s) (`dispatch`, `driver-management`) are rebuilt and the running Windows services are restarted from the new build — **with separate, explicit operator confirmation before touching the live pilot's running services**, consistent with this project's own standing production-safety discipline.
4. Post-deployment, a manual or automated check confirms: an unauthenticated `POST` to `/v1/proposals/{id}/accept`, `/v1/assignments/{id}/arrive`, and `/v1/drivers/{id}/availability` each return `401`, not `200`.
5. This audit's own F-1 finding is updated from "uncommitted fix exists" to "deployed and confirmed" in whatever tracking document the team uses next (this file is not re-edited after the fact, per its own audit-report nature).

## 14. Files Inspected

`CLAUDE.md`, `.claude/CLAUDE.md`, `README.md`, `PIOS_REALITY_AUDIT.md`, `docs/PIOS_DESIGN_SYSTEM.md`, `docs/PIOS_TAXI_DESIGN_BRIEF.md`, `docs/PIOS_DESIGN_IMPLEMENTATION_LOG.md`, `docs/PIOS_VISUAL_VERIFICATION_PLAN.md`, `docs/DRIVER_IDENTITY_DESIGN_DECISION.md`, `docs/PASSENGER_INVITATION_DESIGN_DECISION.md`, `frontend/src/styles/tokens.css`, `frontend/src/index.css`, `frontend/src/pages/DriverHome/DriverHome.tsx` + `.module.css`, `frontend/src/pages/RideRequest/RideRequest.tsx` + `.module.css`, `frontend/src/pages/PassengerLanding/PassengerLanding.tsx` + `.module.css`, `frontend/src/pages/Coordinator/Coordinator.tsx`, `frontend/src/identity/BackendIdentityProvider.ts`, `frontend/src/components/DriverTrustIndicator/DriverTrustIndicator.tsx`, all 29 `.module.css` files (classified by `var(--pios-` presence), `frontend/src/pages/OwnerControlCenter/pilotAnalytics.ts` (diff), `backend/dispatch/src/main/kotlin/com/pios/dispatch/api/{AssignmentController,ProposalController}.kt` (diffs), `backend/driver-management/src/main/kotlin/com/pios/drivermanagement/api/{DriverController,SessionTokenVerifier}.kt`, `ai-advisor/src/main/kotlin/com/pios/aiadvisor/domain/{DeepSeekProvider,MockAIProvider}.kt`, `ai-advisor/src/main/kotlin/com/pios/aiadvisor/api/PilotAnalysisRequest.kt`, `ai-advisor/src/main/resources/application.yml`, `ai-advisor/src/test/kotlin/com/pios/aiadvisor/domain/ProviderSelectionTest.kt`, `windows-services/cloudflared/run-tunnel.ps1`, all five modules' `backend/*/src/test/kotlin/.../persistence/PostgreSQLTestDatabase.kt`.

## 15. Commands Executed

```
git status / git branch --show-current / git log --oneline -20 / git diff --stat / git diff <specific files> / git show --stat b701c6c
curl -o /tmp/pios_index.html -w "HTTP %{http_code}" https://home-pc.tail385153.ts.net/   (fresh, read-only reachability check)
cd frontend && npx tsc -b --noEmit
cd frontend && npm run build
cd frontend && npx vitest run
cd frontend && npm run lint   (oxlint)
cd ai-advisor && ./gradlew.bat test --console=plain -q
grep -n "ConditionalOnProperty" ai-advisor/src/main/kotlin/com/pios/aiadvisor/domain/*.kt
env | grep -i "PIOS_"   (presence-only check, no values disclosed in this report)
grep -n "jdbc:postgresql" backend/*/src/test/kotlin/**/PostgreSQLTestDatabase.kt
grep -l "var(--pios-" **/*.module.css   (classification pass across all 29 CSS modules)
```

No backend Gradle build/test was run for `dispatch`, `driver-management`, `order-management`, `passenger-experience`, `identity`, or `network-management` — deliberate, explained in Section 5.

## 16. Final Verdict

**NOT READY — FIX BLOCKERS**

- A real, currently-exploitable authorization gap (F-1) most likely still describes the deployed pilot, and its fix — already written — has not shipped.
- `npm run build` fails outright on the current working tree (F-2); nothing in this tree, including the F-1 fix, can be built and deployed via the documented path until this is resolved.
- The AI Advisor's documented "safe by default" guarantee does not hold on this host's actual environment (F-4) — an operational fact someone needs to consciously decide about, not leave undiscovered.
- These are not "more validation needed" gaps — they are known, specific, already-diagnosed blockers with already-known fixes (F-1, F-2) or already-known next actions (F-4), which is why this is FIX BLOCKERS rather than MORE VALIDATION REQUIRED.
- Everything else audited — the core order/proposal/assignment flow, the design-system foundation, the backend test-isolation fix, the Tailscale Funnel path — is working, evidenced, and does not itself block the next phase.
- Once F-1 is committed/deployed and F-2 is resolved, this verdict should be re-run, not assumed to flip automatically — a fresh, short confirmation pass (Section 13's Definition of Done) is the correct way to re-certify, not a re-reading of this document.
