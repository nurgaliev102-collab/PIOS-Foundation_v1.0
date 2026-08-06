# ADR-048: Operations Control Plane Ingress — Why Control Must Not Travel Through the Thing It Controls

## Status

**Draft — awaiting Product Owner ratification.**

Authorized in principle by the Product Owner's instruction of 2026-08-06
(which names *«remote management»* — удалённое управление — among the
component's purposes) and by
`docs/PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` (itself a draft).

**One decision in this ADR is architectural and one is not, and they are kept
apart.** *That the control plane must not share a failure path with the
surface it controls* is architectural and is decided here. *Which specific
external mechanism is bought and configured* costs money and a domain name,
is therefore the Product Owner's, and is deferred in that Product Decision's
Section 11.2.

**Package.** ADR-046 (what the component is), ADR-047 (who may make it act),
**048 (this — how the instruction arrives)**, ADR-049 (how the platform
returns to a known-good state). Together they supersede ADR-045.

**Relationship to existing infrastructure documents.** This ADR **extends**
`PIOS_PILOT_INFRASTRUCTURE_DECISION.md` and
`PILOT_INFRASTRUCTURE_ROUTING_DECISION.md`. It supersedes neither, and it
requires **zero** changes to the routing table either of them established —
see Decision 2.

## Context

### The single public path that exists today

Verified against the files, 2026-08-06:

- One Cloudflare quick tunnel points at `vite preview` on port 4173
  (`PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 4, Step 1;
  `frontend/vite.config.ts` lines 13–14 allow `*.trycloudflare.com`).
- That one Vite process proxies **eleven** path prefixes onward to five local
  ports (`vite.config.ts` lines 30–42): six domain prefixes plus the five
  `/v1/health/<module-name>` rows added by
  `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Variant A.
- Every prefix is forwarded **unchanged**. "No prefix is rewritten" is a
  stated, binding condition of the developer handoff
  (`PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 5, condition 1), and
  Variant A was chosen over Variant B specifically to preserve it.
- The quick tunnel's hostname **changes on every restart**, which invalidates
  already-distributed driver invitation links and requires all five services
  to restart and the frontend to be rebuilt (same document, "Если туннель
  оборвался").

### The open question this ADR is forced to confront

`PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Section 7 already recorded it, and
already recorded that it had become sharper:

> **Постоянный публичный адрес.** Быстрый туннель меняет `PILOT_URL` при
> каждом перезапуске… предыдущий документ отклонил [именованный туннель] как
> избыточный **для одного сопровождаемого сеанса** — а этой области больше
> не существует: PIOS в Pilot Operation, Артур работает без оператора рядом.

That document declined to settle it, correctly, because settling an
infrastructure question inside a routing question is the same evasion it was
refusing elsewhere. This ADR does not settle it either — but it removes the
option of continuing to ignore it, because the control plane cannot be
reached at all without an answer.

### The failure mode this ADR exists to prevent

If the operations API were added as a twelfth row in `preview.proxy`, then:

> **`vite preview` would become a dependency of the ability to restart
> `vite preview`.**

The first time that process died, the phone would show nothing and control
nothing — in precisely the situation the tool exists for. The same trap
applies to the tunnel process itself, and to any component the control plane
is asked to manage.

This is not a new argument in this repository. It is the argument that
already decided a different question:
`PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Section 3 rejected Variant C
(one tunnel per service) on a structurally identical ground, and named it as
the decisive one:

> **это отравляет сам инструмент, ради которого затеяно.** Если туннель
> Dispatch оборвался, а сам Dispatch жив и здоров, пульт покажет… красный
> экран о проблеме, которой нет… Каждый добавленный туннель добавляет новый
> источник ложного диагноза в единственный инструмент, который существует
> ради постановки верного диагноза.

Routing control through `vite preview` is the same mistake with the arrow
reversed: instead of producing a false diagnosis, it removes the cure.

## Problem

By what network path does an instruction reach the operations agent from
outside the machine, such that the path does not depend on any component the
agent may be asked to restart — and without inventing a remote-access
mechanism unrelated to the one this project has already chosen?

## Decision

### 1. The control plane has its own ingress, terminated by the tunnel process itself and pointed directly at the agent. It does not traverse `vite preview`.

```
Phone
  |
  |  https://pios.<domain>   -> cloudflared -> 127.0.0.1:4173  (vite preview)
  |                                              -> 8081..8086 (five modules)
  |
  |  https://ops.<domain>    -> cloudflared -> 127.0.0.1:8090  (platform-ops)
        ^                                        ^
        external access gate                     pios.ops.* credential
        (Decision 3 — open)                      (ADR-047 Decision 1)
```

Binding properties:

- The operations hostname resolves to the agent's port **directly**. No Vite
  process, no application-level proxy, no path rewriting.
- The two hostnames fail independently. `vite preview` dying takes the
  driver and passenger surface with it and leaves the control plane intact —
  which is the entire point.
- The agent is reachable on `127.0.0.1` when someone is at the machine,
  independently of any tunnel, so a dead tunnel does not make the platform
  unmanageable in person.

**Named residual, not argued away:** the tunnel process is itself a single
point of failure for *remote* control, and if it dies, remote control dies
with it. It is a target the agent can restart (ADR-046 Decision 2's closed
list includes `tunnel`) — but only from a session that already reached the
agent, which after a tunnel failure means being at the machine. A control
plane hosted on the machine it controls cannot rescue its own last hop. This
is disclosed rather than solved; solving it means a second, independent
channel, which is out of scope and not recommended at pilot scale.

### 2. Nothing is added to `preview.proxy`. The existing routing table and its conditions stay literally true.

Zero rows are added to `frontend/vite.config.ts`. The table keeps its eleven
rows, its flat "prefix → port" shape, and its "no prefix is rewritten"
condition, all unchanged.

This matters beyond tidiness.
`PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 0 drew a line and asked that
it not be crossed silently:

> Если консолидирующий прокси перестанет быть временным процессом… и станет
> постоянным компонентом системы — API-шлюзом, через который модули
> адресуются в норме, — это уже архитектурное решение…

Adding a control path to that table would push it one step further toward
being a gateway — the step that document asked to be ratified before it
happens. Taking a separate ingress means the proxy does not move at all, and
that line stays where it was drawn.

### 3. An external access gate in front of the operations hostname is the recommended outer layer — and it is the Product Owner's decision, not this ADR's.

ADR-044 Decision 10 already named it, in the course of admitting where it
falls short:

> **Defense in Depth — not satisfied, and not claimed.** … Putting Cloudflare
> Access or equivalent in front of the deployment remains available as a
> cheap outer layer and **is the recommended next step** toward it.

ADR-047 Decision 2 declined to build a second factor *inside* the
application, on the ground that it would be the first hand-rolled or
newly-dependency-bearing authentication protocol in PIOS. That reasoning only
holds if the stronger layer is placed somewhere — and this is where.

- **Architecture's recommendation, stated separately from the fact that it
  remains open:** enable an external access gate on the operations hostname
  **before** the first destructive endpoint exists, not after.
- **Why the operations hostname specifically and not both:** putting an
  access gate in front of `pios.<domain>` would put a login in front of
  passengers and drivers, which is participant authentication — out of scope
  and forbidden here (ADR-044 Decision 11). The operations hostname has
  exactly one legitimate user, so gating it costs nothing in reach.
- The decision is deferred in
  `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Section 11.1 and 11.2
  because it costs an account and a domain.

**If the answer is no**, the credential and the confirmation token (ADR-047)
are the only barriers, ADR-011's Defense in Depth stays unmet for this
surface, and that must be stated plainly rather than absorbed quietly.

### 4. A stable hostname is a precondition of this design, not an enhancement of it — and choosing how to get one is the Product Owner's.

A quick tunnel cannot cleanly serve two hostnames and changes its name on
every restart. Both properties are fatal here specifically:

- **Two hostnames** are required by Decision 1.
- **A changing hostname** is tolerable for a link a driver can be re-sent. It
  is not tolerable for the address the owner reaches for when something is
  broken at 03:00, because the moment they need it is exactly the moment
  nobody can tell them the new one.

The mechanism that provides both is a **named Cloudflare Tunnel** (an
account, an owned domain, stable hostnames, `cloudflared` run as a Windows
service so it survives reboot — ADR-049 Decision 1). That is this ADR's
recommendation, and it is a continuation of the mechanism this project has
already chosen rather than a new one: the same `cloudflared`, the same
provider, the same shape, with a name that stays still.

**This ADR does not decide it**, because it costs money and requires a domain
the Product Owner must own. It makes the choice unavoidable: without a stable
hostname, remote operations either does not work or works through the process
it may need to restart, and Decision 1 forbids the second.

Alternatives available to the Product Owner, with their honest costs:

| Option | Cost | Consequence |
| --- | --- | --- |
| Named Cloudflare Tunnel (recommended) | Account + owned domain | Stable hostnames, access gate becomes a checkbox, survives reboot |
| Quick tunnel, second instance for ops | Free | Second hostname changes on every restart; unusable as an emergency address |
| VPN (e.g. WireGuard/Tailscale) to the laptop | New tool, new install, phone client | Strong, and introduces a mechanism unrelated to the one already chosen; larger change than it appears |
| Local network only, no remote control | Free | Requirement "remote management" is not met. Legitimate if the Product Owner decides remote control is not worth its risk |

### 5. TLS is a precondition of the whole package, restated because it is load-bearing here.

ADR-044 Decision 9 says it for the observation credential; it is more
consequential for this one:

> If this surface is ever served over plain HTTP, the mechanism provides no
> protection at all.

The operations credential travels in an `Authorization` header on every
request (ADR-047 Decision 1). Over plain HTTP, everything in ADR-047 is worth
zero, and the surface behind it can stop the platform. **Serving the
operations hostname over `http://` is forbidden, not discouraged.**

### 6. What this ADR does not decide.

- **It does not change how drivers or passengers reach PIOS.** The
  `pios.<domain>` path, the eleven proxy rows, and the CORS configuration in
  six `WebCorsConfiguration` classes are untouched. No participant sees any
  difference.
- **It does not resolve the stable-address question for the pilot surface.**
  If a named tunnel is adopted, the pilot surface benefits — invitation links
  stop being invalidated by a tunnel restart — but that is a consequence to
  be recorded in the infrastructure documents, not a decision taken here.
- **It does not authorize exposing anything else.** No database port, no
  RabbitMQ management UI, no `network-management`, no additional hostname of
  any kind.
- **It does not make PIOS's public APIs authenticated.** They remain open to
  anyone who knows the address (ADR-044 Decision 5, Decision 11). This ADR
  adds a second door to a new room; the front door is still open.

## Alternatives Considered

- **A twelfth row in `preview.proxy`, routing `/v1/ops/**` to 8090.** The
  cheapest possible option — one line — and rejected as the central mistake
  this ADR exists to prevent (Decision 1). It would also move the proxy
  toward being the gateway `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 0
  asked to be ratified before it happens.
- **A second quick tunnel for the operations port.** Free and immediate, and
  rejected: a hostname that changes on every restart cannot be the address
  someone reaches for in an emergency. It also revives the "six tunnels"
  multiplication that `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Section 3
  rejected under Variant C.
- **A VPN to the laptop instead of a public hostname.** Genuinely strong, and
  arguably the most secure option on the list. Not recommended *first*
  because it introduces a mechanism unrelated to the one this project has
  already chosen and operationally proven, and because a phone that must
  connect a VPN before it can help is a phone that will not be used at 03:00.
  Recorded as a legitimate choice if the Product Owner prefers it.
- **Local network only.** Meets no remote requirement, and additionally
  suffers the secure-context problems `PIOS_PILOT_INFRASTRUCTURE_DECISION.md`
  facts 1.8 and 1.10 already documented for the pilot surface. Retained as
  the honest fallback if remote control is judged not worth its risk.
- **An access gate in front of both hostnames.** Rejected: it would put a
  login in front of drivers and passengers, which is participant
  authentication (ADR-044 Decision 11).

## Consequences

### Positive

- The control plane survives the failure of every component it can control,
  including the frontend it may be asked to restart.
- `frontend/vite.config.ts` is not touched, so the proxy table's shape, its
  no-rewrite condition and the boundary drawn in
  `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 0 all stay exactly as
  ratified.
- The one genuinely dangerous surface gets a hostname of its own, which makes
  an external access gate a configuration checkbox rather than a project —
  finally making ADR-044 Decision 10's recommended next step cheap.
- No new remote-access technology is introduced; the existing `cloudflared`
  mechanism is used with a stable name.
- If a named tunnel is adopted, an old and separate problem — invitation
  links dying when the tunnel restarts — improves as a side effect, without
  this ADR claiming to have solved it.

### Negative, disclosed rather than argued away

- **It forces an infrastructure decision with a real price.** An account and
  a domain, and someone to configure `cloudflared` as a service. This ADR
  makes that unavoidable rather than paying for it.
- **The tunnel process remains a single point of failure for remote
  control**, and the agent cannot rescue its own last hop (Decision 1).
- **Two public hostnames are two things to configure correctly.** A
  misconfigured `ops.<domain>` that lands on the wrong port is a silent
  failure, not a loud one, and must be verified from an actual phone before
  it is relied upon.
- **Defense in Depth remains unmet unless Decision 3 is answered yes.** As
  decided here alone, ADR-011's third principle is still only partially met,
  knowingly.
- **Nothing helps when the laptop is off, asleep, or offline.** No ingress
  design can report that the machine is gone; the console must distinguish
  "PIOS is stopped" from "I cannot reach the server", as
  `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 5.5 already requires for
  a related reason.

## Traceability

| Subject | Source |
| --- | --- |
| Authorizing document; remote management as a named purpose | `PRODUCT_DECISION_PLATFORM_OPERATIONS_CENTER.md` Sections 6, 9, 11.2; Product Owner instruction 2026-08-06 |
| The single existing public path and its eleven proxy rows | `frontend/vite.config.ts` lines 13–42; `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Sections 4–5 |
| "No prefix is rewritten" as a binding condition | `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 5, condition 1; `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Variant A |
| The boundary that would make the proxy a gateway | `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 0 |
| Why a monitoring/control path must not share a failure mode with its target | `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Section 3, Variant C, argument 3 |
| Stable address already recorded as open, and already sharper | `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Section 7 |
| Recommended outer access layer | ADR-044 Decision 10; ADR-011 lines 19–23; ADR-047 Decision 2 |
| TLS as precondition, not enhancement | ADR-044 Decision 9 |
| Public APIs stay open; participant auth out of scope | ADR-044 Decisions 5, 11 |
| Quick tunnel hostname instability and its cost | `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` Section 4, "Если туннель оборвался" |
| `tunnel` as a controllable target; `cloudflared` as a service | ADR-046 Decision 2; ADR-049 Decision 1 |
| Sibling decisions | ADR-046 (component), ADR-047 (authorization), ADR-049 (recovery and change safety) |

## Files Changed

This ADR only. No source file is touched by this document, and in particular
`frontend/vite.config.ts` is **not** to be modified under it.

The infrastructure documents are **extended, not superseded**: whichever
option the Product Owner chooses under Decision 4 is to be recorded as an
addition to `PIOS_PILOT_INFRASTRUCTURE_DECISION.md` and
`PILOT_INFRASTRUCTURE_ROUTING_DECISION.md`, leaving their existing text
intact (`CLAUDE.md`, "Never Delete Documentation").
