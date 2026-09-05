# Task 35 — Cloudflare Tunnel External-Path Isolation

**Type: Diagnostic only. No fix applied.** No Cloudflare Tunnel, token, DNS, PIOS code, database, RabbitMQ, Tailscale, Happ/VPN routing, Windows Firewall/networking, or Windows service was created, modified, or deleted.

**Result: INCONCLUSIVE for the LOCAL vs. CLOUDFLARE-SIDE question specifically** — Phase 2's genuine external-network test (a different host, or a full cloudflared run via an alternate network path) was not available and was not attempted, per this task's own explicit contingency. What **was** obtained is a new, concrete, and significant piece of local-host evidence: **traffic to the Cloudflare tunnel edge is currently routed by default through a local SOCKS-tunnel VPN interface (`happ-default-tun`)**, sitting alongside the direct WiFi route at the same route metric. This does not prove that interface causes the registration failure, but it is the most specific, evidenced lead this diagnosis produced, and it was not present in Tasks 33/34's own analysis.

---

## 1. Executive Summary

No other host, VM, or Claude session was reachable to run the required external-network A/B test (`ListAgents` confirmed nothing available), and no way exists to test a genuinely different network path *on this same host* without touching Happ's routing or Windows networking — both explicitly forbidden by this task. Phase 2 (Case C: external host unavailable) therefore did not run in its intended form.

In its place, this task performed everything else in scope: read-only Cloudflare-side inspection (Phase 1), and thorough current-host network evidence gathering (Phase 4), including one **safe, single-connection, non-mutating probe** that bound a raw TCP test socket directly to the WiFi adapter's own address — a genuine A/B data point at the TCP-connect level, obtained without changing any routing, interface, or Happ configuration.

## 2. Task 33/34 Evidence (carried forward, not re-litigated)

