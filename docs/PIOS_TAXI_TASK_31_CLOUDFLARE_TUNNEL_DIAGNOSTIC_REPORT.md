# Task 31 — PIOS Public Web Access / Cloudflare Tunnel Diagnosis

**Type: Strictly read-only diagnosis.** No code, configuration, Cloudflare setting, DNS record, or secret was modified. No service was started, stopped, or restarted. No secret value was printed at any point.

---

## 1. Exact Current State

- **`piosapp.ru` returns Cloudflare Error 1033** ("Cloudflare Tunnel error — Cloudflare is currently unable to resolve it"), confirmed consistent with direct evidence from Cloudflare's own account (Section 3).
- **`cloudflared` is not running on this host** — no process, no Windows service.
- **No Windows service named anything containing "cloudflare" is currently registered** on this machine at all (`Get-Service` returns zero matches), despite a WinSW service definition for one (`pios-cloudflared`) existing in this repository.
- **The named tunnel `pios-pilot` (ID `70a49741-67dd-4f86-8ab1-7c42186c2fbf`) is registered on Cloudflare's account and DNS-routed to `piosapp.ru`, but currently has zero active connector connections**, per a direct, read-only query against Cloudflare's own tunnel registry.
- **The origin the tunnel is meant to reach — the PIOS frontend on `127.0.0.1:4173` — is healthy and responding** (`HTTP 200`).
- **The pilot's actual, currently-active public transport is Tailscale Funnel** (`https://home-pc.tail385153.ts.net`), confirmed running and healthy — this is a separate, already-working public path to the same healthy origin, unaffected by this issue.

## 2. Root Cause

**Cloudflared has no running connector process for the `pios-pilot` tunnel — this is the direct, immediate cause of Error 1033.** There is no Windows service and no process currently attempting to connect it, so Cloudflare's edge has nothing to route `piosapp.ru` traffic to.

There are two independent, non-exclusive contributing conditions behind that:

1. **No cloudflared service is currently installed/running on this host at all**, regardless of which launch method would be used.
2. **The checked-in service definition (`windows-services/cloudflared/pios-cloudflared.xml`) would not succeed even if started as-is**: it invokes `cloudflared tunnel --config C:\...\windows-services\cloudflared\config.yml run`, and **`config.yml` does not exist on this filesystem**. The only currently-viable way to run this specific tunnel on this machine is the alternate, gitignored `run-tunnel.ps1` script, which launches `cloudflared` in **token mode** instead (using `tunnel.token`, which does exist) — because, per that script's own comment, this tunnel has no local per-tunnel credentials JSON file, only the account-level `cert.pem`. `run-tunnel.ps1` is not wired to any Windows service or scheduled task; nothing currently invokes it automatically.

**Contextual, likely explanatory factor (not itself the technical cause, but strongly relevant):** an already-committed operational change, `72655f7` ("ops: switch pilot public transport from Cloudflare Tunnel to Tailscale Funnel," 2026-08-16), deliberately made Tailscale Funnel the pilot's primary public transport. Its own commit message states it "keeps the piosapp.ru/.trycloudflare.com entries in place" — i.e., the DNS/tunnel registration was deliberately left intact rather than torn down, while the active connector was not kept running. Every backend service's own `PIOS_PILOT_FRONTEND_ORIGIN` (confirmed across all five WinSW XML files) has pointed at the Tailscale hostname since that commit, not at `piosapp.ru`. This is consistent with `piosapp.ru` having been intentionally demoted rather than accidentally broken — but this task cannot determine current operator intent, only the technical facts and the historical record. See Section 8.

## 3. Evidence (independent items, more than the required two)

