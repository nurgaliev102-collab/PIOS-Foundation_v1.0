# PIOS Backend

This directory is the backend area defined in [../docs/PROJECT_SCAFFOLDING_PLAN.md](../docs/PROJECT_SCAFFOLDING_PLAN.md) Section 3. It does not duplicate the documentation that governs it; every module here links back to its authoritative source instead.

## Modules

Four module skeletons exist at this milestone, matching the Initial Module Scope in [../docs/REPOSITORY_INITIALIZATION_PLAN.md](../docs/REPOSITORY_INITIALIZATION_PLAN.md) Section 4:

| Module | Responsibility | Governing document |
| --- | --- | --- |
| `driver-management` | A driver's standing, availability, and participation. | [MODULE_STRUCTURE.md](../docs/MODULE_STRUCTURE.md#driver-management-module) |
| `passenger-experience` | Passenger and corporate customer representation (minimal originating-context scope at this milestone). | [MODULE_STRUCTURE.md](../docs/MODULE_STRUCTURE.md#passenger-experience-module) |
| `order-management` | An order's lifecycle from submission through completion or cancellation. | [MODULE_STRUCTURE.md](../docs/MODULE_STRUCTURE.md#order-management-module) |
| `dispatch` | The assignment decision, exclusively. | [MODULE_STRUCTURE.md](../docs/MODULE_STRUCTURE.md#dispatch-module) |

Administration, Analytics, Notifications, and Payments are deferred; see [REPOSITORY_INITIALIZATION_PLAN.md](../docs/REPOSITORY_INITIALIZATION_PLAN.md) Section 5 for why.

## What exists at this milestone

Each module is an empty structural boundary: a buildable, independently startable Spring Boot application with no business logic, no domain entity, no database model, and no API endpoint. Its purpose is to prove the module can be built and run in isolation, consistent with [ADR-023](../docs/ADR/ADR-023-Backend-Technology-Decision.md) and [ADR-026](../docs/ADR/ADR-026-Deployment-Strategy.md), before any business capability is implemented. See [REPOSITORY_INITIALIZATION_PLAN.md](../docs/REPOSITORY_INITIALIZATION_PLAN.md) Section 8 (First Commit Definition).

## Module isolation

No module depends on another module's code. There is no shared library module. Cross-module interaction, once implemented, occurs only through the contracts already defined in [INTERFACE_CONTRACTS.md](../docs/INTERFACE_CONTRACTS.md) — never through a shared dependency declared in this repository's build files. See [MODULE_STRUCTURE.md](../docs/MODULE_STRUCTURE.md) Section 4 (Shared Capabilities) for why no common module exists here.

## Building

This project uses the Gradle Wrapper; a JDK 21 is the only prerequisite for compiling and testing:

```
./gradlew build
```

## Running

Compiling (`./gradlew build`) needs only a JDK. **Actually starting** `driver-management`, `order-management`, or `dispatch` additionally needs a running local PostgreSQL (one database per module: `pios_driver_management`, `pios_order_management`, `pios_dispatch` — Flyway creates the schema inside each automatically on startup, but not the database itself) and a running local RabbitMQ (default port, `guest`/`guest`) — each module's own `src/main/resources/application.yml` names the exact connection details it expects. `passenger-experience` needs neither (no datasource, no AMQP dependency). See [../README.md](../README.md)'s own "Running Locally" section for the full setup sequence, including the three `CREATE DATABASE` statements and the one manual Driver-seeding step the current REST API has no other way to satisfy.

Each module can be run independently, consistent with its independent deployability (ADR-026):

```
./gradlew :driver-management:bootRun
```