- Token and tunnel identity valid; no auth/registration-rejection text ever observed.
- QUIC: connector briefly registers (per Cloudflare's own `tunnel info`), then disappears; explicit, repeated `"timeout: no recent network activity"` errors across three edge IPs.
- HTTP/2 (forced): TCP-level connection reaches `Established` state on the tunnel control port, but no connector ever registers and no error is logged at all — a silent hang, not an explicit failure.
- `localhost:4173` = 200 throughout both prior tasks. `piosapp.ru` = 530 throughout.

## 3. Cloudflare-Side Read-Only Evidence (Phase 1)

- `cloudflared tunnel info --show-recently-disconnected pios-pilot` (structured/`-o json`): 
  ```json
  {
    "id": "70a49741-67dd-4f86-8ab1-7c42186c2fbf",
    "name": "pios-pilot",
    "createdAt": "2026-08-10T13:53:03.873599Z",
    "conns": []
  }
  ```
  **`conns` is empty even including recently-disconnected connections.** This is a meaningful negative result: despite this host's own logs showing a `Generated Connector ID` and a briefly-visible connector row in Task 33's own mid-run check, Cloudflare's account-side view now shows **no record of any connection attempt at all** for this tunnel, live or recent. This suggests whatever registration got far enough to transiently appear did not persist long enough to be retained even in a "recently disconnected" list.
- `cloudflared tunnel route ip list`: "No routes were found for the given filter flags" — expected/normal for a tunnel using only public-hostname routing (not private IP routing); not an error indicator.
- **No explicit Cloudflare-side error text of any kind was found** — none of "unauthorized," "invalid token," "tunnel disabled," "connector rejected," "protocol failure," or an account/zone issue indicator appeared anywhere in any CLI output this task obtained.
- **Limitation, stated plainly:** this task has no access to the Cloudflare Zero Trust dashboard's own activity/connection log (a browser/authenticated-dashboard view was not available), which could plausibly hold more detail than the CLI's own summary view. **This is recorded as a genuine gap, not filled in with speculation** — per this task's own instruction, this specific sub-check is INCONCLUSIVE, not concluded either way.

## 4. External Host/Network Test (Phase 2)

**Not performed — unavailable, per this task's own explicit contingency (Case C).**
- `ListAgents` was checked: no other Claude session, host, or reachable agent exists (`"No reachable agents — no other Claude session is running on this machine right now"`).
- No separate physical or virtual machine with `cloudflared` already installed was available to this task.
- A full alternate-network test *on this same host* (running the actual tunnel registration via a different route) was assessed as impossible to do safely without either changing Happ's routing metric or otherwise mutating Windows networking — both explicitly forbidden. **This was correctly not attempted**, per the task's own "иначе не выполнять" instruction.
- What *was* performed instead, within the bounds of "a safe A/B test that does not touch Happ/VPN/WFP and does not risk the current session": a single, one-off, non-persistent TCP-connect probe (Section 5) — a much narrower test than Phase 2 originally envisioned, and explicitly not equivalent to it.

## 5. Current-Host Network Evidence (Phase 4)

| Check | Result |
|---|---|
| DNS (`region1/region2.v2.argotunnel.com`) | Resolves correctly, same IP ranges as Task 33/34 |
| Proxy environment (`HTTP_PROXY`/`HTTPS_PROXY`/`ALL_PROXY`/`NO_PROXY`, all scopes) | None set |
| System time / timezone | `2026-09-04 00:30:45`, Ekaterinburg Standard Time — consistent, no skew evident |
| Network profile | Three active interfaces: `Беспроводная сеть` (WiFi, Up), `Tailscale` (Up), `happ-default-tun` (Up, described as **"SocksTunnel Tunnel"**) |
| **Default routing (0.0.0.0/0)** | **Two default routes, equal metric (0):** via `happ-default-tun` (next hop `10.6.7.2`) **and** via `Беспроводная сеть` (next hop `192.168.0.1`, `ifIndex 4`) |
| Direct TCP 7844 test, default path | `Test-NetConnection 198.41.200.53:7844` → **succeeded**, and Windows selected `InterfaceAlias: happ-default-tun`, `SourceAddress: 10.6.7.1` — **confirms the default outbound path for this destination currently goes through the Happ SOCKS tunnel, not directly out the WiFi adapter** |
| Direct TCP 7844 test, WiFi-bound (A/B probe) | A raw `.NET TcpClient` bound explicitly to the WiFi adapter's own address (`192.168.0.102`), connecting to the same `198.41.200.53:7844` — **also succeeded**. No routing, interface, or Happ configuration was changed to run this; it was a single explicitly-bound socket, closed immediately after. |
| Named Windows Firewall rule blocking cloudflared/QUIC | None found (already established in Task 33, re-applicable) |
| Security software (AV) via `SecurityCenter2` | Query returned no results (inconclusive — could mean no third-party AV is registered there, or the query itself has a limitation in this context; not investigated further, since a deeper probe risked going beyond read-only "indicator" checking into something more invasive) |
| `Get-MpComputerStatus` (Windows Defender) | Cmdlet not available in this shell context — **not determined** |

**Important honest caveat on the A/B probe:** both the default (Happ-routed) and the WiFi-bound single TCP connection **succeeded equally** at the bare TCP-connect level. This probe does **not** distinguish between the two paths, and does **not** confirm or refute Happ as the cause of the actual observed failure (which, per Tasks 33/34, was never a TCP-connect failure — the TCP handshake always succeeded; the failure was in **sustaining** the QUIC/HTTP2 control stream afterward). A genuine test of that specific, more demanding condition via the WiFi-only path was not attempted, since doing so would require either a flag `cloudflared` does not appear to expose for binding egress interface, or a routing-metric change — both out of this task's bounds.

## 6. Result Matrix

| Case | Outcome |
|---|---|
| A — different host/network → connector registers | Not tested (no other host available) |
| B — different host/network → same failure | Not tested |
| **C — different host unavailable** | **Applies.** Per the task's own matrix: INCONCLUSIVE for the specific local-vs-Cloudflare isolation question. |

## 7. Root-Cause Classification

**INCONCLUSIVE.**

This is not a refusal to draw a conclusion from the evidence gathered — it reflects that the one test (Phase 2) actually capable of cleanly separating "local host/network" from "Cloudflare/tunnel-side" was not available, and no substitute performed here reaches that same level of proof. Per this task's own critical rule, this report does not upgrade "INCONCLUSIVE for Phase 2" into a confident LOCAL HOST/NETWORK or CLOUDFLARE/TUNNEL-SIDE verdict merely because one piece of local evidence (Section 5's routing finding) is suggestive.

