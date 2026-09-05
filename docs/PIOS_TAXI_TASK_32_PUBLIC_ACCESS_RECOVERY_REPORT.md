# Task 32 — Restore PIOS Public Access (Cloudflare Tunnel)

**Outcome: Remediation attempted exactly as authorized. It did not succeed durably. `https://piosapp.ru` remains inaccessible. No workaround was attempted, per this task's own explicit instruction — stopping to report the exact observed failure.**

---

## 1. State Before the Fix

Confirmed identical to Task 31's own findings, re-checked immediately before acting:
- No `cloudflared` process running.
- No Windows service registered for it (`Get-Service -Name "*cloudflare*"` → empty).
- `https://piosapp.ru` → Cloudflare Error 1033.

## 2. Command Executed

Exactly as authorized, verbatim, no modification:
```powershell
Start-Process -FilePath "powershell.exe" `
  -ArgumentList "-NoProfile","-ExecutionPolicy","Bypass","-File","C:\Projects\PIOS-Foundation_v1.0\windows-services\cloudflared\run-tunnel.ps1" `
  -WindowStyle Hidden
```
`Start-Process` returned immediately with no error — it successfully launched the wrapper process.

## 3. What Happened After Launch (chronological)

| Time (local) | Observation |
|---|---|
| 23:55:01 | Wrapper `powershell.exe` (PID 15280) started, command line confirmed matching the authorized invocation exactly. |
| 23:55:05 | `cloudflared.exe` (PID 1128) started, command line: `"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel run` — token mode, as `run-tunnel.ps1` itself performs. |
| ~23:55:13 (checked) | `cloudflared tunnel info pios-pilot` → still "does not have any active connection." |
| ~23:55:13 (checked) | `Get-NetTCPConnection -OwningProcess 1128` showed an **Established** TCP connection to `198.41.200.33:7844` — Cloudflare's own tunnel-protocol edge address/port. This is a real, positive sign: outbound network/firewall access to Cloudflare's edge is not blocked. |
| ~23:57:57 (checked) | `tunnel info` still: no active connection. Public `https://piosapp.ru` → **HTTP 530** (still failing — not yet the specific pre-fix wording, but the same class of tunnel-side failure; Cloudflare returns `530` as the outer HTTP status for its `10xx`-prefixed tunnel error family generally, which includes 1033). |
| ~23:58:44 (checked) | `tunnel info` still: no active connection. |
| ~23:59+ (checked) | **`cloudflared.exe` (PID 1128) had exited on its own.** `Get-Process -Id 1128` returns nothing. The wrapper `powershell.exe` process is also gone. |

**Net result:** the process ran for roughly 3–4 minutes, established a TCP connection to Cloudflare's edge, never completed tunnel registration, and then exited by itself, without any explicit command from this task to stop it.

## 4. Did an Active Connector Appear? (Required Check 1)

**No.** Every check of `cloudflared tunnel info pios-pilot`, from immediately after launch through the process's own exit, returned:
```
Your tunnel 70a49741-67dd-4f86-8ab1-7c42186c2fbf does not have any active connection.
```
At no point did this task observe an active connection registered.

## 5. Process State (Required Check 2)

