# Engineering Journal — Sprint 1: Platform Operations Center MVP

Running log of engineering findings during Stage 2 implementation that are
worth keeping but don't belong in an ADR, a Product Decision, or the task
tracker itself — investigations, false alarms, and root causes, recorded
so the reasoning isn't lost once the immediate conversation ends. Entries
are append-only, most recent last, numbered `J-<n>`.

---

## J-1 (2026-08-06) — T-4 phone verification: false 404, not a network/Firewall/`platform-ops` defect

**Symptom reported.** During T-4's phone verification, an iPhone on the
same Wi-Fi successfully connected to `http://192.168.0.102:8090` but
received a Spring Boot "Whitelabel Error Page" (`HTTP 404`) instead of the
expected `401` from `GET /v1/ops/health`.

**Investigation.** A full network diagnosis (process binding, Firewall
rule scope, network profile, routing table, ARP table, `happ-tun`
interference) found nothing wrong on the network side — confirmed
independently by the phone itself successfully completing a TCP connection
at all. A continuous `pktmon` packet capture (`--capture -m multi-file`,
filtered to port 8090) recorded the phone's actual two connection attempts
in full, including HTTP payload.

**Root cause, read directly from the captured bytes — not inferred:**

- **First attempt** (05:36:19): the captured request line was
  `GET /v1/ops/health: HTTP/1.1` — a trailing `:` immediately after
  `health`, before the space and protocol version. This is a different,
  unmapped path from the registered `/v1/ops/health`, so Spring's
  `DispatcherServlet` correctly found no handler and returned `404` via
  its default error page. Confirmed independently by reproducing the exact
  same `404` from this machine itself when deliberately requesting a
  mismatched path.
- **Second attempt** (05:39:09, ~2.5 minutes later, same device): the
  captured request line was the correct `GET /v1/ops/health HTTP/1.1`. The
  captured response was `HTTP/1.1 401` — the correct, expected answer
  (`pios.ops.*` is intentionally unconfigured at this stage; ADR-050
  Decision 6, "no credential" only applies to a different endpoint's
  question — here it is ADR-047 Decision 1's fail-closed default).

**Conclusion.** The registered route table was never wrong
(`GET /v1/ops/health`, confirmed both by reading `HealthController.kt` and
by the second attempt's own correct response). The first attempt's `404`
was caused by a malformed request path — an extra `:` character not
present in the intended URL — most plausibly a manual typing or
autocomplete artifact on the phone, not a defect in `platform-ops`, the
Windows Firewall rule, the WinSW service, or the network path. The second,
correctly-formed attempt succeeded without any change on this machine
between the two attempts.

**Why this is recorded rather than discarded.** The diagnostic path here
is reusable: a `Whitelabel Error Page` / `404` on a single-endpoint Spring
Boot 3 service is worth checking against the exact captured request bytes
before assuming a routing, Firewall, or network defect — `curl`/
`Invoke-WebRequest` on the same machine can silently send a cleaner request
than what a phone's browser actually transmitted, so a same-machine
reproduction alone does not rule out a client-side malformed URL.

**No code, ADR, or Product Decision changed as a result of this entry.**
