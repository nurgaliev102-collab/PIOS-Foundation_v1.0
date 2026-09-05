# Task 44 — Definitive Isolation Test for 360 Total Security Network Filtering

**Classification: INCONCLUSIVE — exactly the contingency this task's own instructions anticipated: "The 360 component cannot be isolated without rebooting or making a persistent system change."**

No `cloudflared` process was ever started in this task — the test's own precondition (temporarily disabling the relevant 360 network component) could not be met, so steps 5–11 (the actual QUIC/HTTP2 runs) were never reached. Nothing was changed, temporarily or otherwise: no service, no driver, no registry value, no cloudflared binary, no PIOS component.

---

## 1. Baseline (Recorded Before Any Action)

| Item | Value |
|---|---|
| cloudflared version | `2026.7.3` |
| cloudflared SHA-256 | `8635da433b6df8194746e88ed9d2589566c20e38bfc2a80e431a348b7c765841` |
| `localhost:4173` | `HTTP 200` |
| Tunnel status | No active connection |
| cloudflared process | None running |
| 360-related drivers running | `360Box64`, `360FsFlt`, `360Hvm`, `360netmon` — all `Running`, `StartMode: System` |
| `QHActiveDefense` (main service) | `Running`, `StartType: Automatic`, `CanStop: False`, `CanPauseAndContinue: False` |
| Network adapters | `Беспроводная сеть` (Wi-Fi) and `happ-default-tun` both `Up`, two equal-metric default routes — unchanged from every prior task |

## 2. Identifying the Exact Component

`sc.exe qc 360netmon` confirms `360netmon` is registered as:
```
TYPE               : 1  KERNEL_DRIVER
START_TYPE         : 1  SYSTEM_START
LOAD_ORDER_GROUP   : PNP_TDI
```
`PNP_TDI` (Plug-and-Play, Transport Driver Interface load group) confirms this is specifically a network-transport-layer filter driver — consistent with Task 43's hypothesis that this is the component capable of intercepting/monitoring network traffic, not an unrelated 360 subsystem (file-system filtering, camera protection, etc., which are the other `360*` drivers present but not implicated).

File identity confirmed: `FileDescription: 360netmon`, `ProductName: 360netmon`, `CompanyName: 360.cn`, `FileVersion: 2.1.11.5195` — genuinely signed 360 Total Security software, not something unrelated masquerading under a similar name.

A corresponding user-mode component was located: `C:\Program Files (x86)\360\Total Security\netmon\NetworkMon.exe` — but this task did not invoke it (Section 3 explains why).

## 3. Attempted Temporary Disable — Failed at the First, Least-Invasive Step

```
sc.exe stop 360netmon
→ [SC] ControlService FAILED 1052: The requested control is not valid for this service.
```
This is **not** a permissions error — it is the driver itself declaring, via its own service dispatch table, that it does not accept a stop control at runtime at all. This is a deliberate, common self-protection characteristic of security-vendor kernel drivers, not a transient failure this task could retry past.

**Reinforcing evidence, found while checking:** a dedicated watchdog process is running —
```
QHWatchdog.exe (PID 8108) — C:\Program Files (x86)\360\Total Security\safemon\QHWatchdog.exe
```
alongside `QHActiveDefense.exe` (PID 2056) and `QHSafeTray.exe` (PID 10064). A "watchdog" process's specific purpose in this class of software is to detect and reverse exactly this kind of tampering with protection components — meaning even a successful stop, had one been achievable, could plausibly have been silently reversed mid-test by this same watchdog, which would have contaminated the 60-second observation window's own validity without necessarily being noticed.

## 4. Why This Task Did Not Escalate Further

The remaining theoretical avenues, all explicitly declined:

