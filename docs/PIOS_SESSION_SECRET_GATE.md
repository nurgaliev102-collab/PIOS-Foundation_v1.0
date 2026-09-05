# PIOS Session Secret Compatibility Gate

**Date:** 2026-09-04. **Scope:** read-only. No file was edited, no secret value was read or printed, no service was installed, started, stopped, or restarted, nothing was committed or pushed. This gate answers one question: is the live backend correctly configured to authenticate session tokens under the approved release candidate (`3f2946a`, `4aa87bd`, `9c6caea`, `02bd4bb`, `8b6fdd6`, `415ddf6`)?

Throughout this report: **VERIFIED** = established directly from a source inspected in this session; **UNKNOWN** = could not be established through any read-only method available; **NOT APPLICABLE** = the question doesn't apply given what was found.

---

## 1. RC authentication architecture

ADR-055 Decision 1: `identity` is the sole issuer of session tokens. A token is `<base64url(payload)>.<base64url(HMAC-SHA256(secret, base64url(payload)))>`, payload `{"sub":"<identityId>","drv":"<driverId>|null","exp":<epochSeconds>}` (`backend/identity/.../application/SessionTokenIssuer.kt:12-14`). Every verifying module carries its own **replicated, not shared**, `SessionTokenVerifier` class — "replicate, don't share" per `MODULE_STRUCTURE.md` Section 4, the same pattern already used for `OwnerCredentialGate`. Confirmed present, byte-for-byte equivalent implementations, in:

| Module | Class | Guards |
|---|---|---|
| `identity` | `com.pios.identity.api.SessionTokenVerifier` | its own protected endpoints |
| `passenger-experience` | `com.pios.passengerexperience.api.SessionTokenVerifier` | Circle-of-Trust endpoints |
| `dispatch` | `com.pios.dispatch.api.SessionTokenVerifier` | Proposal accept/decline, Assignment arrive/start/complete, `?driverId=` branch of `GET /v1/proposals` (ADR-060 Decision 4) |
| `driver-management` | `com.pios.drivermanagement.api.SessionTokenVerifier` | driver availability toggle (Task 25) |
| `order-management` | `com.pios.ordermanagement.api.SessionTokenVerifier` | order cancellation, Bearer-gated `GET /v1/orders` modes |

`SessionTokenIssuer.issue()` (identity only) **throws `IllegalStateException`** if `pios.session.secret` is blank — "a deployment that can register or log in a caller must configure the secret; there is no 'issue an unusable token' fallback" (`SessionTokenIssuer.kt:39-42`). Every `SessionTokenVerifier.verify()` (all five modules) instead **fails closed**: a blank secret makes `verify()` return `null` unconditionally, so every gated endpoint responds `401` rather than defaulting open (`SessionTokenVerifier.kt:26-28,49-51`, identical wording in all five copies).

**Frontend needs the secret directly: confirmed NO.** Repository-wide search for `HMAC`/`SecretKeySpec`/session-secret handling in `frontend/src` found nothing — the frontend only ever stores and forwards the opaque token string identity issues (`StoredIdentity.token`, ADR-055), never the signing key itself. This is architecturally correct and unchanged by this gate.

## 2. Session secret source

`pios.session.secret` is a Spring `@Value("${pios.session.secret:}")` binding, read identically in all six classes above (five verifiers + one issuer). It is **not hardcoded** and **not defaulted to a usable value** anywhere in the codebase — the default is the empty string, which both `issue()` and `verify()` treat as "unconfigured."

Every module's own `application.yml` leaves it commented out, by explicit design, with matching KDoc/YAML-comment language across all five ("Deliberately left unset here... Each deployment must set it explicitly"):

- `backend/identity/src/main/resources/application.yml:63-72`
- `backend/dispatch/src/main/resources/application.yml:85-86`
- `backend/driver-management/src/main/resources/application.yml:72-73`
- `backend/order-management/src/main/resources/application.yml:73-74`
- `backend/passenger-experience/src/main/resources/application.yml:74-75`

No module has a `config/` override directory, an `application-<profile>.yml`, or any other profile-specific resource file (checked: only one `application.yml` exists per module). No `-Dpios.session.secret=...` JVM argument appears in any WinSW `<arguments>` element or in any currently running `java -jar` process's command line (§9). **VERIFIED**: the value must come from the process's environment, if it comes from anywhere.

