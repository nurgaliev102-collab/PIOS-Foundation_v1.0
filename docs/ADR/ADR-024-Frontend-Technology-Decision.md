# ADR-024: Frontend Technology Decision

## Status

Proposed

## Context

ADR-023 selected Kotlin on the JVM with Spring Boot for PIOS's backend services. PRODUCT_FOUNDATION.md and USE_CASE_CATALOG.md establish several distinct actor-facing surfaces that now need a frontend technology direction: Passenger (UC-001, UC-002), Driver (UC-003 through UC-006), Corporate Customer (UC-009), and Platform Administrator (UC-010), alongside the operational surface implied by platform administration and any fleet-level operational participation (PRODUCT_FOUNDATION.md Section 7). This decision must follow ADR-022's principles, remain compatible with the module boundaries and interaction contracts already established (MODULE_STRUCTURE.md, INTERFACE_CONTRACTS.md, API_SPECIFICATION.md), and stand independently of ADR-023's backend language choice, since a frontend runs in a fundamentally different execution environment — a browser or a mobile device — than a backend service does.

## Decision

PIOS's frontend surfaces are built in **TypeScript**, using **React** as the main framework for web-based surfaces and **React Native** for mobile surfaces, in a component-based, unidirectional-data-flow architectural style. Every frontend application communicates with backend modules only through the interaction and API contracts already established (API_SPECIFICATION.md, INTERFACE_CONTRACTS.md), never directly against a module's internals.

**Why TypeScript.** A browser is the runtime for every web-based surface PIOS needs, and JavaScript — or a typed superset of it — is the only language that runs there natively. TypeScript adds the same compile-time discipline ADR-023 valued in Kotlin for the backend — catching the kind of "hidden assumption" error PROJECT_CONSTITUTION.md Section 4 warns against before it reaches an actor — to a runtime the web already requires.

**Why React.** React is the most widely adopted component-based UI framework, with a large, long-established community, extensive documentation, and among the widest representation of any frontend framework in AI coding tool training data and tooling — directly serving the AI-Assisted Development Compatibility principle in ADR-022 and the AI-Assisted Development Rules in ENGINEERING_GUIDELINES.md Section 12.

**Why React Native for mobile.** PRODUCT_FOUNDATION.md's ecosystem strongly implies the Passenger and Driver surfaces are mobile-first, since drivers and passengers are themselves physically mobile participants. React Native lets those two applications share the same language and component model as the web surfaces, rather than requiring two entirely separate native codebases and skill sets for no requirement any approved document establishes.

**Why a component-based, unidirectional-data-flow style.** This keeps each frontend application's own internal structure predictable and testable, consistent with the Readability, Consistency, and Low Complexity principles in ENGINEERING_GUIDELINES.md Section 3, without this document prescribing a specific state-management library, which remains a later, implementation-level choice.

## Evaluation Against ADR-022

- **Architecture Compatibility.** React's component boundaries compose naturally with the capability contracts already established (API_SPECIFICATION.md Section 5); a frontend application is simply another external consumer of a module's contract (API_SPECIFICATION.md Section 4), and TypeScript's typing can mirror those conceptual contracts without this document defining any schema itself.
- **Long-Term Maintainability.** React and TypeScript are both under active, well-governed long-term development with strong backward-compatibility practices; TypeScript's static typing reduces the risk of undetected breakage as the frontend evolves alongside the backend contracts in API_SPECIFICATION.md.
- **Developer Productivity.** Sharing one language and one component model across every frontend surface reduces context-switching for engineers who work across more than one of them, consistent with ENGINEERING_GUIDELINES.md Section 4.
- **Operational Simplicity.** Web and mobile applications built this way are near-static artifacts at deployment time — bundled assets or an app-store package — operationally simpler than a stateful backend service; this document selects no specific deployment or hosting mechanism, which remains a separate, future decision.
- **Community Maturity.** React is one of the most widely used and longest-supported UI frameworks in active production use, with an extensive ecosystem and documentation.
- **Security.** TypeScript's typing and React's escape-by-default rendering model reduce a common class of frontend vulnerability — unintended code or markup injection — supporting Security by Design (ADR-011) without this document selecting a specific security mechanism.
- **Performance Suitability.** React's rendering model suits the kind of frequently updating, event-driven interfaces PIOS's use cases imply — an order's status changing, an assignment becoming available — consistent with the events already catalogued in EVENT_CATALOG.md that a frontend would need to reflect.
- **Team Capability.** React and TypeScript are among the most widely taught and widely hired-for frontend technologies, giving a large available talent pool without assuming a specialized skill set.
- **AI-Assisted Development Compatibility.** TypeScript and React are extensively represented in AI coding tool training data and tooling support, satisfying ADR-022's criterion directly.

## Architecture Alignment

