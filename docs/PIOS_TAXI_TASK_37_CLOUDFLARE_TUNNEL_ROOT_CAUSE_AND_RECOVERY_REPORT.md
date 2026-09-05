# Task 37 — Cloudflare Tunnel Root-Cause Investigation & Recovery

**Classification: INCONCLUSIVE**

Public access to `https://piosapp.ru` was **not** restored. No remediation was attempted, because none of the hypotheses this task could test pointed at anything fixable from this host with a small, justified, reversible change — applying one would have been guessing, which this task's own critical-discipline rule explicitly forbids ("do not invent certainty"). What this task did produce, going meaningfully beyond Tasks 31–36, is a **much more precise mechanism** for the failure — not just "QUIC/HTTP2 fails," but a specific, protocol-level idle-timeout signature, reproduced identically across three edge IPs — plus a previously-unexamined discovery about the tunnel's own configuration paradigm (Section 5) that narrows, but does not close, the "is the ingress/hostname mapping even correct" question this task was specifically asked to rule in or out.

---

## 1. Executive Conclusion

The tunnel `pios-pilot` cannot sustain a data-plane connection long enough to serve `piosapp.ru`, in either of cloudflared's two transports. This is **not** a token, tunnel-identity, DNS, architecture, clock, proxy, or Happ-routing problem — all independently re-verified in this task and previously ruled out across Tasks 31–36. Debug-level diagnostics run in this task show, for the first time, that both QUIC and HTTP/2 connections reach cloudflared's own internal **"Registering tunnel connection"** checkpoint — a genuine, specific success state, not a vague partial connection — before failing. For QUIC, the failure occurs at **exactly** the connection's own negotiated `MaxIdleTimeout` (5 seconds) every single time, across three different edge IPs. For HTTP/2, the connection registers and then goes silent with no error logged at all, even at debug level, for the remainder of this task's observation window. Separately, this task discovered that the tunnel's own documented deployment plan assumed a locally-configured ingress rule (`config.yml`) that was **never created** on this machine — the tunnel is running in a different mode (token-based, remotely-configured) than its own setup documentation describes, and this task has no way to verify from this host whether an equivalent ingress rule was ever configured on Cloudflare's side instead. Neither finding was fixable with a small, evidenced, reversible local change, so none was attempted.

## 2. Baseline (Re-Verified Fresh, Not Assumed)

| Fact | Value |
|---|---|
| cloudflared version | 2026.7.3 (built 2026-07-22T09:32 UTC) |
| Tunnel name / ID | `pios-pilot` / `70a49741-67dd-4f86-8ab1-7c42186c2fbf` |
| Tunnel status (`tunnel info`) | No active connection |
| `localhost:4173` | `HTTP 200` |
| `https://piosapp.ru` | `HTTP 530` |
| cloudflared process | None running |
| cloudflared Windows service | None installed (confirmed again: `Get-Service -Name "*cloudflare*"` empty) |
| Default routes | Two, equal metric — `happ-default-tun` (10.6.7.2) and `Беспроводная сеть` (192.168.0.1) — **unchanged from Task 36's own post-rollback state**, confirming no residual effect from that task |

## 3. Previous Tasks 31–36 Findings, Re-Verified

