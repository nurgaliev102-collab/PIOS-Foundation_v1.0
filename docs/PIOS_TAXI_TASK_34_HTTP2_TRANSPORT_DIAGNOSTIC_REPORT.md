# Task 34 — Cloudflare Tunnel HTTP/2 Transport Diagnostic

**Type: Diagnostic only.** One diagnostic `cloudflared` process was run in the foreground with `TUNNEL_TRANSPORT_PROTOCOL=http2` forced, output captured to local files, then stopped cleanly. No tunnel, token, DNS, Cloudflare configuration, Windows service, firewall, Windows networking, Tailscale configuration, PIOS application code, PostgreSQL, or RabbitMQ was touched. `run-tunnel.ps1` itself was not modified — its exact token-loading logic was replicated inline for this one-off run, with the transport variable added.

**Conclusion: HTTP/2 FAILS** (does not register a stable connector, does not restore `https://piosapp.ru`) — but fails in a **different way** than QUIC did in Task 33: silently, with no explicit error text, rather than with repeated explicit timeout errors.

---

## 1. Baseline (Phase 0)

| Check | Result |
|---|---|
| cloudflared version | `2026.7.3 (built 2026-07-22T09:32 UTC)` |
| Tunnel name | `pios-pilot` |
| Tunnel ID | `70a49741-67dd-4f86-8ab1-7c42186c2fbf` |
| `localhost:4173` | `HTTP 200` |
| Other cloudflared processes running | None |
| `cloudflared tunnel info pios-pilot` | "Your tunnel ... does not have any active connection." |

All matches Task 33's own closing state exactly — no drift in between tasks.

## 2. Exact Diagnostic Command/Method (token masked)

Replicated `run-tunnel.ps1`'s own token-loading logic exactly (same file, same `Get-Content -Raw | .Trim()`), added the forced-transport variable, and launched without `-WindowStyle Hidden`, with stdout/stderr redirected to files:

```powershell
$env:TUNNEL_TOKEN = (Get-Content -Raw -Path "windows-services\cloudflared\tunnel.token").Trim()   # never printed
$env:TUNNEL_TRANSPORT_PROTOCOL = "http2"
Start-Process -FilePath "C:\Program Files (x86)\cloudflared\cloudflared.exe" `
  -ArgumentList "tunnel","run" `
  -RedirectStandardOutput <stdout.log> -RedirectStandardError <stderr.log> `
  -PassThru -NoNewWindow
```
Token length passed: **180 characters** — identical to Task 33's own run, confirming no truncation or corruption in this invocation either. `run-tunnel.ps1` itself was not edited.

## 3. Connector Registration Result

**No connector was ever registered.** `cloudflared tunnel info pios-pilot`, checked repeatedly (T+0s, T+~30s, T+~90s, T+~110s), returned "does not have any active connection" at every single check, throughout the entire diagnostic run.

## 4. Connector Stability

**Not applicable — no connector ever appeared to observe stability of.** Unlike Task 33's QUIC run (where a connector briefly registered at T+~23s before disappearing again), this HTTP/2-forced run never produced a connector row at all within the ~112 seconds it was observed.

## 5. `https://piosapp.ru` Result

Checked at the end of the observation window: **`HTTP 530`** — unchanged from before this diagnostic run. The public endpoint was not restored.

## 6. Safe GET API Result

**Not performed.** Per this task's own instruction, the public-endpoint checks (Phase 2) were conditioned on the connector becoming stable first ("Если connector становится устойчивым") — since no connector ever appeared, this step does not apply, and no GET (or any other) request was made through the public tunnel path in this diagnostic run. `piosapp.ru`'s own `HTTP 530` (Section 5) was checked directly against the public hostname, not through a proxied backend call.

## 7. Exact stdout/stderr

**stdout: empty** (cloudflared logs to stderr only, same as Task 33). **stderr, in full** — this is the entire captured output for the whole run, nothing omitted:

```
2026-09-03T19:17:39Z INF Starting tunnel tunnelID=70a49741-67dd-4f86-8ab1-7c42186c2fbf
2026-09-03T19:17:39Z INF Version 2026.7.3 (Checksum 8635da433b6df8194746e88ed9d2589566c20e38bfc2a80e431a348b7c765841)
2026-09-03T19:17:39Z INF GOOS: windows, GOVersion: go1.26.4, GoArch: amd64
2026-09-03T19:17:39Z INF Environmental variables map[TUNNEL_TRANSPORT_PROTOCOL:http2]
2026-09-03T19:17:39Z INF cloudflared will not automatically update on Windows systems.
2026-09-03T19:17:39Z INF Generated Connector ID: 9f5e3ccd-5ad3-4e3a-baa4-260b33510cfc
2026-09-03T19:17:39Z INF Initial protocol http2
2026-09-03T19:17:39Z INF ICMP proxy will use 192.168.0.102 as source for IPv4
2026-09-03T19:17:39Z INF ICMP proxy will use fe80::5219:49ab:1a7a:61fa in zone Беспроводная сеть as source for IPv6
2026-09-03T19:17:39Z INF Tunnel connection curve preferences: [X25519MLKEM768 CurveID(65074) CurveP256] connIndex=0 event=0 ip=198.41.200.53
2026-09-03T19:17:39Z INF Starting metrics server on 127.0.0.1:20241/metrics
2026-09-03T19:17:39Z INF +--------------------------------------------------------------------------------------+
2026-09-03T19:17:39Z INF |                               CONNECTIVITY PRE-CHECKS                                |
2026-09-03T19:17:39Z INF +--------------------------------------------------------------------------------------+
2026-09-03T19:17:39Z INF |  COMPONENT         TARGET                     STATUS  DETAILS                        |
2026-09-03T19:17:39Z INF |  DNS Resolution    region1.v2.argotunnel.com  PASS    DNS Resolved successfully      |
2026-09-03T19:17:39Z INF |  DNS Resolution    region2.v2.argotunnel.com  PASS    DNS Resolved successfully      |
2026-09-03T19:17:39Z INF |  UDP Connectivity  region1.v2.argotunnel.com  PASS    QUIC connection successful     |
2026-09-03T19:17:39Z INF |  UDP Connectivity  region2.v2.argotunnel.com  PASS    QUIC connection successful     |
2026-09-03T19:17:39Z INF |  TCP Connectivity  region1.v2.argotunnel.com  PASS    HTTP/2 connection successful   |
2026-09-03T19:17:39Z INF |  TCP Connectivity  region2.v2.argotunnel.com  PASS    HTTP/2 connection successful   |
2026-09-03T19:17:39Z INF |  Cloudflare API    api.cloudflare.com:443     PASS    API is reachable               |
2026-09-03T19:17:39Z INF |  SUMMARY: Environment is healthy. cloudflared will use 'http2' as primary protocol.  |
2026-09-03T19:17:39Z INF +--------------------------------------------------------------------------------------+
2026-09-03T19:17:39Z INF precheck complete hard_fail=false run_id=0b75f86a-d3ba-4154-ba90-671caa74ec29 suggested_protocol=http2
```
*(repeated per-component `precheck` key=value lines omitted — identical content to the table above, all `status=pass`)*

