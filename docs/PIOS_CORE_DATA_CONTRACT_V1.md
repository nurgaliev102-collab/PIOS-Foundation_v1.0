# PIOS Core Data Contract v1

**Status:** Design source-of-truth candidate — v1  
**Scope:** Universal PIOS Core data semantics; Taxi remains the first vertical  
**Date:** 2026-09-10  
**Production impact:** None. Design-only. This document does not authorize schema migration, production deployment, or changes to existing Taxi behavior.

---

## 1. Purpose

This document converts the PIOS Product Contract and Relationship Semantic Contract into a canonical data contract that implementation can consume without prematurely fixing a particular database schema.

The contract answers four questions:

1. What facts does PIOS own?
2. What facts are derived?
3. What is inferred, and how is uncertainty represented?
4. Which module is authoritative for each concept?

The contract is intentionally semantic rather than SQL-specific. Table names, ORM classes, indexes, transport formats, and migration mechanics are implementation decisions that follow from this contract and must not silently redefine it.

---

## 2. Canonical authority model

PIOS distinguishes four classes of information:

### 2.1 Source Fact

A fact obtained from an authoritative source or an explicit user action.

Examples:
- a phone number exists in a user's address book;
- a person accepted an invitation;
- a user explicitly recognized someone as part of their network;
- a Taxi Order was created;
- a Trip was completed.

Source facts must be attributable to a source and must not be rewritten into stronger claims without evidence.

### 2.2 Derived Fact

A deterministic fact calculated from authoritative source facts.

Examples:
- number of completed trips between two people;
- whether a person has ever provided a service to the user;
- whether a relationship is currently primary for Taxi;
- aggregate reputation from recorded ratings.

Derived facts may be recomputed. They are not independent authority unless explicitly promoted by a future contract.

### 2.3 Semantic Inference

A conclusion produced from one or more facts using an inference rule or model.

Examples:
- likely occupation = welder;
- likely identity match between two contact records;
- likely capability = intercity transport.

Inference must always preserve provenance and confidence. An inference cannot silently become a source fact.

### 2.4 Presentation / Product Projection

A user-facing representation assembled from facts, derived data, and permitted inference.

Examples:
- My People;
- person card;
- search ranking;
- “you may know this person”;
- “someone in your network may be able to help.”

A projection is not a new authority layer.

---

## 3. Global identity and ownership rules

### 3.1 Person

`Person` is the canonical PIOS reference to a human participant.

A Person is not the same thing as:
- a phone contact;
- a login account;
- a profile;
- a chat identity;
- an organization;
- a transaction participant record.

A Person may have multiple external identifiers and multiple application roles.

### 3.2 Ownership

A PIOS user owns their private recognition of a relationship, not the global identity of another person.

Therefore:
- one user may recognize another Person as part of their network;
- another user may not recognize that same Person;
- recognition is scoped to the owner of the relationship;
- a user's private contact source must never become globally visible merely because PIOS processed it.

### 3.3 Authority

The authoritative owner of a fact is determined by domain, not by whichever module happens to display it.

Existing Taxi domain facts remain authoritative in their existing bounded contexts until an explicit migration contract says otherwise.

---

## 4. Contact Record

### Semantic purpose

`ContactRecord` represents a source-side record imported or read from a permitted contact source. It is evidence about a person, not automatically a Person and not automatically a PIOS relationship.

### Required semantic fields

- `contactRecordId` — stable identifier within PIOS.
- `ownerPersonId` — the PIOS user whose source contains the record.
- `sourceType` — e.g. PHONE_CONTACTS, EMAIL, or another explicitly supported source.
- `sourceRecordId` — source-local identifier when available.
- `observedAt` — when the source record was observed.
- `sourceIdentifiers` — phone/email/profile identifiers available from the source.

### Optional context

- display name;
- photo reference;
- organization;
- email addresses;
- notes;
- social/messenger identifiers;
- source-specific metadata.

Raw source data must remain attributable to its source. Normalization must not erase the distinction between raw evidence and normalized PIOS meaning.

### Rules

- A ContactRecord does not create a Relationship by itself.
- A ContactRecord does not prove identity with another Person by name alone.
- A ContactRecord may produce a MatchCandidate.
- Contact records are private to their owner unless a separate permission rule allows disclosure.

---

## 5. Person Match Candidate

### Semantic purpose

`PersonMatchCandidate` represents a proposed linkage between a ContactRecord and an existing PIOS Person.

### Required semantic fields

- `candidateId`;
- `contactRecordId`;
- `personId` — nullable until a candidate is resolved;
- `confidence`;
- `provenance`;
- `status` — unresolved / suggested / confirmed / rejected / superseded;
- `evaluatedAt`.

### Confidence

