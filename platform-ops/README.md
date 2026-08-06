# PIOS platform-ops

Platform Operations Center's backend agent. **Not a PIOS module** —
ADR-046 Decision 1: it owns no capability, models no domain, holds no
data. An independent Gradle project, the same relationship `backend/` and
`frontend/` already have to each other.

## What exists at this milestone (T-3, T-4)

A closed-by-default Spring Boot application on port `8090`, reachable on
the LAN, exposing exactly one endpoint: `GET /v1/ops/health`, gated by a
second, independent credential (`pios.ops.*` — never the Owner Control
Center's own `pios.owner.*`). No target list, no service control, no logs
reader yet — those are later tasks (T-5 onward).

## Running

```
cd platform-ops
gradlew.bat bootRun
```

`pios.ops.username` / `pios.ops.password-hash` / `pios.ops.password-salt`
are unset by default (see `src/main/resources/application.yml`) — an
unconfigured deployment denies every request rather than defaulting open.
Generate real values for an actual deployment; there is no default
credential anywhere in this repository.

## Reachability (T-4)

Binds `0.0.0.0:8090` explicitly, not just `127.0.0.1` — required by O-2
(phone/LAN reachability for Sprint 1, no external ingress). Verified
reachable from this machine's own LAN address:

- **LAN address at time of writing:** `http://192.168.0.102:8090` (this
  machine's Wi-Fi interface). **This address is DHCP-assigned and can
  change** — if `platform-ops` stops answering from this URL, check the
  machine's current address first (`ipconfig`, or `Get-NetIPAddress` in
  PowerShell) before assuming a service failure. A future task may assign
  this machine a DHCP reservation or a static address to remove this
  caveat; not done here — out of T-4's own scope.
- **Firewall:** a Windows Defender Firewall inbound rule ("PIOS Platform
  Operations Center (TCP 8090)") allows TCP 8090 on the **Private** network
  profile only — not Public, not Domain. The home Wi-Fi network
  ("Беспроводная сеть") was explicitly set to the Private category as a
  prerequisite; it was previously categorized Public by Windows' own
  default, which would have made a Private-scoped rule silently
  non-functional. This category is per-network (keyed to this specific
  Wi-Fi network), not global — connecting this machine to a different
  network later does not inherit it.
- **Plain HTTP, not HTTPS**, same as every other pilot-flow target this
  Sprint. Acceptable only because O-2 scopes this to the local network with
  no external ingress; ADR-048 Decision 5 forbids this over an
  externally-reachable hostname. Revisit before any external-access work.
