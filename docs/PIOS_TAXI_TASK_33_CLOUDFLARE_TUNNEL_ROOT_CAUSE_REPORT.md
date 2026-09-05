# Task 33 — Cloudflare Tunnel Registration Root-Cause Diagnosis

**Type: Diagnostic only.** One diagnostic `cloudflared` process was run in the foreground with output captured to a local file, then stopped cleanly, per this task's own Phase 2 instruction. No tunnel, token, DNS record, Cloudflare configuration, Windows service, firewall rule, Windows networking setting, or PIOS application/service/database/RabbitMQ configuration was created, modified, or deleted. No STOP condition was triggered — no step in this diagnosis required any of the actions this task's own Stop Conditions list forbids.

---

## 1. Executive Summary

The connector for tunnel `pios-pilot` fails to establish a **sustained** connection to Cloudflare's edge. This is **not** a token, authentication, tunnel-identity, DNS, or basic-connectivity problem — all of those were checked directly and confirmed working. The captured, literal cloudflared output shows the specific, repeatable failure: the QUIC (UDP-based) transport dials or briefly connects to Cloudflare's edge and then times out with **`"timeout: no recent network activity"`** — a pattern consistent with UDP packets reaching the edge for an initial handshake but not sustaining thereafter. This happened identically against three different edge IPs, with exponential retry backoff, before cloudflared switched to its HTTP/2 fallback protocol — which, within this task's own observation window, did not complete either (no further log line for over 90 seconds, and the one connector registration that had briefly appeared on Cloudflare's side subsequently disappeared again).

**The exact reason the underlying UDP traffic does not sustain (firewall, NAT/router UDP timeout, ISP throttling, or security software) is not proven from this host alone** — per this task's own critical rule, that specific external cause is not asserted, only the directly-evidenced transport-layer symptom.

## 2. What Task 32 Already Proved

Re-confirmed, not re-litigated: `cloudflared.exe` runs, a TCP connection to a Cloudflare edge address on port 7844 was observed, the tunnel never registered an active connector, and the process exited on its own after a few minutes with no captured error (that run was launched hidden, with no output redirection). This task's own Phase 2 closes exactly that gap.

## 3. Version and Actual Launch Mechanism

- `cloudflared version 2026.7.3 (built 2026-07-22T09:32 UTC)`.
- Windows: 64-bit. `cloudflared.exe` PE header: **x64 (AMD64)** — architectures match; no 32/64-bit mismatch.
- `windows-services\cloudflared\run-tunnel.ps1` exists, confirmed present, read in full:
  - Reads `windows-services\cloudflared\tunnel.token`, trims it, assigns it to the `TUNNEL_TOKEN` environment variable.
  - Invokes: `& 'C:\Program Files (x86)\cloudflared\cloudflared.exe' tunnel run` — **no explicit `--token` flag and no other flags**; cloudflared picks up `TUNNEL_TOKEN` from the environment automatically (its own documented mechanism).
  - **Masked command actually executed for this diagnosis** (token never appears): `TUNNEL_TOKEN=<redacted, 180 chars> cloudflared.exe tunnel run`.