Confidence describes how strongly the evidence supports the identity linkage. It is not a measure of relationship strength or trust.

Recommended presentation classes:
- **CONFIRMED** — strong authoritative or explicit evidence;
- **LIKELY** — multiple supporting signals, but not sufficient for certainty;
- **UNKNOWN** — insufficient evidence.

The system must not present an uncertain identity match as established fact.

### Identity evidence hierarchy

Evidence should generally be weighted from strongest to weakest:

1. explicit user confirmation;
2. verified platform identifier;
3. exact stable external identifier where legitimately available;
4. multiple corroborating attributes;
5. weak similarity such as name-only matching.

Name-only matching is insufficient for automatic identity confirmation.

---

## 6. Person

### Semantic purpose

`Person` is the stable canonical participant reference used across PIOS.

### Required semantic fields

- `personId`;
- lifecycle status;
- creation timestamp;
- provenance of creation;
- identity references where applicable.

### Important rules

- Person identity is not owned by a single Relationship.
- A Person may have multiple Profiles.
- A Person may hold multiple Roles.
- A Person may have multiple Capabilities.
- A Person may participate in many Transactions/Services.
- A Person may be recognized by one user and not recognized by another.

PIOS must avoid creating duplicate Persons when reliable identity evidence establishes that two records refer to the same participant, but identity resolution must never rely on unsafe name coincidence.

---

## 7. Profile

### Semantic purpose

`Profile` is a representation of a Person for a particular context, audience, or role.

Profile is presentation/context, not identity itself.

### Possible semantic fields

- `profileId`;
- `personId`;
- display name;
- photo reference;
- role labels;
- organization;
- public contact channels;
- visibility policy;
- locale and presentation metadata.

A Profile may change without changing the underlying Person.

---

## 8. Relationship

### Semantic purpose

`Relationship` records that a specific PIOS user has explicitly recognized a persistent association with another Person for future interaction.

Relationship is user-scoped.

### Required semantic fields

- `relationshipId`;
- `ownerPersonId`;
- `subjectPersonId`;
- recognition state;
- created/recognized timestamp;
- provenance;
- lifecycle status.

### Optional semantic dimensions

- relationship context/category;
- trust recognition;
- user notes;
- tags;
- source of introduction;
- application-specific preferences through bounded extensions.

### Rules

- Relationship is not a single universal state machine.
- Relationship existence and trust are separate dimensions.
- Relationship and Transaction are separate concepts.
- Transaction history may provide evidence relevant to a relationship but does not automatically create one.
- Primary is not a generic Relationship field; Taxi may maintain its own primary preference over a recognized relationship.
- Relationship must not contain mutable counters that duplicate authoritative transaction history unless explicitly defined as a cache/projection.

### Recognition

Default recognition mechanism is explicit user action or explicit acceptance of an invitation/connection flow.

Passive observation of a phone contact, message, or transaction is not sufficient by itself to create a persistent relationship.

### De-recognition

Removing recognition should stop the relationship from appearing as an active member of the user's Network projection. Historical facts must not be falsified or deleted merely because recognition ended.

Future contracts must distinguish:
- active recognition;
- archived/de-recognized relationship;
- blocked relationship;
- historical interaction without active relationship.

---

## 9. Capability

### Semantic purpose

`Capability` describes something a Person can provide.

Examples:
- welding;
- taxi transport;
- electrical work;
- legal advice;
- construction;
- vehicle repair.

Role and Capability are distinct:

`Role = how a person participates.`  
`Capability = what the person can provide.`

### Required semantic fields

- `capabilityId`;
- `personId`;
- normalized capability identifier;
- provenance;
- confidence;
- lifecycle/validity information.

### Evidence

Capabilities may originate from:
- explicit user-entered data;
- person's own profile;
- verified service history;
- trusted external source;
- semantic inference from permitted data.

The source and confidence must remain visible to the intelligence layer.

PIOS must never assert a professional capability solely because a contact's name contains an occupational keyword.

---

## 10. Need

### Semantic purpose

`Need` represents a user's current or future requirement that may be satisfied by a Capability.

Examples:
- need a welder;
- need a driver to the airport;
- need a lawyer;
- need someone to repair a vehicle.

### Required semantic fields

- `needId`;
- requestingPersonId;
- normalized intent/capability target;
- status;
- createdAt;
- context where required.

### Lifecycle

At minimum:
- OPEN;
- RESOLVED;
- CANCELLED;
- EXPIRED.

Need is intent, not an Order. A vertical may transform a Need into its own domain request when appropriate.

---

## 11. Opportunity

### Semantic purpose

`Opportunity` is a computed candidate for satisfying a Need.

Canonical model:

`Need + Capability + Context + Availability + Eligibility + Evidence → Opportunity`