## 3. Live service configuration

`windows-services/generate-service-xml.ps1` is the only mechanism this repository defines for getting a secret value into a running module's `<env>` block. It reads Machine-scope registry environment variables once, and substitutes them into the five (six, including `ai-advisor`) generated `pios-<module>.xml` files (gitignored; only the `.xml.template` counterparts are tracked, since commit `cda1721`).

**Its own `$commonSecretNames` list does not include `PIOS_SESSION_SECRET` at all** — only `PIOS_OWNER_USERNAME`, `PIOS_OWNER_PASSWORD_HASH`, `PIOS_OWNER_PASSWORD_SALT` (plus, for `ai-advisor` only, the Qwen provider keys). The generator has no placeholder, and no code path, for `pios.session.secret` in any of the five domain modules. **VERIFIED** by reading `generate-service-xml.ps1:32-42` directly.

Reading the five live, generated WinSW XML files directly confirms this — none of them carry a `PIOS_SESSION_SECRET` (or any session-related) `<env>` entry:

- `windows-services/dispatch/pios-dispatch.xml`
- `windows-services/driver-management/pios-driver-management.xml`
- `windows-services/order-management/pios-order-management.xml`
- `windows-services/identity/pios-identity.xml`
- `windows-services/passenger-experience/pios-passenger-experience.xml`

Each carries only `PIOS_PILOT_FRONTEND_ORIGIN` and the three owner-credential values. **VERIFIED.**

## 4. Secret presence

Checked, by name only — no value was ever read or printed:

| Source | `PIOS_SESSION_SECRET` present? | Method |
|---|---|---|
| All 5 modules' `application.yml` | ABSENT (commented out by design) | Read tool |
| All 5 modules' generated WinSW `<env>` blocks | ABSENT | Read tool |
| `generate-service-xml.ps1`'s injectable-name list | ABSENT (not even a known placeholder) | Read tool |
| Machine-scope registry (`HKLM:\...\Environment`) | ABSENT | `Get-ItemProperty`, property-name enumeration only |
| User-scope registry (`HKCU:\Environment`) | ABSENT | same |
| Current interactive shell environment | ABSENT | `$env:PIOS_SESSION_SECRET` boolean presence check |
| Any `SPRING_PROFILES_ACTIVE` / profile-specific `application-*.yml` that could carry it another way | ABSENT / none exist | Glob + registry check |

**PRESENCE VERDICT: ABSENT.** High confidence, but not absolute (see caveat below) — this is a determination from configuration-source inspection, not from directly reading the memory of a running process.

**Fingerprint: NOT APPLICABLE.** No secret value was ever obtained from any source in this session — there is nothing to hash. This is different from "the value is unreadable"; it is "every place the value could be configured shows no value at all."

**Residual UNKNOWN, stated explicitly:** a Windows process's environment block is fixed at the moment it is created and does not update if the registry changes afterward. It is theoretically possible — though contradicted by every other artifact inspected (git history, the generator's own scope, the current XML and registry state) — that some past, undocumented, manual step (`setx` at Machine scope, run once, later reverted) set this variable before the currently-running `identity` process started on 2026-09-01, and that step left no trace in git or the current registry. This session has no read-only means of inspecting another process's actual environment block to rule that out. Flagged as **UNKNOWN**, not dismissed.

`C:\PIOS-Secrets\lenovo-owner-credentials.txt` was seen (directory listing only) but deliberately **not opened** — its name indicates owner-credential material, not `pios.session.secret`, and reading it was not necessary to answer this gate's question.

## 5. Cross-service consistency

ADR-055's own disclosed Consequence requires the same secret, byte for byte, in every issuing/verifying module. Since **no module shows any configured value** (§4), there is nothing to be inconsistent between — the finding is not "module A has secret X and module B has secret Y" (the dangerous, silently-broken-for-some-users case), it is "no module has any value" (a uniform, fail-closed state). This is the safer of the two bad states, but it is still not a working state: see §6.

## 6. Existing-session compatibility

