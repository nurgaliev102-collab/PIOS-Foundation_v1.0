# PIOS Core Slice 03 — Need → Opportunity

**Status:** Implementation specification candidate — v1  
**Scope:** Universal PIOS Core opportunity resolution over the user's recognized network  
**Date:** 2026-09-10  
**Production impact:** None. Design/implementation specification only. This document does not authorize production deployment, Taxi routing changes, or migration of existing Taxi relationships.

## 1. Objective

Build the first PIOS mechanism that converts a human need into useful candidates from the user's own network.

Target experience:

> **«Мне нужен сварщик»**

PIOS should determine the requested capability, search the user's recognized People, identify evidence-backed candidates, explain why each candidate is relevant, and offer an explicit next action.

The first search boundary is the user's own Network. Expansion beyond it is a later stage and must not silently expose private networks.

---

## 2. Preconditions

Slice 03 depends on:

`Slice 01: Contact → Match Candidate → Person → Recognition → Relationship → My People`

and

`Slice 02: Person Context → Evidence → Capability`

If either foundation is unavailable, Slice 03 must not bypass it by searching raw phone contacts or presenting unrecognized people as members of the user's Network.

---

## 3. Canonical Opportunity model

An Opportunity is a computed candidate for satisfying a Need.

Canonical formula:

`Need + Capability + Context + Availability + Eligibility + Evidence → Opportunity`

Relationship is an additional relevance/preference dimension:

`Relationship → relevance/context/preference`

It does **not** define whether the candidate possesses the capability.

Therefore:

- a trusted friend may rank highly because the relationship is relevant;
- an unknown marketplace provider may still be a valid opportunity;
- a weak relationship does not invalidate a strong capability;
- a strong relationship does not create a capability that has no evidence.

---

## 4. Need

### 4.1 Purpose

`Need` captures what the user is trying to accomplish, independently of how it will eventually be fulfilled.

Examples:
- need a welder;
- need airport transport;
- need a lawyer;
- need vehicle repair.

### 4.2 Required semantics

A Need has:

- `needId`;
- `requestingPersonId`;
- normalized intent/capability target;
- lifecycle status;
- creation timestamp;
- relevant context when necessary;
- provenance of creation.

### 4.3 Need lifecycle

Minimum states:

`OPEN → RESOLVED`

or

`OPEN → CANCELLED`

or

`OPEN → EXPIRED`

A Need may be created by:
- explicit structured input;
- natural-language input interpreted by the intelligence layer;
- a vertical application when a domain request is mapped into Core semantics.

AI interpretation must preserve the original user intent and confidence. Ambiguous intent should produce clarification rather than an invented capability.

---

## 5. Capability matching

Capability matching should use normalized semantic identifiers rather than raw string equality alone.

Example:

`«сварщик»`

may map to a normalized capability concept such as:

`WELDING_SERVICE`

while preserving the original user wording.

The taxonomy must support future expansion without forcing all verticals into a single flat category list.

### Evidence classes

Candidate capability evidence should be classified at minimum as:

- **CONFIRMED** — explicit or sufficiently authoritative evidence;
- **LIKELY** — strong but inferential evidence;
- **UNKNOWN** — insufficient evidence.

Unknown must not be presented as a positive capability match.

---

## 6. Search boundary: Network-first

For a new Need, candidate resolution proceeds in this order:

### Tier 1 — My People

Search active user-recognized Relationships and their associated Capabilities.

This is the primary PIOS differentiator.

### Tier 2 — Network context enrichment

Use permitted evidence and history to improve relevance, while preserving source/confidence boundaries.

Examples:
- person explicitly says they weld;
- previous service history indicates repeated welding work;
- user previously recorded that this person is a welder.

### Tier 3 — Second-degree Network

If no sufficient direct candidate exists, later versions may search through recognized relationships for introductions.

This is not part of the first executable implementation unless explicitly enabled by a separate contract.

### Tier 4 — PIOS Opportunity Network / Marketplace

If the user's network cannot satisfy the Need, PIOS may expand to the broader opportunity network.

Marketplace is therefore an expansion mechanism, not the starting point.

---

## 7. Candidate scoring

Candidate ranking must be explainable and must not collapse unrelated concepts into one opaque AI score.

Conceptual relevance dimensions:

1. capability match strength;
2. capability evidence confidence;
3. current availability;
4. eligibility/context fit;
5. relationship relevance;
6. verified interaction history;
7. recency of relevant evidence.

