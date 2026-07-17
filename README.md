# PIOS

Next Generation Taxi Platform

## Purpose

PIOS is a platform under active foundation setup. This repository establishes the structural, documentation, and process foundation the platform will be built on. It does not yet contain product functionality, business logic, or application code.

## Repository Structure

```
PIOS-Foundation_v1.0/
├── CLAUDE.md                        Entry point and operating rules for Claude Code sessions
├── README.md                        This file
├── LICENSE                          Repository license
├── .ai/
│   └── EXECUTION_PROTOCOL.md        Execution rules, milestone workflow, and reporting format
├── docs/
│   └── README.md                    Documentation hierarchy and index
└── .github/
    └── PULL_REQUEST_TEMPLATE.md     Pull request template
```

## Technology

Technology stack: to be determined and documented in `docs/Architecture` before implementation begins.

## Documentation

All documentation lives under [docs/](docs/README.md), organized into Architecture, ADR, Domain, API, Database, Security, Observability, Deployment, Development, and Product sections. Documentation precedes implementation, as described in [CLAUDE.md](CLAUDE.md).

## Development Workflow

1. Work proceeds in defined milestones, each with an explicit scope. See [.ai/EXECUTION_PROTOCOL.md](.ai/EXECUTION_PROTOCOL.md).
2. Documentation and architecture are established before any implementation.
3. Architectural changes require an Architecture Decision Record in `docs/ADR/`.
4. Changes are submitted using the template in [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md).

## Current Status

Foundation. The repository structure, entry-point documentation, and execution protocol are in place. No architecture, domain, or implementation work has started.
