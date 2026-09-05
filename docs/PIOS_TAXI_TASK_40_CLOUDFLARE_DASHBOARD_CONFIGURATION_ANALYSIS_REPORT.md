# Task 40 — Cloudflare Tunnel Configuration Verification (Dashboard Evidence Analysis)

**Type: Read-only analysis of dashboard evidence supplied by the operator.** This task made no Cloudflare mutation, no DNS change, no route/ingress change, no token rotation, no local network change, and started no new diagnostic `cloudflared` process (not needed — the dashboard evidence already answers the question that would have required one). Two safe, read-only local re-checks were performed to confirm nothing has drifted since Task 39.

**Classification: CLOUDFLARE CONFIGURATION ERROR** — but with an important qualifier spelled out in Section 6: this is not the *only* thing broken, and fixing it alone would not restore `piosapp.ru`.

---

## 1. What the Dashboard Evidence Establishes

| Field | Value | Interpretation |
|---|---|---|
| Tunnel name | `pios-pilot` | Matches every local finding exactly |
| Tunnel ID | `70a49741-67dd-4f86-8ab1-7c42186c2fbf` | **Identical** to the ID `cloudflared` has decoded from the local token at the start of every diagnostic run since Task 33 (re-confirmed again this task) — token/tunnel identity is now confirmed matching from **both** sides, not just inferred from the local side alone |
| Tunnel type | `cloudflared` | Expected — not a WARP-connector or other tunnel type |
| **Status** | **Down** | Direct, account-side confirmation of exactly what Tasks 33–38's own local logs already showed: no connector has ever successfully stayed connected |
| **Active replicas** | **0** | Same conclusion, restated in Cloudflare's own connector-count terms |
| **Routes** | **0** | **The single most important new fact this task adds.** Zero ingress rules exist for this tunnel, for any hostname |
| Live Logs | "Cannot retrieve connector logs" / "No tunnel replicas found" | Consistent with `conns: []` from `cloudflared tunnel info --show-recently-disconnected`, re-confirmed again this task — Cloudflare's own log retrieval mechanism has nothing to show because no connector has ever registered *durably* enough to produce any |

**DNS records:**
- `piosapp.ru | Type: Tunnel | Content: pios-pilot | Proxied` — a DNS record **does** exist mapping `piosapp.ru` to this tunnel. This is the artifact of `cloudflared tunnel route dns pios-pilot piosapp.ru` (Step 5 of `docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md`, re-read this task) — that specific command **was** run, at some point, by someone. The record type "Tunnel" (not a plain CNAME to `<uuid>.cfargotunnel.com`) is Cloudflare's own newer, more direct DNS-record representation of exactly that same mapping — functionally equivalent, not evidence of anything unusual.
- `pilot.piosapp.ru | CNAME | home-pc.tail385153.ts.net | Proxied` — a separate, previously-undocumented-in-this-arc record, proxying a *different* subdomain through Cloudflare to the already-working Tailscale Funnel hostname. Not investigated further — out of this task's scope (it concerns `pios-pilot`/`piosapp.ru` specifically), noted only because it's new information: Cloudflare's proxy layer for this account/zone is demonstrably capable of serving *something* correctly, which is mild, indirect evidence against a zone-wide or account-wide Cloudflare outage.
- `www.piosapp.ru` carries MX/TXT records — unrelated (mail routing), noted only for completeness.

## 2. Does the DNS Record Represent the Intended Hostname Configuration?

**Half of it, yes; half of it, no.** A DNS-level mapping (`piosapp.ru` → tunnel `pios-pilot`) is necessary but **not sufficient** on its own — Cloudflare's tunnel system requires a *separate* configuration layer, the tunnel's own ingress rules (what the dashboard calls "Public Hostnames" / "Published Applications" for this hostname), telling the tunnel **what to do** with traffic that arrives for that hostname (which local origin/port to proxy it to). The DNS record exists. **The ingress rule does not** — "Routes: 0" is the direct, first-hand confirmation of that absence.

This maps precisely onto the gap Task 37 (Section 5) and Task 38 (Section 4) already identified from the *local* side, purely by reasoning about which setup steps had and hadn't been completed: `docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md`'s own Step 5 bundles *two* actions together — `cloudflared tunnel route dns pios-pilot piosapp.ru` (the DNS mapping) **and** hand-creating a local `config.yml` with an explicit `ingress:` block (`piosapp.ru` → `http://127.0.0.1:4173`). Only the first half was ever done. The second half — whether expressed as a local `config.yml` (never created, confirmed absent since Task 31) or as its dashboard-side equivalent (a Public Hostname / Published Application entry) — was never completed either. **This is now proven, not merely inferred**: "Routes: 0" is that proof.

