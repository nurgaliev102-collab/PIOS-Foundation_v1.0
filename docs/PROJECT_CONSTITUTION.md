# PIOS Project Constitution

Status: Foundational. This document is the highest-level specification governing the PIOS platform. It is the Single Source of Truth for the project's vision, principles, and governance. Every other document produced for this project must reference this Constitution and must not contradict it.

This document contains no implementation details, technology choices, architecture designs, or business rules. Those belong to documents that sit below this one in the hierarchy defined in Section 5.

---

## 1. Vision

PIOS exists to establish a next generation taxi platform built on disciplined engineering practice, transparent decision-making, and durable documentation. The platform is conceived as a long-lived system whose structure, decisions, and reasoning remain legible to every future contributor, human or artificial, long after the individuals who made the original decisions have moved on.

## 2. Mission

The mission of PIOS is to build a platform that earns and keeps the trust of the people who depend on it, by ensuring that every capability the platform offers is grounded in a documented decision, every decision is traceable, and every change is deliberate. PIOS pursues sustainable, well-governed growth over speed achieved through shortcuts.

## 3. Product Principles

These principles describe the philosophy that product decisions must honor. They are not specifications of features, workflows, or business rules; they are the standard against which future product decisions are evaluated.

- **Fair Dispatch.** Mechanisms that connect riders and drivers must be designed and evaluated for fairness, not merely for efficiency.
- **Driver Ownership.** Drivers are treated as stakeholders of the platform, not as undifferentiated supply. Their interests are a first-class product concern.
- **Transparency.** The platform's behavior toward the people who use it must be explainable. Opacity is not an acceptable trade-off for convenience.
- **Extensibility.** The product is built to accommodate future capability without requiring its foundations to be discarded.
- **Platform Thinking.** PIOS is designed as a platform that enables capabilities, not as a fixed set of features.
- **Simplicity.** The simplest solution that satisfies a genuine need is preferred over a more elaborate one.
- **Reliability.** The platform must behave predictably and consistently for the people who depend on it.
- **Trust.** Every product decision is evaluated, in part, by whether it strengthens or weakens the trust riders, drivers, and operators place in the platform.

## 4. Engineering Principles

- **Documentation First.** No capability is implemented before it is documented. Documentation describes intent before code expresses it.
- **Architecture First.** Structural decisions precede implementation. Code is not the place where architecture is decided.
- **Code Follows Architecture.** Implementation is an expression of approved architecture, not a substitute for it.
- **No Shortcuts.** Expedience is not a justification for bypassing documentation, architecture, or governance.
- **No Hidden Assumptions.** Every assumption material to a decision must be written down. Undocumented assumptions are treated as defects.
- **Single Source of Truth.** For any given fact, decision, or rule, exactly one document is authoritative. Duplication of authority is avoided; references are used instead.

## 5. Documentation Hierarchy

Documentation within PIOS is organized in a strict precedence order. A document may extend or clarify anything above it in this hierarchy; it may not contradict it.

```
Constitution
    |
    v
ADR (Architecture Decision Records)
    |
    v
Architecture
    |
    v
Domain
    |
    v
API
    |
    v
Database
    |
    v
Implementation
```

This Constitution sits above all other documents. When a conflict is found between two documents, the document higher in this hierarchy prevails, and the lower document is corrected.

## 6. Architecture Governance

Architecture is owned by the role designated as Chief Software Architect for the project, or by whoever holds equivalent authority at a given time. Ownership of architecture means accountability for its consistency with this Constitution and with the ADR record, not exclusive authorship.

Architecture may be proposed by any contributor, human or artificial. Architecture may be modified only through the process defined in Section 7. No individual contribution, regardless of source, may alter architecture outside that process.

An ADR is required whenever a change affects system structure, component boundaries, the relationships between components, or a previously recorded architectural decision. Changes that do not affect these are not architectural changes and are governed by ordinary change management under Section 12.

## 7. ADR Policy

Every architectural decision is recorded as an Architecture Decision Record before or alongside the change it authorizes. An architectural change made without a corresponding ADR is considered undocumented and is not valid, regardless of whether it has been implemented.

ADRs are never deleted. A superseded ADR is marked as superseded and remains in the record, with a reference to the ADR that replaces it. The history of architectural reasoning is treated as permanent.

## 8. Repository Rules

The repository is organized so that its structure reflects the documentation hierarchy defined in Section 5. Each folder has a single, stated purpose, and content is placed according to that purpose rather than convenience.

Documentation is written in Markdown, uses consistent heading structure, and cross-references related documents explicitly rather than restating their content. Naming of files and folders is descriptive, consistent, and stable; renaming an existing document is treated as a change subject to the same scrutiny as its content.

