# PIOS Relationship Semantic Contract v1

**Status:** Domain semantic source-of-truth candidate — v1  
**Scope:** Universal PIOS Relationship semantics + coexistence with Taxi relationship models  
**Date:** 2026-09-10  
**Production impact:** None. This document is design-only.

---

## 0. Purpose

This document defines what a **Relationship** means in PIOS and prevents different modules from using the same word for different facts.

The contract is intentionally semantic rather than implementation-specific. It does not require immediate schema changes and does not authorize production migration.

The central rule is:

> **A Person is who someone is. A Relationship is what exists between two people. A Transaction is what they did. History records what actually happened.**

These concepts must not be collapsed into one mutable object.

---

# I. CANONICAL RELATIONSHIP MEANING

## 1. Definition

A **Relationship** is a user-recognized, persistent association between two Persons that the user considers relevant for future interaction.

Relationship answers:

- Who is this person to me?
- Why is this person part of my network?
- Is this relationship still relevant?
- What context should PIOS use when helping me interact with this person?

Relationship is therefore a **product/domain concept**, not merely a database edge.

## 2. Relationship is not a single lifecycle

Relationship must not be modeled as one linear state machine such as:

`NEW → TRUSTED → PRIMARY → ACTIVE → INACTIVE`.

These are different dimensions.

Canonical conceptual structure:

```text
Relationship
├── existence / recognition
├── trust recognition
├── user preference
├── provenance
├── context / labels
└── historical interaction = derived from facts
```

A relationship may exist without being trusted. A person may be trusted without being primary. Primary is a Taxi-specific preference and is not a universal relationship state.

---

# II. HOW A RELATIONSHIP IS CREATED

## 3. Explicit recognition is the default rule

A phone contact, messenger account, social profile, name, photograph, email address, or transaction is **not by itself** a permanent PIOS Relationship.

A Relationship may be established through:

1. explicit user action;
2. accepted connection/invitation flow;
3. another product flow that explicitly asks the user to recognize/save the person as a continuing relationship.

Examples:

- User selects a contact and adds them to My People.
- User accepts an invitation/connection.
- After a completed service, PIOS asks whether the user wants to keep this person for future interaction and the user confirms.

The final example is important: **the transaction supplies evidence and context; the user's decision creates the continuing relationship.**

## 4. Transaction does not automatically create Relationship

The following rule is mandatory:

```text
Transaction ≠ Relationship
```

A single taxi ride, purchase, consultation, delivery, or other completed service creates historical evidence of interaction. It does not automatically create a permanent relationship.

This prevents the network from filling with every person with whom the user has ever had a one-time transaction.

---

# III. CONTACTS, PERSONS AND IDENTITY

## 5. Contact Record is not Person

An external phone/contact record belongs to a source system or to the user's address book.

Canonical flow:

```text
Contact Record
      ↓
Identity Resolution
      ↓
Person / Person Candidate
      ↓
User recognition
      ↓
Relationship
```

PIOS must not assume that two records with the same or similar name are the same Person.

## 6. Source fact, identity linkage and inference are separate

PIOS must distinguish:

- **Source Fact:** what an external source actually says.
- **Identity Linkage:** confidence that the source record belongs to a particular Person.
- **Semantic Inference:** what PIOS believes a person may represent or be capable of doing.

These must not be merged into a single confidence value.

Example:

`Contact name = "Иван Сварщик"`

This may be strong evidence that the source record is labelled that way. It is not proof that Ivan currently works as a welder.

---

# IV. RELATIONSHIP ATTRIBUTES

## 7. Allowed dimensions

A universal Relationship may carry or reference:

- recognition/existence;
- relationship context or category;
- user-authored notes/labels;
- provenance (how the relationship became known);
- trust recognition where applicable;
- temporal validity where applicable;
- links to historical facts;
- user preferences relevant to future interaction.

Attributes must have clear semantics. Do not introduce fields merely because an existing Taxi table happens to contain them.

## 8. Primary is not universal

`Primary` means a user-selected preferred provider/person for a specific vertical or use case.

For Taxi:

```text
Passenger → Primary Driver
```

This is a Taxi application preference, not a generic Relationship state.

Therefore the universal Relationship model must not contain `isPrimary` merely to accommodate Taxi.

The Taxi-specific primary relationship may reference the universal Person/Relationship layer once the integration contract is implemented.

---

# V. TRUST, RATING AND REPUTATION

## 9. These concepts remain separate

PIOS must preserve the distinction:

```text
Relationship = recognized ongoing association
Trust        = verification-backed/user-recognized trust fact
Rating       = atomic evaluation of a concrete interaction
Reputation   = derived aggregate over evidence
```

A rating must not silently mutate the existence or trust status of a Relationship.

A poor rating does not automatically delete a relationship. A good rating does not automatically create one.

Reputation is derived and must not become a hidden paid ranking mechanism.

---

# VI. HISTORY

## 10. History is factual memory, not relationship state

History is derived from actual domain events and transactions.

Examples:

- completed trips;
- completed services;
- payments;
- accepted/declined proposals;
- explicit relationship recognition;
- invitations and accepted connections.

Historical facts should remain immutable wherever practical. Relationship state may change because the user changes their preference or recognition, but historical facts must not be rewritten to make the current relationship look different.

Example:

```text
Trip on 2026-08-01 = historical fact
Relationship saved on 2026-08-02 = separate fact
Primary selected on 2026-08-05 = separate Taxi preference
```

---

# VII. RELATIONSHIP AND NETWORK

## 11. Network is a product projection over relationships

**My Network / My People** is the user-facing set of recognized relationships that PIOS can use to help the user.

It must not be treated as:

- the raw phonebook;
- every person ever encountered;
- every PIOS user;
- another user's private address book.

The persistence/projection strategy for Network remains an implementation decision and is not fixed by this document.

## 12. Second-degree discovery

A second-degree recommendation may use network relationships without exposing another person's private address book.

Conceptually:

```text
Me
 ↓
My Person
 ↓
Relevant Person
```

The system may explain that a suitable person is reachable through a trusted network path, subject to privacy and visibility rules.

It must not reveal private contact lists, hidden relationships, phone numbers, or other protected data merely because a path exists.

---

# VIII. RELATIONSHIP AND CAPABILITY

## 13. Capability is not a Relationship attribute

A person may be in my network without me knowing what they can do.

A capability may also be known about a person who is not yet in my network.

Therefore:

```text
Person ↔ Relationship
Person ↔ Capability
```

are separate concepts.

Capability must carry evidence/provenance/confidence and, where relevant, validity or recency.

Availability is separate from Capability.

```text
Capability = can do
Availability = can do now / at requested time
```

---

# IX. RELATIONSHIP AND OPPORTUNITY

## 14. Relationship affects relevance, not opportunity existence

An Opportunity can exist without a Relationship.

Canonical conceptual resolution:

```text
Need
 + Capability
 + Context
 + Availability
 + Eligibility
 + Evidence
 → Opportunity
```

Relationship may then influence:

- preference;
- trust/context;
- explanation;
- convenience;
- likelihood of successful interaction.

It must not be used as an undocumented universal ranking override.

For Taxi specifically, First Refusal is an explicit application behavior and remains separate from generic opportunity ranking.

---

# X. TAXI COEXISTENCE

## 15. Existing Taxi Connection models are context-specific until proven otherwise

Current Taxi concepts include relationship/connection data in Passenger Experience and related primary-driver structures.

The universal `network-management.Connection` scaffold and Taxi `Connection` must not be renamed, merged, or migrated merely because their names are similar.

Before integration, compare their exact semantics, invariants, ownership, lifecycle, identifiers, and consumers.

Possible outcomes:

### A. Same semantics
Canonicalize onto the universal Relationship model and migrate safely.

### B. Partially overlapping semantics
Retain separate application concepts with an explicit mapping to universal Relationship.

### C. Different semantics
Retain both. Do not force a false abstraction.

No outcome is authorized by this document alone.

## 16. Taxi First Refusal remains separate

First Refusal is a Taxi routing behavior:

```text
Order
 → Relationship Resolution
 → Routing Instruction
 → First Refusal
 → Proposal
 → Assignment
 → Trip
```

It is not:

- a Relationship state;
- a Network ranking algorithm;
- a generic Opportunity rule;
- a replacement for fair dispatch.

Primary relationship affects the next Order according to Taxi rules. Changing a Primary Driver must not rewrite an already-started routing decision.

---

# XI. PRIVACY AND USER CONTROL

## 17. Network belongs to the user's product context

PIOS must make the user the controlling actor for recognition and organization of their network.

The system must provide clear boundaries for:

- contact discovery;
- invitations;
- visibility;
- blocked people;
- duplicate identities;
- identity uncertainty;
- source provenance;
- deletion/de-recognition;
- external integrations.

The product must never imply certainty where the underlying identity or semantic inference is uncertain.

Recommended user-facing confidence language:

- 🟢 Confirmed
- 🟡 Likely
- ⚪ Unknown

These are presentation categories, not a substitute for the underlying provenance model.

---

# XII. IMPLEMENTATION CONSEQUENCES

## 18. Canonical implementation direction

The first universal Core slice should be:

```text
Source Contact
 → Identity / Match Candidate
 → Person
 → User Recognition
 → Relationship
 → My People
```

Only after this foundation should capability enrichment and network intelligence be layered on top.

Do not begin by automatically classifying all contacts into professions.

## 19. AI boundary

AI may:

- interpret natural-language needs;
- summarize relationship context;
- propose identity matches;
- extract candidate capabilities from evidence;
- recommend relevant people;
- explain why a person is relevant;
- orchestrate user-approved actions.

AI must not be the source of truth for:

- identity;
- relationship existence;
- trust facts;
- transaction completion;
- payment facts;
- Taxi assignment;
- authorization.

Deterministic domain facts remain authoritative.

---

# XIII. NON-NEGOTIABLE INVARIANTS

1. `Person ≠ Relationship ≠ Transaction ≠ History`.
2. A phone contact is not automatically a PIOS Relationship.
3. A one-time transaction does not automatically create a permanent Relationship.
4. Relationship is not a single linear state machine.
5. Primary is Taxi-specific, not a universal Relationship state.
6. Capability is separate from Relationship and Availability.
7. Identity linkage is separate from semantic inference.
8. Second-degree discovery must not expose another user's private network data.
9. Relationship must not become a hidden universal ranking mechanism.
10. Taxi First Refusal remains an explicit Taxi routing behavior.
11. AI may interpret and recommend but does not own authoritative domain facts.
12. Existing Taxi Connection models must not be migrated or merged without a semantic comparison.
13. This document creates no production schema, API, deployment, or behavior change.

---

# XIV. OPEN DECISIONS

The following remain intentionally open until implementation analysis:

- exact universal Relationship persistence model;
- canonical identifiers and cross-module mapping;
- relationship categories/tags taxonomy;
- trust evidence model;
- visibility rules for second-degree paths;
- lifecycle for user de-recognition/archive;
- identity-resolution thresholds and confirmation UX;
- mapping strategy between universal Relationship and existing Taxi Connection records.

These decisions should be resolved from actual repository/domain evidence, not by guessing from names.