1. **`Get-Service -Name "*cloudflare*"`** — zero results. No cloudflared Windows service is registered on this host.
2. **`Get-Process -Name "cloudflared"`** — zero results. No cloudflared process is running.
3. **`cloudflared tunnel list`** (a read-only query against Cloudflare's own account, authenticated via the existing local `cert.pem`, not a config-changing call):
   ```
   ID                                   NAME       CREATED              CONNECTIONS
   70a49741-67dd-4f86-8ab1-7c42186c2fbf pios-pilot 2026-08-10T13:53:03Z
   ```
   The `CONNECTIONS` column is **empty** — Cloudflare's own control plane confirms zero active connectors for this tunnel right now. This is the most direct possible confirmation of the root cause, independent of anything observed locally.
4. **`config.yml` absence**: `windows-services/cloudflared/config.yml` — does not exist (`ls`/`Test-Path` both negative). The checked-in service definition's own required argument is missing.
5. **`nslookup piosapp.ru`** — resolves correctly to Cloudflare's own anycast edge IPs (`104.21.44.18`, `172.67.193.241`, and IPv6 equivalents) — DNS itself is functioning normally; the domain is correctly proxied through Cloudflare. This rules out a DNS resolution problem.
6. **Origin reachability**: `curl http://127.0.0.1:4173/` from this same host returns `HTTP 200` with a normal HTML response — the frontend the tunnel is configured to reach is healthy.
7. **`tailscale status`** — confirms Tailscale Funnel is active (`Funnel on: https://home-pc.tail385153.ts.net`) and serving the same origin successfully via a separate public path, corroborating that the underlying application stack is not the problem.
8. **No cloudflared log file exists anywhere searched** (`windows-services/cloudflared/`, common log locations) — consistent with the service never having been started as a persistent process on this host, or having been stopped cleanly enough to leave nothing (the WinSW definition also explicitly disables its own wrapper log, `<log mode="none"/>`).

## 4. Affected Component

**Only the `piosapp.ru` public access path (the Cloudflare named tunnel `pios-pilot`) is affected.** No PIOS backend service, no database, no RabbitMQ topology, and no other public access path is implicated.

## 5. Is PIOS Backend/Frontend Itself Healthy?

**Yes.** The frontend origin the tunnel would route to (`127.0.0.1:4173`) responds `HTTP 200`. This is consistent with Task 30's own final runtime-health confirmation of all four redeployed backend services plus the untouched frontend/identity/passenger-experience services. The pilot is currently reachable and functioning via its other, already-active public path (Tailscale Funnel, `https://home-pc.tail385153.ts.net`) — this diagnosis found no evidence of any application-level regression connected to `piosapp.ru`'s failure.

## 6. Root Cause Classification (per this task's own options)

- **A) cloudflared not running — YES, this is the direct cause**, confirmed both locally (no process, no service) and remotely (Cloudflare's own tunnel list shows zero connections).
- B) cloudflared running but disconnected — No; nothing is running at all, so this doesn't apply.
- **C) wrong/missing tunnel configuration — partially, as a contributing factor**: the checked-in WinSW service definition references a `config.yml` that doesn't exist, so it could not be started successfully even if the service itself were installed and invoked as written. The tunnel's own registration/ID is *not* wrong — it matches Cloudflare's account exactly (Section 3, item 3).
- D) origin/frontend unavailable — No; confirmed healthy (Section 3, item 6).
- E) DNS/Cloudflare-side issue — No; DNS resolves correctly, and Cloudflare's own account shows the tunnel correctly registered — the issue is the absence of a live connector, not a DNS or account-side fault.
- F) another concrete cause — Not identified beyond A/C above.

## 7. Exact Minimal Remediation Required (not performed by this task)

