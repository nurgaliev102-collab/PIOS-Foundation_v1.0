# PIOS Core Slice 01 — Source Contact to My People

**Status:** Implementation specification — v1  
**Scope:** First executable PIOS Core slice  
**Date:** 2026-09-10  
**Production impact:** None until separately admitted through the production safety gate.

## 1. Objective

Implement the smallest real PIOS Core flow that turns a user's existing contact-source records into explicit, user-recognized PIOS relationships.

Canonical flow:

`Contact Source → ContactRecord → MatchCandidate → Person → Explicit Recognition → Relationship → My People`

This slice establishes the identity/relationship foundation. It does **not** implement capability intelligence, Need/Opportunity matching, second-degree search, Marketplace, AI enrichment, or Taxi routing changes.

## 2. Product outcome

A user should be able to:

1. provide a permitted contact source;
2. see which contacts may correspond to existing PIOS participants;
3. understand the confidence of the match;
4. explicitly recognize a person or invite them;
5. see the recognized person in **My People**;
6. repeat the operation without creating duplicates.

The product must feel like recovery and organization of an existing human network, not forced CRM data entry.

## 3. Non-goals

Do not implement in this slice:

- automatic relationship creation from contact presence;
- automatic relationship creation from one transaction;
- WhatsApp/Telegram scraping;
- social-network scraping;
- occupational inference as authoritative fact;
- Capability Graph;
- Need/Opportunity engine;
- second-degree network traversal;
- Marketplace;
- Reputation/rating redesign;
- universal Interaction entity;
- generic Transaction entity;
- Taxi First Refusal;
- Taxi Primary Driver changes;
- replacement of existing Taxi Connection/Circle of Trust;
- production database migration;
- production deployment.

## 4. Production safety gate

Before any code is merged toward production, Claude Code must prove:

### Gate A — repository and runtime isolation

- identify the actual repository containing the production Taxi backend;
- identify production database/configuration references;
- identify the QA/test database;
- confirm that tests cannot write to the production database;
- use a dedicated Core QA database/schema or isolated test container;
- do not modify production environment variables or services.

### Gate B — existing Taxi preservation

The implementation must not change the behavior of:

`Order → Proposal → Assignment → Trip`

and must not alter:

- existing Taxi Connection semantics;
- Primary Driver;
- First Refusal;
- proposal acceptance/expiry behavior;
- payment behavior;
- passenger/driver production screens.

If an existing production model must be touched to satisfy compilation, stop and produce a boundary analysis before changing it.

### Gate C — explicit admission

A Core implementation is not production-ready merely because tests pass. Production admission requires an explicit review of:

- data ownership;
- migration strategy;
- privacy;
- rollback;
- operational observability;
- production database safety.

## 5. Domain objects in Slice 01

### 5.1 ContactRecord

Represents a source-side contact record owned by one PIOS user.

Minimum semantics:

- `contactRecordId`;
- `ownerPersonId`;
- `sourceType`;
- `sourceRecordId` when available;
- normalized identifiers;
- observed timestamp;
- source provenance.

A ContactRecord is private source evidence.

### 5.2 MatchCandidate

Represents a proposed linkage from ContactRecord to Person.

Minimum semantics:

- candidate identifier;
- ContactRecord reference;
- Person reference when known;
- confidence class;
- provenance/evidence;
- status;
- evaluated timestamp.

Recommended confidence classes:

`CONFIRMED`, `LIKELY`, `UNKNOWN`.

### 5.3 Person

Stable canonical PIOS participant reference.

For an existing PIOS account, resolve to that participant. For a new invited person, the relationship/invitation may exist before the invitee completes account creation, but the implementation must preserve a distinction between an external contact and a registered Person.

### 5.4 Relationship

User-scoped persistent recognition that the other participant belongs to the user's usable network.

Minimum semantics:

- relationship identifier;
- owner;
- subject;
- recognition source;
- created/recognized timestamp;
- active/lifecycle state;
- provenance.

### 5.5 My People

A read projection over active user-scoped Relationships.

It is not a separate authoritative graph fact.

