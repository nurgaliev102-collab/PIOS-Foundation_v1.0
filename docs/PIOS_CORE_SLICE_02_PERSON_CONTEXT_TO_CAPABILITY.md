# PIOS Core Slice 02 — Person Context → Capability

**Status:** Implementation specification candidate — v1  
**Scope:** PIOS Core capability intelligence over recognized People  
**Production impact:** None. This document is design/implementation specification only. It does not authorize production deployment or Taxi behavior changes.

## 1. Objective

Build the first intelligence layer above `My People`.

The user should be able to ask:

> **«Мне нужен сварщик»**

and PIOS should search the user's recognized network for people who have evidence of the requested capability, while clearly separating confirmed facts from probable inferences.

This slice must make PIOS useful without becoming a generic AI chatbot or a marketplace.

## 2. Preconditions

Slice 02 assumes Slice 01 provides:

`ContactRecord → MatchCandidate → Person → Explicit Recognition → Relationship → My People`

If Slice 01 is not stable, Slice 02 must not bypass it by searching raw phone contacts and presenting them as network members.

## 3. Core semantic model

`Capability` answers:

> What can this Person provide?

It is distinct from:

- Role — how the person participates;
- Availability — whether they can act now;
- Relationship — what exists between the user and person;
- Transaction — what actually happened;
- Reputation — derived quality signal.

Canonical structure:

`Person + Capability + Evidence + Confidence + Validity`

## 4. Evidence hierarchy

Capability evidence should be evaluated in this order:

1. explicit self-declared capability;
2. explicit user-entered capability about the person;
3. verified professional/service profile;
4. repeated completed service history strongly identifying the capability;
5. trusted external source explicitly naming the capability;
6. semantic inference from permitted contact/profile context.

Weak name-only keyword matches are never sufficient for a confirmed capability.

Example:

`«Игорь Свар Омск»`

may generate:

`likely capability = welding`

but must not become:

`confirmed capability = welding`

without stronger evidence or explicit confirmation.

## 5. Capability record

Semantic fields:

- `capabilityId`;
- `personId`;
- `capabilityType` — normalized semantic identifier;
- `status` — active / inactive / unknown;
- `evidence`;
- `provenance`;
- `confidence`;
- `observedAt`;
- `validFrom` / `validUntil` where known;
- optional user context/note.

The exact taxonomy is intentionally open. The implementation must support normalized identifiers without hard-coding the entire future PIOS capability universe.

## 6. Evidence object

Evidence should be first-class enough to explain a capability without making AI the authority.

Semantic fields:

- `evidenceId`;
- source type;
- source reference;
- observed timestamp;
- evidence type;
- extraction method;
- confidence;
- optional excerpt/structured attribute where privacy permits.

Evidence can be:

- USER_DECLARATION;
- PERSON_DECLARATION;
- VERIFIED_PROFILE;
- SERVICE_HISTORY;
- EXTERNAL_SOURCE;
- INFERENCE.

An inference must point to the facts/evidence that caused it.

## 7. Confidence model

Use semantic confidence classes rather than pretending a model score is absolute truth:

- **CONFIRMED** — authoritative or explicitly confirmed;
- **LIKELY** — strong supporting evidence but not confirmed;
- **UNKNOWN** — insufficient evidence.

Internal numeric scores may exist later, but the product contract must remain understandable and deterministic at the presentation boundary.

## 8. Progressive enrichment

Do not process all 792 contacts deeply at import time.

The system should use progressive enrichment:

`Existing Person → relevant evidence → capability candidate → confirmation when necessary`

Enrichment should occur when:

- the user opens a person;
- the user asks a relevant Need;
- a new trusted source becomes available;
- a relevant transaction creates evidence;
- the user explicitly adds context.

This controls cost and avoids unnecessary inference over private data.

## 9. Need interpretation

Slice 02 may accept a normalized capability query such as:

`Need(capabilityTarget = WELDING)`

but it does not yet implement the complete Need lifecycle.

Natural-language interpretation may map:

- «нужен сварщик»;
- «ищу сварщика»;
- «кто умеет варить металл?»

to the same normalized target.

The normalized capability taxonomy and multilingual synonym system remain implementation decisions.

## 10. Search boundary

Default search scope:

1. user's active Relationships / My People;
2. confirmed capabilities;
3. likely capabilities;
4. historical evidence that can support a capability candidate.

Do not expand to second-degree network or marketplace in Slice 02.

That expansion belongs to a later Opportunity slice.

## 11. Result contract

A capability search result should contain enough information to answer:

- who is the person?
- what capability matched?
- how certain is PIOS?
- why did PIOS select this person?
- what relationship/context exists?
- what can the user do next?

Conceptual result:

```text
Person
Capability
Confidence
EvidenceSummary
RelationshipContext
NextAction
```

Example:

```text
Виктор Егоров
Сварочные работы
🟢 Подтверждено
Источник: указано человеком / подтверждено
Ваш человек
[Связаться] [Запросить услугу]
```

or:

```text
Игорь
Сварочные работы
🟡 Вероятно
Основание: профиль/контекст контакта
Требует подтверждения
[Уточнить] [Посмотреть]
```

The UI must not claim a capability stronger than the evidence supports.

## 12. User confirmation

For inferred capabilities, PIOS may ask the user:

> «Похоже, Игорь занимается сваркой. Верно?»

Possible outcomes:

- confirm → capability becomes explicit user-recognized evidence;
- reject → inference is suppressed for this user/context;
- skip → retain uncertainty;
- later correction → new explicit evidence supersedes prior inference without rewriting historical source evidence.

The confirmation action is attributable to the user and must be idempotent.

## 13. Person Context card