## 3. Comparison Against `docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md` and `run-tunnel.ps1`

Re-read both this task, not assumed from memory:

- The deployment doc's own Step 5 `config.yml` template specifies exactly one ingress rule: `hostname: piosapp.ru` → `service: http://127.0.0.1:4173`, plus the required catch-all `service: http_status:404`. **This is the objectively correct, already-authorized intended configuration** — it comes from the project's own documented, product-owner-approved setup plan (Section 3, "Что уже сделано в репозиторием этим документом" / "Traceability" table), not from this task inventing a new design.
- `run-tunnel.ps1`'s own comment (re-read again this task) already anticipated exactly this kind of gap in spirit: it explains that token mode was adopted specifically because the classic `config.yml`+credentials-JSON path could not be completed on this machine — but its comment focuses only on the *credentials file* being missing, not on whether the *ingress rule itself* was ever separately configured via the dashboard as token-mode requires. That specific gap was never explicitly called out until Task 37/38's own analysis, and is now directly confirmed by Section 1's "Routes: 0."

**Conclusion of this comparison: the Cloudflare-side configuration does not match the documented, intended design.** The intended design has exactly one route: `piosapp.ru` → `http://127.0.0.1:4173`. The actual configuration has zero.

## 4. Why the "Failed to Add Published Application" Attempt Likely Failed

The operator's own attempt to add exactly the correct route (`piosapp.ru` → `http://127.0.0.1:4173`) failed with **"Failed to add published application"**, and explicitly did not create a new route. This task was not able to observe the dashboard directly during that attempt, so the following is reasoned hypothesis, not confirmed fact — presented as such, not as certainty:

**Most likely explanation: a hostname collision with the pre-existing DNS record.** `piosapp.ru` already has a DNS record of type `Tunnel` pointing at `pios-pilot` (Section 1) — created directly via `cloudflared tunnel route dns`, a lower-level CLI action that writes the DNS record without going through the dashboard's own "Published Application" creation flow. That dashboard flow, when creating a **new** Public Hostname, may itself attempt to create or claim the same DNS record as part of the same action — which would collide with the one that already exists, plausibly producing exactly this kind of generic "Failed to add" error without a more specific reason shown. **This is a reasoned hypothesis based on how Cloudflare's tunnel/DNS systems are documented to interact, not something this task can confirm without either attempting the change again (not authorized) or seeing the exact error detail Cloudflare's UI may show on hover/expand (not captured in what was reported).**

A second, less likely possibility: some tunnel configuration flows require at least one connector to have registered (even transiently) before a route can be saved against it — and this tunnel's own `Status: Down` / `Active replicas: 0` means no connector has ever registered durably enough for that precondition, if it exists, to be satisfied. This task cannot confirm or deny this from the evidence available either.

**Neither hypothesis is treated as proven.** Per this report's own discipline (matching this whole task arc's own standing rule), only what "Routes: 0" itself directly proves is asserted as fact; the *reason the fix attempt failed* is offered as reasoned-but-unconfirmed analysis.

## 5. Root-Cause Classification

**CLOUDFLARE CONFIGURATION ERROR** — confirmed, not inferred: **the tunnel has zero ingress routes configured**, for `piosapp.ru` or any other hostname. This is a real, demonstrated discrepancy between the documented intended configuration (Section 3) and the actual Cloudflare-side state (Section 1), established directly from dashboard evidence rather than guessed from local symptoms alone.

## 6. Critical Qualifier — This Does Not, By Itself, Explain Everything

**Fixing the missing route would not, on its own, restore `piosapp.ru`.** The dashboard's own `Status: Down` / `Active replicas: 0` is the *same* underlying problem Tasks 33–38 already spent five tasks characterizing from the local side: cloudflared's connector reaches `"Registering tunnel connection"` and then fails, consistently, at almost exactly its own negotiated QUIC `MaxIdleTimeout` (5 seconds) — reproduced identically across at least five separate registration events, two structurally different local networks (a cellular hotspot and a broadband router), with Happ both present and deliberately bypassed, and both of cloudflared's own transports (QUIC explicitly, HTTP/2 silently). **That failure is completely independent of whether a route exists.** A tunnel with a correctly-configured route but no durable connector would still show `Status: Down` and would still fail to serve `piosapp.ru` — Cloudflare's edge has nowhere to route traffic *to* if no connector is actually, sustainedly attached, regardless of what the ingress rule says.