## 6. Identity matching rules

Matching must be deterministic where reliable identifiers exist and conservative otherwise.

### Strong candidates

- verified PIOS identifier;
- exact normalized phone number where policy permits matching;
- exact verified email where policy permits matching.

### Supporting evidence

- name similarity;
- organization;
- available profile metadata;
- source photo or external identifier where legally and technically permitted.

### Prohibited automatic confirmation

Name-only similarity must never produce `CONFIRMED` identity.

A weak match may be shown as `LIKELY` or `UNKNOWN`, requiring user confirmation before relationship recognition.

## 7. Contact ingestion

The source adapter must normalize source records into the Core semantic contract without making product-level relationship decisions.

Conceptually:

`Raw Contact → normalized ContactRecord`

The adapter should be idempotent for repeated synchronization of the same source record.

A repeated sync must update the source observation rather than create uncontrolled duplicates.

The Core must preserve provenance sufficient to answer:

> “Why does PIOS think this contact may be this Person?”

## 8. Recognition commands

The first slice needs explicit domain operations rather than generic database writes.

Recommended operations:

### `RecognizePerson`

Input:
- owner user/person;
- resolved Person;
- source candidate or recognition provenance.

Effect:
- creates or activates one user-scoped Relationship.

Rules:
- repeated execution is idempotent;
- cannot create a duplicate active relationship;
- does not alter Taxi-specific primary/trust state unless a later explicit integration contract says so.

### `InviteContact`

Input:
- owner;
- ContactRecord;
- permitted destination/identifier.

Effect:
- creates or reuses an invitation intent;
- does not imply that the invitee has accepted a Relationship.

Acceptance may later create/activate the appropriate relationship according to the invitation contract.

### `RemoveFromMyPeople`

Effect:
- ends active recognition for that user;
- preserves historical facts;
- does not delete the global Person merely because the user stopped recognizing them.

### `BlockPerson`

Effect:
- prevents the blocked person from appearing or interacting through the permitted Core surfaces according to the future privacy policy.

Blocking semantics must not be conflated with ordinary de-recognition.

## 9. API boundary

The exact transport technology is implementation-specific, but the first slice should expose equivalent operations for:

### Contact discovery

`GET/POST contacts/discover`

Returns contact candidates with:

- source display data;
- match status;
- confidence;
- permitted profile preview;
- action availability.

### Recognize

`POST people/{personId}/recognize`

### Remove recognition

`DELETE people/{personId}/recognition`

### My People

`GET people/me/network`

These names are semantic examples, not an instruction to break existing API conventions. If the production repository already has an API convention, follow it while preserving the contract.

## 10. Response safety

The API must not expose:

- another user's private contact list;
- raw source data belonging to another user;
- hidden relationship edges;
- unverified inferred occupations as facts;
- identifiers that the caller is not authorized to see.

For a match, return only the minimum evidence needed for the user to make a recognition decision.

## 11. Idempotency and uniqueness

The following operations must converge under retries:

- contact synchronization;
- match generation;
- recognition;
- invitation creation;
- removal/de-recognition.

Semantic uniqueness:

`one owner + one subject + one active relationship`

must yield at most one active logical Relationship.

Concurrent recognition requests must not create two active relationships.

## 12. State model

### MatchCandidate

```text
UNRESOLVED
   ↓
SUGGESTED
   ├── CONFIRMED
   └── REJECTED
```

A candidate may become superseded when new evidence replaces an earlier linkage.

### Relationship

Do not create a universal state machine. At minimum the implementation must distinguish:

- ACTIVE recognition;
- DE_RECOGNIZED / ARCHIVED;
- BLOCKED where blocking is implemented.

Relationship lifecycle is user-scoped.

## 13. Privacy model for first slice

The first slice follows four rules:

1. The user's contacts are their private source data.
2. A discovered PIOS account can be shown only through permitted matching rules.
3. Recognition is explicit.
4. Recognition by user A does not expose user B's private address book.

No second-degree traversal is needed in Slice 01. Designing for it must not leak private graph data prematurely.

