# PIOS Session Secret Mechanism Investigation

**Date:** 2026-09-04. **Scope:** read-only investigation, three questions from the operator. No secret value was ever read, decoded, printed, or exposed — every check below is presence-only or SHA-256-fingerprint-only. No service was restarted or stopped, no file was modified, no database was written to, nothing was committed or pushed.

---

## Task 1 — Reproducible mechanism for `pios.session.secret`

### Where it actually lives

**`HKLM:\SYSTEM\CurrentControlSet\Services\<service-name>\Environment`** — a standard, native Windows Service Control Manager (SCM) feature: any Windows Service can carry its own `Environment` `REG_MULTI_SZ` registry value, which the SCM applies to that service's process at the moment it starts, layered on top of (and independent of) the system-wide environment and whatever the service's own launcher (WinSW, here) puts in its `<env>` XML tags. This is **not** the location any prior gate in this arc's own investigation checked — those checked `HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment` (the *machine-wide* environment) and the WinSW XML files themselves, both of which are genuinely empty for this variable. The per-service SCM key is a third, separate location, and it is where the value actually is.

### How this was found

Not derived from first principles — found in four **uncommitted** reports already sitting in this working tree (`docs/PIOS_TAXI_TASK_26` through `_29`, file timestamps 2026-09-03 21:15–22:32, i.e. the evening before this security-remediation arc began). That earlier task series independently discovered this exact mechanism, used it to diagnose that `driver-management` was missing the secret (a real gap at the time — Task 25 had just added that module's *first* `SessionTokenVerifier`, so it had never needed the secret before), and — with explicit operator approval at each mutating step — copied the already-confirmed-identical secret from `dispatch`'s own registry key into `driver-management`'s, verified only by SHA-256 fingerprint and byte-length, never by displaying the value. Task 29 (the last in that series) re-confirmed the fix consistent, then explicitly stopped short of deployment ("READY... not authorization to proceed").

### Independent re-verification, today, this session

Read each of the five domain services' own SCM `Environment` value, live, right now. Only the value's **presence**, **byte-length**, and a **SHA-256 fingerprint** were computed — the raw secret was never assigned to a variable this session could print, and never appeared in any tool output:

| Service | `PIOS_SESSION_SECRET` present? | Length | SHA-256 |
|---|---|---|---|
| `pios-identity` | Yes | 44 | `0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839` |
| `pios-dispatch` | Yes | 44 | `0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839` |
| `pios-order-management` | Yes | 44 | `0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839` |
| `pios-driver-management` | Yes | 44 | `0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839` |
| `pios-passenger-experience` | Yes | 44 | `0c18719929f4f74924d861ba5b622b15081b3dcd96818541c3f4b68c574fe839` |
| `pios-ai-advisor` | Absent (correct — this module never verifies a session token) | — | — |

**Identical fingerprint on all five services that need it — no drift from Task 28/29's own last-recorded state.** This also finally explains the standing mystery from every earlier gate in this arc: the live, continuously-running `identity` process (PID 6532, since 2026-09-01) has a working secret, and always did — it was never missing, it was only invisible to every location this arc's own earlier gates happened to check. The empirical registration success (`docs/PIOS_SESSION_AUTH_EMPIRICAL_RESULT.md`, HTTP 201 + token) is now fully explained rather than merely observed.

### Classification

**B — an undocumented (in this project's own committed/ratified sense) but confirmed, currently-active mechanism.**

Not A: nothing in the committed `docs/ADR/` tree describes *where* the secret lives — ADR-055 says only "injected per-deployment, never committed," which is true but not actionable; a future operator or a future Claude session, reading only committed documentation, could not find this. The only place this mechanism is described at all is inside four **uncommitted** reports, which — per this project's own Documentation First principle ("Code that has no corresponding documentation is considered incomplete") — do not count as documentation until reviewed and committed.

Not C: the mechanism is real, currently active, independently reconfirmed today, and consistent across every service that needs it. There is no reason to have the operator generate and redistribute a brand-new secret.

### Recommendation, per classification B's own order — document, then deploy

Before any future restart is attempted:
1. Record this mechanism in a committed, canonical place — either an addition to ADR-055's own Consequences section, or a short, dedicated ops note (e.g. `docs/PIOS_DEPLOYMENT_SECRETS.md`) stating: *"`pios.session.secret` (and the owner credential) live in each `pios-*` Windows Service's own SCM registry `Environment` override, not in the WinSW XML and not in the machine-wide environment; `generate-service-xml.ps1` intentionally does not manage this value, and neither should any future tooling change that assumption without updating this note."*
2. This closes the exact gap that made every earlier gate in this arc unable to answer the question — not because the secret was missing, but because nothing pointed at where to look.
3. Once committed, a restart of `identity`/`dispatch`/`driver-management`/`order-management`/`passenger-experience` can be treated as safe with respect to this specific prerequisite — the value that would apply on restart is the one just independently fingerprinted above, matching what the currently-running processes already use.

---

## Task 2 — does `isTest` actually allow a safe, non-polluting real ride?

### What `isTest` actually protects

Confirmed by source, not assumed: `Order`, `Proposal`, `Assignment`, and `Driver` each carry a persisted `is_test` column (`orders` via `V10__add_order_is_test.sql`, already applied — order-management is at V10; `proposals`/`assignments` via dispatch's `V11__add_proposal_and_assignment_is_test.sql`, already applied — dispatch is at V11; `drivers` via `V5__add_driver_is_test.sql`, already applied — driver-management is at V5. All four are live in production **today**, confirmed by Task 29's own independent schema-version check, itself consistent with every process-uptime fact this whole arc has established). `POST /v1/orders`, `POST /v1/proposals`, `POST /v1/drivers` (and the assignment an accepted proposal creates) all accept and persist this flag, unauthenticated fields, defaulting to `false`.

`frontend/src/pages/OwnerControlCenter/todayData.ts`'s `excludeTestData()` filters every `isTest: true` record out of the Owner Control Center's own "today" dashboard before it is ever counted, displayed, or handed to the AI Advisor — confirmed by its own KDoc ("nothing downstream ever sees a `isTest: true` counter or an event") and by reading the function itself. **This is real, and it does exactly what it claims: a `isTest: true` order/proposal/assignment cannot contaminate the owner's own pilot analytics.**

### What `isTest` does *not* protect — the part worth being explicit about

**`DriverHome.tsx` — the actual driver-facing screen a real person's phone runs — never once references `isTest`.** Confirmed by a direct search of that file: zero matches. `GET /v1/proposals?driverId=...` (the poll this screen uses) returns `isTest: true` proposals mixed in with real ones, with **no filtering and no visual marker**. A `POST /v1/proposals` naming a **real** pilot driver's `driverId`, even with `isTest: true`, would appear on that real driver's real phone as an ordinary, indistinguishable ride request — fully accept/decline-able, and accepting it creates a real, live Assignment that driver would be expected to act on. `isTest` is an **analytics-exclusion flag**, not a **routing/visibility** flag. Treating it as the latter would be exactly the kind of invented workaround this task asked not to assume.

### The exact safe scenario

A smoke test can safely exercise the full mutation surface (order → proposal → accept/decline → arrive/start/complete → availability → cancel) **only if no real driver or real passenger is ever named as a party to it** — `isTest: true` on top of that is a correct, useful belt-and-suspenders addition (keeps it out of analytics too), but the passenger/driver identities being synthetic is what actually keeps it off a real person's device:

1. **Synthetic passenger**: register a new identity with a clearly synthetic phone (the same reserved-fictional-range approach already used and already proven safe in this arc's own prior empirical test — `docs/PIOS_SESSION_AUTH_EMPIRICAL_RESULT.md`).
2. **Synthetic driver**: `POST /v1/drivers` with a clearly synthetic `driverId` (e.g. a `smoke-test-`-prefixed, unguessable id) and `isTest: true` — this endpoint is deliberately unauthenticated by design (`DriverController`'s own KDoc: "a pre-authentication 'first contact' action creating a brand-new resource... not a mutation of an existing, already-owned resource"), so creating one is itself an uncontroversial, already-anticipated action, not something this investigation is inventing.
3. **Associate them**: `POST /v1/identities/{id}/driver` with that synthetic `driverId`, using the synthetic passenger's own session token — this is the same, already-existing `attachDriver` flow the real app uses, giving the synthetic identity a token whose `drv` claim matches the synthetic driver, which is what every `SessionTokenVerifier`-gated driver endpoint (accept/decline/arrive/start/complete/availability) actually checks against.
4. **Everything downstream** — the order, the proposal (targeting only that synthetic `driverId`, never a real one), the resulting assignment — carries `isTest: true` throughout, keeping it out of the owner's own analytics on top of never having touched a real device.

Nothing in this scenario requires modifying application code, bypassing an authorization check, or inventing a mechanism the codebase doesn't already have — every piece (`isTest`, the unauthenticated driver-create endpoint, `attachDriver`) already exists and is already exercised by this codebase's own tests. This has not been executed by this investigation — it is a determination of the safe scenario, not a smoke-test run.

---

## Correction — `network-management` excluded from deployment scope

Confirmed, not merely noted: `network-management` is not part of the seven approved RC commits (`3f2946a`/`4aa87bd`/`9c6caea`/`02bd4bb`/`8b6fdd6`/`415ddf6`/`92953ed` — none touch it), is not currently running as a Windows service (absent from the live `Get-Service pios-*` listing throughout this entire arc), and ADR-037 excludes it from pilot scope. A future deployment task's own service list should read: `identity`, `dispatch`, `driver-management`, `order-management`, `passenger-experience` — the five that actually verify or issue session tokens and are actually part of this RC — plus `ai-advisor` only if that module's own change set is ever included in an RC (it carries no session-token dependency either way, per Task 1's own table above). `network-management` should not be started "because it once existed in the infrastructure list" — that would be scope expansion this project's own governance rules (`.claude/CLAUDE.md`: "preserve existing architecture unless explicitly instructed... never expand a milestone's scope without explicit instruction") don't authorize.

---

## Summary

| Question | Answer |
|---|---|
| Where does `pios.session.secret` come from on restart? | Each service's own SCM registry `Environment` key — confirmed present, identical (SHA-256), on all five services that need it, right now |
| Classification | **B** — real, confirmed, currently dormant-but-correct mechanism, not yet documented in this project's own committed docs |
| Required before next deployment attempt | Commit a short note recording this mechanism (ADR-055 addendum or a new ops doc) — then a restart can be treated as safe on this specific point |
| Does `isTest` alone make a smoke test safe? | No — it protects owner analytics only; `DriverHome.tsx` shows `isTest` and real proposals identically |
| Safe smoke-test scenario | Synthetic passenger + synthetic driver (created via the already-existing, already-unauthenticated `POST /v1/drivers`) + `isTest: true` throughout — never a real driver/passenger id |
| `network-management` | Excluded from any future deployment's service list — not part of this RC, not currently running, out of pilot scope per ADR-037 |