## 9. AI Development Rules

Any AI assistant operating in this repository, including Claude Code and other AI tooling used on this project, is bound by this Constitution and by the execution rules defined in `.ai/EXECUTION_PROTOCOL.md`. An AI assistant's responsibilities are to:

- Follow the documentation hierarchy and never implement ahead of it.
- Never invent business rules, architecture, or product decisions that have not been documented or explicitly instructed.
- Never modify architecture without a corresponding ADR.
- Never delete documentation.
- Report completed work accurately and completely.
- Stop and request clarification when an instruction is ambiguous or would require a decision outside its authority.

Human developers are responsible for reviewing and approving changes proposed by AI assistants, for making decisions that require judgment beyond documented rules, and for ensuring that work merged into the repository complies with this Constitution.

The Product Owner is responsible for defining product intent, prioritizing milestones, and approving product-level decisions, without prescribing implementation or architecture.

## 10. Development Lifecycle

Work in PIOS proceeds through the following sequence. A stage is not entered until the stage before it is complete for the scope in question.

```
Idea
  |
  v
Constitution
  |
  v
ADR
  |
  v
Architecture
  |
  v
Domain
  |
  v
Implementation
  |
  v
Testing
  |
  v
Release
```

## 11. Definition of Done

**Project level.** The project is done with a given scope when every capability within that scope is documented, architecturally grounded, implemented in accordance with that architecture, tested, and released, with no undocumented decisions outstanding.

**Document level.** A document is done when it fully covers its stated purpose, does not contradict any document above it in the hierarchy, contains no placeholder or fabricated content, and its links and formatting have been verified.

**Milestone level.** A milestone is done when every deliverable defined for it exists, has been verified against this Constitution and applicable ADRs, and has been reported in accordance with `.ai/EXECUTION_PROTOCOL.md`.

## 12. Change Management

A change to any document or to the system it describes is approved by the owner of that document's layer in the hierarchy defined in Section 5, or by their designate. Changes that affect a layer above the one being edited require approval at that higher layer as well.

Every change is recorded through the repository's version control history and, where it affects architecture, through an ADR. No change is considered approved solely because it has been implemented; approval is a documented act, not an inference from code.

## 13. Risk Management

**Architectural risk** is managed by requiring an ADR for every structural decision, so that the reasoning behind the current architecture, and the alternatives considered, remains available for review.

**Documentation risk** is managed by never deleting documentation and by requiring that every document be checked for internal consistency and consistency with this Constitution before it is considered complete.

**Product risk** is managed by requiring that business rules and product behavior be explicitly documented before implementation, so that unintended or unexamined behavior does not reach the platform's users.

## 14. Quality Principles

- **Maintainability.** The system and its documentation must remain comprehensible and modifiable as they grow.
- **Consistency.** The same concept is named and described the same way everywhere it appears.
- **Scalability.** Decisions are made with the platform's long-term growth in mind, not only its immediate scope.
- **Readability.** Documentation and code are written to be understood by someone encountering them for the first time.
- **Traceability.** Every decision can be traced to the document, ADR, or instruction that authorized it.

## 15. Non Goals

This Constitution does not define, and PIOS at this stage does not attempt to solve:

- The specific technology stack, programming languages, frameworks, or infrastructure the platform will use.
- The system's architecture, service boundaries, or component design.
- The domain model, data model, or database design.
- API contracts or interface specifications.
- Business rules such as pricing, dispatch algorithms, or operational policy.
- Deployment topology, environments, or release mechanics.
- Any code, pseudo-code, or executable artifact.

These are the responsibility of documents lower in the hierarchy defined in Section 5, once the corresponding milestone is reached.

## 16. Success Criteria

The Constitution and the foundation it governs are successful to the extent that:

- Every subsequent document produced for PIOS can be traced back to this Constitution without contradiction.
- Architectural decisions are consistently recorded as ADRs and remain available for review.
- Contributors, human or artificial, can determine the correct next step from the documentation hierarchy alone, without relying on undocumented context.
- No undocumented business rule, architectural decision, or technology assumption is found embedded in the system.
- The platform, as it is built, remains explainable to a new contributor using only the documentation record.

## 17. Constitutional Rules

This Constitution overrides every other document produced for this project. In the event of a conflict, this Constitution prevails and the conflicting document is corrected.

Future documents may extend this Constitution by adding detail within the scope it defines. Future documents may clarify this Constitution by resolving ambiguity without changing its meaning. Future documents may not contradict this Constitution.

Any amendment to this Constitution itself is a governance act, not a documentation act, and requires explicit, deliberate approval at the highest level of project authority. It is not amended implicitly through the accumulation of lower-level documents.