- **PID:** 1128 (now exited; no longer present).
- **Command line (confirmed while running):** `"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel run`.
- **Start time:** 2026-09-03 23:55:05.
- **End time:** not directly captured; confirmed gone by the check at ~23:59.
- **Exit code:** **not determinable** — the process was launched detached/hidden with no output or exit-code capture (exactly as the authorized command specified: `-WindowStyle Hidden`, no redirection), and no crash record was found in the Windows Application Event Log for the relevant window (checked, zero matching entries), nor did the process leave any log file anywhere under `windows-services/cloudflared/` (checked via file-modification-time search — no new file since `tunnel.token`'s own last-modified time).

**This is the exact limitation this task's own instruction anticipated ("stop and report the exact error" rather than invent a workaround) — no more precise error than "TCP-connected, never registered, then exited silently" is available from this invocation, because the authorized command itself did not capture output.** Obtaining the literal cloudflared log line that explains the registration failure would require re-invoking it with output redirected — a different invocation than the one authorized, so this task did not do that.

## 6. Local PIOS (Required Check 3)

`http://localhost:4173` → **HTTP 200**, before, during, and after the attempt. Unaffected throughout.

## 7. Public `https://piosapp.ru` (Required Check 4)

Checked from this host against the real public hostname (traverses the actual internet path to Cloudflare's edge, not a local/LAN shortcut):
- Before: Error 1033.
- During the brief TCP-established window: HTTP 530 (Cloudflare's general tunnel-error status).
- After the process exited: HTTP 530, unchanged.

**`https://piosapp.ru` is not currently reachable.** This task did not obtain external confirmation from a genuinely separate network (e.g., an actual iPhone on cellular) — checks were run from this host's own outbound connection to the public internet, which is a real round-trip to Cloudflare's edge, not a localhost shortcut, but is not the same as a fully independent client. Since the connector never registered at all, this distinction does not change the outcome.

## 8. Backend API Through the Public Proxy (Required Check 5)

**Not meaningfully testable** — since no connector ever registered, `piosapp.ru` never routed to the frontend at any point during this attempt, so there was no working page through which to exercise a proxied `/v1/...` call. This check could not be performed as intended.

## 9. Tailscale Funnel (Required Check 6)

Confirmed still active and unaffected: `tailscale status` continues to report `Funnel on: https://home-pc.tail385153.ts.net`. This task's actions had no effect on it.

## 10. PIOS Services Not Restarted (Required Check 7)

Confirmed via `Get-Service`/`Get-Process` before and after: all seven `pios-*` services remain `Running`, and every one of the four backend `java.exe` processes touched by Task 30 (PIDs 15152, 13208, 7740, 14100) — plus the untouched ones (5388, 16076, 6532, 6644) — show the **identical** start times as before this task began. **No PIOS service was stopped, started, or restarted.**

## 11. Root Cause of This Attempt's Failure — What Is and Is Not Known

**Known, with direct evidence:**
- The launch command executed exactly as authorized and did start `cloudflared.exe` in the correct mode (token mode, `tunnel run`, matching `run-tunnel.ps1`'s own design).
- Outbound network connectivity to Cloudflare's edge is **not** the blocker — a real TCP connection to `198.41.200.33:7844` was established.
- The process did not stay up — it exited on its own within a few minutes, without ever completing tunnel registration.

**Not known, and not determinable from this invocation:**
- The literal reason registration failed (e.g., the token in `tunnel.token` being invalid, expired, or revoked on Cloudflare's side; an authentication rejection; a version-incompatibility issue — `cloudflared` did report its own installed version, 2026.7.3, as outdated, but this task cannot establish whether that is related). No log line, exit code, or Event Log entry captured this.
- Whether a subsequent attempt would behave identically or differently — this task made exactly one attempt, per its own scope, and did not retry.

## 12. Remaining Risk (as requested, restated with this attempt's outcome)

The originally-flagged risk — "runs manually, does not survive a reboot, does not auto-restart on crash" — is now **directly demonstrated, not just theoretical**: this exact process exited on its own after a few minutes with nothing to restart it, which is precisely the durability gap Task 31's diagnosis called out in advance. Beyond that already-known risk, this attempt surfaces a **new, more fundamental risk**: the connector did not merely lack persistence — it never successfully registered with Cloudflare at all before exiting, which is a separate, currently-unexplained problem that installing a persistent service would not, by itself, fix (a WinSW-wrapped version of the same failing invocation would just enter the same restart/backoff loop already seen on the checked-in `pios-cloudflared.xml` definition, repeatedly failing the same way).

## 13. Final State

Identical to before this task, with one exception: `cloudflared` was launched once, ran briefly, and is no longer running. `https://piosapp.ru` remains inaccessible (HTTP 530 / effectively the same failure class as Error 1033). No PIOS service, database, RabbitMQ topology, DNS record, tunnel configuration, or application code was modified. No new Cloudflare Tunnel was created. No Windows service was installed. No workaround or alternative command was attempted.

## 14. What This Task Recommends (not authorized to act on)

Before a second remediation attempt, the operator likely needs to determine — outside this task's read-only-after-one-authorized-command scope — whether `tunnel.token` is still a valid, unrevoked credential for tunnel `pios-pilot`. The cleanest way to get the actual error text would be to run the same command once more with its output captured to a file (e.g., redirecting `cloudflared`'s own stdout/stderr) rather than hidden — but this task does not do that itself, since it is a different invocation than the one explicitly authorized, and this task's own instruction was to stop and report rather than improvise. That is a decision for the next, separately-authorized task.

---

*Task 32 complete. Per its own instruction: stopping after this report. No permanent Windows service was installed. No workaround was attempted. Public access to `https://piosapp.ru` was not restored.*