The Person Context layer should progressively assemble:

```text
Identity
├── name
├── photo
├── identifiers
│
├── Relationship
│   ├── how connected
│   ├── recognition
│   └── user notes
│
├── Capabilities
│   ├── confirmed
│   ├── likely
│   └── unknown
│
└── History
    └── relevant completed interactions
```

A card must not imply knowledge that does not exist.

## 14. Privacy rules

Capability intelligence is scoped to data the user is permitted to use.

Do not:

- scrape private WhatsApp/Telegram chats;
- expose another person's private contact book;
- infer sensitive attributes unrelated to a legitimate product need;
- present weak identity matches as facts;
- use hidden data sources without explicit permission;
- expose evidence from another user's private context.

The capability layer should operate on permitted source facts and explicit user context.

## 15. AI boundary

AI may:

- normalize a natural-language Need;
- classify permitted evidence;
- suggest capability candidates;
- explain evidence;
- propose a confirmation question.

AI may not:

- silently confirm a capability;
- invent evidence;
- overwrite source facts;
- create a relationship;
- alter Taxi domain facts;
- expose private data.

Recommended architecture:

`Evidence → deterministic normalization/rules → optional AI inference → Capability Candidate → confidence/provenance → user projection`

AI is replaceable.

## 16. Deterministic baseline before AI

The first implementation should work without a paid model.

Baseline pipeline:

`explicit capability data → normalized taxonomy → deterministic matching → result`

Then optionally:

`permitted text/context → inference → candidate → confirmation`

This preserves predictable cost and makes AI an enhancement rather than an infrastructure dependency.

## 17. Real-contact test baseline

The existing contact experiment provides a useful QA fixture class: occupational keywords can identify candidate evidence but cannot establish truth.

For the real 792-contact dataset, the system should be able to distinguish:

- contacts with explicit capability evidence;
- contacts with probable capability evidence;
- contacts with no usable evidence.

The test must never assert that every keyword match is a real professional capability.

## 18. API-level semantic operations

Suggested operations:

### `SearchMyPeopleByCapability`

Input:

- requestingPersonId;
- normalized capability target;
- optional query text;
- optional result limit.

Output:

- Person;
- Capability candidate;
- confidence;
- evidence summary;
- relationship context;
- next action.

### `ConfirmCapability`

Input:

- actorPersonId;
- targetPersonId;
- capabilityType;
- evidence context;
- idempotency key.

Effect:

Creates or updates explicit user-recognized capability evidence. It does not modify the original source evidence.

### `RejectCapabilityInference`

Input:

- actorPersonId;
- targetPersonId;
- capabilityType;
- inference/evidence reference;
- idempotency key.

Effect:

Suppresses the current inference for that user/context without falsifying source facts.

Exact HTTP paths and transport schemas are implementation decisions.

## 19. Idempotency and concurrency

The following must converge under retries:

- repeated capability confirmation;
- repeated rejection;
- repeated search;
- duplicate evidence ingestion.

Concurrent confirmations must not create duplicate semantic capability records.

A stale inference must not overwrite a newer explicit confirmation.

## 20. Testing requirements

### Unit tests

Cover:

- capability normalization;
- evidence classification;
- confidence mapping;
- confirmed vs likely vs unknown;
- explicit confirmation;
- rejection/suppression;
- duplicate confirmation;
- stale inference protection.

### Integration tests

Cover:

- recognized Person → capability search;
- contact evidence → candidate capability;
- explicit confirmation → search result changes;
- relationship removal → capability no longer appears in My People search;
- source deletion does not erase unrelated historical facts.

### Security/privacy tests

Cover:

- one user's private source evidence cannot appear in another user's results;
- second-degree data is not exposed;
- unauthorized capability mutation is rejected.

### Regression tests

Existing Taxi tests must remain green.

No Taxi Order, Proposal, Assignment, Trip, Primary Driver, First Refusal, or payment behavior may change as a side effect of this slice.

## 21. Production safety gate

Before implementation:

1. Confirm the actual production Taxi repository and module boundaries.
2. Confirm the isolated QA database.
3. Confirm tests cannot connect to production databases.
4. Implement Core changes behind isolated boundaries.
5. Run Core tests.
6. Run Taxi regression suite.
7. Perform read-only production compatibility verification.
8. Only after explicit evidence is obtained may deployment be considered.

A failing or ambiguous safety gate means STOP. Do not modify production to make the test pass.

## 22. Non-goals

Slice 02 does not implement:

- marketplace;
- second-degree discovery;
- general opportunity ranking;
- reputation ranking;
- payment;
- universal transaction model;
- WhatsApp/Telegram scraping;
- autonomous messaging;
- paid AI dependency;
- Taxi routing changes.

## 23. Definition of Done

Slice 02 is complete when:

1. PIOS can represent a Capability separately from Person and Relationship.
2. Evidence and confidence are preserved.
3. A user's My People can be searched by normalized capability.
4. Confirmed and likely results are visibly distinct.
5. User confirmation is explicit and idempotent.
6. Weak evidence cannot become a confirmed fact automatically.
7. No private source data crosses user boundaries.
8. The baseline works without a paid AI provider.
9. Existing Taxi behavior is regression-tested and unchanged.
10. The implementation can later feed Need → Opportunity without redesigning Person/Relationship semantics.

## 24. Next slice

After Slice 02, the next meaningful capability is:

**Need → Opportunity → Action**

This is where PIOS moves from:

> «У меня есть люди, которые что-то умеют»

to:

> **«У меня возникла потребность — PIOS сам нашёл среди моих людей подходящего человека и предложил следующий шаг».**

Only after that should second-degree discovery and broader PIOS marketplace expansion be introduced.
