# ADR-023: Backend Technology Decision

## Status

Proposed

## Context

The architecture foundation for PIOS is complete, and ADR-022 established the principles, boundaries, and constraints that every future technology decision must satisfy. Backend implementation is the next concrete step, and it cannot proceed further without an actual decision: a programming language, an application framework, and an architectural style for how that language and framework realize the modules already defined (MODULE_STRUCTURE.md). Unlike ADR-022, which deliberately selected no technology, this ADR is required to make one, evaluated explicitly against ADR-022's nine principles and against the architecture already approved.

## Decision

PIOS's backend is implemented in **Kotlin, running on the JVM**, using **Spring Boot** as the application framework, in an architectural style where **each of the eight modules already ratified (MODULE_STRUCTURE.md Section 3) is implemented as its own independently deployable service**, internally organized around the domain concepts already established (aggregates, domain services, value objects — DOMAIN_MODEL.md), communicating with other modules only through the synchronous interactions and published events already established in INTERFACE_CONTRACTS.md and EVENT_CATALOG.md.

**Why Kotlin.** PIOS's entire documentation set — DOMAIN_MODEL.md's aggregates, entities, and value objects; LOGICAL_DATA_MODEL.md's uniqueness and lifecycle constraints; DOMAIN_MODEL.md Section 11's invariants — describes a domain that benefits from being expressed in a language with strong, nominal typing and first-class support for representing a finite set of valid states. Kotlin's sealed classes and data classes express an aggregate's lifecycle (DOMAIN_MODEL.md Section 12) as a closed set of valid states that the compiler itself can check, and its null safety removes an entire class of the "hidden assumption" errors PROJECT_CONSTITUTION.md Section 4 warns against. It retains full interoperability with the mature JVM ecosystem while reducing the ceremony that has historically made Java comparatively more error-prone for this kind of modeling.

**Why Spring Boot.** Spring's conventions for dependency injection, modular application structure, and event publishing map directly onto concepts PIOS has already ratified: a module boundary (MODULE_STRUCTURE.md), a domain event published without direct coupling to its consumers (ADR-003), and a layered separation between coordinating a request and deciding its business meaning (APPLICATION_ARCHITECTURE.md Section 2). Its production-readiness tooling gives every module a mature, unopinionated starting point for the observability every module must provide under ADR-012, without this document selecting a specific monitoring product.

**Why one independently deployable service per module.** ADR-014 already requires that "each domain is deployed independently of the others, so that a change to one domain does not require redeploying unrelated domains." A single deployed process containing all eight modules would violate that requirement outright. This decision is therefore the direct technical consequence of ADR-014, not a new architectural preference introduced here.

## Evaluation Against ADR-022

- **Architecture Compatibility.** Kotlin's type system expresses the aggregates, value objects, and invariants in DOMAIN_MODEL.md Sections 4–6 and 11 directly; Spring Boot's module and event-publishing conventions match MODULE_STRUCTURE.md and INTERFACE_CONTRACTS.md Section 5 without requiring either to be compromised.
- **Long-Term Maintainability.** The JVM ecosystem has a multi-decade track record with long-lived, evolving enterprise systems; Kotlin's null safety and strong typing catch a wide class of errors at compile time, consistent with the Long-Term Maintainability element of ADR-001.
- **Developer Productivity.** Kotlin's concise syntax reduces boilerplate relative to Java while retaining full JVM interoperability; Spring Boot's conventions let engineers follow the small-controlled-change discipline in ENGINEERING_GUIDELINES.md Section 4 without reinventing infrastructure per module.
- **Operational Simplicity.** The JVM is one of the most widely understood, well-tooled runtimes for long-running backend services; Spring Boot's built-in health and metrics support serves ADR-012 directly, without this document selecting a specific operations product.
- **Community Maturity.** Both Kotlin and Spring have large, long-established communities and extensive documentation, reducing the risk of unsupported dependencies over PIOS's intended long life.
- **Security.** Spring's security ecosystem is mature and widely audited; Kotlin's null safety removes a common class of vulnerability, together supporting Security by Design and Defense in Depth (ADR-011), without this document selecting an authentication mechanism.
- **Performance Suitability.** PIOS's workload, per APPLICATION_ARCHITECTURE.md, is coordination and I/O-bound — sequencing commands, queries, and event reactions across modules — rather than CPU-intensive; the JVM's well-understood performance characteristics for this workload are sufficient without speculative optimization, consistent with ENGINEERING_GUIDELINES.md Section 3.
- **Team Capability.** Kotlin and Spring are both widely taught and widely hired-for, with a large available talent pool, satisfying this criterion without assuming a specialized skill set.
- **AI-Assisted Development Compatibility.** Kotlin and Spring are extensively represented in documentation, public code, and AI coding tool training data, satisfying ADR-022's AI-Assisted Development Compatibility criterion and the AI-Assisted Development Rules in ENGINEERING_GUIDELINES.md Section 12.

## Architecture Alignment

