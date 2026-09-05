# Task 43 — Cloudflare Tunnel Control-Stream Investigation

**Type: Read-only checks plus one authorized diagnostic foreground run.** No system configuration was changed. No PIOS/DNS/token/routing/firewall/WinSW change was made.

**Two major findings emerged, one expected, one not:**
1. A concrete, previously-unidentified candidate mechanism was found on this host: a kernel-level network-monitoring driver belonging to third-party security software (`360netmon.sys`, part of 360 Total Security), running continuously throughout this entire investigation, never checked for in Tasks 33–42.
2. **The tunnel's live, freshly-fetched remote ingress configuration still contains no route for `piosapp.ru`** — confirmed moments before this report was written, independent of and more current than the Task 40/41 dashboard evidence.

---

## 1. Full Debug/Transport Log — Reconfirmed, No New Error Text

One more foreground run (`tunnel --loglevel debug --transport-loglevel debug run`, 2026.7.3, the restored/verified binary) reproduced the identical, by-now-familiar pattern: `Registering tunnel connection` → `~5s` → `timeout: no recent network activity`. No new error class appeared. This report does not re-paste that log in full — Tasks 37/38/41/42 already captured it verbatim multiple times with identical content.

## 2. Transport Parameters Negotiated

```
Received transport parameters MaxDatagramFrameSize=16383 MaxIdleTimeout=5000 MaxUDPPayloadSize=1360 ...
```
Same three values every single time, across every version and network tested.

## 3. Who Sets `MaxIdleTimeout=5s`?

