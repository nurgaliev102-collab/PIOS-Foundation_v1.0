# PIOS Deployment Secrets and Runtime Credentials

**Status: authoritative launch-preparation inventory, 2026-09-23.** This
document records names, purpose, injection point, and failure behavior. It
never records, logs, fingerprints, or supplies a real value.

## Required inventory

| Variable | Service(s) | Purpose | Launch requirement | Configuration location | Missing/invalid behavior |
|---|---|---|---|---|---|
| `PIOS_SESSION_SECRET` | identity, dispatch, driver-management, order-management, passenger-experience | Shared HMAC secret for session issue/verification | Required; identical value on all five services | Per-service SCM `Environment` entry (details below) | Protected endpoints fail closed; services whose verifier is eagerly constructed reject blank/invalid configuration |
| `PIOS_OWNER_USERNAME` | all six WinSW templates | Single-owner Basic-auth name | Required by the XML generator | Machine-scope environment, rendered by `windows-services/generate-service-xml.ps1` | Generator aborts before writing XML |
| `PIOS_OWNER_PASSWORD_HASH` | all six WinSW templates | PBKDF2 owner credential hash | Required by the XML generator | Same | Generator aborts before writing XML; never store plaintext owner password |
| `PIOS_OWNER_PASSWORD_SALT` | all six WinSW templates | Salt paired with owner hash | Required by the XML generator | Same | Generator aborts before writing XML |
| `PIOS_SMS_LOGIN` | identity | SMS Aero Basic-auth login | Required | Machine-scope environment, rendered into ignored identity WinSW XML | Generator and Identity startup fail closed when blank |
| `PIOS_SMS_API_KEY` | identity | SMS Aero Basic-auth secret | Required secret | Same | Generator and Identity startup fail closed when blank |
| `PIOS_SMS_SENDER` | identity | Approved SMS Aero sender name | Required configuration | Same | Generator and Identity startup fail closed when blank |
| `PIOS_OTP_RELAY_KEY` | identity | AES-256 key encrypting queued OTP plaintext until relay | Required secret; exactly 32 decoded bytes | Same | Generator aborts when absent; Identity startup rejects blank, invalid Base64, or wrong length |
| `PIOS_PUSH_VAPID_PUBLIC_KEY` | dispatch | Browser-visible VAPID public key | Required for Web Push launch | Machine-scope environment, rendered into ignored dispatch WinSW XML | Generator aborts when absent; without it the application disables push and public-key API returns 404 |
| `PIOS_PUSH_VAPID_PRIVATE_KEY` | dispatch | VAPID signing private key | Required secret for Web Push launch | Same; backend only | Generator aborts when absent; application disables push if blank; never expose through API or frontend source |
| `PIOS_PUSH_VAPID_SUBJECT` | dispatch | RFC 8292 operator contact (`mailto:` or HTTPS URI) | Required for Web Push launch | Same | Generator aborts when absent; invalid contact can make delivery fail, so validate in preflight |
| `SPRING_DATASOURCE_URL` | every deployed database-owning backend | PostgreSQL JDBC endpoint | Required production override per service | Service environment/secret deployment layer | Current source defaults are local-development values; missing override is **not** an application-level fail-closed gate and must block deployment preflight |
| `SPRING_DATASOURCE_USERNAME` | same | PostgreSQL role | Required production override | Same | Same preflight requirement |
| `SPRING_DATASOURCE_PASSWORD` | same | PostgreSQL password | Required secret | Same | Missing/invalid normally fails connection/Flyway, but must be rejected before service launch |
| `SPRING_RABBITMQ_USERNAME` | dispatch, driver-management, order-management, passenger-experience | RabbitMQ runtime role | Required production override | Service environment/secret deployment layer | Checked-in `guest` is local-only and is not a production-safe fallback; deployment preflight must reject it |
| `SPRING_RABBITMQ_PASSWORD` | same | RabbitMQ password | Required secret | Same | Same preflight requirement |
| `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_VIRTUAL_HOST` | same | Broker endpoint and isolated vhost | Required deployment configuration (not secrets) | Same | Local defaults are not evidence of production readiness |
| `PIOS_AI_ADVISOR_QWEN_API_KEY` | ai-advisor WinSW service | Qwen provider credential | Required secret when that service/provider is enabled | Machine-scope environment, rendered into ignored XML | Generator aborts when absent |
| `PIOS_AI_ADVISOR_PROVIDER`, `PIOS_AI_ADVISOR_QWEN_MODEL` | ai-advisor WinSW service | Provider/model selection | Required by current generator | Same | Generator aborts when absent |
| `PIOS_CLOUDFLARED_TUNNEL_TOKEN` | pios-cloudflared | Connector credential for the existing `piosapp.ru` tunnel (token mode) | Required before tunnel service start | Per-service SCM `Environment` value at `HKLM:\SYSTEM\CurrentControlSet\Services\pios-cloudflared`; never source, XML, token file, or Machine-wide environment | `run-tunnel.ps1` exits `1` before invoking cloudflared when blank/missing |
| `PIOS_TEST_DATA_CREDENTIAL_HASH` / `pios.test-data.credential-hash` | driver-management only when synthetic-data API is intentionally enabled | SHA-256 hash of test-data credential | Optional; leave blank to disable | Service property/environment mapping | Blank disables the privileged test-data branch (fail closed) |