## 14. Frontend behavior

The initial UI can be simple.

Recommended sequence:

### Screen A — Find my people

User grants/selects the permitted contact source.

### Screen B — Contact matches

Show sections such as:

- `Уже в PIOS`;
- `Возможно, это он`;
- `Пригласить`.

Do not show a confidence score as if it were scientific precision. Use understandable labels and explain the evidence where useful.

### Screen C — Person preview

Show permitted:

- name;
- photo if available and permitted;
- known PIOS profile information;
- why this person was suggested;
- available action.

### Screen D — My People

Show recognized Relationships, not all imported contacts.

This distinction is fundamental to the product.

## 15. Test requirements

### Unit tests

At minimum:

1. ContactRecord normalization is deterministic.
2. Same source record sync is idempotent.
3. Exact phone match produces a candidate under the configured policy.
4. Name-only match never becomes confirmed automatically.
5. Recognition creates one Relationship.
6. Repeated recognition remains one Relationship.
7. Concurrent recognition remains one active Relationship.
8. De-recognition hides the person from My People.
9. De-recognition preserves historical facts.
10. Invitation does not equal accepted relationship.
11. Blocked users are excluded from permitted Core surfaces.

### Integration tests

Prove:

`Contact → Candidate → Person → Recognize → My People`

against an isolated test database.

### Regression tests

Run the existing Taxi test suite and prove no behavior change in the existing Taxi lifecycle.

### Production database safety test

Explicitly prove that test configuration points to a non-production database. A test that merely “usually uses QA” is insufficient.

## 16. Observability

Record operationally useful events without logging sensitive raw contact data.

Useful events include:

- contact_sync_started/completed;
- match_candidate_created;
- person_recognition_requested;
- relationship_recognized;
- relationship_de_recognized;
- invitation_created;
- invitation_accepted.

Logs must avoid raw phonebooks, full contact dumps, private notes, or unnecessary identifiers.

## 17. Migration strategy

Slice 01 must not migrate the existing Taxi Connection model automatically.

Before any integration, perform a source-code semantic comparison:

1. identify Taxi Connection ownership;
2. identify its fields and lifecycle;
3. identify who creates it;
4. identify whether it means the same thing as Core Relationship;
5. determine A/B/C outcome from the Relationship Semantic Contract.

Only after that comparison may a migration or mapping be designed.

## 18. Definition of Done

Slice 01 is complete when all are true:

- Core semantic objects are implemented without violating the Data Contract;
- contact records remain source evidence;
- identity matches carry provenance/confidence;
- recognition is explicit;
- My People contains recognized relationships, not raw contacts;
- duplicate recognition converges safely;
- privacy tests pass;
- isolated QA database is proven;
- existing Taxi regression tests pass;
- no production Taxi behavior is modified;
- no production migration is performed;
- implementation documentation identifies every touched module/file;
- rollback is defined;
- Claude Code can explain ownership of every new piece of state.

## 19. Handoff to Claude Code

Claude Code should treat this document as an implementation contract, not as permission to refactor unrelated code.

Required working sequence:

1. Inspect the actual repository and locate the real production Taxi backend.
2. Inspect current identity, network-management, passenger-experience, and Taxi Connection models.
3. Produce a short boundary report before writing code.
4. Confirm test database isolation.
5. Implement the Core slice in an isolated feature branch.
6. Add unit/integration/regression tests.
7. Run the full test suite.
8. Produce a file-by-file change report.
9. Do not deploy.
10. Do not change production configuration.
11. Do not merge or migrate existing Taxi Connection semantics without an explicit decision.

If any requirement conflicts with existing production behavior, stop at the boundary and report the conflict instead of silently changing the existing system.

## 20. Next slice

After Slice 01 is stable, implement:

**Core Slice 02 — Person Context → Evidence → Capability**

Its first commercially meaningful scenario will be:

`Мне нужен сварщик → поиск среди Моих Людей → подтверждённые/вероятные Capability → объяснимый результат`

Only after this works should the system expand toward second-degree discovery and broader PIOS Opportunity search.
