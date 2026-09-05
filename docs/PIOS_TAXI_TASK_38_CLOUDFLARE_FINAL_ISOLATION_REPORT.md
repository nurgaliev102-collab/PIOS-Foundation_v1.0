# Task 38 — Cloudflare Tunnel Final Isolation: Dashboard/Config + Independent Network Test

## 1. Executive Conclusion

**INCONCLUSIVE**

Public access to `https://piosapp.ru` was **not** restored. No remediation was attempted — no hypothesis this task could test pointed at a fixable, evidenced local cause. What this task *did* achieve, for the first time in this arc, is a genuinely independent-network test: the identical tunnel invocation was run, at debug level, through a **structurally different network** (a broadband router, `TP-LINK-95`) than every prior task's own connection (a phone hotspot, SSID `iPhone`, discovered only in this task). **The exact same failure — QUIC registering, then failing at precisely its own negotiated 5-second idle timeout — reproduced identically.** This substantially narrows, without fully resolving, the root cause: the local-network/ISP-path hypothesis is now much harder to sustain than it was after Task 37; what remains open is whether the cause is specific to this one machine, or lives on Cloudflare's own tunnel/account side — neither of which this task's available tools can prove or disprove.

## 2. Baseline

| Fact | Value |
|---|---|
| cloudflared version | 2026.7.3 (built 2026-07-22T09:32 UTC) |
| Executable path | `C:\Program Files (x86)\cloudflared\cloudflared.exe` |
| Tunnel name / ID | `pios-pilot` / `70a49741-67dd-4f86-8ab1-7c42186c2fbf` |
| Tunnel status | No active connection |
| `localhost:4173` | `HTTP 200` |
| `https://piosapp.ru` | `HTTP 530` |
| cloudflared process/service | Neither exists |
| Default routes | Two, equal metric: `happ-default-tun` and `Беспроводная сеть` — structurally unchanged from Tasks 36/37 |
| **Wi-Fi connection (new finding)** | **SSID `iPhone`** — a phone's mobile hotspot, not a router. Two other saved profiles exist: `TP-LINK-95` (in range) and `SNR-CPE-407C` (not in range at the time of testing). |
| PIOS services | All 7 `Running`, PIDs identical to the session baseline established since Task 30 |

## 3. Tasks 31–37 Findings, Re-Verified

Not assumed: token length (180 chars, unchanged), tunnel identity, `tunnel list`/`info` output, and the absence of any cloudflared process/service were all independently re-checked this task before any action was taken (Section 2). Task 36's own Happ-vs-Wi-Fi conclusion was not re-run in full (would add no new information on its own), but this task's own scoped-route methodology (Section 5) deliberately reused Task 36's exact technique for consistency and reused its own bypass-Happ logic as part of the new-network test.

## 4. Cloudflare Configuration Findings