The exact numerical weighting is deliberately not fixed in v1.

### Hard rule

A high relationship score cannot compensate for absence of capability evidence.

A candidate should not become the top result merely because:
- they are a close friend;
- they have many transactions;
- they have a high reputation;
- an AI model likes the candidate.

The system must first establish candidate eligibility, then rank eligible candidates.

---

## 8. Availability

Availability answers whether a capable Person can act in the required context and time.

Capability and Availability are separate.

Example:

`Сергей = confirmed welder`

does not mean:

`Сергей = available tomorrow at 15:00`.

Availability may come from:
- explicit current availability;
- vertical-specific availability service;
- appointment/calendar integration in a future version;
- provider response to an action request.

If availability is unknown, the UI should say so rather than infer availability.

---

## 9. Eligibility and context

A candidate must satisfy hard constraints before ranking.

Possible constraints:
- location/radius;
- service type;
- time window;
- role or legal eligibility where relevant;
- required equipment/capability subtype;
- language or other user-specified requirement.

Context must be explicit enough to explain exclusion.

Example:

> «Иван умеет сваривать, но находится в другом городе и не указал удалённую работу.»

This is preferable to silently omitting him with an unexplained AI score.

---

## 10. Relationship relevance

Relationship can improve relevance through known context:

- how the people know each other;
- whether the user has previously used the person's service;
- whether the relationship is trusted;
- whether the person is a preferred provider in a vertical;
- whether previous interactions were successful.

But these must remain distinct facts.

Do not create a universal `relationshipScore` that pretends to be trust, reputation, quality, or capability.

Taxi `Primary Driver` remains a Taxi-specific preference and may influence Taxi opportunity resolution without becoming a generic Core relationship state.

---

## 11. History as evidence

Historical interaction can improve candidate explanation and relevance.

Examples:
- «Вы уже обращались к Сергею 4 раза»;
- «Сергей выполнял для вас сварочные работы».

History does not automatically create a Relationship.

History does not automatically create a Capability unless the vertical's evidence contract explicitly supports that inference.

Historical facts must remain traceable to authoritative domain records.

---

## 12. Opportunity representation

An Opportunity should conceptually contain:

- `opportunityId`;
- `needId`;
- candidate `personId`;
- matched capability;
- evidence references;
- confidence;
- availability status;
- eligibility result;
- relationship relevance;
- explanation/provenance;
- generated timestamp;
- lifecycle/expiration where appropriate.

Opportunity is disposable/derivable. It must not become a second source of truth for Person, Relationship, Capability, or History.

Regeneration of Opportunities must not mutate those source facts.

---

## 13. User experience

The first UX should be deliberately simple.

### User

> Мне нужен сварщик

### PIOS

> Нашёл 3 подходящих человека среди ваших людей.

Possible result:

```text
1. Сергей Иванов
   Сварка — подтверждено
   Вы уже работали: 4 раза
   Доступность: неизвестна
   [Написать] [Позвонить] [Запросить работу]

2. Виктор Егоров
   Сварка — вероятно
   Источник: ваш контакт + контекст
   [Уточнить] [Написать]

3. Руслан
   Сварка — подтверждено
   Вы знакомы через Рустама
   [Попросить связать]
```

The exact UI is not prescribed here; the semantic requirement is explanation and explicit action.

PIOS should not force communication into a new messenger. Existing permitted channels can remain available.

---

## 14. AI role

AI may:
- interpret natural-language Need;
- normalize synonyms;
- extract constraints from natural language;
- summarize evidence;
- explain candidate relevance;
- suggest an action.

AI may not:
- invent a Capability;
- silently confirm an identity;
- silently create a Relationship;
- silently claim availability;
- rewrite History;
- override hard eligibility rules;
- expose private network data.

A deterministic retrieval layer should remain capable of operating without a paid AI provider for straightforward structured searches.

---

## 15. Action model

Finding a candidate is not the same as contacting or booking them.

The first version should expose explicit actions such as:

- `CONTACT_PERSON`;
- `ASK_AVAILABILITY`;
- `REQUEST_SERVICE` where a vertical supports it;
- `ASK_FOR_INTRODUCTION` for an authorized second-degree flow.

An Opportunity does not itself create a Transaction.

The chosen Action may later produce a vertical Transaction/Service Result.

---

