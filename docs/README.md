# Documentation

This directory is the documentation hierarchy for the PIOS platform. It is the source of truth for architecture, decisions, and domain knowledge, and it is maintained ahead of implementation as described in [CLAUDE.md](../CLAUDE.md).

## Structure

- [Constitution](#constitution)
- [Architecture](#architecture)
- [ADR](#adr)
- [Domain](#domain)
- [Events](#events)
- [Use Cases](#use-cases)
- [Application Architecture](#application-architecture)
- [API](#api)
- [Data](#data)
- [Database](#database)
- [Module Structure](#module-structure)
- [Interface Contracts](#interface-contracts)
- [Security](#security)
- [Observability](#observability)
- [Deployment](#deployment)
- [Development](#development)
- [Product](#product)

## Constitution

The [Project Constitution](PROJECT_CONSTITUTION.md) is the highest-level specification for PIOS. It defines the project's vision, mission, and governance, and every other document in this hierarchy must reference it and must not contradict it.

## Architecture

The [System Architecture](SYSTEM_ARCHITECTURE.md) describes how PIOS is organized: its major architectural components, their responsibilities, and how they relate to one another. It is derived exclusively from the Constitution, the ADR Foundation, and the Product Foundation; where a required architectural decision does not yet exist, the document marks that section "Architecture Decision Required" rather than inventing one.

## ADR

Architecture Decision Records. Every change to an architectural decision is recorded here before or alongside the change, per [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md) Section 7. All ADRs listed below are Proposed and require explicit approval by the Human Architect, per [ADR-007](adr/ADR-007-AI-Development-Workflow.md), before they become Accepted.

| ADR | Title | Status |
| --- | --- | --- |
| [ADR-001](adr/ADR-001-System-Philosophy.md) | System Philosophy | Proposed |
| [ADR-002](adr/ADR-002-Dispatch-Engine.md) | Dispatch Engine | Proposed |
| [ADR-003](adr/ADR-003-Event-Driven-Domain.md) | Event-Driven Domain | Proposed |
| [ADR-004](adr/ADR-004-API-Style.md) | API Style | Proposed |
| [ADR-005](adr/ADR-005-Data-Ownership.md) | Data Ownership | Proposed |
| [ADR-006](adr/ADR-006-Documentation-Driven-Development.md) | Documentation Driven Development | Proposed |
| [ADR-007](adr/ADR-007-AI-Development-Workflow.md) | AI Development Workflow | Proposed |
| [ADR-008](adr/ADR-008-Repository-Strategy.md) | Repository Strategy | Proposed |
| [ADR-009](adr/ADR-009-Domain-Isolation.md) | Domain Isolation | Proposed |
| [ADR-010](adr/ADR-010-Versioning-Strategy.md) | Versioning Strategy | Proposed |
| [ADR-011](adr/ADR-011-Security-Principles.md) | Security Principles | Proposed |
| [ADR-012](adr/ADR-012-Observability-Principles.md) | Observability Principles | Proposed |
| [ADR-013](adr/ADR-013-Testing-Philosophy.md) | Testing Philosophy | Proposed |
| [ADR-014](adr/ADR-014-Deployment-Philosophy.md) | Deployment Philosophy | Proposed |
| [ADR-015](adr/ADR-015-Evolution-Strategy.md) | Evolution Strategy | Proposed |
| [ADR-016](adr/ADR-016-Architectural-Layers.md) | Architectural Layers | Proposed |
| [ADR-017](adr/ADR-017-Bounded-Context-Strategy.md) | Bounded Context Strategy | Proposed |
| [ADR-018](adr/ADR-018-Core-Architectural-Components.md) | Core Architectural Components | Proposed |
| [ADR-019](adr/ADR-019-Conceptual-Data-Ownership.md) | Conceptual Data Ownership | Proposed |
| [ADR-020](adr/ADR-020-Integration-Philosophy.md) | Integration Philosophy | Proposed |
| [ADR-021](adr/ADR-021-Database-Technology-Strategy.md) | Database Technology Strategy | Proposed |
| [ADR-022](adr/ADR-022-Technology-Stack-Strategy.md) | Technology Stack Strategy | Proposed |
| [ADR-023](adr/ADR-023-Backend-Technology-Decision.md) | Backend Technology Decision | Proposed |
| [ADR-024](adr/ADR-024-Frontend-Technology-Decision.md) | Frontend Technology Decision | Proposed |
| [ADR-025](adr/ADR-025-Database-Technology-Decision.md) | Database Technology Decision | Proposed |
| [ADR-026](adr/ADR-026-Deployment-Strategy.md) | Deployment Strategy | Proposed |

## Domain

The [Domain Model](DOMAIN_MODEL.md) is the technology-independent business domain of PIOS: domains, aggregates, entities, value objects, domain services, events, commands, queries, invariants, lifecycles, and relationships. It is derived from the Constitution, the Product Foundation, the System Architecture, and the ADR Foundation, and is the single source of truth for any future API, database, event, or implementation design.

## Events

The [Event Catalog](EVENT_CATALOG.md) is a business-language catalog of the domain events already established in the Domain Model: what meaningful facts occur, why they matter, which domain owns them, and what they represent. It introduces no event the Domain Model does not already establish, and contains no message broker design, payload, or schema.

## Use Cases

The [Use Case Catalog](USE_CASE_CATALOG.md) describes who interacts with PIOS, what goals they achieve, and what a successful outcome means for each of them, mapped to the domains, aggregates, and events already established. It is a business behavior document, not a UI, API, or database specification.

## Application Architecture

The [Application Architecture](APPLICATION_ARCHITECTURE.md) describes how application responsibilities are organized between actor intentions and domain capabilities: how the goals in the Use Case Catalog are executed conceptually, and how application capabilities coordinate the domain behavior already established. It is the bridge between business intentions and system implementation, not an API, database, frontend, backend, or deployment design.

## API

The [API Specification](API_SPECIFICATION.md) defines what interactions cross PIOS's system boundaries, what capabilities are exposed, and what contracts exist conceptually, at a technology-independent level. It is not a REST, GraphQL, gRPC, or OpenAPI specification; transport and implementation are left to future documents.

## Data

The [Conceptual Data Model](CONCEPTUAL_DATA_MODEL.md) defines what information exists in PIOS, who owns it, and how information concepts relate, from the business domain perspective. It is not a database schema, table design, or storage architecture; it is the business-grounded source a future Logical Data Model or Database Design derives from.

The [Logical Data Model](LOGICAL_DATA_MODEL.md) carries the Conceptual Data Model one level closer to implementation: logical entities, relationships, attribute groups, and constraints, still independent of storage technology. It is not a database schema, SQL model, or physical storage design; it is the bridge to any future Database Design.

The [Persistence Architecture](PERSISTENCE_ARCHITECTURE.md) defines the architectural principles governing persistence across PIOS: responsibility, boundaries, ownership, consistency, and evolution. It is the architectural bridge between the Logical Data Model and any future Physical Database Design; it selects no technology and defines no schema.

## Database

The [Database Design](DATABASE_DESIGN.md) translates the Logical Data Model and Persistence Architecture into technology-neutral physical storage structures: physical shape, relationships, constraints, and indexing principles for each domain. It selects no specific database product or vendor; per-domain technology selection remains a distinct, later decision under ADR-021.

## Module Structure

The [Module Structure](MODULE_STRUCTURE.md) defines software module responsibilities, dependency boundaries, ownership, and architectural isolation, before any implementation. It maps one module to each of the eight ratified capabilities; it is not code, a repository or folder structure, a framework choice, or a microservice deployment design.

## Interface Contracts

The [Interface Contracts](INTERFACE_CONTRACTS.md) define architectural interaction contracts between modules: interaction boundaries, ownership of operations, allowed communication directions, and exchanged conceptual information. It is not an API specification, a programming interface, or a message schema; it consolidates what the Event Catalog, Application Architecture, and Module Structure already establish about which module depends on which.

## Security

Security model, authentication, authorization, and related concerns.

Content pending.

## Observability

Logging, metrics, tracing, and monitoring.

Content pending.

## Deployment

Deployment topology, environments, and release process.

Content pending.

## Development

The [Engineering Guidelines](ENGINEERING_GUIDELINES.md) define development principles, quality standards, change discipline, review practices, testing philosophy, and operational expectations, including explicit rules for AI-assisted development. It selects no programming language, framework, tool, or repository structure.

The [Project Scaffolding Plan](PROJECT_SCAFFOLDING_PLAN.md) defines how the repository and initial project structure will be organized before implementation begins: repository organization, module and frontend placement, documentation placement, development sequence, and the first implementation milestone. It creates no repository file, code, or infrastructure.

The [Initial Implementation Plan](INITIAL_IMPLEMENTATION_PLAN.md) converts the approved architecture into the first controlled implementation roadmap: MVP definition, implementation sequence, module priority, the first vertical slice, MVP exclusions, and validation criteria. It creates no code, user story, ticket, or implementation detail.

The [Repository Initialization Plan](REPOSITORY_INITIALIZATION_PLAN.md) defines the controlled transition from architecture documentation to the first real software repository: initial repository and module scope, deferred modules, first commit definition, and the transition to implementation. It creates no repository, folder, file, or source code.

## Product

Product scope and requirements.

The [Product Foundation](PRODUCT_FOUNDATION.md) defines the product domain: what PIOS is, who participates in it, why it exists, and where its architectural boundaries originate. It derives from the Constitution and the ADR Foundation and precedes any future PRD; the Domain Model and Use Case Catalog now build on it.
