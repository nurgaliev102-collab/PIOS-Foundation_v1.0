# Task 42 — cloudflared Version/Upgrade Diagnostic (2026.7.3 → 2026.8.3)

**Type: Research and diagnosis only.** No file was modified, no binary was downloaded or replaced, no Cloudflare configuration was touched, no PIOS/Tailscale/DNS/network/firewall change was made. This is documentation- and evidence-gathering, per the task's own instruction to stop before any change.

---

## 1. Current State (Re-Confirmed)

| Item | Value |
|---|---|
| Binary path | `C:\Program Files (x86)\cloudflared\cloudflared.exe` |
| Version | `2026.7.3` (built 2026-07-22T09:32 UTC) |
| Binary SHA-256 | `8635da433b6df8194746e88ed9d2589566c20e38bfc2a80e431a348b7c765841` — matches the `Checksum` cloudflared reports of itself in every debug log this whole arc |
| Binary size / mtime | 54,213,360 bytes, 2026-08-12 18:41:13 |
| cloudflared process | None running |
| Recommended version (per cloudflared's own startup warning) | `2026.8.3` |

## 2. Does 2026.7.3 Have a Known, Version-Specific QUIC/HTTP2 Registration Bug?

**Checked cloudflared's own GitHub release notes** for every version between 2026.7.3 and 2026.8.3. Found exactly two "known issue" callouts in that range:
- **2026.8.0**: "strips trailing slashes from requests..." — unrelated (HTTP path handling, not transport/connection stability).
- **2026.8.1**: "normalize paths from requests sent to HTTP origins..." — same, unrelated.

**No changelog entry in this range mentions QUIC idle timeout, keepalive, control stream, datagram handling, or connection stability of any kind.** There is no documented fix that would explain our specific symptom being introduced in 2026.7.3 and resolved by 2026.8.3.

## 3. Is This a Known, General cloudflared Issue (Not Version-Specific)?

**Yes — and this is the most important finding of this task.**

**GitHub Issue #1728** (opened 2026-08-26, still **Open**, no maintainer response documented):
> "Cloudflare Tunnel QUIC connections intermittently terminate with: 'timeout: no recent network activity'"

- **cloudflared version: 2026.8.2** — one version *below* the version this task was asked to evaluate upgrading to.
- Environment: Linux container, Azure Container Apps — a completely different OS, architecture, and network environment than this Windows host.
- Symptom, quoted directly: connectivity precheck (DNS, UDP/QUIC, TCP/HTTP2, Cloudflare API) all pass, then `"failed to accept QUIC stream: timeout: no recent network activity"` and `"failed to run the datagram handler: timeout"`, then automatic reconnection — **the exact same shape and even overlapping wording** as every debug log captured in Tasks 33–38/41 on this machine.
- The reporter's own open questions mirror ours almost exactly: is this an idle-timeout/keepalive issue, does switching to HTTP/2 help, would multiple replicas help — **none of these are answered in the issue**, and no Cloudflare staff response is recorded.

**GitHub Issue #1503** (opened 2025-07-07, **Closed**, resolution details not retrievable via this task's tools — GitHub's comment thread did not load through the available fetch tool):
> "Failed to dial a quic connection error='failed to dial to edge with quic: timeout: no recent network activity'"
- cloudflared version: **2025.7.0** — over a year before our own version, on Linux/ARM.
- Same exact error text, same exponential-backoff retry pattern (2s, 4s, 8s — identical shape to every retry sequence this arc has observed).
- Closed, but this task could not determine *why* — no visible resolution comment, no linked fix commit was surfaced by the available tooling.

**Interpretation:** this specific error text and failure shape has been independently reported by unrelated users, in unrelated environments, spanning **at least 14 months of cloudflared versions** (2025.7.0 through 2026.8.2) — including on a version *newer* than the one we'd be upgrading to. This is strong evidence against "this is a 2026.7.3-specific regression fixed in 2026.8.3," and weak-to-moderate evidence that this is either a long-standing characteristic of cloudflared's QUIC transport under certain network conditions, or a recurring-but-different-root-cause symptom that happens to produce identical log text each time.

## 4. Token-Mode Compatibility of 2026.8.3

**Not directly confirmable.** The specific 2026.8.3 release-notes page's detailed content did not load through this task's fetch tool (a client-side rendering failure on GitHub's own page, not a finding about the release itself). No release note in the checked range (2026.8.0 through 2026.8.3) mentions any change to `TUNNEL_TOKEN`, token-mode operation, or remotely-managed-tunnel behavior — token mode is a long-standing, core, widely-used cloudflared feature, and nothing found suggests a breaking change to it in this range. This is an absence of contrary evidence, not a positive confirmation — stated honestly as such.

## 5. Is Upgrading a Justified Next Experiment?

**Weakly justified — worth trying because it is cheap and safely reversible, but the evidence available does not support strong optimism that it fixes this specific problem.** The central finding (Section 3) — the identical symptom persisting on a version essentially adjacent to the upgrade target, in an entirely different environment — argues that this is unlikely to be a version-specific bug that a bump alone resolves. It remains a reasonable, low-cost, low-risk experiment precisely *because* it's cheap to try and cheap to undo (Section 6) — not because the research points to it as the likely fix.

## 6. Proposed A/B Plan — NOT EXECUTED, Awaiting Separate Authorization

**Exact proposed sequence, if authorized:**

1. **Backup the current binary** (already characterized, Section 1): copy `C:\Program Files (x86)\cloudflared\cloudflared.exe` to a scratchpad path (e.g., `cloudflared-2026.7.3.PRE-UPGRADE.exe`), verify the copy's SHA-256 matches `8635da433b6df8194746e88ed9d2589566c20e38bfc2a80e431a348b7c765841` before proceeding.
2. **Download 2026.8.3** from cloudflared's official GitHub releases (the Windows amd64 asset), verify its published SHA-256 checksum against what GitHub's release page lists, before replacing anything.
3. **Replace only the binary** at the existing path — no service installed, no config file created, no token touched, no DNS/network change.
4. **Run the identical foreground, debug-level diagnostic** already used in Tasks 37/38/41 (`tunnel --loglevel debug --transport-loglevel debug run`, output captured, not hidden) — same token, same method, only the binary version differs — to get a directly comparable result.
5. **Observe**: does a connector register and *stay* registered past the ~5-second mark this time? Does `piosapp.ru` return anything other than 530?
6. **Rollback, in all cases** (success or failure — matching this whole arc's own discipline of not leaving anything running unattended): stop the diagnostic process, restore the original 2026.7.3 binary from the verified backup, confirm `cloudflared --version` reports `2026.7.3` again and its SHA-256 matches the original.

**Rollback is trivial and low-risk**: a single file replacement, reversed by copying the preserved original back — no registry change, no service, no persistent state of any kind is touched by an upgrade attempt scoped this way.

**This task does not execute this plan.** Per its own Instruction 12, it stops here and presents the plan for separate authorization.

## 7. Answer to the Central Question

**Why does the connector reach "Registering tunnel connection" but not hold?** This task did not find a definitive answer — but it did find that the question is not unique to this machine, this network, or even this cloudflared version: it is an open, unresolved, independently-reported issue in cloudflared's own GitHub tracker, occurring on a newer version in an unrelated (Linux/Azure) environment. **This does not point at "our version" as the cause** — it points toward something either broader in cloudflared's QUIC implementation across versions, or something about the specific class of network path(s) involved (both Issue #1728's Azure Container Apps environment and our own two independently-tested networks share nothing obvious except being non-trivial network paths to Cloudflare's edge that aren't a simple direct residential connection) — a hypothesis this task cannot confirm further with the tools available.

---

## 8. A/B Test Execution (Authorized and Performed)

The operator authorized the exact plan in Section 6, with one explicit reframing: the upgrade is treated as a **controlled experiment, not a claimed fix**, and success requires all three of: **(A)** a connector that stays registered, **(B)** `piosapp.ru` actually reachable externally, **(C)** a request through `piosapp.ru` actually reaching `localhost:4173`.

### 8.1 Backup and Checksum Verification (before any replacement)

| Step | Result |
|---|---|
| Backed up current binary | `scratchpad/task42/cloudflared-2026.7.3.PRE-UPGRADE.exe` |
| Backup SHA-256 matches original | `8635da433b6df8194746e88ed9d2589566c20e38bfc2a80e431a348b7c765841` — confirmed identical |
| Downloaded | `cloudflared-windows-amd64.exe` from `github.com/cloudflare/cloudflared/releases/download/2026.8.3/` |
| Downloaded file SHA-256 | `83e726ed18ea78c5ad5213c4c3a3a27051393950d2bc8ed4de69bec12d14eaae` |
| GitHub's own published checksum for this asset | `83e726ed18ea78c5ad5213c4c3a3a27051393950d2bc8ed4de69bec12d14eaae` — **exact match, verified before replacement** |
| New binary runs and reports correct version (pre-replacement, from scratchpad) | `cloudflared version 2026.8.3 (built 2026-08-31T02:48 UTC)` |

### 8.2 Replacement and Diagnostic Run

Binary replaced at `C:\Program Files (x86)\cloudflared\cloudflared.exe`; verified in place (`--version` → `2026.8.3`, SHA-256 matches). Same foreground, debug-level diagnostic as every prior task (`tunnel --loglevel debug --transport-loglevel debug run`, same token, same method — only the binary version differs).

**Result: identical failure, same precise timing.**

```
2026-09-03T22:37:52Z INF Version 2026.8.3 (Checksum 83e726ed18ea78c5ad5213c4c3a3a27051393950d2bc8ed4de69bec12d14eaae)
2026-09-03T22:37:52Z DBG Received transport parameters MaxDatagramFrameSize=16383 MaxIdleTimeout=5000 MaxUDPPayloadSize=1360 connIndex=0 event=0 ip=198.41.192.67
2026-09-03T22:37:52Z DBG Registering tunnel connection connIndex=0 event=0 ip=198.41.192.67 protocol=quic
2026-09-03T22:37:52Z INF |  SUMMARY: Environment is healthy. cloudflared will use 'quic' as primary protocol.  |
2026-09-03T22:37:57Z ERR failed to run the datagram handler error="context canceled" connIndex=0 event=0 ip=198.41.192.67
2026-09-03T22:37:57Z ERR failed to serve tunnel connection error="control stream encountered a failure while serving" connIndex=0 event=0 ip=198.41.192.67
2026-09-03T22:37:57Z INF Retrying connection in up to 2s connIndex=0 event=0 ip=198.41.192.67
```
`22:37:52` → `22:37:57`: **exactly 5 seconds** — matching `MaxIdleTimeout=5000` (ms) precisely, the identical signature from every prior task on 2026.7.3. The retry sequence continued identically (2s → 4s → 8s → 16s → 32s backoff, cycling through edge IPs `198.41.192.67`, `.47`, `.57`) with the same repeating error text (`"failed to dial to edge with quic: timeout: no recent network activity"`).

One superficial format change was noted (cosmetic only, not a behavior difference): 2026.8.3 logs `MaxIdleTimeout=5000` (milliseconds, no unit) where 2026.7.3 logged `MaxIdleTimeout=5s` — the value itself is identical (5 seconds); only the log's number formatting differs between versions.

`cloudflared tunnel info pios-pilot`, checked twice (once during the run, once after): showed connector `5ffc68f0-6797-413d-9f27-0f40a509aa72` created `2026-09-03T22:38:31Z` — the same "briefly visible in Cloudflare's own account view" pattern as every prior task, not a sustained one.

### 8.3 Criteria A/B/C — Explicit Check

| Criterion | Result |
|---|---|
| **A. Connector sustains** | **FAILED** — same ~5-second registration-to-timeout cycle, repeating indefinitely, exactly as on 2026.7.3 |
| **B. `piosapp.ru` reachable externally** | **FAILED** — `HTTP 530`, checked immediately after registration, again after the retry sequence, and once more before rollback — no change at any point |
| **C. Request reaches `localhost:4173` via `piosapp.ru`** | **Not reached** — moot, since B never succeeded; there was never a working page to trace a request through |

**Per the user's own stated rule: not one of the three criteria was met. This is not a partial or ambiguous result.**

### 8.4 Rollback (Performed Regardless of Outcome, Per the Plan)

| Step | Result |
|---|---|
| Diagnostic process stopped | PID 1520, confirmed gone; no `cloudflared` process of any kind remains |
| Original binary restored | Copied back from the verified backup |
| Version confirmed | `cloudflared version 2026.7.3 (built 2026-07-22T09:32 UTC)` |
| SHA-256 confirmed | `8635da433b6df8194746e88ed9d2589566c20e38bfc2a80e431a348b7c765841` — **exact match to the original**, confirmed after restoration |
| `localhost:4173` | `HTTP 200` |
| All 7 `pios-*` services | `Running`, identical PIDs/start times to this session's established baseline — no restart occurred |
| Tailscale Funnel | Confirmed active |
| No further changes made | Per the plan's own "no additional fixes without separate authorization" condition — none were attempted |

## 9. Final Classification: **FAIL**

The 2026.8.3 upgrade did not resolve the connector-sustain problem, by any of the user's own three success criteria. This is a clean, direct, reproducible negative result — not inconclusive, not partially successful.

**What this settles, per the user's own framing:** cloudflared's version can now be crossed off the suspect list. Section 3's pre-test research (the identical symptom independently reported on 2026.8.2, in an unrelated Linux/Azure environment) predicted exactly this outcome, and the controlled experiment confirms it directly rather than by inference alone. Continuing to chase local Windows-environment or cloudflared-version explanations is no longer indicated. Per the user's own stated next step: the remaining investigation belongs on Cloudflare Tunnel configuration/runtime and Cloudflare-side diagnostics — not further local changes, and **WinSW remains deliberately not installed**, since a persistent service would only auto-restart a connector that has never once, in any test across this entire arc, stayed up.

---

*Task complete. A/B test performed exactly as authorized, fully reverted regardless of outcome. FAIL — cloudflared version is not the cause. No PIOS, DNS, token, ingress, network, or firewall change was made. No WinSW was installed.*
