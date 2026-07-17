# Documentation

This directory is the documentation hierarchy for the PIOS platform. It is the source of truth for architecture, decisions, and domain knowledge, and it is maintained ahead of implementation as described in [CLAUDE.md](../CLAUDE.md).

## Structure

- [Constitution](#constitution)
- [Architecture](#architecture)
- [ADR](#adr)
- [Domain](#domain)
- [Events](#events)
- [API](#api)
- [Database](#database)
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

## Domain

The [Domain Model](DOMAIN_MODEL.md) is the technology-independent business domain of PIOS: domains, aggregates, entities, value objects, domain services, events, commands, queries, invariants, lifecycles, and relationships. It is derived from the Constitution, the Product Foundation, the System Architecture, and the ADR Foundation, and is the single source of truth for any future API, database, event, or implementation design.

## Events

The [Event Catalog](EVENT_CATALOG.md) is a business-language catalog of the domain events already established in the Domain Model: what meaningful facts occur, why they matter, which domain owns them, and what they represent. It introduces no event the Domain Model does not already establish, and contains no message broker design, payload, or schema.

## API

API contracts and interface specifications.

Content pending.

## Database

Data model, schema, and storage design.

Content pending.

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

Local development setup and contributor workflow.

Content pending.

## Product

Product scope and requirements.

The [Product Foundation](PRODUCT_FOUNDATION.md) defines the product domain: what PIOS is, who participates in it, why it exists, and where its architectural boundaries originate. It derives from the Constitution and the ADR Foundation and precedes any future PRD, domain model, or use-case documentation.