## 16. Idempotency and concurrency

Opportunity computation must tolerate retries and duplicate requests.

Requirements:

- repeated Need submission must not create uncontrolled duplicate active Needs unless the user intentionally creates separate requests;
- repeated search requests may regenerate equivalent Opportunities without duplicating authoritative facts;
- accepting an Opportunity must use an idempotent Action/command in the owning vertical;
- concurrent candidate updates must not create contradictory authoritative state;
- stale Opportunities must not override newer availability or eligibility facts.

Where a vertical has stronger transactional guarantees, those guarantees remain authoritative.

---

## 17. Privacy and second-degree discovery

Second-degree discovery must be graph-aware but privacy-safe.

Allowed conceptual result:

> «Рустам может познакомить вас с Сергеем.»

Disallowed behavior:

> «Вот все контакты Рустама.»

The path itself may be exposed only when the visibility policy permits it.

Private source data must never become a public capability index merely because PIOS observed it while processing another user's contacts.

---

## 18. Taxi integration boundary

Taxi remains a vertical consumer of Core concepts.

For Taxi:

`Need / Order intent → relationship resolution → First Refusal → Proposal → Assignment → Trip`

Core Opportunity resolution must not replace or rewrite this existing Taxi routing flow in Slice 03.

If Taxi later consumes Core Opportunities, an explicit integration contract must define:
- which Opportunity facts Taxi trusts;
- how availability is sourced;
- how Proposal creation occurs;
- how races/idempotency are handled;
- how existing Primary Driver semantics map to Core Relationship.

Until then, no production Taxi routing change is permitted.

---

## 19. Implementation sequence

### Phase A — deterministic Need

Support a small normalized capability vocabulary and explicit Need creation.

### Phase B — direct Network retrieval

Search recognized People and confirmed Capabilities.

### Phase C — evidence-aware ranking

Add confidence, history, availability, and relationship relevance as separate dimensions.

### Phase D — explicit action

Allow the user to contact/request/ask availability from a selected candidate.

### Phase E — second-degree expansion

Implement privacy-safe introductions as a separate bounded capability.

### Phase F — broader opportunity network

Only after network-first retrieval is reliable should PIOS expand to broader providers/marketplace.

---

## 20. Definition of Done for first executable Slice 03

The first implementation is complete only when:

1. A user can create a Need such as «Мне нужен сварщик».
2. The Need is normalized deterministically for supported vocabulary.
3. Only recognized Network People are searched in the first tier.
4. Candidates require explicit Capability evidence.
5. Confirmed and likely evidence are visibly distinguished.
6. Unknown capability is not presented as a match.
7. Results expose evidence/provenance sufficient for explanation.
8. Relationship relevance is separate from capability evidence.
9. Availability is not fabricated.
10. A selected candidate leads to an explicit user action.
11. Opportunity computation does not create Relationships automatically.
12. Opportunity computation does not modify Taxi routing.
13. Existing Taxi regression tests remain green.
14. QA uses isolated data and cannot write production records.
15. Repeated requests do not create uncontrolled duplicate authoritative facts.
16. AI is optional for deterministic searches.
17. Private second-degree contact data is not exposed.

---

## 21. Production safety gate

Before implementation touches any running environment:

1. Identify the actual production Taxi repository and bounded contexts.
2. Verify the current branch and deployment pipeline.
3. Create an isolated QA environment/database.
4. Add regression tests around existing Taxi Order → Proposal → Assignment → Trip behavior.
5. Implement Core behind an explicit boundary/feature flag if required.
6. Prove that Core queries cannot mutate Taxi facts.
7. Prove idempotency and retry behavior.
8. Run the full relevant test suite against QA only.
9. Perform read-only verification of production if needed; do not seed or mutate production for testing.
10. Deploy to production only through the existing approved GitHub → Beget workflow after QA acceptance.

No developer should interpret this specification as permission to refactor the production Taxi architecture.

---

## 22. Non-goals

Slice 03 does not implement:

- universal marketplace;
- recommendation ads;
- paid priority;
- reputation selling;
- commission routing;
- universal messaging;
- autonomous AI decisions;
- automatic Relationship creation from transactions;
- unrestricted address-book discovery;
- full second-degree graph traversal;
- card/payment custody;
- replacement of Taxi Dispatch.

The objective is narrower:

> **Turn a user's own network into the first place PIOS looks when the user needs something.**