**There are therefore two separate, compounding problems, not one:**
1. **Missing ingress/route configuration** — newly confirmed this task, Cloudflare-side, objectively fixable per the already-documented intended design (Section 3).
2. **The connector cannot sustain a registered connection** — established Tasks 33–38, still completely unexplained, and **this task adds nothing new toward resolving it.**

Fixing #1 without also resolving #2 would very likely still leave `piosapp.ru` returning an error — a different one than 530 perhaps (Cloudflare has a distinct error family for "connected tunnel, no matching route" vs. "tunnel not connected at all"), but still not a working page.

## 7. What This Changes in the Task 37–39 Hypotheses

| Hypothesis (from Task 39) | Prior status | Status after this task |
|---|---|---|
| A. Cloudflare-side ingress/hostname configuration missing or wrong | Moderate, circumstantial | **CONFIRMED** — "Routes: 0" is direct proof, not inference |
| B. Cause specific to this one machine (transport/connector-sustain failure) | Weak-to-moderate, by elimination | **Unchanged, still open** — this task neither strengthens nor weakens it; the dashboard's own `Status: Down`/`Active replicas: 0` independently corroborates that this failure is real and ongoing (already known), but says nothing about *why*, and does not distinguish "this machine" from any other remaining possibility |

Both hypotheses now coexist as **confirmed-but-insufficient (A)** and **still-unresolved (B)** — A explains why a healthy tunnel connection still wouldn't serve `piosapp.ru` today, B explains why the tunnel connection itself isn't healthy in the first place. Resolving only one leaves the site broken.

## 8. Exact Root Cause (Proven Portion Only)

**Proven:** Tunnel `pios-pilot` has zero configured ingress routes on Cloudflare's own account records, confirmed via direct dashboard inspection. The DNS-level mapping (`piosapp.ru` → `pios-pilot`) exists and is correctly targeted at the right tunnel — this part of the setup was completed. The ingress-rule half of the same original setup step (Step 5 of the deployment doc) was never completed, on either the local (`config.yml`, confirmed absent since Task 31) or remote (dashboard Public Hostname, confirmed "Routes: 0" this task) side.

**Not proven:** why the connector itself cannot sustain a registered connection (Tasks 33–38's own open question, unchanged by this task); why the one attempt to add the missing route failed (Section 4, reasoned hypothesis only).

## 9. Exact Proposed Change (Not Made — Stopping Per This Task's Own Instruction 9)

**Change:** Add a Public Hostname / Published Application to tunnel `pios-pilot`: hostname `piosapp.ru`, service `HTTP` at `127.0.0.1:4173` — exactly, field-for-field, what `docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md`'s own already-authorized `config.yml` template already specifies. This is not a new design; it is completing a documented step that was left half-done.

**Why it would (partially) address the demonstrated failure:** it directly closes the "Routes: 0" gap (Section 1/5) — the objectively missing half of the tunnel's own intended configuration.

**Why it would likely NOT be sufficient by itself:** Section 6 — the connector-sustain failure (Tasks 33–38) is untouched by this change and would very likely still prevent `piosapp.ru` from actually serving anything, even with the route correctly in place.

**Rollback procedure, if made:** deleting a single Public Hostname entry from a tunnel's own configuration is a routine, fully reversible dashboard action (remove the one entry; the tunnel and its DNS record are unaffected) — no DNS record deletion, no token change, no tunnel deletion/recreation would be involved.

**This task does not make this change.** Per its own Instruction 9, it stops here and reports the above instead, since a configuration change is indicated but this task's own mandate was to diagnose, not to act unilaterally.

## 10. Minimal Next Experiment

Two independent next steps, addressing the two separate problems from Section 6 — neither is a "guessed fix," both are the direct, smallest next action for each already-confirmed gap:

1. **For the missing route (Section 9):** retry adding the Public Hostname, but first capture the *exact* error Cloudflare's dashboard shows (not just the generic toast message already reported) — expand/hover any error detail, check the browser's own network tab if convenient, or simply attempt it while a diagnostic `cloudflared` instance is running locally (to test whether an active-connector precondition, Section 4's second hypothesis, is the actual blocker). This task does not attempt this itself, per Instruction 9.
2. **For the connector-sustain failure (unchanged from Task 38's own conclusion):** the two remaining options are still (a) testing from a genuinely different physical machine (not yet done in this entire arc), or (b) if Cloudflare support/status channels are available to the account owner, checking whether there is any account-level or tunnel-level restriction Cloudflare's own support could see that this task's tools cannot.

---

*Task 40 continuation complete. CLOUDFLARE CONFIGURATION ERROR confirmed (missing ingress route) — but explicitly not sufficient to explain the full failure on its own. No Cloudflare, DNS, token, network, or PIOS change was made. Stopping — Task 41 not started.*