- **Domain isolation.** Each module is its own deployable service with its own process boundary, giving ADR-009's isolation rule and ADR-005's no-shared-database rule a hard, technology-enforced boundary rather than only a documentation-level one.
- **Module Structure.** Spring Boot's per-service application context maps one-to-one with the module boundaries in MODULE_STRUCTURE.md Section 3; no service contains more than one module's responsibility.
- **Interface Contracts.** Spring's support for both synchronous request handling and asynchronous event publishing accommodates the interaction and event contracts in INTERFACE_CONTRACTS.md Section 5 without prescribing which protocol carries them — that remains a separate, not-yet-made API-level decision, consistent with API_SPECIFICATION.md.
- **Application Architecture.** Kotlin and Spring's conventional separation between a request-handling layer and a domain layer maps onto the Application Coordinates versus Domain Decides Business Meaning distinction already established in APPLICATION_ARCHITECTURE.md Section 2.
- **Engineering Guidelines.** Kotlin's strong typing and Spring's structure directly support the Readability, Consistency, and Clear Responsibility principles in ENGINEERING_GUIDELINES.md Section 3.

## Consequences

**Positive.** A consistent, well-understood technology serves a long-lived, invariant-heavy, modular system; mature tooling already exists for the observability, security, and testing disciplines ADR-011, ADR-012, and ADR-013 already require; a large talent pool reduces Team Capability risk.

**Negative.** The JVM's startup and memory footprint is heavier than some lighter-weight runtimes; this is a deliberate tradeoff in favor of maintainability and ecosystem fit, accepted here. Spring's convention-driven structure can obscure control flow for engineers unfamiliar with it, requiring onboarding investment.

**Long-term implications.** Every future backend module is expected to use Kotlin on the JVM with Spring Boot unless a future ADR supersedes this one for a specific module; this decision is orthogonal to, and does not override, the per-domain persistence technology evaluation already established in ADR-021.

**Developer experience impact.** Engineers gain strong compile-time safety and mature IDE tooling, at the cost of more ceremony than a dynamically typed alternative.

**Operational impact.** JVM operations are mature and well documented, but each independently deployed module instance carries its own memory and startup characteristics, a concern left to a future Deployment Architecture decision.

## Alternatives Considered

- **TypeScript on Node.js.** A strong alternative with excellent AI-assisted development compatibility and a very large community. Not selected here because its structural type system and lack of built-in algebraic data types make expressing the strict aggregate invariants and finite lifecycle states in DOMAIN_MODEL.md Section 11 and LOGICAL_DATA_MODEL.md Section 7 less naturally enforced at compile time than Kotlin's sealed classes and null safety. It remains a legitimate candidate a future ADR could select for a specific, lighter-weight module.
- **Python.** Offers the strongest AI-assisted development compatibility of any candidate and a very large community. Not selected because its dynamic typing, even with type hints, provides weaker compile-time enforcement of the domain invariants and module boundaries this architecture's strict domain-isolation discipline (ADR-005, ADR-009) benefits from having enforced as early as possible.
- **Go.** Offers excellent operational simplicity and performance. Not selected because its minimal type system is a weaker fit for the rich aggregate, entity, and value-object vocabulary already established throughout DOMAIN_MODEL.md; better suited to infrastructure-level services than rich business-domain modules.
- **C# on .NET.** A closely comparable alternative in type-system strength and enterprise maturity. Not selected here primarily on Team Capability and Community Maturity grounds relative to the JVM ecosystem's broader open-source and cross-platform tooling history; this is a closer call than the other alternatives, and a future ADR could reasonably revisit it for a specific boundary.
- **Java, without Kotlin.** The more conservative JVM choice. Not selected because Kotlin retains full JVM and Spring ecosystem compatibility while reducing the boilerplate and null-safety gaps that make Java comparatively more error-prone for expressing the invariant-heavy domain model already established, consistent with Developer Productivity and Long-Term Maintainability.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Sections 4, 14
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-003: Event-Driven Domain](ADR-003-Event-Driven-Domain.md)
- [ADR-005: Data Ownership](ADR-005-Data-Ownership.md)
- [ADR-009: Domain Isolation](ADR-009-Domain-Isolation.md)
- [ADR-011: Security Principles](ADR-011-Security-Principles.md)
- [ADR-012: Observability Principles](ADR-012-Observability-Principles.md)
- [ADR-013: Testing Philosophy](ADR-013-Testing-Philosophy.md)
- [ADR-014: Deployment Philosophy](ADR-014-Deployment-Philosophy.md)
- [ADR-021: Database Technology Strategy](ADR-021-Database-Technology-Strategy.md)
- [ADR-022: Technology Stack Strategy](ADR-022-Technology-Stack-Strategy.md)
- [DOMAIN_MODEL.md](../DOMAIN_MODEL.md), Sections 4–6, 11–12
- [APPLICATION_ARCHITECTURE.md](../APPLICATION_ARCHITECTURE.md), Section 2
- [MODULE_STRUCTURE.md](../MODULE_STRUCTURE.md), Section 3
- [INTERFACE_CONTRACTS.md](../INTERFACE_CONTRACTS.md), Section 5
- [ENGINEERING_GUIDELINES.md](../ENGINEERING_GUIDELINES.md), Sections 3–4