Relationship is a relevance/preference dimension, not a prerequisite for Opportunity existence.

### Required semantic inputs

- need;
- candidate Person;
- matching Capability;
- relevant context;
- availability when required;
- eligibility constraints;
- evidence/confidence;
- relationship relevance if a relationship exists.

### Rules

- Opportunity is derived, not a permanent relationship fact.
- An Opportunity must be reproducible from its inputs or carry enough provenance to explain why it was generated.
- Ranking must not mutate authoritative Person or Relationship data.
- AI may propose opportunities but cannot create authoritative capabilities or relationships without the required confirmation flow.

---

## 12. Transaction / Service Result

### Semantic position

A Transaction or Service Result records what actually happened in a vertical domain.

PIOS Core must not prematurely force every interaction into one universal transaction schema.

For Taxi V1, existing domain facts such as:

`Order → Proposal → Assignment → Trip → Payment/Transaction`

remain authoritative in their existing bounded contexts.

The Core may later consume normalized events/facts from those contexts without taking ownership of Taxi lifecycle semantics.

### Non-negotiable distinction

`Transaction ≠ Relationship`

One successful transaction may provide evidence for a future relationship, but the relationship must still follow its recognition rules.

---

## 13. History

History is a factual view over recorded events, transactions, services, and relationship changes.

History answers:
- what happened;
- when it happened;
- between whom;
- in what context;
- with what result.

History must not be used as a hidden mutable substitute for Relationship state.

Where possible, factual history should be append-oriented and reconstructible from authoritative events/domain records.

---

## 14. Network

`Network` is a product projection over a user's recognized Relationships, enriched by permitted facts and derived history.

It is not equivalent to:
- the phonebook;
- all people known by PIOS;
- all Persons in the system;
- all transaction counterparties.

A user's Network may include:
- recognized people;
- relationship context;
- trusted providers;
- useful capabilities;
- historical interaction indicators;
- second-degree discovery paths where privacy permits.

Network size must never be treated as the primary success metric. Utility and quality of relationships matter more.

---

## 15. Provenance and confidence

Every non-trivial identity linkage, capability, or inferred semantic attribute must be traceable to evidence.

### Provenance should answer

- Where did this information come from?
- When was it observed?
- Was it explicitly provided, verified, derived, or inferred?
- Which rule/model produced it?

### Confidence should answer

- How certain is the system?
- Is user confirmation required before acting on it?

Provenance and confidence are orthogonal:

`source = WhatsApp profile` does not mean `confidence = confirmed identity`.

Likewise:

`source = completed Taxi trips` can be high-confidence evidence that a service interaction occurred, without proving that the person should be recognized as a permanent relationship.

---

## 16. Lifecycle and immutability principles

The Core should prefer preserving facts and deriving mutable views from them.

### Immutable or append-oriented facts

Examples:
- source observation;
- invitation acceptance;
- completed trip;
- completed service;
- rating submitted;
- explicit relationship recognition event.

### Mutable state

Examples:
- current profile representation;
- active relationship status;
- current capability validity;
- open Need status;
- current availability;
- Taxi primary preference.

A mutable projection must not overwrite the historical fact that caused it.

---

## 17. Deletion, privacy, and de-recognition

PIOS must separate:

1. deleting a user's source data;
2. removing a user's recognition of a relationship;
3. blocking another participant;
4. removing a derived/inferred attribute;
5. deleting an authoritative domain fact where the domain permits it.

Deleting a ContactRecord must not automatically erase a Person or historical transaction.

De-recognizing a Relationship must not rewrite completed transactions.

Private source data must not leak through second-degree discovery.

The product must support the principle:

> **PIOS can help me use my network without exposing another person's private network.**

---

## 18. Deduplication and idempotency

Core operations must be safe against repeated delivery, retries, and duplicate user actions.

At semantic level:

- the same source observation should not create uncontrolled duplicate ContactRecords;
- the same explicit recognition should converge on one active user-scoped Relationship;
- invitation retries must not create duplicate logical relationships;
- repeated capability evidence should be merged or versioned without multiplying semantic capabilities;
- derived Opportunity records may be regenerated without changing authoritative facts.

Exact database uniqueness constraints remain an implementation concern, but the semantic contract requires convergent behavior.

---

## 19. Cross-module boundaries

### PIOS Core owns / is intended to own

- Person;
- Profile;
- ContactRecord;
- identity linkage evidence;
- user-scoped Relationship;
- generic Capability and Need semantics;
- Opportunity interpretation/projection;
- provenance/confidence framework.

### Vertical modules own

- vertical-specific order/service lifecycle;
- vertical-specific routing rules;
- vertical-specific assignment mechanics;
- vertical-specific payment semantics;
- vertical-specific preferences such as Taxi Primary Driver;
- vertical-specific availability semantics where already established.

