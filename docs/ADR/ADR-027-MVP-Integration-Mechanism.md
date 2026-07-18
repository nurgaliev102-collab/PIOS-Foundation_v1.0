# ADR-027: MVP Integration Mechanism

## Status

Proposed

## Context

PIOS's four MVP modules — Passenger Experience, Order Management, Dispatch, and Driver Management — are each independently deployable as their own release artifact from day one (ADR-026), and communicate with one another only through the declared contracts in INTERFACE_CONTRACTS.md Section 5, expressed as events (ADR-003). ADR-003 already considered and rejected "direct, synchronous calls between capabilities for every interaction" as PIOS's general communication mechanism, specifically because it creates temporal coupling that undermines the independent evolution ADR-001 intends and that ADR-026 operationalizes as a release-process guarantee. Neither ADR-003 nor any later ADR selects a concrete delivery mechanism — message broker, HTTP, or otherwise — for carrying an event from one independently deployed module to another; ADR-020 resolves the equivalent question only for dependencies *external* to PIOS, not for communication among PIOS's own modules.

The MVP Integration Layer initiative needs to demonstrate that the three MVP communication flows — Passenger Experience → Order Management, Driver Management → Dispatch, and Dispatch → Order Management — rest on correct, mutually compatible application-level contracts, before any concrete transport technology has been chosen, and while every currently available transport option (message broker, REST/HTTP, a shared library of common types) is either explicitly out of scope for this initiative or would itself contradict ADR-003 or ADR-026 if adopted as a production-code shortcut.

## Problem

How can PIOS verify, at this stage of implementation, that its modules' application-level contracts — the shape of the data each provider exposes and each consumer expects — are correctly formed and mutually compatible, without introducing production-code coupling between independently deployable modules, and without prematurely selecting a real transport mechanism that deserves its own dedicated decision?

## Decision

- Each module's application layer gains an explicit, minimal contract boundary for every interaction contract it participates in (INTERFACE_CONTRACTS.md Section 5), expressed only in primitive types (`String`, `Boolean`, and similar) or types entirely local to that module — never another module's domain classes.
  - A **provider** module gains a small application-layer type or method that translates its own domain event into the minimal primitive payload the corresponding contract describes.
  - A **consumer** module gains a small application-layer handler method that accepts that same primitive payload shape and sequences it into that module's own existing application service, exactly as any other command would be sequenced.
- **No module's main source set gains a build dependency on another module's code.** Every module's production/deployable artifact remains exactly as independently buildable, versionable, and releasable as ADR-026 requires. No production code performs a direct synchronous call into another capability, so ADR-003's rejected alternative is not reintroduced.
- Compatibility between a provider's payload shape and a consumer's handler signature is verified exclusively in test code, through a test-scoped-only Gradle dependency (`testImplementation`) from the module exercising a flow onto the module(s) needed to construct that test. Test-scoped dependencies are excluded from each module's packaged/deployed artifact (including Spring Boot's `bootJar`) and therefore do not affect independent deployability.
- This mechanism is explicitly provisional and scoped to the MVP. It proves the contracts are shaped correctly; it does not deliver events between independently running instances of these services. Actually wiring these flows into running, independently deployed services requires a future decision selecting a concrete transport mechanism, which is out of scope for this ADR and remains open — comparable to the "Architecture Decision Required" markers SYSTEM_ARCHITECTURE.md already uses for other undecided integration mechanisms.

## Alternatives Considered

- **Direct, synchronous production-code calls** (a Gradle project dependency between two business modules' main source, with the provider calling the consumer's handler at runtime). Rejected: directly contradicts ADR-003's explicitly rejected alternative — "creates temporal coupling... undermining the independent evolution intended by ADR-001" — and ADR-026's guarantee that "each module can be built, versioned, and released without requiring any other module to be rebuilt or redeployed alongside it." A production dependency ties both modules' builds and releases together.
- **A shared "integration" or "contracts" Gradle subproject** holding common event or DTO types both provider and consumer import. Rejected: this initiative's own instructions explicitly forbid shared common domain libraries and shared business objects, consistent with the zero-shared-subproject rule already established during Repository Foundation v1.0.
- **Selecting and implementing a concrete transport now** (an in-memory publish/subscribe utility, an embedded message queue, or similar). Rejected for this ADR specifically: choosing a transport mechanism is a substantive, independent architectural decision — comparable in weight to ADR-023's and ADR-025's technology selections — and deserves its own dedicated ADR and review rather than being bundled into establishing the contract-verification approach.

## Consequences

### Positive Consequences

- Fully preserves ADR-003 and ADR-026 with no exception or amendment to either.
- Lets the MVP demonstrate that its application-level contracts are well-formed and mutually compatible ahead of committing to any specific transport technology.
- Keeps the meaning of "module isolation" unambiguous in tests: production code never gains a cross-module import; only test code does, and only to prove a contract, never to execute production logic.

### Negative Consequences

- The MVP's running services do not yet actually deliver events to one another at runtime. Results described conversationally as "the order creation flow starts" or "Dispatch receives availability information" are demonstrated by tests, not by an operating end-to-end runtime path, until a transport mechanism is chosen.
- A future ADR selecting a real transport is required before any of these three flows becomes true of the actually running, independently deployed services.

## Evolution Path

This ADR establishes a documented, reusable pattern for proving contract correctness ahead of transport selection; it applies to any future module pair facing the same gap, not only the three MVP flows. Selecting the concrete transport mechanism that eventually carries these events between independently running services is a distinct decision, to be recorded as its own future ADR consistent with ADR-015 (Evolution Strategy); it is not anticipated or pre-selected here.

## Related ADRs

- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md) — independent evolution of capabilities, which this decision preserves.
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md) — the rejected direct-call alternative this decision does not reintroduce.
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md) — no module accesses another's internals; this decision confines cross-module references to primitives and test scope only.
- [ADR-015: Evolution Strategy](ADR-015-Evolution-Strategy.md) — governs how the future transport-selection decision must be recorded.
- [ADR-020: Integration Philosophy](ADR-020-Integration-Philosophy.md) — the nearest existing precedent, though scoped to external dependencies rather than PIOS's own modules.
- [ADR-026: Deployment Strategy](ADR-026-Deployment-Strategy.md) — the independent-deployability guarantee this decision does not compromise.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 5 (Documentation-Driven Development)
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md), Section 5 (Interaction Contracts)
- [EVENT_CATALOG.md](../EVENT_CATALOG.md)
- [MODULE_STRUCTURE.md](../MODULE_STRUCTURE.md)