- Token file: exists, 181 bytes on disk (180 characters of token content + 1 trailing newline, which `.Trim()` removes before use — confirmed no truncation: the environment variable's own reported length inside the diagnostic run was exactly **180**, matching file-size-minus-newline exactly). SHA-256 of the file: `8694a388bb4d5f4035cf4f3ca4464feaa3436117a5dbedaf6649b2f7ff2d416d`. **Token content was never printed at any point in this task.**

## 4. Tunnel Identity

| Field | Value |
|---|---|
| Name | `pios-pilot` |
| ID | `70a49741-67dd-4f86-8ab1-7c42186c2fbf` |
| Created | `2026-08-10 13:53:03.873599 +0000 UTC` |

Confirmed via `cloudflared tunnel list` and `cloudflared tunnel info pios-pilot` (both read-only) — **matches exactly** the ID `run-tunnel.ps1`'s own comment documents, and matches the `tunnelID=` cloudflared itself printed at startup from decoding the token (`Starting tunnel tunnelID=70a49741-67dd-4f86-8ab1-7c42186c2fbf`). **No identity mismatch of any kind.**

## 5. Full Diagnostic Output (secrets removed — there were none to remove; nothing sensitive was ever printed)

Captured via `Start-Process` with `-RedirectStandardOutput`/`-RedirectStandardError` to local files (not `-WindowStyle Hidden`), started 19:07:23Z, stopped by this task at approximately 19:10:xxZ. stdout was empty throughout (cloudflared logs to stderr only). Full stderr, verbatim:

```
2026-09-03T19:07:23Z INF Starting tunnel tunnelID=70a49741-67dd-4f86-8ab1-7c42186c2fbf
2026-09-03T19:07:23Z INF Version 2026.7.3 (Checksum 8635da433b6df8194746e88ed9d2589566c20e38bfc2a80e431a348b7c765841)
2026-09-03T19:07:23Z INF GOOS: windows, GOVersion: go1.26.4, GoArch: amd64
2026-09-03T19:07:23Z INF cloudflared will not automatically update on Windows systems.
2026-09-03T19:07:23Z INF Generated Connector ID: 1306337c-03f6-44e2-8881-78b6fa38740f
2026-09-03T19:07:23Z INF Initial protocol quic
2026-09-03T19:07:23Z INF ICMP proxy will use 192.168.0.102 as source for IPv4
2026-09-03T19:07:23Z INF ICMP proxy will use fe80::5219:49ab:1a7a:61fa in zone Беспроводная сеть as source for IPv6
2026-09-03T19:07:23Z INF Tunnel connection curve preferences: [X25519MLKEM768 CurveID(65074) CurveP256] connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:07:23Z INF Starting metrics server on 127.0.0.1:20241/metrics
2026-09-03T19:07:23Z INF +-------------------------------------------------------------------------------------+
2026-09-03T19:07:23Z INF |                               CONNECTIVITY PRE-CHECKS                               |
2026-09-03T19:07:23Z INF +-------------------------------------------------------------------------------------+
2026-09-03T19:07:23Z INF |  COMPONENT         TARGET                     STATUS  DETAILS                       |
2026-09-03T19:07:23Z INF |  DNS Resolution    region1.v2.argotunnel.com  PASS    DNS Resolved successfully     |
2026-09-03T19:07:23Z INF |  DNS Resolution    region2.v2.argotunnel.com  PASS    DNS Resolved successfully     |
2026-09-03T19:07:23Z INF |  UDP Connectivity  region1.v2.argotunnel.com  PASS    QUIC connection successful    |
2026-09-03T19:07:23Z INF |  UDP Connectivity  region2.v2.argotunnel.com  PASS    QUIC connection successful    |
2026-09-03T19:07:23Z INF |  TCP Connectivity  region1.v2.argotunnel.com  PASS    HTTP/2 connection successful  |
2026-09-03T19:07:23Z INF |  TCP Connectivity  region2.v2.argotunnel.com  PASS    HTTP/2 connection successful  |
2026-09-03T19:07:23Z INF |  Cloudflare API    api.cloudflare.com:443     PASS    API is reachable              |
2026-09-03T19:07:23Z INF |  SUMMARY: Environment is healthy. cloudflared will use 'quic' as primary protocol.  |
2026-09-03T19:07:23Z INF +-------------------------------------------------------------------------------------+
2026-09-03T19:07:23Z INF precheck complete hard_fail=false run_id=57771f0c-521d-4d90-923a-1ef1ca1ef8a9 suggested_protocol=quic
2026-09-03T19:07:28Z ERR failed to accept incoming stream requests error="failed to accept QUIC stream: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:07:28Z ERR failed to run the datagram handler error="timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:07:28Z ERR failed to serve tunnel connection error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:07:28Z ERR Serve tunnel error error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:07:28Z INF Retrying connection in up to 2s connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:07:33Z ERR Failed to dial a quic connection error="failed to dial to edge with quic: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:07:33Z INF Retrying connection in up to 4s connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:07:40Z ERR failed to run the datagram handler error="context canceled" connIndex=0 event=0 ip=198.41.192.7
2026-09-03T19:07:40Z ERR failed to serve tunnel connection error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.192.7
2026-09-03T19:07:40Z ERR Serve tunnel error error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.192.7
2026-09-03T19:07:40Z INF Retrying connection in up to 8s connIndex=0 event=0 ip=198.41.192.7
2026-09-03T19:07:52Z ERR Failed to dial a quic connection error="failed to dial to edge with quic: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.192.7
2026-09-03T19:07:52Z INF Retrying connection in up to 16s connIndex=0 event=0 ip=198.41.192.7
2026-09-03T19:08:04Z ERR Failed to dial a quic connection error="failed to dial to edge with quic: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:08:04Z INF Retrying connection in up to 32s connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:08:17Z ERR failed to accept incoming stream requests error="failed to accept QUIC stream: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.192.107
2026-09-03T19:08:17Z ERR failed to run the datagram handler error="context canceled" connIndex=0 event=0 ip=198.41.192.107
2026-09-03T19:08:17Z ERR failed to serve tunnel connection error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.192.107
2026-09-03T19:08:17Z ERR Serve tunnel error error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.192.107
2026-09-03T19:08:17Z INF Retrying connection in up to 1m4s connIndex=0 event=0 ip=198.41.192.107
2026-09-03T19:08:42Z INF Switching to fallback protocol http2 connIndex=0 event=0 ip=198.41.192.107
2026-09-03T19:08:43Z INF Tunnel connection curve preferences: [X25519MLKEM768 CurveID(65074) CurveP256] connIndex=0 event=0 ip=198.41.192.107
```
*(Repeated identical `precheck` key=value lines omitted from this excerpt for brevity — all 7 components PASS, shown fully in the table above; nothing omitted changes the finding. No line beyond this point was written to the log before this task stopped the process — 90+ seconds elapsed with no further output.)*

Saved copies of both raw log files remain at `C:\Temp\claude\...\scratchpad\task33\cloudflared_stdout.log` (empty) and `cloudflared_stderr.log` (61 lines, the source of the excerpt above) — local scratch files, not committed to the repository, contain no secret.

## 6. Exact Connector-Registration Error

Two literal, repeating error strings, both transport-layer, **neither related to authentication or identity**:
```
failed to accept QUIC stream: timeout: no recent network activity
failed to dial to edge with quic: timeout: no recent network activity
```
Both occurred across **three distinct Cloudflare edge IPs** (`198.41.200.53`, `198.41.192.7`, `198.41.192.107`), each attempt following the same shape: a connection/dial attempt begins, then fails on a timeout waiting for "recent network activity" on the QUIC stream. After the 5th consecutive QUIC failure (backoff reaching 1m4s), cloudflared itself logged `"Switching to fallback protocol http2"` — its own built-in mitigation for exactly this class of QUIC problem. The HTTP/2 attempt began (`Tunnel connection curve preferences` logged for it) but **produced no further log line — no success, no error — for the remainder of this task's observation window.**

**Separately noted, not fully explained:** `cloudflared tunnel info pios-pilot`, checked mid-diagnosis, briefly showed one registered connector (`1306337c-03f6-44e2-8881-78b6fa38740f`, `CREATED: 2026-09-03T19:08:02Z` — matching the "Generated Connector ID" cloudflared itself logged at startup). A later check, after the HTTP/2 attempt had stalled, showed **no active connection again**. This is consistent with a connector briefly announcing itself to Cloudflare's control plane without ever completing a stable data-plane tunnel connection capable of carrying `piosapp.ru` traffic — `https://piosapp.ru` returned `HTTP 530` throughout, including during the brief window the connector row was visible.

## 7. Root Cause — Only What Is Proven

**Proven, directly from the captured log text (not inferred):** the tunnel's QUIC (UDP) transport cannot sustain a connection to Cloudflare's edge from this host — it times out waiting for network activity, repeatedly, across multiple edge servers, despite an initial connectivity precheck reporting QUIC as working. This is a **transport/network-layer failure**, not an application-, authentication-, or configuration-layer one.

**Explicitly ruled out, with direct evidence, not assumption:**
- **Token/authentication failure** — no occurrence anywhere in the captured output of any of the strings this task asked to search for (`unauthorized`, `invalid token`, `authentication failed`, `credentials`, `tunnel not found`, `permission denied`, `registration failed`, `websocket failure`, `protocol error`, `certificate error`). The token successfully decoded to the correct tunnel ID at the very first log line, and the Cloudflare API precheck passed.
- **Tunnel identity mismatch** — Section 4 confirms exact match, both locally and against Cloudflare's own account.
- **cloudflared/Windows architecture incompatibility** — both confirmed x64, matching.
- **DNS failure** — `region1/region2.v2.argotunnel.com` both resolved successfully, confirmed independently via `nslookup` outside of cloudflared's own precheck, to the same IP ranges cloudflared then tried to reach.
- **System clock/timezone skew** — local time `2026-09-04 00:11:17 +05:00` (Ekaterinburg Standard Time), consistent with the UTC timestamps in cloudflared's own log (5-hour offset, correctly applied); a major skew would also have broken the TLS handshakes in the connectivity precheck, which passed.
- **A named Windows Firewall rule specifically blocking cloudflared or QUIC** — none found (`Get-NetFirewallRule` search, read-only). Windows Firewall itself is active on all three profiles (Domain/Private/Public), which is normal and does not by itself indicate interference.
- **A configured system HTTP/HTTPS/ALL proxy** — none set at Process, User, or Machine environment scope, and WinINet's own `ProxyEnable` is `0` (disabled), though a stale, currently-inactive `ProxyServer` value (`http://127.0.0.1:12334`) remains in the registry from some earlier, now-disabled configuration — inactive, so not implicated, but noted for completeness.

**NOT proven — per this task's own critical rule, not asserted as the cause:** *why* the QUIC data-plane stream specifically times out after an initially-successful precheck. The plausible external explanations (a stateful firewall or NAT/router that permits a brief UDP handshake but drops the sustained flow; ISP-level UDP/QUIC throttling; endpoint security software intercepting without a discoverable named firewall rule) are each **consistent with the observed symptom pattern** but **none was confirmed** from this host alone — doing so would require testing from a different network path or deeper packet-level inspection, which this diagnostic task did not perform.

## 8. Remaining Hypotheses (root cause of the network-layer failure itself — none proven)

| Hypothesis | Evidence for | Evidence against / not determined |
|---|---|---|
| NAT/router silently drops sustained UDP flow after initial handshake | Textbook match for this exact symptom (brief precheck success, then "no recent network activity" on the live stream); very commonly reported for this cloudflared error text | Cannot be confirmed or denied from this host alone — would need testing from a different network |
| ISP-level UDP/QUIC throttling or interference | Same symptom match as above | Same limitation — not testable from this host alone |
| Endpoint security software intercepting/dropping UDP without a named firewall rule | Not ruled out — a rule-based search would not catch application-layer interception | No such software was identified by name in this diagnosis (out of scope to inventory installed security products) |
| Windows Firewall (named rule) | — | Directly checked, no matching rule found |
| Token/tunnel identity/DNS/architecture/clock problem | — | All directly checked and ruled out (Section 7) |

## 9. Minimal Fix

**Not established as proven-effective by this diagnosis, and not attempted — this task is diagnostic only.** The one concrete, low-risk *next diagnostic step* this evidence points toward is forcing HTTP/2 as the **primary** transport from the start (via `TUNNEL_TRANSPORT_PROTOCOL=http2`, a documented cloudflared environment variable, or `cloudflared tunnel run --protocol http2`) in another foreground, output-captured diagnostic run — this would avoid burning ~80 seconds on QUIC retries before HTTP/2 is even attempted, and would give a clean answer on whether HTTP/2 alone can sustain a connection in this environment (this task's own automatic-fallback attempt did not resolve either way within the observed window). This is proposed as the next task's action, not performed here.

## 10. What Must NOT Be Changed (per this task's own explicit list — none of this was touched)

Tunnel identity/configuration, the token (not rotated, not regenerated), DNS, Cloudflare account configuration, any Windows service (none installed), Windows Firewall rules, Windows networking settings, any PIOS application service, PostgreSQL, RabbitMQ, and PIOS application configuration. **None of these was modified by this task.**

## 11. Checks Performed After Diagnosis (Phase 5)

- Diagnostic `cloudflared` process (PID 2264) stopped cleanly; confirmed no `cloudflared` process of any kind remains running.
- `http://localhost:4173` → `HTTP 200`, unaffected throughout and after.
- Tailscale Funnel confirmed still active (`Funnel on: https://home-pc.tail385153.ts.net`), unaffected.
- All seven `pios-*` Windows services confirmed `Running`, and every backend `java.exe` process shows the **identical** start time as before this task began — **no PIOS service was restarted**.
- Production PostgreSQL and RabbitMQ were not connected to or touched at any point in this task.

## 12. Clear Next Step

This diagnosis has isolated the failure to a specific, evidenced transport-layer symptom (QUIC stream timeout after initial handshake, across multiple edge IPs, with an inconclusive HTTP/2 fallback). The next step is a **separately authorized** follow-up diagnostic task that forces `TUNNEL_TRANSPORT_PROTOCOL=http2` on a single foreground, output-captured run (same non-destructive shape as this task's own Phase 2), to determine cleanly whether HTTP/2-only connectivity succeeds where QUIC does not. That result would either (a) point toward a durable, low-risk remediation (running the tunnel with HTTP/2 forced, if it proves stable), or (b) indicate the underlying network interference affects both transports, which would then need investigation outside this host (e.g., a different network path) before any further tunnel remediation is attempted. This task does not authorize or begin that follow-up itself.

---

*Task 33 complete. Diagnostic only — no fix was applied, no configuration was changed, no PIOS service was restarted. Stopping for review.*