### Taxi boundary

The existing Taxi Circle of Trust / Connection model must be compared against the Core Relationship semantics before any physical merge.

Possible outcomes remain:

**A. Same semantics:** canonicalize/migrate.  
**B. Partial overlap:** retain application-specific concept plus explicit mapping.  
**C. Different semantics:** retain both with clear bounded-context ownership.

This document does not choose A/B/C without a code-level semantic diff against the actual production Taxi source repository.

---

## 20. AI boundary

AI is an intelligence layer, not an authority layer.

AI may:
- interpret natural-language Need;
- identify likely Capabilities from permitted evidence;
- propose Person matches;
- explain why a person appears relevant;
- propose Opportunities;
- summarize History;
- suggest actions.

AI may not silently:
- create a confirmed identity match;
- create a persistent Relationship;
- invent a Capability;
- alter authoritative Taxi facts;
- rewrite historical events;
- expose private contact data outside permitted scope.

Where AI produces an inference, the system must preserve provenance and confidence.

---

## 21. Search semantics

For a Need, default candidate search should proceed in layers:

1. user's recognized Network;
2. direct network capabilities with strong evidence;
3. likely capabilities requiring confirmation where appropriate;
4. second-degree paths through recognized relationships;
5. broader PIOS opportunity network / marketplace.

The exact ranking algorithm is a later implementation decision.

The important invariant is:

> **Search starts with the user's people before expanding outward.**

Second-degree discovery may reveal a path such as “Рустам knows Сергей,” but must not reveal Rustam's private address book or unrelated contacts.

---

## 22. Person Context

The future Person Card should be assembled from multiple permitted evidence sources rather than from one contact field.

Conceptual composition:

`Phone Contact → Person → External Profiles/Identifiers → Relationship Context → Capabilities → History → Current Opportunities`

The system should distinguish:
- confirmed facts;
- likely facts;
- unknown information.

This supports the product experience:

> “I already knew this person; PIOS helps me remember who they are and what I can do with that relationship.”

---

## 23. Core invariants

The following are non-negotiable semantic invariants:

1. ContactRecord ≠ Person.
2. ContactRecord ≠ Relationship.
3. Person ≠ Profile.
4. Person Match ≠ confirmed identity unless the confidence/evidence threshold is met.
5. Relationship is user-scoped.
6. Transaction ≠ Relationship.
7. History does not silently mutate Relationship state.
8. Capability ≠ Role.
9. Capability ≠ Availability.
10. Need ≠ Transaction.
11. Opportunity is derived from Need/Capability/context/evidence, not a permanent identity fact.
12. Relationship affects relevance/preference, not whether a Capability exists.
13. AI inference ≠ authoritative fact.
14. Private contact data must not become public network data.
15. Taxi Primary Driver is Taxi-specific.
16. Taxi First Refusal is Taxi routing behavior, not Core Relationship state.
17. Existing Taxi lifecycle remains authoritative until an explicit migration/integration contract changes ownership.
18. Production behavior must not be changed by this document alone.

---

## 24. What this contract deliberately does not decide

The following remain open implementation/product decisions:

- exact SQL tables and columns;
- ORM structure;
- event names and broker topology;
- universal Transaction schema;
- universal Interaction persistence;
- exact identity matching thresholds;
- capability taxonomy;
- Need taxonomy;
- privacy visibility matrix in every product surface;
- retention periods for source data;
- exact second-degree traversal rules;
- exact search ranking;
- exact reputation aggregation;
- physical integration strategy between Network Management and Taxi Connection;
- which external sources can be connected and under what permissions.

Open decisions must not be resolved implicitly by implementation convenience.

---

## 25. Implementation gate

Before any production implementation of this contract:

1. Compare the actual Taxi source models and APIs against these semantics.
2. Identify every existing Connection/relationship concept.
3. Identify ownership boundaries and data stores.
4. Produce an explicit migration/integration decision.
5. Define an isolated QA database and test environment.
6. Prove that existing Taxi Order → Proposal → Assignment → Trip behavior is unchanged.
7. Prove idempotency and race-safety for any cross-module flow.
8. Only then implement the first Core slice.

No production migration, deployment, schema change, or routing change is implied by this document.

---

## 26. First implementation slice

The first implementation slice should be deliberately narrow:

**`Contact → Match Candidate → Person → Explicit Recognition → Relationship → My People`**

It should establish the Core identity/relationship foundation without attempting to implement the whole PIOS intelligence platform.

The slice should demonstrate that a user's existing contacts can become useful PIOS people through explicit, privacy-safe recognition, while preserving the distinction between source evidence, identity, relationship, and historical facts.

After that foundation is stable, capability enrichment and Need → Opportunity search can be implemented on top of it.