Not taken on faith — each independently re-checked this session:
- Token exists, length 180 characters (unchanged across every run since Task 33), successfully decodes to the correct tunnel ID at cloudflared's own startup line every time — **not corrupted, not the wrong token.**
- `cloudflared tunnel list`/`info` confirm identity matches exactly: `pios-pilot`, `70a49741-67dd-4f86-8ab1-7c42186c2fbf`.
- DNS resolves normally (re-confirmed via the connectivity precheck each run, which independently re-resolves `region1/region2.v2.argotunnel.com` every time).
- No named Windows Firewall rule, no configured system proxy — re-confirmed structurally unchanged (registry/env checks not repeated in full this session since nothing in the environment changed since Task 33/35 established them, and this task's own new evidence, Section 4, does not implicate either).
- Happ vs. direct Wi-Fi: Task 36's own controlled A/B test already proved the failure is identical regardless of which local interface carries the traffic — this task did not repeat that experiment (it would add no new information), but treats its conclusion as established.

## 4. New Evidence

Obtained via one foreground, debug-level (`--loglevel debug --transport-loglevel debug`) diagnostic run — not simply a repeat of Tasks 33/34/36's own info-level runs. Full relevant excerpt (secrets never appeared and none were redacted because none were ever printed):

```
2026-09-03T20:01:50Z DBG Received transport parameters: MaxUDPPayloadSize=1360, MaxIdleTimeout=5s, MaxDatagramFrameSize=16383 connIndex=0 event=0 ip=198.41.200.233
2026-09-03T20:01:50Z DBG Registering tunnel connection connIndex=0 event=0 ip=198.41.200.233 protocol=quic
2026-09-03T20:01:56Z ERR failed to accept incoming stream requests error="failed to accept QUIC stream: timeout: no recent network activity" connIndex=0 event=0 ip=198.41.200.233
```
`20:01:50` → `20:01:56` is **exactly 6 seconds of wall-clock, matching a 5-second idle timeout plus normal logging/processing lag** — not an arbitrary delay. This exact pattern — negotiated `MaxIdleTimeout=5s`, `"Registering tunnel connection"`, then the identical timeout error almost exactly 5 seconds later — repeated **three times**, once per retried edge IP:

| Edge IP | Registered at | Timeout error at | Elapsed |
|---|---|---|---|
| 198.41.200.233 | 20:01:50 | 20:01:56 | ~6s |
| 198.41.192.57 | 20:02:04 | 20:02:09 | ~5s |
| 198.41.200.53 | 20:02:18 | 20:02:23 | ~5s |

This is new, more precise evidence than Tasks 33/34/36 had: those runs showed the same *error text*, but not that the connection reaches a genuine internal "registered" state first, nor that the failure timing matches the connection's own negotiated idle window this exactly.

For HTTP/2 (after the QUIC retries exhausted and cloudflared switched fallback protocol at `20:02:48`):
```
2026-09-03T20:02:49Z DBG Connecting via http2 connIndex=0 event=0 ip=198.41.200.53
2026-09-03T20:02:49Z DBG Registering tunnel connection connIndex=0 event=0 ip=198.41.200.53 protocol=http2
```
**No further log line of any kind followed**, for the remainder of this task's observation window (checked repeatedly through `20:03:16`+, over 25 seconds later) — not even at debug level. This matches Task 34's own "silent stall" finding, but now confirms specifically that HTTP/2, too, reaches the same internal `"Registering tunnel connection"` checkpoint QUIC does before going silent — the two transports fail differently in their *symptom* (one times out with an explicit, precisely-timed error; the other simply stops logging), but both reach the identical internal milestone first.

`cloudflared tunnel info --show-recently-disconnected -o json pios-pilot`, checked immediately after this run: `"conns": []` — **still empty**, exactly as in Task 35, even though this run's own log shows three separate QUIC registration events and one HTTP/2 registration event. Cloudflare's own account-side view retains no record of any of them, even as "recently disconnected."

## 5. Root-Cause Hypothesis(es)

### Hypothesis 1 — Upstream network path drops sustained/keepalive traffic after initial handshake (QUIC and HTTP/2 both)
**Evidence for:** The precise idle-timeout match (Section 4) is a strong, specific signature: the connection completes its cryptographic handshake and registers, meaning enough packets got through in both directions to complete that — but then nothing further arrives within the negotiated window. Task 36 already proved this happens identically via two structurally different local paths (Happ SOCKS tunnel and direct Wi-Fi), meaning whatever is dropping the follow-up traffic is not specific to either local interface. HTTP/2 (TCP-based, immune to the specific QUIC/UDP idle-timeout mechanism) shows an analogous "registers then goes silent" pattern, suggesting the interference is not QUIC/UDP-specific either.
**Evidence against / unresolved:** This task cannot identify *where* upstream this interference occurs (this host's own network stack, a shared point both Happ and Wi-Fi eventually route through, the ISP, or something else) without testing from a genuinely independent network — exactly the missing experiment Task 35 already named and this task did not have the means to perform either.

### Hypothesis 2 — Ingress/public-hostname configuration is missing or wrong on Cloudflare's side
**Evidence for:** `docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md` (the tunnel's own original setup documentation, re-read in full this task) specifies a **classic, locally-configured named tunnel**: `cloudflared tunnel create pios-pilot` → a per-tunnel credentials JSON saved locally → a hand-written `config.yml` containing an explicit ingress rule (`hostname: piosapp.ru` → `service: http://127.0.0.1:4173`) → run via `--config config.yml run`. **None of that exists on this machine** — no credentials JSON, no `config.yml` (both facts independently re-confirmed this task and consistently across Tasks 31–36). What actually runs is `run-tunnel.ps1`'s own token-mode invocation (`TUNNEL_TOKEN` + bare `cloudflared tunnel run`) — a fundamentally different Cloudflare tunnel management paradigm, where ingress rules are **not** read from any local file and must instead live in Cloudflare's own dashboard-side "Public Hostname" configuration for this tunnel object. Whether that dashboard-side configuration was ever actually created is **not verifiable from this host** — this task has no browser/dashboard access and no general-purpose Cloudflare API token (only `cert.pem`, which authenticates cloudflared's own CLI tunnel-management calls, not a documented general REST API credential this task could safely wield via raw HTTP calls without risking an unintended mutation).
**Evidence against:** If ingress/hostname routing were the *entire* problem, a more commonly-expected symptom would be a **connected** tunnel serving a Cloudflare-generated 404 or "no ingress rule matched" response for the specific hostname — not an inability to sustain the control-stream connection itself, which is a layer *below* where ingress routing would even become relevant. This makes Hypothesis 2 a plausible **compounding** risk rather than a demonstrated primary cause: even if Hypothesis 1's transport problem were somehow resolved, this task cannot currently promise `piosapp.ru` would then serve PIOS correctly, because this second, independent unknown was never ruled out.

### Hypothesis 3 — Local Windows/security-software interference
**Evidence against:** No named Windows Firewall rule found (Task 33, not repeated this task since nothing changed). System proxy disabled. No clock skew. Task 36's own controlled test already shows the failure is identical via two structurally different local egress paths, which is hard to reconcile with a purely local, single-interface-specific interference mechanism (though it does not fully rule out something common to the host's TCP/IP stack itself, e.g., a driver or OS-level QUIC/UDP handling quirk — genuinely not testable without a second physical machine).

## 6. Evidence Table (Summary)

| Hypothesis | Supporting evidence | Contradicting/limiting evidence |
|---|---|---|
| 1. Upstream network drops sustained traffic | Precise idle-timeout match; identical failure via two local paths (Task 36); HTTP/2 shows analogous registered-then-silent pattern | Cannot localize further without an independent network/device |
| 2. Ingress/hostname misconfigured on Cloudflare side | Deployment doc describes a config mode never actually implemented on this host; token-mode ingress is unverifiable locally | Failure occurs below the layer where ingress routing would normally matter; no direct evidence ingress is actually wrong, only that it's unverified |
| 3. Local Windows/security software | — | No named firewall rule; identical failure across two different local interfaces |

## 7. Exact Changes Made

**None persisted.** One foreground diagnostic `cloudflared` process was run with debug-level logging (Section 4), then stopped cleanly. No routing change, no file change, no Cloudflare-side change, no Windows service installed, no PIOS service touched.

## 8. Verification Results

Not applicable in the "post-fix" sense — no fix was applied. Post-diagnosis state verified instead:
- No `cloudflared` process remains.
- `localhost:4173`: `HTTP 200`.
- `https://piosapp.ru`: `HTTP 530` — unchanged from baseline.
- All seven `pios-*` services: `Running`, identical PIDs/start times to this session's established baseline — **no PIOS service was restarted.**
- Tailscale Funnel: confirmed active (`Funnel on: https://home-pc.tail385153.ts.net`).
- Default routing table: identical to Task 36's own post-rollback baseline — no residual change from this task (this task made no routing change at all).

## 9. Security/Safety Checks

Token never printed (only its length, 180 characters, and its successful-decode behavior were observed). No Cloudflare configuration mutation of any kind was attempted. No API token or credential beyond the already-existing `cert.pem`/`tunnel.token` (both pre-existing, neither created or rotated) was used. Production PostgreSQL and RabbitMQ were not connected to or touched. No hard-stop condition was triggered — this task never reached a point requiring a Cloudflare mutation, a secret exposure, a permanent networking change, disabling security controls, creating a new tunnel, or testing from another device/network (it identifies that last one as the missing experiment, per Section 11, rather than attempting to route around not having it).

## 10. Remaining Uncertainty

Two independent, unresolved unknowns, neither reducible further from this machine:
1. **Where**, in the path between this host and Cloudflare's edge, the sustained/keepalive traffic is being lost — this host's own stack, a shared upstream point, or the ISP.
2. **Whether** the tunnel's ingress/public-hostname mapping is even correctly configured on Cloudflare's side at all, given the documented local-config-file setup was never completed and the actual token-mode configuration's ingress rules are not locally inspectable.

## 11. Recommended Next Step

The single experiment that would resolve Uncertainty #1 is exactly what Task 35 already named: running the identical diagnostic (ideally at debug level, per this task's own improved method) from a **genuinely independent device and network** — a different computer, phone, or cloud VM with `cloudflared` installed, using the same existing token, not this host's network path in any form. This task did not have such a device available (same limitation as Task 35).

Resolving Uncertainty #2 requires either: (a) Cloudflare Zero Trust dashboard access (a browser session logged into the account that owns this tunnel, checking the tunnel's own "Public Hostname" tab), or (b) a documented, scoped Cloudflare API token with read access to `GET /accounts/{account_id}/cfd_tunnel/{tunnel_id}/configurations`, used strictly read-only. Neither is available to this task's own tools; both are safe, non-mutating checks the operator could perform directly.

**This task does not recommend attempting a local "fix" in the meantime** — no local hypothesis was confirmed as the cause, and Task 30's own already-deployed backend/frontend stack remains completely healthy and unaffected regardless of this outstanding question.

## 12. Exact Final Public URL Status

`https://piosapp.ru` → **HTTP 530**. Public access remains **not restored**. The PIOS application itself (backend and frontend) remains fully healthy and reachable via its already-working alternate path, `https://home-pc.tail385153.ts.net` (Tailscale Funnel), unaffected by anything in this task.

---

*Task 37 complete. INCONCLUSIVE — the failure mechanism was narrowed further than any prior task (Section 4), and a previously-unexamined configuration-paradigm gap was surfaced (Section 5), but the ultimate root cause remains outside what this host and its available tools can prove. No fix was applied. No PIOS service was restarted. No production data was touched. Stopping — Task 38 not started.*