**Nothing further was ever written to the log** — no `ERR` line, no additional `INF` line, no success confirmation, for the entire ~112-second observation window. This is the single most important, literal fact of this diagnostic: **the confirmation of `TUNNEL_TRANSPORT_PROTOCOL=http2` was honored** (`"Environmental variables map[TUNNEL_TRANSPORT_PROTOCOL:http2]"`, `"Initial protocol http2"`), the connectivity precheck passed exactly as it did for QUIC, and then the process went silent.

**Network-level evidence, gathered read-only** (`Get-NetTCPConnection`), taken partway through the silent window: one **Established** connection to `198.41.200.53:7844` (a Cloudflare tunnel-protocol edge address, the same port family QUIC used in Task 33), plus two connections in `FinWait2` (one to `104.19.192.29:443` — plausibly `api.cloudflare.com`; one to a different edge IP, `198.41.192.57:7844`, apparently an earlier attempt now closing). **This shows the process did reach and hold a TCP connection to an edge server's control-stream port, the same as Task 32/33's own observations, without that connection ever producing a registered connector.**

## 8. Conclusion

**HTTP/2 FAILS.**

Per this task's own critical rule, this is not concluded merely because a process was running — no stable connector was ever observed (Section 3), and `https://piosapp.ru` never returned anything but its pre-existing failure (Section 5). Both required conditions for "WORKS" are absent.

This is distinct from "INCONCLUSIVE": the evidence is a clean, unambiguous absence — 112 seconds of silence after a passing precheck, with zero connector registrations and zero external success, is itself a definite (if not fully explained) result, not an ambiguous one.

## 9. Root Cause Status

**Still not proven**, and this diagnostic does not claim otherwise, per this task's own instruction not to assert NAT/ISP/security-software causes without proof.

What this run **adds** to Task 33's own findings: the failure is **not** specific to QUIC. Both transports reach and hold a TCP-level connection to a Cloudflare edge control-stream port (`:7844`), and both fail to complete tunnel registration — QUIC failed **loudly and repeatedly** with an explicit `"timeout: no recent network activity"` error; HTTP/2 failed **silently**, with no error text of any kind, just an unproductive held connection. This shifts the most likely locus of the problem toward something that affects the **tunnel control-stream/registration handshake generally** (regardless of which transport carries it) rather than something QUIC/UDP-specific — but this is an inference from the pattern of two data points, not a proven mechanism, and this report does not assert a specific external cause (NAT, ISP, firewall, security software) for either transport's failure.

## 10. Minimal Next Action

Neither transport has been shown to work from this diagnosis. Since HTTP/2 (Task 34) and QUIC (Task 33) both fail — one loudly, one silently — the next diagnostically useful action is **not** another transport variant on this same network path (both of cloudflared's own transport options have now been exhausted and both fail), but rather determining whether the failure is specific to *this host's* network path at all — e.g., testing tunnel registration from a genuinely different network, or inspecting the Cloudflare-side tunnel/account state for anything (e.g., an account-level restriction) that the `cert.pem`/token-based CLI checks used so far would not surface. This task does not propose a specific mechanism for that next step or attempt to begin it — that is a decision for the next, separately authorized task.

## Cleanup Verification (Phase 5)

- Diagnostic process (PID 400) stopped; confirmed no `cloudflared` process remains.
- `http://localhost:4173` → `HTTP 200`, unaffected.
- Tailscale Funnel confirmed still active (`Funnel on: https://home-pc.tail385153.ts.net`).
- All seven `pios-*` services confirmed `Running`; every backend `java.exe` process shows the identical start time as this session's established baseline (PIDs 15152/13208/7740/14100 and the untouched 5388/16076/6532/6644) — **no PIOS service was restarted.**
- Production PostgreSQL and RabbitMQ were not connected to or touched at any point in this task.

---

*Task 34 complete. HTTP/2 shown to fail, distinctly from QUIC's own failure mode. No fix was applied, no configuration was changed, no PIOS service was restarted. Stopping for the next decision.*
