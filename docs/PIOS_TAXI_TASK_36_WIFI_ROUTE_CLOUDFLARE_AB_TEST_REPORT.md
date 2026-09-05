# Task 36 — Controlled Wi-Fi Route A/B Test for Cloudflare Tunnel

**Type: Diagnostic only, with one temporary, fully-reverted routing change.** Happ was never disabled or reconfigured. No WFP, DNS, Cloudflare Tunnel, token, PIOS code, PostgreSQL, RabbitMQ, Windows Firewall, or Windows service was touched. The routing change made was scoped, verified, and rolled back exactly.

**Result: Case B — the connector-registration failure reproduces identically via the direct Wi-Fi path, with Happ's route completely out of the picture.** The Happ default route is not the cause. `piosapp.ru` did not become reachable during this test.

---

## 1. Executive Summary

Rather than changing the general default route (which would have risked this very session's own connectivity to Anthropic, since that traffic almost certainly also transits the same default path), this task added two temporary, narrowly-scoped static routes covering exactly the two Cloudflare tunnel-edge `/24` ranges cloudflared has been observed contacting (`198.41.192.0/24`, `198.41.200.0/24`), pointed at the Wi-Fi gateway with a low metric. This forced *only* traffic to those specific ranges through Wi-Fi, leaving the general default route — and everything else on this machine, including this session's own connectivity — completely untouched. Verified before and after with a direct TCP probe.

With Happ structurally excluded from the path to the Cloudflare tunnel edge, the diagnostic `cloudflared` run reproduced the **exact same failure signature** already seen in Tasks 33–35: QUIC dials/streams timing out with `"timeout: no recent network activity"` across multiple edge IPs, a fallback to HTTP/2 that then stalls silently, and `piosapp.ru` remaining at `HTTP 530` throughout. One connector ID did register momentarily and remained visible in `tunnel info` for the duration of this run — but, exactly as Task 36's own critical rule anticipated, that alone did not translate into a working public endpoint, and per the same rule this is **not** counted as success.

Both temporary routes were removed immediately after the one diagnostic run, and the routing table was verified, byte-for-byte, back to its pre-test state.

## 2. Baseline Routing (Phase 0)

**Default routes (`Get-NetRoute -DestinationPrefix "0.0.0.0/0"`):**

| DestinationPrefix | NextHop | InterfaceAlias | InterfaceIndex | RouteMetric |
|---|---|---|---|---|
| 0.0.0.0/0 | 10.6.7.2 | happ-default-tun | 42 | 0 |
| 0.0.0.0/0 | 192.168.0.1 | Беспроводная сеть (Wi-Fi) | 4 | 0 |

**Interfaces:**

| Interface | ifIndex | Status | IPv4 Address | IPv4 InterfaceMetric | Description |
|---|---|---|---|---|---|
| happ-default-tun | 42 | Up | 10.6.7.1/30 | (automatic) | SocksTunnel Tunnel |
| Беспроводная сеть | 4 | Up | 192.168.0.102/24 | 55 | Intel(R) Centrino(R) Wireless-N 2230 |
| Tailscale | 41 | Up | 100.98.250.89/32 | 5 | Tailscale Tunnel |

No pre-existing specific route for either `198.41.192.0/24` or `198.41.200.0/24` — traffic to them fell through to whichever default route Windows preferred (confirmed: Happ, by combined effective metric, since `happ-default-tun` reports a synthetic 100 Gbps link speed that yields a very low automatic interface metric).

**Other baseline facts:**
- `cloudflared tunnel info pios-pilot`: "does not have any active connection."
- `localhost:4173`: `HTTP 200`.
- `Test-NetConnection 198.41.200.53:7844`: succeeded, via `happ-default-tun`, source `10.6.7.1`.
- No `cloudflared` process running.

## 3. Safety/Rollback Verification (Phase 1)

Both Wi-Fi and Happ interfaces confirmed `Up` before any change. **The planned change was deliberately narrowed from "adjust the general default route metric" to "add two host-range-specific routes"** — a stricter, safer implementation of this task's own instruction than a literal reading might suggest, chosen because a general default-route change could not be proven safe for this session's own connectivity (which very plausibly also transits `happ-default-tun`), whereas the two Cloudflare-tunnel-specific `/24` ranges added here do not overlap with anything this session's own traffic would use. This made the rollback method trivial and exact: delete precisely the two routes added, by prefix, next-hop, and interface — no ambiguity about restoring an original metric value.

## 4. Exact Temporary Change (Phase 2)

```powershell
New-NetRoute -DestinationPrefix "198.41.192.0/24" -InterfaceIndex 4 -NextHop 192.168.0.1 -RouteMetric 1
New-NetRoute -DestinationPrefix "198.41.200.0/24" -InterfaceIndex 4 -NextHop 192.168.0.1 -RouteMetric 1
```
No gateway, IP, DNS, or interface was changed; no interface was disabled; Happ's own configuration was not touched. Verified immediately: `Test-NetConnection 198.41.200.53:7844` now resolved via `Беспроводная сеть`, source `192.168.0.102` — the path change took effect exactly as intended, scoped to only these ranges.

## 5. Cloudflared Result Through Wi-Fi (Phase 3)

Launched foreground (not hidden), same token-loading logic as `run-tunnel.ps1` (token never printed; length 180, unchanged from Tasks 33/34/35's own runs), no forced transport protocol (same as the tunnel's own default QUIC-first behavior). stdout/stderr captured to local files.

Timeline:
- `19:45:11` — started; connectivity precheck: all 7 components PASS (identical shape to Tasks 33/34), `"cloudflared will use 'quic' as primary protocol."`
- `19:45:18`–`19:45:59` — three consecutive QUIC dial/stream failures across **three different edge IPs** (`198.41.200.13`, `198.41.200.193`, `198.41.200.53`), each with the identical error text already seen in Task 33, exponential backoff (4s → 8s → 16s → 32s).
- `19:46:14` — `"Switching to fallback protocol http2"`.
- `19:46:39` (last check, 25s after the fallback attempt began) — **no further log line at all** — the same silent stall already observed in Task 34's own HTTP/2-forced run.

## 6. Connector Status

`cloudflared tunnel info pios-pilot`, checked at `19:45:50`-ish and again at `19:46:39`, both showed:
```
CONNECTOR ID                         CREATED              ARCHITECTURE  VERSION  ORIGIN IP     EDGE
6e537971-4090-4458-ab47-4f83732b02f0 2026-09-03T19:45:50Z windows_amd64 2026.7.3 77.79.178.178 1xwaw04
```
A connector row **did** remain visible across both checks (unlike Task 33, where it later disappeared) — this is a genuinely different observation from the prior Happ-routed runs. **Per this task's own critical rule, this alone is not treated as success**, since the public endpoint never worked during the same window (Section 7) and the underlying log shows the QUIC/HTTP2 connections themselves continuing to fail throughout. The persisting connector row appears to reflect an administrative/control-plane registration state that does not require, or guarantee, an actual working data-plane connection — consistent with what Task 33 already suggested.

## 7. `piosapp.ru` Result

Checked three times during the run (immediately after the connector first appeared, again ~30s later, and again after the HTTP/2 fallback had stalled): **`HTTP 530` every time, no change from the pre-test baseline.** No safe GET through the public endpoint could be meaningfully attempted, since the endpoint never returned anything but this failure status — there was no working page to GET from.

## 8. Exact stdout/stderr Evidence

Full captured stderr (stdout was empty, as in every prior run) — nothing omitted beyond the repeated per-component `precheck` key=value lines (identical content to the table already shown, all `status=pass`):

```
2026-09-03T19:45:11Z INF Starting tunnel tunnelID=70a49741-67dd-4f86-8ab1-7c42186c2fbf
2026-09-03T19:45:11Z INF Version 2026.7.3 (Checksum 8635da433b6df8194746e88ed9d2589566c20e38bfc2a80e431a348b7c765841)
2026-09-03T19:45:11Z INF GOOS: windows, GOVersion: go1.26.4, GoArch: amd64
2026-09-03T19:45:11Z INF Generated Connector ID: 6e537971-4090-4458-ab47-4f83732b02f0
2026-09-03T19:45:11Z INF Initial protocol quic
2026-09-03T19:45:11Z INF Tunnel connection curve preferences: [...] connIndex=0 event=0 ip=198.41.200.13
2026-09-03T19:45:11Z INF Starting metrics server on 127.0.0.1:20241/metrics
2026-09-03T19:45:11Z INF |  SUMMARY: Environment is healthy. cloudflared will use 'quic' as primary protocol.  |
2026-09-03T19:45:11Z INF precheck complete hard_fail=false run_id=e88bc4cf-b2f3-4471-91d5-b709737f5b2a suggested_protocol=quic
2026-09-03T19:45:18Z INF Tunnel connection curve preferences: [...] connIndex=0 event=0 ip=198.41.200.13
2026-09-03T19:45:23Z ERR Failed to dial a quic connection error="failed to dial to edge with quic: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.13
2026-09-03T19:45:23Z INF Retrying connection in up to 4s connIndex=0 event=0 ip=198.41.200.13
2026-09-03T19:45:25Z INF Tunnel connection curve preferences: [...] connIndex=0 event=0 ip=198.41.200.193
2026-09-03T19:45:31Z ERR failed to run the datagram handler error="context canceled" connIndex=0 event=0 ip=198.41.200.193
2026-09-03T19:45:31Z ERR failed to accept incoming stream requests error="failed to accept QUIC stream: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.193
2026-09-03T19:45:31Z ERR failed to serve tunnel connection error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.200.193
2026-09-03T19:45:31Z ERR Serve tunnel error error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.200.193
2026-09-03T19:45:31Z INF Retrying connection in up to 8s connIndex=0 event=0 ip=198.41.200.193
2026-09-03T19:45:36Z INF Tunnel connection curve preferences: [...] connIndex=0 event=0 ip=198.41.200.193
2026-09-03T19:45:41Z ERR Failed to dial a quic connection error="failed to dial to edge with quic: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.193
2026-09-03T19:45:41Z INF Retrying connection in up to 16s connIndex=0 event=0 ip=198.41.200.193
2026-09-03T19:45:53Z INF Tunnel connection curve preferences: [...] connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:45:59Z ERR failed to run the datagram handler error="context canceled" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:45:59Z ERR failed to accept incoming stream requests error="failed to accept QUIC stream: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:45:59Z ERR failed to serve tunnel connection error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:45:59Z ERR Serve tunnel error error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:45:59Z INF Retrying connection in up to 32s connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:46:14Z INF Switching to fallback protocol http2 connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:46:14Z INF Tunnel connection curve preferences: [...] connIndex=0 event=0 ip=198.41.200.53
```
*(no further line for the remainder of the observation window — the same silent-stall pattern as Task 34's own forced-HTTP/2 run)*

## 9. Rollback Verification (Phase 4)

- Diagnostic `cloudflared` process (PID 10388) stopped; confirmed no `cloudflared` process of any kind remains.
- Both temporary routes removed (`Remove-NetRoute` for `198.41.192.0/24` and `198.41.200.0/24`, exact prefix/next-hop/interface match); `Get-NetRoute` for those two prefixes now returns nothing.
- Default-route table re-checked: **identical to Section 2's baseline**, byte-for-byte (same two routes, same next-hops, same metrics).
- `Test-NetConnection 198.41.200.53:7844` re-run: **Happ (`happ-default-tun`, source `10.6.7.1`) is again the preferred path** — confirms the rollback is not just table-level but behaviorally effective.

## 10. A/B Comparison With Tasks 33–35

| | Via Happ (Tasks 33/35) | Via Wi-Fi (Task 36, this test) |
|---|---|---|
| Connectivity precheck | All PASS | All PASS |
| QUIC dial/stream error text | `"timeout: no recent network activity"`, repeated | **Identical text, repeated** |
| Edge IPs affected | 198.41.200.53, 198.41.192.7, 198.41.192.107 (Task 33) | 198.41.200.13, 198.41.200.193, 198.41.200.53 (this test) — different specific IPs, same failure |
| HTTP/2 fallback | Silent stall, no further log line (Task 34) | **Identical silent stall** |
| Connector registration | Brief, then disappeared (Task 33) | Registered, remained visible for the observed window (this test) — a genuine difference in this one specific observation |
| `piosapp.ru` | 530 throughout | **530 throughout** |

## 11. Root-Cause Classification

**Case B, per this task's own matrix: the same failure reproduces with the Happ route entirely excluded from the path.**

**What this rules out, with direct, controlled evidence:** the Happ SOCKS-tunnel route being the mechanism responsible for the QUIC/HTTP2 registration failure. With traffic to the specific Cloudflare tunnel-edge ranges forced through a completely different interface, gateway, and network stack path, the failure signature — same error text, same retry/backoff shape, same HTTP/2 stall — did not change in any way that would indicate Happ-specific interference.

**What this does not resolve:** per this task's own instruction, this result does not by itself prove a Cloudflare-side or account-side cause — it only makes the *local default-route/Happ* hypothesis substantially less likely than Task 35 left it. The underlying mechanism could still be something else entirely local (e.g., something common to this Windows host's TCP/IP stack, its ISP-level path regardless of which local interface is used, or a characteristic of this specific network connection's own upstream) or genuinely on Cloudflare's side (a tunnel/account-level issue independent of which client network reaches it) — Task 35's own Cloudflare-side read-only check found no explicit error either way, and this task did not repeat that check.

**Genuinely new, unresolved observation:** the connector registered and *stayed* registered for the whole observed window this time, unlike Task 33's brief flash — a real difference between the two runs that this task cannot fully explain. It does not change the classification (piosapp.ru still never worked), but it is recorded honestly rather than smoothed over.

## 12. Minimal Next Action

Since the Happ/local-default-route hypothesis is now substantially weakened by direct, controlled evidence, the next useful diagnostic step is the one Task 35 already named and this task did not repeat: genuinely independent-host testing (a different physical device, ideally on a different network entirely, not just a different local interface on this same host) — which would test whether the failure is specific to *this host's* network stack/ISP path altogether, as opposed to something on Cloudflare's own tunnel/account side. That remains outside this task's own available tools and is not attempted here.

## 13. Persistent Changes

**Zero.** Confirmed explicitly:
- Routing table: identical to pre-test baseline (Section 9).
- Happ: never disabled, never reconfigured, interface still `Up`.
- No WinSW service installed.
- `http://localhost:4173`: `HTTP 200`.
- Tailscale Funnel: confirmed still active (`Funnel on: https://home-pc.tail385153.ts.net`).
- All seven `pios-*` services: `Running`, every backend `java.exe` process shows the identical start time as this session's established baseline — **no PIOS service was restarted.**
- Production PostgreSQL and RabbitMQ: not connected to or touched at any point in this task.

---

*Task 36 complete. Case B: the Happ default route is not the cause — the same failure reproduces via direct Wi-Fi. All temporary changes fully reverted and verified. No fix was applied. Stopping — Task 37 not started, no WinSW installed.*