`SessionTokenIssuer.issue()` throws `IllegalStateException` whenever `pios.session.secret` is blank (§1). Given §4's finding that the secret is absent from every configuration channel currently reaching the live `identity` process, the direct implication is: **`identity` cannot currently mint a session token at all** — every registration or login attempt against the live service, as currently configured, would fail with a 5xx/exception, not merely produce a token nobody can use.

This means the premise of the question — "will the RC's fixes authenticate *existing* pilot session tokens" — may not have a population to apply to: if issuance has been failing since this configuration took effect, there are no live, currently-valid session tokens for real drivers/passengers to be broken by a restart. This cannot be stated as fully VERIFIED (the §4 residual UNKNOWN applies here too — if the running `identity` process's actual environment differs from every static source checked, tokens could have been issued under a value this session could not see), but it is the best-supported conclusion available from read-only inspection.

**VERIFIED:** the RC's own security-fix commits (`3f2946a`, `4aa87bd`) do not change `SessionTokenIssuer`, `SessionTokenVerifier`, or any module's `application.yml` — they add authorization checks that *use* the existing, already-live token-verification mechanism. The compatibility question is entirely about deployment configuration, not about anything introduced by this security-remediation arc.

## 7. Restart implications

Restarting `dispatch`, `driver-management`, or `order-management` with the RC's jars, **using the exact same (verified-absent) configuration these services have today**, would not invalidate anything, because nothing currently validates: every `SessionTokenVerifier.verify()` call already returns `null` today (secret blank), and would continue to after restart. The newly-added authorization checks (Proposal/Assignment, driver availability, order cancellation) would uniformly return `401` for every caller, exactly as `identity`'s own login/registration path is, on the evidence gathered, already uniformly failing today.

If the operator instead sets `pios.session.secret` **for the first time**, at or before this restart, then: whatever `identity` process last issued a token (if any ever succeeded, contradicting §6's inference) minted it with whatever value — knowable or not — was in effect at that moment; any token from a past, differently-configured `identity` process would fail to verify against a newly-set, different value. This is the standard "changing the secret invalidates prior tokens" behavior HMAC schemes always have, not something specific to this RC.

**Restart implication in one sentence:** restarting today, unchanged, changes nothing about authentication (it stays uniformly closed); restarting **while also configuring the secret** is safe for future tokens but will invalidate any token minted before that configuration change, if any such token exists.

## 8. Security considerations

- Fail-closed design is correctly implemented throughout: an absent secret denies rather than defaults open, on both the issuing and every verifying side. This is a defensible current state from a pure security standpoint — no forged or bypass-able token is possible — but it likely also means the pilot's registration/login flow is non-functional right now, which is a product/availability concern the security posture does not itself detect or surface (no health-check field reports `pios.session.secret` presence, unlike `OwnerCredentialGate.isConfigured()`, which the identity `HealthController` does gate on, but does not report *on*).
- The owner-credential values (`PIOS_OWNER_PASSWORD_HASH`/`PIOS_OWNER_PASSWORD_SALT`) are committed to WinSW `<env>` (gitignored, machine-local) via a working generator pipeline; `pios.session.secret` has no equivalent pipeline at all today — it was simply never added to `generate-service-xml.ps1`'s scope. This is a configuration-completeness gap, not a code defect in the RC.
- No secret value, of any kind, was printed to any tool output, file, or log in the course of this gate. Only variable **names** were ever enumerated.

## 9. Required operator action

Because this cannot be resolved through any read-only method available in this session, the following operator action is required before any restart is treated as safe for real users:

1. **Confirm directly** (on the machine, outside this session) whether `pios.session.secret` is configured anywhere reaching the currently-running `identity`, `dispatch`, `driver-management`, `order-management`, and `passenger-experience` processes — the one channel this gate cannot rule out (§4's residual UNKNOWN) is the actual live process environment block, which only the operator (or a process inspector run with sufficient privilege) can read directly.
2. **Confirm empirically** whether registration/login currently succeeds at all against the live pilot (e.g., attempt one real registration through the live frontend, or inspect `identity`'s own rolling log at `backend/logs/identity.log` for `IllegalStateException: pios.session.secret must be configured` around request times) — this would directly confirm or refute §6's inference without needing to read the secret itself.
3. **If the secret is genuinely unconfigured:** generate one (a fresh, random, base64-encoded value — never reuse the owner-credential material), add `PIOS_SESSION_SECRET` to `generate-service-xml.ps1`'s `$commonSecretNames` list and to all five domain modules' `.xml.template` files (plus `passenger-experience`'s, which also verifies), set it once at Machine scope, regenerate the five XML files, and only then restart — restarting without this step leaves the pilot's session-token flow exactly as functional (or non-functional) as it is today.
4. **If the secret is found to already be configured** (contradicting this gate's findings, e.g. by some channel this session could not see): identify that channel and reconcile it with the git-tracked generator mechanism, so future restarts are reproducible and this gate's method is not blind to it again.

No item above was performed by this session — all are handed to the operator, as instructed.

## 10. GO / NO-GO

- RC authentication architecture: sound, matches ADR-055, frontend correctly holds no secret material. VERIFIED.
- `pios.session.secret`: ABSENT from every configuration channel this session could inspect, uniformly across all five backend modules. VERIFIED (with one stated residual UNKNOWN: the live process's own environment block, unreadable by any method available here).
- Cross-module consistency: not a mismatch risk today (uniformly absent), but not a working state either.
- Existing-session compatibility: most likely NOT APPLICABLE — the evidence suggests no valid session tokens currently exist to be made incompatible, because issuance itself appears to be failing. Not fully VERIFIED; requires operator confirmation (§9.2).
- Restarting the RC today, without operator action, does not make anything worse — it preserves the current uniformly-fail-closed state. It also does not make the pilot's login/registration functional.

**This session did not verify that the live backend can currently authenticate a single real session token.** Deploying the RC without first resolving §9 would ship correct authorization *logic* onto a session-token mechanism whose live configuration this gate could not confirm is functional at all.

FINAL VERDICT:
**NO-GO**

(Blocking condition: §9 operator actions 1-2 must be completed, and, if the secret is confirmed absent, action 3 must be completed, before this session — or the operator — can respond GO on this specific question.)

---

## Compact terminal execution summary

- **Architecture:** ADR-055 session-token issuance (`identity`) + local, replicated `SessionTokenVerifier` (identity, passenger-experience, dispatch, driver-management, order-management). Frontend never touches the signing secret — confirmed clean.
- **Secret location, checked, name-presence only, no value ever read:** all 5 `application.yml` files (unset by design) · all 5 live WinSW `<env>` blocks (absent) · `generate-service-xml.ps1`'s own injectable-name list (doesn't even include it) · Machine-scope registry (absent) · User-scope registry (absent) · current shell (absent). **PRESENCE: ABSENT**, high confidence. **FINGERPRINT: NOT APPLICABLE** — no value was ever obtained to hash.
- **Residual UNKNOWN, disclosed:** the actual live process's environment block (fixed at process start, unreadable read-only from here) could theoretically differ from every static source checked — flagged, not ruled out.
- **Consequence:** `SessionTokenIssuer.issue()` throws when the secret is blank — meaning `identity`'s live registration/login likely cannot succeed right now, independent of anything in the approved commits. This is a pre-existing configuration gap, not something the RC introduced.
- **Restart implication:** restarting today with no operator action changes nothing (stays uniformly closed, safe but non-functional); restarting while first configuring the secret is safe going forward but would invalidate any pre-existing token, if any exists.
- **Processes inspected (read-only):** `identity`/`ai-advisor` running since 2026-09-01 22:47; `order-management`/`passenger-experience`/`driver-management`/`dispatch` since 2026-09-03 22:55-23:08 — all older than every one of the 6 approved commits (all committed 2026-09-04, 15:20-18:09), confirming none of the RC's fixes are live yet.
- **Operator action required:** confirm on-machine whether the secret truly reaches the live processes; empirically check whether login/registration works today; if genuinely unconfigured, generate one, wire it into `generate-service-xml.ps1` + the five `.xml.template` files, then restart.
- Nothing was restarted, stopped, edited (application code, configuration, or secrets), committed, or pushed.

**FINAL VERDICT: NO-GO** — blocked on operator confirmation of the live secret's actual state (§9).