**LIKELY: the Cloudflare edge server, not cloudflared or this host.** Reasoning, not proof (GitHub's own code search requires an authenticated session this task does not have, so the exact source line could not be inspected directly):
- The log's own wording — `"Received transport parameters"` — is the standard phrasing QUIC implementations (cloudflared uses `quic-go`) use for parameters **received from the peer**, not parameters the local endpoint is configured to send.
- `cloudflared`'s own `--help` output (checked again this task, both `tunnel run --help` and the top-level `tunnel --help`) exposes no flag or documented environment variable for setting a client-side max idle timeout — if this were a local, client-configurable setting, a corresponding flag would be expected.
- This value was identical across two cloudflared versions (2026.7.3, 2026.8.3) and, per Task 38, two different networks — consistent with a value fixed by the remote peer rather than derived from local configuration or environment.

**Is 5 seconds a normal, intentional value?** Cannot be fully confirmed from this host, but it is not inherently unreasonable for a tunnel control connection — many QUIC deployments use short idle timeouts specifically *because* they expect the client to send periodic keepalive/PING traffic well within that window, precisely to detect a dead peer quickly. **The abnormal fact is not the 5-second value itself — it is that literally nothing, not even an automatic keepalive, gets through in that window, every single time, regardless of network or version.**

## 4. Local keepalive/idle-timeout Configuration

**None found.** No `config.yml` exists (confirmed absent since Task 31). No flag or environment variable in the actual invocation (`run-tunnel.ps1`, re-read this task, unchanged) sets any keepalive/idle-timeout-related value. The diagnostic bundle's own `cli-configuration.json` (Section 8) confirms exactly what was passed: `{"loglevel": "debug", "transport-loglevel": "debug", "uid": "-1"}` — nothing else.

## 5. `TUNNEL_*`/`CLOUDFLARE*` Environment Variables

**None exist**, at Process, User, or Machine scope — checked explicitly this task (not merely assumed). Only `TUNNEL_TOKEN` is ever set, and only transiently, in-memory, by `run-tunnel.ps1` itself at process start (confirmed via its own source, unchanged since Task 31).

## 6. Full Process Command Line

Captured live via WMI/CIM during this task's own diagnostic run:
```
"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --loglevel debug --transport-loglevel debug run
```
Exactly the intended invocation — no injected, hidden, or unexpected argument.

## 7. WinHTTP / System Proxy Configuration

**No proxy configured**, confirmed via `netsh winhttp show proxy` this task: `"Direct access (no proxy server)"`. This is a *separate* Windows proxy subsystem from WinINet, which Task 33 already confirmed disabled (`ProxyEnable: 0`). **Both of Windows' own proxy subsystems are now confirmed clean.**

## 8. Transparent Proxy / VPN / WFP / Filter Drivers — **The Major New Finding**

`netsh wfp show state` was run (read-only; exports the current Windows Filtering Platform state to a file, does not modify it) — 596 filters exist, overwhelmingly standard Windows Firewall/IPsec default filters; no non-Microsoft filter provider names were found in a scan of the exported state.

`Get-NetAdapterBinding` on the Wi-Fi adapter showed only standard Microsoft-provided protocol bindings (TCP/IPv4, TCP/IPv6, LLDP, file/printer sharing, QoS packet scheduler) — no unusual third-party NDIS filter/lightweight-filter driver bound to that adapter specifically.

**However, a broader driver enumeration (`Get-CimInstance Win32_SystemDriver`) surfaced something Tasks 33–42 never checked for:**

| Driver | Display Name | State | Start Mode |
|---|---|---|---|
| `360Box64` | 360Box mini-filter driver | **Running** | System (boot-time) |
| `360FsFlt` | 360FsFlt mini-filter driver | **Running** | System (boot-time) |
| `360Hvm` | 360Safe HVM | **Running** | System (boot-time) |
| **`360netmon`** | **360netmon** | **Running** | **System (boot-time)** |

Plus a running service: `QHActiveDefense` — display name **"360 Total Security"**, `Status: Running`.

**This is third-party security software (Qihoo 360's "360 Total Security"), with a network-monitoring driver (`360netmon.sys`) loaded at boot and running continuously, that this entire investigation (Tasks 33–42) never identified**, because every prior driver/process name search (Task 33, and this task's own first attempt) checked only a fixed list of Western AV/security vendor names (Kaspersky, Avast, ESET, Norton, etc.) and never included "360". This was found only because this task's driver enumeration was run without a name filter first, then inspected.

**Why this is a strong candidate, reasoned from the evidence pattern itself:**
- It explains why the failure is **independent of local network path** (Task 38: cellular hotspot vs. broadband router, identical result) — a kernel-resident driver on this host operates regardless of which physical network carries the packets.
- It explains why the failure is **independent of Happ** (Task 36) — same reasoning; a host-level driver sits below/alongside routing choices, not inside the VPN tunnel itself.
- It explains why the failure is **independent of cloudflared version** (Task 42) — the interference, if real, would be a Windows-driver-level behavior, not something either client version could code around differently.
- `netmon` in the driver's own name is a strong, direct hint at network-traffic-monitoring functionality — precisely the class of software (deep packet inspection, connection-state tracking, heuristic traffic analysis) known, in general, to sometimes interfere with long-lived, low-throughput, encrypted control connections while allowing initial handshakes through cleanly (which matches the observed pattern: connectivity prechecks always pass, initial registration always succeeds, only the *sustained* stream fails).

**What this task did NOT do, and could not do within its own read-only mandate:** disable, stop, or reconfigure any of these drivers or the 360 Total Security service — that would be exactly the kind of "disable security software" action explicitly forbidden without separate authorization across this entire task arc. **This is reported as a strong candidate, not a proven cause.**

## 9. TLS/QUIC/TCP Interception Check

No evidence of TLS interception was found — no unexpected root/intermediate certificate was checked for specifically (out of scope for a quick pass), but the connectivity precheck's own TLS/QUIC/HTTP2 handshakes to `region1/region2.v2.argotunnel.com` and `api.cloudflare.com:443` consistently **succeed** (Section 1, and every prior task) — a classic TLS-intercepting proxy typically causes certificate validation failures at the handshake stage itself, which has never been observed here. This is mild evidence against *TLS-terminating* interception specifically, though it does not rule out non-TLS-terminating traffic monitoring (which is what `360netmon` would more plausibly be, if involved).

## 10. IPv4 vs. IPv6

Every single tunnel connection attempt, across all tasks including this one, has connected to an **IPv4** Cloudflare edge address (`198.41.192.x` / `198.41.200.x`) — never IPv6, despite IPv6 being enabled and "connected" on both the Wi-Fi and Happ interfaces, and despite Cloudflare's own `region1/region2.v2.argotunnel.com` DNS names resolving both A and AAAA records (confirmed in Task 33's own fuller lookup). This task could not force an IPv6-only test — no supported cloudflared flag for edge IP version selection was found in this version's `--help` output (re-checked), and forcing it via a Windows-level IPv6 preference change would be a system change outside this task's read-only mandate. **Recorded as UNKNOWN** — cloudflared/quic-go's own connection racing appears to consistently prefer or win on IPv4 in this environment, for reasons this task cannot further isolate without a config change.