The generated `windows-services/*/pios-*.xml` files contain secrets and are
gitignored deployment artifacts. `generate-service-xml.ps1` creates an empty
temporary file, disables inherited ACLs, grants FullControl only to the WinSW
service account (LocalSystem by default), BUILTIN\Administrators, and SYSTEM,
verifies that exact allowlist, and only then writes secret-bearing XML. It
verifies the final file again after the atomic move and fails closed on any
ACL error. Run the generator from an elevated administrative shell; never
attach generated XML to tickets, logs, chat, or commits.

Deterministic verification without production values:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File windows-services\test-production-config.ps1
```

The test uses a temporary HKCU fixture registry key and the current test
account as an isolated service account. Deployment verification may inspect
only `AreAccessRulesProtected` and the ACL principals/rights; it must never
print generated file contents. Ordinary Users (`S-1-5-32-545`) and
Authenticated Users (`S-1-5-11`) must have no rule.

## VAPID generation and configuration

Generate one keypair on a trusted administrative machine, not in frontend
code and not on a public CI log. A standard compatible command is:

```powershell
npx web-push generate-vapid-keys
```

Store the two outputs separately as `PIOS_PUSH_VAPID_PUBLIC_KEY` and
`PIOS_PUSH_VAPID_PRIVATE_KEY` in the Machine-scope secret/config store. Set
`PIOS_PUSH_VAPID_SUBJECT` to an operator-controlled `mailto:` address or HTTPS
contact URI. Run `windows-services/generate-service-xml.ps1`; it validates
presence by variable name only, XML-escapes values, and never prints them.
The public key may be returned only by Dispatch's authenticated
`GET /v1/driver-push-subscriptions/public-key`; the private key must remain
backend-only. Rotating the keypair invalidates existing browser subscriptions,
so rotation requires drivers to subscribe again.

## SMS/OTP generation and configuration

Provision SMS Aero credentials without recording their values in repository
files. Generate the relay key once with `openssl rand -base64 32`, store the
result as `PIOS_OTP_RELAY_KEY`, and keep it available while queued OTP records
encrypted with it can still exist. `PIOS_SMS_API_BASE_URL` defaults to the
fixed HTTPS SMS Aero origin; connect/read timeouts default to 2000/5000 ms and
may be overridden by the non-secret variables already present in the Identity
template.

## PostgreSQL and RabbitMQ launch rule

Spring's checked-in datasource and broker values are development defaults,
not production credentials and not a production safety gate. Launch
preparation must explicitly provide per-service database roles/passwords and
non-`guest`, isolated RabbitMQ credentials, then validate connectivity without
printing values. This document does not authorize changing the checked-in core
or module `application.yml` files, applying migrations, or starting services.

## Where `pios.session.secret` actually lives

**`HKLM:\SYSTEM\CurrentControlSet\Services\<service-name>\Environment`** — a per-service `REG_MULTI_SZ` value, a standard, native Windows Service Control Manager (SCM) feature. Any Windows Service may carry its own `Environment` override at this registry path; the SCM applies it to that service's process at the moment it starts, layered on top of (and independent of) both the machine-wide environment (`HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment`) and whatever the service's own launcher places in its own configuration.

This is deliberately **not** the same thing as:
- The checked-in WinSW `.xml.template` files or their generated `.xml` counterparts (`windows-services/`) — these never carry `PIOS_SESSION_SECRET` and are not meant to; `generate-service-xml.ps1`'s own `$commonSecretNames` list only manages the owner credential (`PIOS_OWNER_USERNAME`/`PIOS_OWNER_PASSWORD_HASH`/`PIOS_OWNER_PASSWORD_SALT`), by design.
- The machine-wide environment (`Session Manager\Environment`) — checked and confirmed empty of this value in this arc's own earlier gates; that finding was correct, it was simply looking in the wrong place for this particular value.

**Any future tooling change that touches secret provisioning must be reconciled with this note first** — in particular, `generate-service-xml.ps1` does not manage this value today, and should not silently start to without this document being updated alongside.

## Which services need it

| Service | Requires `pios.session.secret`? | Role |
|---|---|---|
| `pios-identity` | **Yes** | Issues the token (`SessionTokenIssuer`) and verifies its own (`SessionTokenVerifier`) |
| `pios-dispatch` | **Yes** | Verifies (`SessionTokenVerifier`) — Proposal accept/decline, Assignment arrive/start/complete, `?driverId=` proposal listing |
| `pios-driver-management` | **Yes** | Verifies — driver availability toggle |
| `pios-order-management` | **Yes** | Verifies — order cancellation, Bearer-gated `GET /v1/orders` modes |
| `pios-passenger-experience` | **Yes** | Verifies — Circle-of-Trust endpoints |
| `pios-ai-advisor` | **No** | Carries no `SessionTokenVerifier` of any kind — gated only by the separate, independent `OwnerCredentialGate` (Basic auth, ADR-044), which is a different mechanism entirely and out of this document's scope |

`network-management` does not appear in this table at all — see the dedicated section below.

## Safe way to confirm presence — without ever exposing the value

Read the service's own registry `Environment` multi-string value, split each `NAME=value` entry, and check only whether a line begins with `PIOS_SESSION_SECRET=`. Never assign, print, log, or hash the substring after `=`.

```powershell
$services = @("pios-identity","pios-dispatch","pios-order-management","pios-driver-management","pios-passenger-experience")
foreach ($svc in $services) {
  $env = (Get-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\$svc" -ErrorAction Stop).Environment
  $present = ($env | Where-Object { $_ -like 'PIOS_SESSION_SECRET=*' }) -ne $null
  Write-Output "$svc : PIOS_SESSION_SECRET present = $present"
}
```

This is the *only* form this check should take in any future gate or deployment task: a boolean per service. It deliberately omits length and any fingerprint — a boolean alone is sufficient to confirm the prerequisite this document exists to protect ("is it there at all"), and consistency *between* services (whether they'd actually accept each other's tokens) is a materially stronger claim than presence alone and is out of scope for a presence-only check by design.

**Verified using exactly this method, presence-only, no fingerprint recorded:** as of 2026-09-04, `PIOS_SESSION_SECRET` is present on all five services listed above, and absent on `pios-ai-advisor` (correctly — it does not need one).

## `network-management` — excluded from pilot deployment scope

`network-management` is excluded from the pilot by **ADR-037** and is not part of this document's service table, the approved RC's own commit set, or the currently-running `pios-*` service list. No future deployment task should start it "because it once existed in the infrastructure" — that would be scope expansion, not a defensible operational default, per this project's own governance rules (`.claude/CLAUDE.md`: preserve existing architecture, no scope expansion without explicit instruction).

## Approved synthetic E2E smoke-test scenario

Full detail: `docs/PIOS_SMOKE_TEST_DESIGN_GATE.md` (design-only — nothing in it has been executed as of this document). Restated here, compactly, as the scenario this deployment track has approved for whenever a smoke test is actually authorized:

- **Test entities, created fresh, never a real pilot account:** one synthetic passenger (`+1555555010x`, NANP-reserved fictional range — format-only validated, never contacted) and one synthetic driver (`smoke-test-driver-<uuid>`, `isTest: true`, display name `SMOKE TEST — DO NOT DISPATCH`).
- **Sequence:** register passenger → create synthetic driver (unauthenticated by design) → associate driver with passenger identity (`POST /v1/identities/{id}/driver`) → toggle synthetic driver's availability to `AVAILABLE` → submit order (`isTest: true`, `explicitDriverIntent: true`, no auth — the accepted `POST /v1/orders` risk, unchanged) → propose to the synthetic driver (`isTest: true`) → accept → arrive → start → complete; a second order exercises cancellation separately.
- **Isolation:** `isTest: true` propagates through Order/Proposal/Assignment/Driver and is excluded from owner analytics in both `todayData.ts` and `pilotAnalytics.ts` — verified in source. `DriverHome.tsx` does **not** filter by `isTest` — isolation from a real driver's device comes from never naming a real `driverId`, not from any code-enforced filter; this is stated explicitly, not assumed. `Identity`/`Credential` rows carry no `isTest` concept at all — the synthetic passenger row is a real, permanent row in production unless separately, manually cleaned up.
- **`POST /v1/orders` ownership gap:** unchanged, accepted risk — the smoke test relies on this endpoint's actual (unauthenticated) contract, it does not work around it.
- **Cleanup:** no delete endpoint exists for any of Order/Proposal/Assignment/Driver — cleanup, if performed, is a direct, backed-up database `DELETE` scoped by `is_test = true` (same pattern already validated by this arc's own prior remediation work), or the rows may simply be left in place since they are invisible to analytics either way. Identity/credential cleanup is the one item with no `isTest` guard and requires a separate, explicit operator decision.

## What this document is, and is not

This document records a mechanism and a scenario. It does not authorize a restart, a deployment, a database mutation, or a smoke-test execution — those remain separate, explicitly-authorized tasks.
