# PIOS Deployment Secrets — `pios.session.secret`

**Status: confirmed, currently active mechanism.** This document exists to close the exact gap that made this value invisible to every earlier read-only gate in the pilot's security-remediation arc: nothing pointed at where to look. It records *where the value lives* and *how to safely confirm its presence* — it never records, logs, or fingerprints the value itself, in this document or in any check it prescribes.

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