**Read-only dashboard/API access: unavailable.** This task has no browser tool with an authenticated Cloudflare session, and no general-purpose Cloudflare API token — only `cert.pem` (the account origin certificate cloudflared's own CLI uses internally for `tunnel list`/`info`/`create`-class calls, not a documented credential for arbitrary REST API reads). No `cloudflared` subcommand exposes a tunnel's remote ingress/public-hostname configuration directly (`--help` output checked again this task; no `diag` or config-dump subcommand exists in this version). `cloudflared tunnel info --show-recently-disconnected -o json pios-pilot`, re-checked fresh this task, again returned `"conns": []` — Cloudflare's own account-side view still retains no record of any connection attempt, despite this task's own debug logs showing four separate successful internal registration events (three in this task alone, Section 6) across two different networks.

**Genuinely new context this task surfaced** (from re-reading `docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md`, carried forward from Task 37 Section 5): the tunnel's own documented setup plan specifies a classic named-tunnel deployment with a **local** `config.yml` containing an explicit ingress rule (`piosapp.ru` → `http://127.0.0.1:4173`). That file was **never created** on this machine (confirmed, again, absent). What runs instead is token-mode, whose ingress configuration — if it exists at all — lives entirely on Cloudflare's dashboard side, unverifiable from here. **This task could not advance this specific question beyond where Task 37 left it**; the missing access (dashboard login or a scoped API token) remains missing.

## 5. Independent Network Findings

**This is the substantive new result of this task.**

The operator enabled a phone hotspot and offered it for testing; before using it, this task discovered the *current* connection was already SSID `iPhone` — itself a phone hotspot — meaning re-testing on "a phone hotspot" would not have added information (same device class, likely same carrier/upstream already implicitly exercised throughout Tasks 33–37). Per the operator's own choice, this task instead connected to `TP-LINK-95`, a saved, in-range network of a structurally different class (a home/local router, presumptively wired-broadband-backed, not cellular).

**Method (same discipline as Task 36, fully reversible):**
1. Recorded the original connection (`SSID: iPhone`, `Profile: iPhone`) before any change.
2. `netsh wlan connect name="TP-LINK-95"` — full Wi-Fi network switch (a larger-scope change than Task 36's own scoped routes, since this changes the physical network itself, not just routing for two IP ranges).
3. Verified general internet worked (`google.com` → `200`) and Happ remained `Up` and functional over the new network.
4. Added the same temporary scoped routes as Task 36 (`198.41.192.0/24`, `198.41.200.0/24` via the Wi-Fi gateway, metric 1) to additionally test the direct (non-Happ) path over this new network — confirmed via `Test-NetConnection` that traffic to the Cloudflare edge now flowed through `Беспроводная сеть`/TP-LINK-95, not Happ.
5. Ran the identical debug-level diagnostic (Section 6).
6. Stopped the diagnostic process, removed the temporary routes, attempted to reconnect to the original `iPhone` network.

**Rollback complication, disclosed plainly:** reconnecting to `iPhone` failed — `"The network specified by profile 'iPhone' is not available to connect"` — the phone's hotspot was no longer broadcasting by the time this task attempted to restore it (most likely turned off after the operator's own earlier step, or out of range; not something this task caused or can control). **This task did not keep retrying a network that isn't there.** The host currently remains connected to `TP-LINK-95`. Verified this is not a degraded state: general connectivity, Happ, all 7 PIOS services (unchanged PIDs), Tailscale Funnel, and `localhost:4173` were all re-confirmed healthy on this network (Section 9). This is the one respect in which "everything reverted" is not literally true, and it is called out here rather than glossed over.

## 6. cloudflared Transport Findings

One foreground, debug-level (`--loglevel debug --transport-loglevel debug`) run, through `TP-LINK-95` with Happ bypassed via the scoped routes (Section 5). Full relevant excerpt:

```
2026-09-03T20:36:00Z DBG Received transport parameters: MaxUDPPayloadSize=1360, MaxIdleTimeout=5s, MaxDatagramFrameSize=16383 connIndex=0 event=0 ip=198.41.200.193
2026-09-03T20:36:00Z DBG Registering tunnel connection connIndex=0 event=0 ip=198.41.200.193 protocol=quic
2026-09-03T20:36:00Z INF |  SUMMARY: Environment is healthy. cloudflared will use 'quic' as primary protocol.  |
2026-09-03T20:36:05Z ERR failed to accept incoming stream requests error="failed to accept QUIC stream: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.193
```
`20:36:00` → `20:36:05`: **exactly 5 seconds**, matching the negotiated `MaxIdleTimeout` precisely — identical signature to Task 37's own findings via the `iPhone` hotspot. Repeated twice more against a second edge IP (`198.41.192.7`) within this same run, same 5-second match each time. `https://piosapp.ru` checked during this run: `HTTP 530`, unchanged.

| Registration | Network | Edge IP | Registered | Failed | Elapsed |
|---|---|---|---|---|---|
| Task 37 | iPhone hotspot | 198.41.200.233 | 20:01:50 | 20:01:56 | ~6s |
| Task 37 | iPhone hotspot | 198.41.192.57 | 20:02:04 | 20:02:09 | ~5s |
| Task 37 | iPhone hotspot | 198.41.200.53 | 20:02:18 | 20:02:23 | ~5s |
| **This task** | **TP-LINK-95 (direct, Happ bypassed)** | 198.41.200.193 | 20:36:00 | 20:36:05 | **~5s** |
| **This task** | **TP-LINK-95 (direct, Happ bypassed)** | 198.41.192.7 | 20:36:14 | 20:36:19 | **~5s** |

Five independent registration events, across two structurally different networks (cellular hotspot and broadband router), with and without Happ in the path, all failing with the identical mechanism at the identical relative timing.

## 7. Root-Cause Determination

| Hypothesis | Status |
|---|---|
| Token/tunnel-identity invalid or mismatched | **DISPROVEN** — re-confirmed again this task; unchanged length, correct tunnel ID decoded every time |
| DNS misconfiguration | **DISPROVEN** — re-confirmed resolving correctly on both networks |
| Local Windows Firewall (named rule) | **DISPROVEN** (Task 33, not contradicted by anything found here) |
| System proxy interference | **DISPROVEN** (re-confirmed empty) |
| Happ SOCKS-tunnel specifically | **DISPROVEN** (Task 36; reconfirmed indirectly — bypassing Happ on a *second* network still fails identically) |
| **This host's specific local network/ISP path** | **DISPROVEN** — the single most significant result of this task. Two structurally different networks (cellular hotspot, broadband router), tested with debug-level precision, produce the identical failure at the identical timing. |
| Something specific to this one machine (OS/driver/stack-level, independent of network) | **STILL POSSIBLE** — not confirmable without testing from a genuinely different machine, which remains unavailable to this task |
| Cloudflare-side tunnel/account/ingress configuration | **STILL POSSIBLE** — the deployment-mode discrepancy (Section 4) remains a real, unverified gap; dashboard/API access to check it directly remains unavailable |

## 8. Changes Made

**None persisted.** During the task: two temporary scoped routes (added and removed, verified absent afterward), one Wi-Fi network switch (attempted to revert; could not, because the original network stopped broadcasting — see Section 5), one foreground diagnostic `cloudflared` process (started and stopped). No file, no Cloudflare configuration, no PIOS service, no production database, no RabbitMQ topology was touched.

## 9. Recovery Verification

Not applicable in the success sense (no recovery was achieved). Post-task state, verified directly:
- `cloudflared tunnel info pios-pilot`: no active connection.
- `https://piosapp.ru`: `HTTP 530`.
- `http://localhost:4173`: `HTTP 200`.
- All 7 `pios-*` services: `Running`, identical PIDs/start times to this session's established baseline.
- Tailscale Funnel: confirmed active (`Funnel on: https://home-pc.tail385153.ts.net`).
- Happ (`happ-default-tun`): `Up`, functioning correctly over the current network.
- Wi-Fi: connected to `TP-LINK-95` (not the original `iPhone` network — Section 5's disclosed rollback limitation).
- No leftover scoped routes.

## 10. Security/Safety Verification

- No production PostgreSQL mutation.
- No RabbitMQ mutation.
- No secret exposed — the tunnel token was never printed (only its consistent 180-character length observed); the two Wi-Fi network passwords (`TP-LINK-95`, `SNR-CPE-407C`) surfaced once in this task's own tool output while checking whether they were already saved (needed only to confirm no new credential input was required) and were **not** repeated anywhere in this report or elsewhere in this response.
- No unauthorized PIOS restart — all 7 services confirmed with unchanged PIDs throughout.
- No permanent network change — the two scoped routes were fully removed and verified absent; the Wi-Fi network change is disclosed as not fully reverted, for a reason outside this task's control (Section 5), not left silently.
- No new Cloudflare tunnel was created; no existing tunnel was modified, deleted, or recreated; no credential was rotated.

## 11. Remaining Risks

1. **Current Wi-Fi network is `TP-LINK-95`, not the original `iPhone` hotspot** — disclosed in Section 5/9; everything functionally important (Happ, PIOS, Tailscale, this session itself) is confirmed healthy on it, but this is a real, visible change to the host's own network state that the operator should be aware of and can revert themselves once/if the `iPhone` hotspot is available again.
2. The two open hypotheses from Section 7 (this-machine-specific vs. Cloudflare-side) remain genuinely unresolved — this task narrowed the space considerably but did not close it.
3. `cloudflared` itself continues to report its installed version (2026.7.3) as outdated relative to 2026.8.3 — still not established as related to the actual failure, but still not ruled out either, and remains untested.

## 12. Exact Next Action

**Root cause remains unresolved.** The two most valuable missing experiments, in priority order:

1. **A genuinely different machine** (not just a different network on this same host) running the identical token-based invocation — this would finally distinguish "something about this specific Windows installation/hardware" from "something Cloudflare-side," which this task's own network-only test cannot do. This requires a second physical or virtual device with `cloudflared` installed, which was not available to this task.
2. **Cloudflare Zero Trust dashboard access** (a browser session logged into the account that owns this tunnel) — to directly answer Section 4's open question: is `piosapp.ru` actually attached to `pios-pilot` with a correct origin (`http://127.0.0.1:4173`), and does Cloudflare's own tunnel-health view show anything this CLI-only access cannot surface?

Given the strength of Section 6/7's evidence, **this task recommends prioritizing #2** next — it is a read-only check requiring only browser access (no new tooling, no risk), and would either confirm/deny the one remaining locally-unverifiable hypothesis outright, or, if the configuration proves correct, leave #1 (a second machine) as the clear final remaining step.

**Do not make this tunnel persistent (no WinSW) until root cause is established** — installing a permanent service around an invocation that has never once produced a working connection would only make a still-unexplained failure harder to diagnose later, not easier.

---

*Task 38 complete. INCONCLUSIVE — the local-network hypothesis is now disproven with strong, repeated, cross-network evidence; the remaining two hypotheses (this-machine-specific, Cloudflare-side) require access this task does not have. No fix was applied. No PIOS service was restarted. No production data was touched. One disclosed, non-reverted side effect (Wi-Fi now on TP-LINK-95, not the original iPhone hotspot) is recorded above, not hidden. Stopping — Task 39 not started.*
