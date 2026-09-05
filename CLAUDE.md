# CLAUDE.md

This file is the entry point for every Claude Code session working in the PIOS repository. It defines the mission, philosophy, and operating rules that govern all work in this codebase. Read this file first, before making any change.

## Project Mission

PIOS is the foundation of a next generation taxi platform. The current phase of this repository is Foundation v1.0: establishing the structure, documentation discipline, and execution rules that all future work will build on. No product functionality, business logic, or application code exists yet, and none should be added until the corresponding documentation and architecture are in place.

## Repository Philosophy

### Documentation First Development

Documentation precedes implementation. Every capability, module, or interface must be described in `docs/` before it is built. Code that has no corresponding documentation is considered incomplete, regardless of whether it runs.

### Architecture First

Architecture decisions precede code. The structure of the system, its boundaries, and its major components must be defined and recorded before implementation begins. No component is implemented ahead of its architectural definition.

### No Implementation Before Documentation

Do not write backend code, frontend code, database schemas, or business logic until the relevant documentation exists and has been reviewed. If a task requires implementation but the supporting documentation is missing, stop and request it instead of inferring it.

### Milestone-Based Execution

Work proceeds in defined milestones. Each milestone has an explicit scope. Do not begin work belonging to a later milestone while an earlier one is in progress, and do not expand a milestone's scope without explicit instruction.

### Never Change Architecture Without an ADR

Any change to an architectural decision must be recorded as an Architecture Decision Record in `docs/ADR/` before or alongside the change. Architecture is not modified silently or implicitly through code changes.

### Never Delete Documentation

Documentation is not removed. If documentation becomes outdated, it is revised or superseded and the history of the change is preserved, not deleted.

### Never Invent Business Rules

Do not assume, infer, or fabricate business logic, pricing rules, workflows, or product behavior that has not been explicitly specified. If a business rule is required and undefined, stop and ask.

### Always Report Completed Work

At the end of every task, report exactly what was created, modified, and verified. Do not report work as complete unless it has been done and checked.

## Scope of This Session

This file governs all sessions. Individual milestones may add further constraints in `.ai/EXECUTION_PROTOCOL.md`, which should be read alongside this file before starting any task.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