- **Killing `QHWatchdog.exe` first, to prevent it from reversing a subsequent stop attempt** — this is adversarial tampering with a real, licensed security product's own self-protection, not "temporarily disabling ONLY the relevant network-monitoring component" as authorized. Not attempted.
- **Invoking `NetworkMon.exe` or another 360-branded executable with guessed command-line flags** — no vendor documentation for this specific tool's CLI syntax is available to this task, and guessing flags against an unfamiliar security-product executable risks an unpredictable, not-verifiably-reversible state change (license/cloud-sync side effects, corrupted configuration, etc.), which this task cannot respond to a "no change other than the intended one" requirement about. Not attempted.
- **Changing `360netmon`'s registry `Start` value from `System` (1) to `Disabled` (4)** — this is exactly the "persistent system change" this task's own INCONCLUSIVE criterion names, since it would not take effect until the next boot (the driver is already loaded and running in memory; a `Start` value change only affects the *next* system startup, not the current session) — and even then would require a reboot this task is not authorized to perform, followed by a second reboot to revert. Not attempted.
- **GUI interaction with 360 Total Security's own settings panel** — no browser/desktop-automation tool is available to this task. Not attempted, and not possible with current tooling regardless of authorization.

## 5. Result

**INCONCLUSIVE.** The 360 network-monitoring component could not be isolated within the bounds this task itself set (no reboot, no persistent change, no other component touched, no adversarial tampering with the software's own self-protection). Per the task's own classification rules, this is not a FAIL (no test of the actual QUIC/HTTP2 behavior was performed, since the precondition was never met) and not a PASS (obviously). Task 43's own `360netmon` hypothesis remains exactly where it was left — a strong, plausible, unproven candidate — neither strengthened nor weakened by this task's own attempt, since the attempt could not reach the point of producing evidence either way.

## 6. Safety Verification

- `sc.exe stop 360netmon` failed cleanly with no side effect — re-confirmed after the attempt: all four `360*` drivers remain in their exact original state (`Running`, `System` start mode), unchanged.
- No cloudflared process was ever started (the test's own precondition was never met, so steps 5–11 were never reached) — confirmed no `cloudflared` process exists.
- cloudflared binary: version `2026.7.3`, SHA-256 `8635da433b6df8194746e88ed9d2589566c20e38bfc2a80e431a348b7c765841` — unchanged.
- `localhost:4173`: `HTTP 200`.
- All 7 `pios-*` services: `Running`, identical PIDs/start times to this session's established baseline — no restart occurred.
- No PIOS, DNS, Cloudflare tunnel/route/token, Windows routing, firewall, or WinSW change was made.
- 360 Total Security's protection was never actually reduced at any point — there was no window, however brief, where `360netmon` was confirmed non-filtering, so there is nothing to "restore."

## 7. What Would Actually Resolve This

Since a live, in-session, no-reboot isolation is not achievable through this task's own available tools against this specific software's self-protection design, the remaining options — none performed by this task, each requiring the operator's own explicit authorization and physical/administrative action — are:

1. **Use 360 Total Security's own GUI directly** (the operator, at the keyboard) to locate its network-monitoring/firewall feature and pause it through the product's own supported settings — likely under a "Traffic Firewall" / "Network Protection" style panel, given the `netmon`/`PNP_TDI` identity established in Section 2. This is the natural, low-risk, officially-supported equivalent of what this task attempted and could not do remotely.
2. **A scoped reboot test**: set `360netmon`'s `Start` registry value to `Disabled`, reboot once, run the same cloudflared diagnostic, then revert the value and reboot again — explicitly the "persistent change + reboot" path this task's own INCONCLUSIVE classification was written to avoid without separate authorization.
3. **Uninstall 360 Total Security entirely for the duration of one test, then reinstall** — a much larger, more disruptive action than anything authorized so far in this arc; not recommended as a first step given options 1 and 2 are less invasive and would answer the same question.

---

*Task 44 complete. INCONCLUSIVE, for the reason this task's own instructions specifically defined in advance. No system change of any kind occurred. Stopping for the operator's own decision on which of Section 7's options, if any, to authorize next.*