## 8. Exact Evidence Supporting the Classification

**Toward "local host/network" (suggestive, not conclusive):**
- All outbound traffic to the specific Cloudflare tunnel edge IPs, by default, currently routes through a local SOCKS-tunnel VPN interface (`happ-default-tun`) rather than directly — a genuinely unusual topology (two equal-metric default routes) that was not previously identified in Tasks 31–34.
- Both prior explicit-error transports (QUIC) and silent-hang transports (HTTP/2) exhibited the same general shape: initial TCP/handshake-level success, followed by failure to sustain the actual tunnel control stream — a pattern consistent with, though not proof of, an intermediate proxy/tunnel layer with its own idle-connection handling.

**Toward "Cloudflare/tunnel-side" (equally suggestive, not conclusive):**
- Cloudflare's own account-side connection history (`--show-recently-disconnected`) shows **zero** record of any connection attempt for this tunnel, despite this host generating connector IDs and briefly showing a live connector in Task 33. If the failure were purely local-network-layer (e.g., simple packet loss), a partially-completed handshake might be expected to at least register as a fleeting/disconnected entry on Cloudflare's side more often than what was observed here.

**Neutral / ruling nothing in or out:**
- The WiFi-bound A/B TCP probe succeeded exactly as well as the default Happ-routed path at the TCP-connect level — this data point does not favor either hypothesis, since the actual observed failure occurs at a later stage than what this narrow probe could test.
- No explicit Cloudflare-side error/rejection text was found anywhere — but this task also lacks dashboard-level access that might show more, so this absence is not strong evidence either way.

## 9. Minimal Next Action

The single most direct next diagnostic step — running the *actual* tunnel registration attempt via the non-Happ (WiFi-only) path, to see whether the connector then sustains — **requires either a Happ/VPN routing change or a Windows networking change**, both of which this task's own Stop Conditions explicitly forbid without separate authorization. Per those Stop Conditions, this task does not propose performing that step itself; it names it as the next decision point for the operator, requiring explicit authorization before any attempt (e.g., a temporary, reversible route-metric adjustment favoring the WiFi interface for the duration of one diagnostic cloudflared run, immediately reverted afterward — a specific, bounded action the operator would need to approve, not something to improvise).

A lower-risk alternative, if available: running the identical foreground+`TUNNEL_TRANSPORT_PROTOCOL=http2` (or QUIC) diagnostic from a genuinely separate device already on this same LAN (e.g., another computer or phone with `cloudflared` installed) would satisfy Phase 2's original intent without touching this host's own routing at all — this task did not have such a device available to it, but the operator may.

## 10. Actions Explicitly NOT Required (and not performed)

Disabling or reconfiguring Happ; any Windows Firewall or WFP change; any DNS change; any Cloudflare Tunnel/token change; installing a persistent Windows service; restarting any PIOS service; touching production PostgreSQL or RabbitMQ. None of these was needed to produce the evidence in this report, and none was performed.

## Phase 5 — Cleanup Verification

- No `cloudflared` process remains (confirmed after the diagnostic checks in Sections 3–5; no long-running diagnostic tunnel process was ever started in this task — only short CLI queries and one single, immediately-closed TCP probe socket).
- `http://localhost:4173` → `HTTP 200`.
- Tailscale Funnel confirmed still active (`Funnel on: https://home-pc.tail385153.ts.net`).
- All seven `pios-*` services `Running`; every backend `java.exe` process shows the identical start time as this session's established baseline — **no PIOS service was restarted.**
- Production PostgreSQL and RabbitMQ were not connected to or touched at any point in this task.

---

*Task 35 complete. Genuinely inconclusive on the local-vs-Cloudflare question, with one new, concrete, unresolved lead (the Happ SOCKS-tunnel default route) documented for the operator's own decision. No fix was applied, no configuration was changed, no PIOS service was restarted. Stopping for the next decision.*