## 11. cloudflared's Own Diagnostic Bundle (`tunnel diag`)

Run against the live diagnostic instance (read-only, targets an already-running local process's own metrics endpoint) — **the single most informative new evidence this task produced**:

- **Traceroute (both `raw-network.txt` and `network.json`) failed at every hop** — no ICMP reply at all, not even from hop 1 (the local gateway), for both `region1` and `region2` over IPv4. This is inconclusive by itself — many consumer routers/ISPs silently drop or ignore the ICMP probes traceroute relies on, which is common and not inherently suspicious — but it means this task **could not** use cloudflared's own traceroute to pinpoint where in the path packets stop.
- **`configuration.json` — fetched live, moments before this report, directly from Cloudflare's own remote config for this tunnel:**
  ```json
  {
    "config": {
      "ingress": [
        { "hostname": "", "service": "http_status:503" }
      ],
      "originRequest": { "connectTimeout": 30, "keepAliveConnections": 100, "keepAliveTimeout": 90, "tcpKeepAlive": 30, ... },
      "warp-routing": {}
    },
    "version": -1
  }
  ```
  **`ingress` contains only the default catch-all (`hostname: ""` → `http_status:503`) — no rule for `piosapp.ru`, and `"version": -1"` indicates no real configuration version has ever been successfully pushed and fetched.** This is corroborated by `metrics.txt`'s own gauge: `cloudflared_orchestration_config_version 0`.

**This directly contradicts the premise (stated in this task's own incoming context) that the Published Application for `piosapp.ru` "was added/verified separately"** — as of this task's own live check, it had not taken effect, or had not actually been saved, or targets something other than this tunnel's remotely-fetched configuration. **This is independent, fresher, and more authoritative than the Task 40 dashboard screenshot** (which was a point-in-time human observation) — this is cloudflared's own client fetching its live configuration from Cloudflare during this very task.

## 12. Documentation / GitHub Issues Research

- **No changelog entry** between 2026.7.3 and 2026.8.3 mentions QUIC idle timeout, keepalive, or control-stream stability (re-confirmed, consistent with Task 42's own finding).
- **GitHub Issue #1728** (still open, cloudflared 2026.8.2, Azure Container Apps, Linux) — the exact same error text and failure shape, unresolved, no maintainer response. Already documented in Task 42.
- **This task's own additional search** for issues combining "control stream" timeouts with antivirus/security-software interference **found no direct community corroboration** — the closest matches (#1440, #917) describe control-stream/HTTP2 disconnection symptoms without attributing them to local security software. This neither confirms nor disproves the `360netmon` hypothesis (Section 8); it means this specific combination has not been publicly documented by other cloudflared users in a way this task's search could surface.

## 13. Hypothesis Classification

| Hypothesis | Classification | Basis |
|---|---|---|
| Token/tunnel identity invalid | **DISPROVED** | Re-confirmed correct and consistent, every task including this one |
| DNS misconfiguration | **DISPROVED** | Resolves correctly, every task |
| System proxy (WinINet or WinHTTP) | **DISPROVED** | Both subsystems confirmed clean this task |
| `TUNNEL_*`/`CLOUDFLARE*` env var interference | **DISPROVED** | None exist at any scope, checked directly this task |
| Local network/ISP path specific to one network | **DISPROVED** | Task 38: two structurally different networks, identical failure |
| Happ VPN specifically | **DISPROVED** | Task 36/38: excluded, identical failure |
| cloudflared version-specific bug | **DISPROVED** | Task 42: 2026.8.3 A/B test, identical failure |
| Named Windows Firewall rule | **DISPROVED** | Task 33; no relevant rule found |
| **Missing Cloudflare-side ingress route** | **CONFIRMED** (as a real, currently-still-present gap) — but does not by itself explain the control-stream failure | Section 11: live-fetched remote config still shows only the default 503 catch-all |
| **Third-party security software (360 Total Security / `360netmon.sys`) interfering with sustained QUIC/HTTP2 traffic** | **POSSIBLE, strongest untested candidate** | Section 8: driver confirmed running, boot-persistent; plausible mechanism fits every cross-network/cross-version/cross-Happ observation; not directly tested or disproven |
| Cloudflare edge/account-side transport-level restriction | **POSSIBLE, unresolved** | Cannot be confirmed or disproven without Cloudflare-side diagnostics this task has no access to (Task 39's own conclusion, unchanged) |
| TLS-terminating interception | **UNLIKELY, not fully disproved** | TLS/QUIC handshakes to Cloudflare consistently succeed at the precheck stage, arguing against certificate-level interception specifically |
| IPv4 vs. IPv6 as the differentiator | **UNKNOWN** | Every attempt used IPv4; could not force an isolated IPv6 test within this task's read-only bounds |
| Exact source of `MaxIdleTimeout=5s` | **LIKELY server-set (Cloudflare edge), not client-configurable** | Reasoned from log wording and absence of any client-side flag; not confirmed via direct source access (GitHub code search required authentication this task does not have) |

## 14. Most Informative Next Experiment

**Two, in priority order, addressing the two live findings from this task specifically:**

1. **Highest priority, lowest cost, most directly actionable: re-verify with the account owner whether the `piosapp.ru` Published Application was actually saved successfully** — Section 11's live-fetched configuration shows it was not, as of this task's own check. This must be resolved (or re-confirmed still failing, with the exact current dashboard state) before any transport-level fix would even matter, since a working connector today would still serve a 503 catch-all, not PIOS.
2. **For the control-stream question specifically: temporarily and reversibly test with 360 Total Security's real-time/network-monitoring protection paused** (not uninstalled, not permanently disabled — a scoped, time-boxed pause, explicitly authorized and immediately reverted, mirroring this whole arc's own established discipline for the Wi-Fi-network and cloudflared-version A/B tests). If the connector then sustains past 5 seconds, this closes the investigation decisively. If it doesn't, this specific hypothesis is cleanly disproven and the remaining open question (Cloudflare edge/account-side) becomes the sole focus. **This task does not perform this step itself** — pausing security software crosses into exactly the kind of change this task's own mandate reserves for separate, explicit authorization.

## 15. Safety Verification

- No `cloudflared` process remains (diagnostic PID 2240 stopped and confirmed gone).
- `cloudflared --version`: `2026.7.3`, matching the already-verified restored binary — no version change occurred or persisted this task.
- The diagnostic zip `tunnel diag` wrote to the repository's own working directory was identified and deleted — confirmed via `git status` that no trace remains.
- `localhost:4173`: `HTTP 200`.
- All 7 `pios-*` services: `Running`, identical PIDs/start times to this session's established baseline.
- Tailscale Funnel: confirmed active.
- No DNS, token, ingress, routing, firewall, or WinSW change was made.
- One incidental exposure occurred and is disclosed rather than hidden: a broad registry environment-variable query (`Get-ItemProperty HKCU:\Environment`) intended to check for `TUNNEL_*`/`CLOUDFLARE*` variables also returned two unrelated API keys already present in this user's own environment (`DEEPSEEK_API_KEY`, `PIOS_AI_ADVISOR_QWEN_API_KEY`) in this session's own tool output. Neither value is reproduced in this report or anywhere else in this task's own output beyond that one tool call. No Cloudflare-related secret was exposed at any point.

---

*Task 43 complete. No system change was made. Two concrete findings — a live-reconfirmed missing ingress route, and a previously-unchecked, plausible local security-software candidate — are reported for the operator's own decision on which to pursue next. Neither was acted upon without separate authorization.*