- **Module Structure.** A frontend application is not itself a module — MODULE_STRUCTURE.md Section 3 enumerates only backend capabilities — it is an external consumer that reaches a module only through that module's own interaction contract, never through direct access to a module's internals.
- **Interface Contracts.** Every frontend surface interacts with backend modules only through the boundaries already named in INTERFACE_CONTRACTS.md Section 9: Passenger and Driver applications reach Order Management, Dispatch, and Driver Management; the Administrator interface reaches Administration; the Corporate interface reaches Order Management through Passenger Experience's originating context.
- **API Specification.** The frontend consumes exactly the capability contracts already defined in API_SPECIFICATION.md Section 5, at whatever protocol a future, separate decision selects to carry them; API_SPECIFICATION.md deliberately defers transport, and this ADR does not resolve that either.
- **Engineering Guidelines.** React and TypeScript's component and type conventions directly support Readability, Consistency, and Clear Responsibility (ENGINEERING_GUIDELINES.md Section 3), and their maturity supports the Testing Philosophy in Section 6 without this document naming a testing tool.

## Multi-Experience Consideration

- **Passenger application.** React Native (TypeScript), mobile-first, consuming Order Management and Passenger Experience per UC-001 and UC-002.
- **Driver application.** React Native (TypeScript), mobile-first, consuming Driver Management and Dispatch per UC-003 through UC-006.
- **Dispatcher / Operations interface.** No independent business capability is established for this actor beyond what Administration already provides (PRODUCT_FOUNDATION.md Section 7; USE_CASE_CATALOG.md, Areas Not Catalogued). Where an operational surface is needed, it is realized as part of the Administrator interface below, using the same React (TypeScript) web technology, rather than as a separately justified application.
- **Corporate interface.** React (TypeScript) web application, consuming Order Management through Passenger Experience's originating context per UC-009.
- **Administrator interface.** React (TypeScript) web application, consuming Administration per UC-010.

## Consequences

**Positive.** One shared language and component model across every frontend surface reduces Team Capability and Developer Productivity risk; the ecosystem's maturity supports the Security and AI-Assisted Development Compatibility criteria directly.

**Negative.** React Native shares language and component model with web React, but is not identical to it; some platform-specific work remains necessary per mobile surface, so shared codebase is a productivity benefit, not a guarantee of one unmodified codebase across web and mobile.

**Long-term implications.** Every future frontend surface is expected to use TypeScript, with React for web and React Native for mobile, unless a future ADR supersedes this one for a specific surface.

## Alternatives Considered

- **Flutter (Dart).** A legitimate cross-platform alternative with a single codebase spanning mobile and, to a lesser extent, web. Not selected because it introduces a second language beyond what any web-only surface already requires (JavaScript or TypeScript is unavoidable for a browser-based Administrator or Corporate interface), reducing rather than improving Developer Productivity and Team Capability once both mobile and web surfaces are counted together; its AI-assisted development compatibility, while credible, is less extensively represented than TypeScript and React's.
- **Native iOS and Android development, per platform.** Offers the strongest possible platform-native experience, but requires two entirely separate codebases and skill sets for the Passenger and Driver applications alone, before any web surface is even considered. Rejected primarily on Developer Productivity and Team Capability grounds, since no approved document establishes a requirement for deep native-platform integration that would justify the added cost.
- **Kotlin Multiplatform with Compose Multiplatform.** An appealing option given ADR-023's backend language choice, potentially unifying backend and frontend under one language. Not selected for the frontend because its web and cross-platform UI tooling is materially less mature than React's, and its representation in AI-assisted development tooling and training data is currently far smaller, weighing against both Community Maturity and AI-Assisted Development Compatibility. It remains a reasonable option to revisit in a future ADR if the ecosystem matures significantly.
- **Vue or Angular, in place of React.** Both are mature, credible alternatives within the same TypeScript-based web ecosystem. Not selected primarily because React's ecosystem and community are larger by most available measures, and React Native's maturity for the mobile surfaces this decision must also cover has no equally mature direct equivalent tied to Vue or Angular. This is a closer call than the other alternatives.

## References

- [PROJECT_CONSTITUTION.md](../PROJECT_CONSTITUTION.md), Section 4
- [ADR-001: System Philosophy](ADR-001-System-Philosophy.md)
- [ADR-011: Security Principles](ADR-011-Security-Principles.md)
- [ADR-022: Technology Stack Strategy](ADR-022-Technology-Stack-Strategy.md)
- [ADR-023: Backend Technology Decision](ADR-023-Backend-Technology-Decision.md)
- PRODUCT_FOUNDATION.md, Sections 6–7
- USE_CASE_CATALOG.md, UC-001 through UC-010, Areas Not Catalogued
- API_SPECIFICATION.md, Sections 4–5
- INTERFACE_CONTRACTS.md, Section 9
- MODULE_STRUCTURE.md, Section 3
- ENGINEERING_GUIDELINES.md, Sections 3, 4, 6, 12