The **minimum** action to restore `piosapp.ru` is: start a `cloudflared` connector process for tunnel `pios-pilot` on this host, using the **token-mode** path that is actually viable given the current credential files present (`run-tunnel.ps1` + `tunnel.token`), since the config-file-mode path referenced by the checked-in WinSW XML cannot work until a `config.yml` is created (per that file's own committed comment, this is expected to be done "once, by hand" and is deliberately not in version control).

This task does not perform or recommend a specific implementation of that remediation (e.g., whether to install `run-tunnel.ps1` as a proper Windows service, run it manually, or first resolve whether `piosapp.ru` should even remain a maintained path given the Tailscale Funnel switch) — that is a decision for the next task, made with explicit authorization.

## 8. Does Remediation Require a Service Restart?

**Not of any PIOS application service.** Restoring `piosapp.ru` requires starting (not restarting — nothing is currently running) a `cloudflared` connector process. This is entirely independent of, and would not require touching, any of the seven `pios-*` application services (dispatch, order-management, driver-management, passenger-experience, identity, ai-advisor, frontend) — none of which this task touched, per its own explicit constraint.

## 9. Security Considerations

- **No secret content was printed at any point in this task.** The token-mode credential (`tunnel.token`) and the account certificate (`cert.pem`) were confirmed to exist by file listing only (path, size, modified date) — their contents were never read or displayed.
- `cloudflared tunnel list` was run because it is a **read-only** query against Cloudflare's account (equivalent to a `GET`/list API call) — it does not create, modify, delete, or rotate any tunnel, credential, or DNS record. No `cloudflared tunnel run`, `cloudflared tunnel token`, `cloudflared login`, or any other state-changing `cloudflared` subcommand was executed.
- `tunnel.token` (181 bytes) and `cert.pem` (282 bytes) both exist on disk, unmodified, exactly as found. No fingerprint was taken of either, since their mere presence (not their content or validity) was the only fact needed for this diagnosis.
- No `.gitignore`-excluded file's contents were read or committed. No credential left this machine.

## 10. Explicit Statement — What Was NOT Checked or Is NOT Determinable

- **This task did not determine whether `cloudflared`, if started, would actually succeed in re-establishing a connection** (e.g., whether the token in `tunnel.token` is still valid/unexpired/unrevoked on Cloudflare's side) — only that the prerequisite files exist and the tunnel itself is still registered. A successful `tunnel list` proves the *account* credential (`cert.pem`) is valid; it does not by itself prove the *connector token* (`tunnel.token`) is still valid, since those are different credentials for different purposes.
- **Whether `piosapp.ru` is still intended to be a maintained, active public path, or was deliberately left to lapse when Tailscale Funnel became primary, is not determinable from the repository or host alone** — Section 2's contextual finding is evidence toward "deliberately demoted," not proof of current operator intent. This is a product/ops decision, not a technical fact this diagnosis can settle.
- **Whether the checked-in `pios-cloudflared.xml` WinSW service was ever actually installed on this specific machine and later uninstalled, or was never installed here at all, was not determined** — `Get-Service` only shows the current state, not history, and no Windows Event Log or install-record search was performed (out of this task's read-only-diagnosis scope, and not necessary to establish the root cause already confirmed directly via Cloudflare's own account).
- **The Cloudflare-side tunnel configuration (ingress rules, public hostname routing) was not inspected** beyond `tunnel list`'s own summary — `cloudflared tunnel info pios-pilot` (also read-only) was not run; the existing evidence was already sufficient to establish root cause without it, and this task stopped once that bar was clearly met rather than gathering more than necessary.
- **cloudflared's own installed version (2026.7.3) is reported as outdated** by its own `tunnel list` output ("We recommend upgrading it to 2026.8.3") — this is unrelated to the current outage (the binary is not currently running at all) but is noted for the operator's awareness; this task did not investigate whether the outdated version has any bearing on a future reconnection attempt.

---

## 11. Addendum — Follow-Up Diagnosis (same Task 31, re-issued with additional requirements)

A second Task 31 prompt asked for PID/crash history, an explicit ingress check, an exact proposed remediation command (to stop before, not execute), and an explicit answer on iPhone reachability. Re-verified nothing changed since Section 1–10 (service/process state, frontend health — identical). New findings below.

### 11.1 Crash/exit history
No history exists because there is nothing to have a history of: `HKLM:\SYSTEM\CurrentControlSet\Services\pios-cloudflared` **does not exist in the registry at all** (`Test-Path` → `False`), and a search of the most recent 3000 Windows **Application** and **System** event log entries each for any mention of "cloudflare" returned **zero results**. This is stronger than Section 2's earlier "not determined": it now positively indicates the `pios-cloudflared` service has never been installed on this specific host (no install trace, no start/stop/crash record) — not a case of "it used to run and crashed," but "it has not been running here as a service, ever, within the current log/registry retention."

### 11.2 Ingress / hostname routing — direct check
`cloudflared tunnel info pios-pilot` (read-only):
```
Your tunnel 70a49741-67dd-4f86-8ab1-7c42186c2fbf does not have any active connection.
```
Even more direct than `tunnel list`'s empty column. No ingress rules are returned locally because, per `run-tunnel.ps1`'s own comment (Section 3 of the original diagnosis) and confirmed by this command's own output, this tunnel's public-hostname routing is configured on Cloudflare's side (dashboard-managed, token-mode connector), not in any local `ingress:` block — there is no local ingress config to inspect for this tunnel, and that absence is expected, not itself a fault.

### 11.3 New finding relevant to the iPhone question — CORS origin scope
Checked whether `piosapp.ru` is still accepted end-to-end, not just tunnel-routed:
- **Vite's `allowedHosts`** (`frontend/vite.config.ts`): `['.trycloudflare.com', 'piosapp.ru', 'home-pc.tail385153.ts.net']` — **`piosapp.ru` is still present**, confirming the Tailscale-switch commit was additive, not a removal, exactly as its own message claimed.
- **Backend CORS** (`WebCorsConfiguration.kt`, dispatch and equivalently every module): allowed origins come from `PIOS_PILOT_FRONTEND_ORIGIN`, which is currently `https://home-pc.tail385153.ts.net` only (confirmed in every WinSW service's registry `Environment`) — **`piosapp.ru` is not in this list.**
- **Practical impact is likely none for normal page use**, because the browser only ever talks to `piosapp.ru` itself — `vite preview`'s own `proxy` table (`frontend/vite.config.ts`) forwards `/v1/*` calls **server-side**, from the Vite process to each backend port, which is not a browser-originated cross-origin request and is therefore not subject to CORS at all. CORS would only matter if some client code made a **direct** browser request to a backend port under a different origin than `piosapp.ru`, which is not how this frontend is built to behave. **Not verified live** (the tunnel is down), so this is flagged as a post-recovery check (Section 11.6), not asserted as certain.

### 11.4 Exact Root Cause (restated plainly)
No `cloudflared` connector process exists anywhere for tunnel `pios-pilot` — confirmed with no install/crash trace locally and an explicit "does not have any active connection" from Cloudflare's own account. The origin (frontend, port 4173) is healthy. Nothing about DNS, the tunnel's registration, or the origin is broken — only the connector is absent.

### 11.5 What Works / What Doesn't

**Works right now:**
- PIOS backend + frontend (confirmed healthy, Task 30 + this task's own re-check, `HTTP 200` on `127.0.0.1:4173`).
- Public access via Tailscale Funnel: `https://home-pc.tail385153.ts.net`.
- DNS for `piosapp.ru` (resolves correctly to Cloudflare's edge).
- The tunnel's registration and credentials on Cloudflare's side (`tunnel list`/`tunnel info` both succeed against the account).
- The token-mode credential (`tunnel.token`) and account cert (`cert.pem`) both exist on disk.

**Does not work:**
- `https://piosapp.ru` — Error 1033, because no connector is running.
- The checked-in `pios-cloudflared.xml` service definition, if started as-is — it requires `config.yml`, which does not exist.

### 11.6 Minimal Fix — Exact Proposed Command (NOT EXECUTED — stopping for confirmation)

**Proposed command:**
```powershell
Start-Process -FilePath "powershell.exe" `
  -ArgumentList "-NoProfile","-ExecutionPolicy","Bypass","-File","C:\Projects\PIOS-Foundation_v1.0\windows-services\cloudflared\run-tunnel.ps1" `
  -WindowStyle Hidden
```

**Why this exact command:** it runs the already-existing, already-committed-intent script (`run-tunnel.ps1`) with the already-existing token credential (`tunnel.token`) — no file is modified, no new tunnel is created, no DNS record changes, no application code changes. It is the only currently-viable launch path, since the alternative (the checked-in WinSW service, config-file mode) cannot work without a `config.yml` that does not exist and this task was told not to create configuration.

**Expected effect:** a `cloudflared.exe` process starts in token mode, authenticates as tunnel `pios-pilot`, and establishes a connection to Cloudflare's edge — typically within 10–30 seconds. Once connected, `tunnel list`/`tunnel info` would show an active connection, and `https://piosapp.ru` would begin serving traffic again, routed to the already-healthy frontend on port 4173, exactly as it did before this component stopped.

**Important limitation to disclose before this is authorized:** this command runs the tunnel as a plain background process, **not** as a Windows service. It will **not** survive a reboot of this machine and will **not** auto-restart if it crashes — WinSW's own restart/backoff policy (already relied on by all seven `pios-*` services) would not apply to it. It restores access now; it does not make that access durable. Making it durable would require either editing the checked-in `pios-cloudflared.xml` to invoke `run-tunnel.ps1` instead of the missing `config.yml`, or installing a new service pointed at the script — both are the kind of configuration/architecture change this task was explicitly told not to make without separate authorization, so neither is proposed here as part of the minimal fix.

**This command was not run.** Awaiting explicit confirmation before executing it or any alternative.

### 11.7 Can `https://piosapp.ru` Be Opened From an iPhone After This Fix?

**Yes, expected to work normally once the connector is live** — Error 1033 is entirely edge-side (Cloudflare has no connector to route to); it has nothing to do with client device, network, or location. An iPhone on cellular data or Wi-Fi resolves the same public DNS (Section 3, item 5) and reaches the same Cloudflare edge as any other client. `piosapp.ru` remains in Vite's own `allowedHosts` (Section 11.3), so the page itself will load. The one thing not verified live (Section 11.3's CORS note) is a lower-confidence, likely-inconsequential detail for normal page use, not a reason to expect the page itself to fail to load.

### 11.8 Required Post-Recovery Checks

1. `cloudflared tunnel info pios-pilot` shows an active connection (not "does not have any active connection").
2. `https://piosapp.ru` loads the passenger/driver landing page from an external network (e.g., cellular, not this LAN/Tailscale) — confirms the fix from outside, not just locally.
3. From that same external load, exercise one read-only flow that calls a backend `/v1/...` endpoint through the page (e.g., viewing a driver invite page) — confirms Vite's own proxy is correctly forwarding through the tunnel, and surfaces the CORS question from Section 11.3 concretely if it exists.
4. Confirm Tailscale Funnel (`home-pc.tail385153.ts.net`) still works unaffected — this fix should not touch it.
5. Confirm no PIOS backend service needed a restart (per Section 8, none should have) — check all seven `pios-*` services remain in the same running state as before this fix.
6. If the operator wants this durable across a reboot, treat that as its own, separately-authorized follow-up — not assumed by this fix.

---

*Diagnosis complete (original + follow-up). Per both tasks' own instruction: the tunnel was not fixed, cloudflared was not started, no PIOS service was restarted, no Cloudflare/DNS/tunnel configuration was changed, no application code was touched. Stopping for review and explicit confirmation before Section 11.6's command or any alternative is executed.*
