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
- **Relationship Protection.** Durable, recognized relationships between a driver and the passengers or corporate customers they recurrently serve are a first-class product concern, never owned or captured by the platform, and never a byproduct of any single transaction.
- **No Hidden Algorithmic Decisions.** Replacing a human decision-making process with a software one never justifies less transparency than the process it replaces; an algorithmic decision must remain as explainable as Fair Dispatch and Transparency already require of any other decision.

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
Product Decisions (docs/PRODUCT_DECISION_*.md)
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

A Product Decision (for example, `docs/PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md`) records a Product Owner-authority decision about product meaning, principle, or scope — informing the ADRs and architecture below it, never itself an architectural or implementation decision, and never contradicting this Constitution.

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

---

**Sections 18 onward** were added as a deliberate, explicit governance act consolidating product philosophy already approved across PRODUCT_FOUNDATION.md, DOMAIN_MODEL.md, USE_CASE_CATALOG.md, EVENT_CATALOG.md, PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md, PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md, and ADR-001 through ADR-034 — per this Section's own rule, this is an addition of detail within the scope Sections 1–17 already define, not a change to their meaning. Numbering above is untouched so that every existing cross-reference to a section of this Constitution elsewhere in the documentation set remains valid.

## 18. Fundamental Laws

The Product Principles in Section 3 are this Constitution's fundamental, immutable laws. Consolidated here under the specific names this addition uses elsewhere in the document, each pointing back to where it is actually established rather than restating it:

- **Driver Independence** — Section 3, Driver Ownership; PRODUCT_FOUNDATION.md Section 12 ("the platform cannot assume centralized control or ownership of drivers, vehicles, or fleets").
- **Trust Preservation** — Section 3, Trust.
- **Fair Shared Dispatch** — Section 3, Fair Dispatch.
- **Transparency** — Section 3, Transparency.
- **Relationship Protection** — Section 3 (added above); PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md.
- **No Hidden Algorithmic Decisions** — Section 3 (added above); PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 6.

**Reciprocity is deliberately not included as a law.** ADR-034 Part 4 names "reciprocal balancing" only as one of several unapproved candidate Assignment Policy strategies, explicitly stating "none is approved, previewed, or ranked." Including it here as an immutable law would contradict that ADR directly. It is carried instead as an open question (Section 26).

## 19. Identity

**What PIOS is.** A trustworthy, transparent mechanism connecting independent supply with demand from multiple sources, without concentrating unaccountable control over that connection in a single intermediary (PRODUCT_FOUNDATION.md Section 1).

**What PIOS is not:**

- **Not an opaque aggregator.** PRODUCT_FOUNDATION.md Section 1's own Purpose is stated in direct opposition to this; Section 3's Transparency law reinforces it.
- **Not a hidden dispatcher.** A digital system that reproduces the same opacity as the manual dispatching it replaces would repeat exactly the failure mode PRODUCT_FOUNDATION.md Section 4's Core Problem exists to correct — see Section 18's No Hidden Algorithmic Decisions law.
- **Not an owner of driver relationships.** PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md Part 1 establishes directly that a Personal Client Relationship is not ownership of either party.
- **Not (necessarily) a commission-based marketplace — stated precisely, not overclaimed.** DOMAIN_MODEL.md Section 14 is explicit: "any pricing, commission, or regulatory rule, none of which is established by any approved document." PIOS's identity does not include commission-based aggregation, but this is because the commercial model remains genuinely undecided, not because a commission model has been ratified against. Treated as an open question (Section 26), not a settled identity claim.

## 20. Economic Model Principles

- A driver remains an independent entrepreneur, never an employee of the platform (PRODUCT_FOUNDATION.md Section 12).
- Personal Client relationships are preserved, not absorbed into platform-mediated demand (PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md; PRODUCT_FOUNDATION.md Section 13).
- Shared (Platform Order) demand is executed through the platform's own dispatch mechanism (ADR-002) — this is an execution responsibility, not a claim of ownership over the demand or the relationships fulfilling it.
- The platform does not capture a driver's personal client relationships as its own asset (Section 18, Relationship Protection).

## 21. Relationship Principles

Personal Client Relationship, consolidated from PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md — only what that document ratifies:

- **Not ownership** of either party (Part 1).
- **Not guaranteed orders** — no ratified document connects it to Assignment (Part 6).
- **Independent from any single order** — DOMAIN_MODEL.md Section 13, stated directly.
- **Recognized, recurring** — the ratified characterization (PRODUCT_FOUNDATION.md Section 10); "recognition" itself is real, its precise mechanism is not yet defined.

**Deliberately not stated as principles here, because they are open questions, not ratified facts:** whether the relationship is exclusive, and whether recognition requires mutual confirmation from both parties. PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md Part 8 lists both explicitly as unresolved; stating either here as settled would contradict that document. See Section 26.

## 22. Dispatch Principles

- Dispatch handles the execution of shared assignment, exclusively (ADR-002).
- Dispatch does not own, and has no ratified access to, Personal Client Relationship information (PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md Part 2).
- Dispatch does not invent business rules; the specific assignment algorithm and its criteria remain undefined by every document in this project, deliberately (ADR-002; ADR-034).
- Dispatch's eventual assignment decision is made through Assignment Policy — a replaceable abstraction, not a decision embedded in Dispatch's own infrastructure (ADR-034 Part 3).

## 23. Network Principles

The platform's ecosystem (PRODUCT_FOUNDATION.md Section 6) exists and endures because its participants preserve mutual trust in it (Section 18, Trust Preservation) — this is the Constitution's own Mission (Section 2) applied to the ecosystem as a whole, not a new claim.

**Reciprocity as the basis of help between participants is not stated as a principle here.** No approved document establishes this; ADR-034 treats reciprocal balancing only as one unapproved candidate among several for a future Assignment Policy. Carried as an open question (Section 26).

## 24. Anti-Principles

PIOS must never become: an opaque aggregator; a platform that replaces manual dispatching with an equally hidden algorithmic one; a system optimized only for platform benefit at drivers' expense. Each is established directly in Section 19 above. The fuller reasoning and evidence for each is recorded in PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md Section 6, referenced here rather than restated, consistent with the Single Source of Truth principle (Section 4).

## 25. Evolution Rules

A new feature, product decision, or architectural decision must not violate the Fundamental Laws (Section 18) or contradict this Constitution, consistent with Section 17's existing rule and the Change Management process in Section 12. A feature that would require violating a Fundamental Law is not implemented as proposed; either the feature is reshaped to comply, or a deliberate constitutional amendment (Section 17) is sought first — a law is never quietly worked around at a lower layer of the hierarchy (Section 5).

## 26. Open Product Questions

Consolidated from PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md and PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md; listed, not resolved:

- The concrete fairness metric or model for Dispatch's eventual assignment decision.
- Whether, and how, reciprocity factors into help between participants, or into any future Assignment Policy — currently only one of several unapproved candidate strategies (ADR-034).
- The assignment algorithm itself — deliberately undefined by every document in this project (ADR-002; ADR-034).
- The platform's commercial/commission model (Section 19).
- Personal Client Relationship priority rules relative to shared dispatch, and whether a personal-client-directed order is even routed through Dispatch's decision at all.
- Whether a Personal Client Relationship can be exclusive, and whether it requires mutual confirmation from both parties (Section 21).
- Any driver incentive, contribution, or reward model — currently unsupported by any evidence, not merely undecided in detail.

Resolution of any of these requires a Product Owner decision (Section 9) recorded as a Product Decision (Section 5) before any lower-layer document may act on it.
