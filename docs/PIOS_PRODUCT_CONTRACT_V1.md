# PIOS Product Contract v1

**Status:** Product source-of-truth candidate — v1  
**Scope:** Universal PIOS product model + Taxi as first vertical  
**Date:** 2026-09-10  
**Production impact:** None. This document is design-only.

---

## 0. Purpose

This document defines the product contract for PIOS before implementation of the universal Network Core.

It deliberately separates four things:

1. **Product truth** — what PIOS is and what problem it solves.
2. **Domain truth** — the concepts that form the universal PIOS model.
3. **Application truth** — Taxi-specific behavior that consumes the Core.
4. **Implementation status** — what already exists, what has been ratified, what is target architecture, and what remains open.

This document must not be interpreted as evidence that target concepts already exist in production code.

---

# I. PRODUCT TRUTH

## 1. What PIOS is

PIOS is a **Personal Network Operating System**.

Its first commercially concrete form is a **Personal Business Network (PBN)**, with Taxi as the first vertical application.

PIOS helps a person:

- know and recognize the people around them;
- preserve useful context about those people;
- understand what people can do or offer;
- express what the person currently needs;
- find suitable people through their own network first;
- reach suitable people through trusted network paths when no direct person exists;
- use broader PIOS opportunities when the personal network is insufficient;
- turn successful interactions into reusable history and, when explicitly desired, continuing relationships;
- develop the person's network over time.

PIOS is therefore not primarily a contact manager, messenger, marketplace, or AI chatbot.

Its core product proposition is:

> **PIOS turns the people a person already knows into a usable network of relationships and opportunities, and helps that network become more useful over time.**

The user experience should create the feeling:

> **«Оказывается, у меня уже всё это было. Я просто не мог этим пользоваться.»**

A concise product expression is:

> **«Ты забываешь людей. PIOS — нет.»**

These are product propositions, not claims that PIOS currently has complete memory or intelligence.

---

## 2. The fundamental product problem

Existing contacts are fragmented across:

- phone contacts;
- messaging applications;
- social profiles;
- email;
- call history;
- previous transactions;
- personal notes;
- memory.

A conventional address book answers approximately:

> «Кто записан у меня?»

A personal CRM primarily answers:

> «Что я знаю об этом человеке?»

PIOS must answer a more operational question:

> **«Кто из людей вокруг меня может решить мою текущую задачу?»**

And, when there is no direct match:

> **«Через кого из моих людей я могу выйти на человека, который может её решить?»**

This is the product-level distinction from a conventional Personal CRM.

---

## 3. PIOS is network-first, not network-only

The preferred search order is:

1. the user's recognized people;
2. people reachable through the user's network;
3. relevant people/opportunities in PIOS;
4. broader marketplace/open opportunity sources where applicable.

This is a **product preference**, not a requirement to exhaustively scan every contact before showing another useful option.

Network-first must never make PIOS unusable for a person with no existing network.

Therefore:

> **Network is a result of using PIOS, not a precondition for entry.**

---

## 4. Two network modes

PIOS serves two complementary jobs.

### Network Management

> **«У меня уже есть люди.»**

PIOS helps the user recognize, organize, preserve, and use existing relationships.

### Network Development

> **«Помоги мне получить и развить нужные отношения.»**

PIOS helps the user reach suitable people, create new relationships where appropriate, and increase the usefulness of the network.

These are two modes of the same product, not two separate products.

---

# II. UNIVERSAL PRODUCT LOOP

## 5. The six core actions

PIOS can be understood through six user-level actions:

1. **ЗНАТЬ** — understand who a person is and the relevant context.
2. **НАЙТИ** — find a person who can solve a need.
3. **ВЗАИМОДЕЙСТВОВАТЬ** — initiate or continue an action.
4. **СДЕЛАТЬ** — achieve a real result.
5. **СОХРАНИТЬ** — explicitly recognize that a relationship is worth retaining.
6. **ПОВТОРИТЬ / РАЗВИТЬ** — reuse or deepen the relationship and create future value.

The product should expose these concepts in ordinary user language. Internal domain names such as Relationship, Capability, Need, and Opportunity should not be required for the user to understand the product.

---

## 6. Universal lifecycle

```text
REAL NEED
   ↓
FIND
   ↓
INTERACT
   ↓
RESULT
   ↓
SAVE?
   ↓ yes
RELATIONSHIP
   ↓
HISTORY
   ↓
REPEAT / DEVELOP
   ↓
MORE CONTEXT
   ↓
BETTER FUTURE SEARCH
   ↓
BETTER RESULT
   ↓
REPEAT
```

This is the central PIOS product loop.

Not every loop requires every stage explicitly. For example, a user can simply recognize a person and establish a relationship without a transaction.

---

# III. DOMAIN TRUTH

## 7. Identity

**Identity** answers:

> «Как система знает, что этот account/source belongs to this participant?»

Identity is an authentication and identity-resolution concern, not a Taxi domain concept.

Target relationship:

```text
Identity
   ↓
Person
   ↓
Role
```

The current `Identity` implementation contains Taxi coupling such as `driverId`/`drv`. This is a transitional implementation fact, not the target universal model.

**Status: EXISTING + TRANSITIONAL.**

Do not remove the Taxi coupling until the replacement Person/Role identity path exists and is migrated safely.

---

## 8. Contact Record

A phone/address-book record is **not a Person** and is **not a Relationship**.

A Contact Record is an external/user-owned source record that may contain:

- name;
- phone;
- email;
- organization;
- title;
- notes;
- URLs;
- available profile identifiers;
- photo.

Target flow:

```text
CONTACT RECORD
      ↓
IDENTITY RESOLUTION
      ↓
PERSON / PERSON CANDIDATE
```

A contact record may fail to resolve to a unique Person.

Name-only matches must never silently merge people.

**Status: TARGET CORE.**

---

## 9. Person

**Person** is the canonical stable participant reference used by PIOS.

Person is not:

- a phone contact;
- an authentication credential;
- a Taxi driver;
- a Profile;
- a Relationship.

A Person may hold multiple roles.

Example:

```text
Person
 ├── Role: Driver
 ├── Role: Passenger
 ├── Role: Entrepreneur
 └── Role: Customer
```

The same Person must not be duplicated merely because they participate in different verticals.

The existing `network-management.Person` is a candidate implementation of this concept.

**Status: EXISTING SCAFFOLD → TARGET CORE.**

---

## 10. Profile

**Profile** is a representation of a Person used to present relevant information.

It may contain:

- name;
- photo;
- public/shared information;
- role/capability presentation;
- contact representations permitted by policy.

Profile must not become a container for every private fact known by the user.

The existing `network-management.Profile` is a candidate implementation.

**Status: EXISTING SCAFFOLD → TARGET CORE.**

---

## 11. Relationship

**Relationship** answers:

> «Что существует между мной and this person?»

Relationship is the central universal network concept.

It is multidimensional and must not be implemented as a simple linear state machine such as:

```text
INVITED → CONNECTED → TRUSTED → PRIMARY
```

Instead, relationship semantics may contain separate dimensions such as:

```text
Relationship
 ├── existence / recognition
 ├── directionality / parties
 ├── context / provenance
 ├── trust signals
 ├── preferences
 ├── visibility
 └── lifecycle
```

Historical interactions are not mutable Relationship state. They are facts derived from interaction/transaction sources.

A Relationship can be established by an explicit user action, mutual connection process, or another explicitly defined relationship-creation mechanism. A transaction alone must not automatically create a permanent Relationship.

**Status: TARGET CORE.**

The current `network-management.Connection` is only a starting implementation and must not be blindly renamed into Relationship.

---

## 12. Invitation

Invitation is a mechanism for inviting another participant to connect/join/recognize a relationship.

Invitation is not itself the Relationship.

```text
Invitation
   ↓ acceptance / other outcome
Relationship (where product rules permit)
```

The existing `network-management.Invitation` is a candidate universal mechanism.

**Status: EXISTING SCAFFOLD → TARGET CORE.**

---

## 13. Trust

Trust is distinct from Relationship, Rating, and Reputation.

Relationship means:

> «Мы признаны как people connected in some context.»

Trust means:

> «There are evidence-backed reasons to treat this person as trusted in a defined context.»

Trust must not be inferred solely from the existence of a Relationship.

**Status: RATIFIED DESIGN; full universal implementation remains TARGET.**

---

## 14. Rating and Reputation

These concepts remain separate:

- **Rating** — an atomic fact about a specific completed interaction/service.
- **Reputation** — an aggregate/derived view from ratings and relevant history.
- **Trust** — a relationship/context property supported by evidence.

Reputation must not become a paid ranking mechanism and must not silently become dispatch priority.

**Status: RATIFIED DESIGN; implementation incomplete.**

---

## 15. Capability

**Capability** describes what a Person can offer or do.

A Person can have many capabilities.

Examples:

```text
Driver
 ├── City Taxi
 ├── Airport Transfer
 └── Intercity

Person
 ├── Welding
 ├── Metal Structures
 └── Repair
```

Role and Capability are different:

```text
Role = Driver
Capability = Airport Transfer
```

Capability must support evidence/provenance and confidence.

A capability is not automatically current availability.

```text
Capability ≠ Availability
```

Target conceptual data includes:

- capability type/description;
- source;
- confidence;
- confirmation;
- observed/updated time;
- optional validity/expiry.

**Status: TARGET CORE.**

---

## 16. Need

**Need** describes what a person is trying to accomplish.

Examples:

- «Мне нужен сварщик.»
- «Нужно отвезти маму в аэропорт.»
- «Нужен хороший юрист.»

Need is not automatically an Order.

A Taxi application may transform a Need into a Taxi Order.

```text
Need
  ↓
Taxi Order
```

**Status: TARGET CORE.**

---

## 17. Context

Context describes circumstances relevant to a specific person, need, relationship, or action.

Examples:

- where people met;
- who introduced them;
- location;
- timing;
- service requirements;
- previous relevant interaction;
- user-provided notes.

Context must be subject to privacy/visibility rules.

**Status: TARGET CORE.**

---

## 18. Evidence, Provenance, and Confidence

Every non-trivial derived fact must preserve where it came from and how reliable the identity/fact linkage is.

The source hierarchy is:

1. explicit user statement — confirmed for the stated fact;
2. existing PIOS transaction/interaction fact — confirmed for the relevant historical fact;
3. user-authorized structured external source — confirmed as source data, while identity linkage is scored separately;
4. AI inference from weak evidence — likely, not confirmed;
5. no evidence — unknown.

The following must be distinguished:

```text
SOURCE FACT
     ≠
IDENTITY LINKAGE
     ≠
SEMANTIC INFERENCE
```

Example:

```text
External profile says «Sergey»
        ↓
Source fact = confirmed
        ↓
Is this the same Sergey?
        ↓
Identity linkage = confidence score
```

User-visible states should be understandable, for example:

- 🟢 confirmed;
- 🟡 likely;
- ⚪ unknown.

AI must never silently promote a guess into a confirmed fact.

**Status: TARGET CORE / RATIFIED DESIGN PRINCIPLE.**

---

## 19. Opportunity

**Opportunity** is a candidate way to solve a Need using a suitable Capability under relevant context.

Conceptually:

```text
Need
 + Capability
 + Context
 + Availability
 + Eligibility
 + Evidence
       ↓
Opportunity
```

Relationship is not mandatory for an Opportunity.

Relationship is one factor that can affect relevance, trust, explanation, or preference when it exists.

This distinction is essential for zero-network users.

Example:

```text
Need: airport transfer
Capability: taxi
Availability: available
Context: Moscow airport
Relationship: none
       ↓
Valid Opportunity
```

And:

```text
Need: welding
Capability: welding
Relationship: direct
Evidence: confirmed
       ↓
Higher-context Opportunity
```

**Status: TARGET CORE.**

A generic Opportunity persistence/lifecycle should not be implemented until its exact boundaries are specified.

---

## 20. Action

Action is a product-level abstraction for something the user does or initiates:

- request;
- invite;
- offer;
- contact;
- call;
- message;
- recommend;
- save;
- order;
- accept.

A universal `Action` persistence entity is not required for v1.

Concrete workflows should remain owned by their bounded context.

**Status: PRODUCT ABSTRACTION; generic persistence is OPEN / NOT REQUIRED FOR V1.**

---

## 21. Transaction / Service Result

A Transaction represents a real economic/service outcome conceptually.

However, PIOS v1 must not prematurely replace mature Taxi models with a generic Transaction entity.

For Taxi:

```text
Order
 → Proposal
 → Assignment
 → Trip
```

remains the application-owned lifecycle.

Its completed facts can feed universal history/intelligence.

**Status: TARGET CONCEPT; no forced generic implementation in v1.**

---

## 22. History

History is the user-facing temporal view of factual events and completed interactions.

It is not a second mutable copy of every domain object.

History can derive from:

- invitations;
- relationships;
- orders;
- trips;
- transactions;
- relevant interactions;
- capability/context changes where useful.

```text
Domain facts
    ↓
History projections / queries
```

**Status: RATIFIED DESIGN; existing Taxi history remains protected. Universal aggregation is TARGET.**

---

## 23. Network

Network is the user's usable set of recognized relationships and associated context.

Product meaning:

> **«Мои люди»**

Network is not synonymous with:

- phonebook;
- every Person known to PIOS;
- every transaction partner;
- second-degree people;
- marketplace participants.

The technical persistence strategy for Network is deliberately not fixed here. It may be represented through relationship queries/projections/indexes as implementation requires.

**Status: PRODUCT TRUTH / TARGET TECHNICAL MODEL.**

---

# IV. CONTACT AND IDENTITY INTELLIGENCE

## 24. Contact Discovery

Contact discovery converts user-controlled external contact records into useful candidates for recognition.

Correct flow:

```text
Phone Contacts
      ↓
Normalize
      ↓
Match candidates
      ↓
Show context
      ↓
User recognizes person
      ↓
Add / invite / connect
      ↓
Relationship
```

PIOS must not automatically turn the entire phonebook into Relationships.

Contact import is user-initiated and selection remains explicit.

---

## 25. Progressive enrichment

PIOS must not require the user to classify hundreds of contacts manually.

Enrichment should happen when useful:

- user searches for a capability;
- user opens a person;
- a new interaction supplies context;
- user explicitly corrects or labels a person;
- an authorized integration supplies reliable context.

For a real contact export, lexical matches such as names containing «сварщик» are useful signals for pipeline validation but are not proof of current professional capability.

The observed real export contained 792 contact cards and 17 clear welding-related lexical matches. This demonstrates the product problem but does not constitute verified capability data.

---

## 26. External services and messaging applications

PIOS may eventually use authorized external profile/context sources where technically and legally supported.

PIOS must **not assume** it can automatically read private WhatsApp, Telegram, or other conversations.

The product boundary is:

> PIOS organizes and makes useful the user's relationships; it is not a surveillance system.

External enrichment requires:

- explicit authorization where required;
- supported APIs/access mechanisms;
- auditable source/provenance;
- privacy controls;
- independent identity-linkage confidence.

---

# V. NETWORK SEARCH

## 27. Search contract

A user should be able to express a need naturally:

> «Мне нужен сварщик.»

PIOS interprets the request into a Need, then performs deterministic network/opportunity search.

The search should prefer:

```text
1. Direct recognized people with confirmed relevant capabilities
2. Direct recognized people with likely relevant capabilities
3. Second-degree / network-of-network candidates
4. Broader PIOS opportunities
5. Marketplace/open opportunity sources where needed
```

The system must never silently mix confidence levels.

Results should explain why they are relevant.

Examples:

> «Виктор — ваш знакомый, вы работали вместе, сварка подтверждена.»

or:

> «Сергей, возможно занимается сваркой — найдено в вашем контакте; информация не подтверждена.»

---

## 28. Second-degree discovery

If no direct candidate exists, PIOS may search paths through recognized relationships.

Conceptually:

```text
User
  ↓
Known Person
  ↓
Connected / Introduced Person
  ↓
Capability
```

The output should be an introduction/recommendation path, not automatic access to another person's private network.

A second-degree relationship does not grant access to:

- private phone numbers;
- private notes;
- private chats;
- hidden relationships;
- unrelated personal data.

The product should say, in effect:

> «Через Рустама можно попробовать выйти на Сергея.»

rather than:

> «Вот вся адресная книга Рустама.»

---

# VI. USER EXPERIENCE CONTRACT

## 29. Primary navigation

A candidate universal navigation is:

```text
Главная
Люди
История
Профиль
```

Role-specific functionality such as Taxi business can appear as a contextual application layer:

```text
Мой бизнес
```

The exact visual navigation may evolve during implementation, but the information architecture must remain centered on people, actions, needs, and history rather than generic CRM administration.

---

## 30. Home

Home should begin with useful action rather than a CRM dashboard.

Core actions:

- **Мне нужно**
- **Я могу**
- **Найти**

A natural starting prompt can be:

> **«Что вам нужно?»**

For a returning user, Home can surface useful network context such as relevant people, recent activity, repeat opportunities, or unfinished actions.

---

## 31. My People

My People is not a raw list of all phone contacts.

It is the user's useful recognized network.

A person card should prioritize recognition:

- photo where authorized;
- name;
- why the user knows them;
- relevant capabilities;
- roles/organization;
- recent relevant history;
- authorized connected profiles;
- confidence/provenance where uncertainty matters;
- actions such as call, message, request, introduce, save, or update context.

---

## 32. «Кто это?»

The person experience should answer:

1. Who is this?
2. How do I know them?
3. What can they do?
4. What happened between us?
5. What can I do with them now?

This is the primary product expression of Network Intelligence.

---

## 33. «Мне нужно»

The user should be able to enter a natural-language need.

Example:

> «Мне нужно отвезти маму в аэропорт.»

PIOS may show an interpreted summary before execution when ambiguity exists.

The user should not need to understand the internal Need/Opportunity model.

---

## 34. «Я могу»

Any Person may express capabilities.

Example:

> «Я могу делать межгород и трансферы в аэропорт.»

The system may structure this into capabilities, preserving provenance and allowing correction.

This supports both:

- demand-side network use;
- supply-side personal business development.

---

# VII. TAXI APPLICATION CONTRACT

## 35. Taxi is a vertical, not the Core

Taxi is the first concrete application of PIOS.

Taxi-specific concepts remain inside Taxi/Application bounded contexts:

- Driver;
- Passenger;
- Order;
- Proposal;
- Assignment;
- Trip;
- vehicle/availability;
- Taxi Primary Driver;
- First Refusal;
- Dispatch;
- Taxi payment lifecycle.

They must consume Core concepts where appropriate without making the Core Taxi-specific.

---

## 36. Primary Driver

Primary Driver is a passenger-controlled Taxi preference.

It is:

- not driver-owned;
- not exclusive;
- not a guarantee of assignment;
- not a generic Relationship state.

Recommended invariant:

```text
isPrimary = true ⇒ isTrusted = true
```

Primary Driver is Taxi-specific.

**Status: RATIFIED / EXISTING.**

---

## 37. First Refusal

First Refusal is a Taxi routing behavior, not a Relationship state and not a separate Order type.

Target sequence:

```text
Taxi Order
   ↓
Relationship Resolution
   ↓
Routing Instruction
   ↓
First Refusal
   ↓
Proposal
   ↓
Assignment
   ↓
Trip
```

Routing instruction conceptually contains:

```text
orderId
candidateDriverId
mode = FIRST_REFUSAL
reason = PRIMARY_RELATIONSHIP
expiresAt
```

If the Primary Driver is unavailable, First Refusal is skipped and the order proceeds to the General Opportunity Flow.

If the Primary Driver declines or the proposal lapses, the order proceeds to the General Opportunity Flow.

Primary and General Opportunity proposals must not be sent simultaneously.

Changes to the Relationship or Primary Driver after routing starts must not rewrite an in-flight routing decision.

**Status: RATIFIED DESIGN; current implementation is partial and requires safe fallback/invariants.**

---

## 38. Taxi Connection models

The current system contains multiple connection concepts with different semantics.

### `network-management.Connection`

Directed social/network link between Persons.

**Target:** evolve toward universal Relationship infrastructure after semantic redesign.

### `passenger-experience.Connection`

Taxi-specific connection/provenance associated with the driver's personal invitation/link and passenger reference.

**Target:** remain Taxi-specific; map explicitly to Core Relationship where appropriate.

### `primary_connections`

Passenger's Taxi Primary Driver preference.

**Target:** remain Taxi-specific.

### `primary_driver_records`

Dispatch projection used to execute First Refusal.

**Target:** remain in Dispatch.

These models must not be merged merely because they all contain the word «connection».

---

# VIII. DRIVER PRODUCT CONTRACT

## 39. Driver with an existing customer base

Target journey:

```text
Driver joins PIOS
   ↓
«У вас уже есть свои клиенты?»
   ↓
Find people in contacts
   ↓
Explicitly select / invite
   ↓
Relationship
   ↓
Customers order through PIOS
   ↓
Repeat interactions
   ↓
Own customer network grows
```

PIOS does not take ownership of the driver's customer relationships.

The product goal is to replace operational friction of repeated direct calls with a structured channel while preserving the driver's network.

---

## 40. Driver with no customer base

The zero-network driver must be able to enter through general opportunities:

```text
New Driver
   ↓
No existing network
   ↓
General Opportunity Flow
   ↓
First completed service
   ↓
Passenger may save driver
   ↓
Relationship
   ↓
Repeat business
   ↓
Own network
```

This is essential to avoid making PIOS a closed club.

The driver proposition is:

> **«Получай заказы сегодня. Строй свой бизнес на завтра.»**

And the strategic positioning remains:

> **«В агрегаторе ты работаешь на платформу. В PIOS ты строишь свой бизнес, используя платформу.»**

---

# IX. PASSENGER PRODUCT CONTRACT

## 41. Passenger with existing network

The passenger should be able to:

- recognize trusted drivers/people from contacts;
- connect/invite them;
- designate Taxi-specific Primary Driver where appropriate;
- use First Refusal;
- fall back to General Opportunity Flow;
- save useful providers for future use.

---

## 42. Passenger with zero network

The passenger can still:

```text
Need
 ↓
General Opportunity Flow
 ↓
Service
 ↓
Save useful provider?
 ↓
Relationship
 ↓
Repeat
```

Again:

> **Network is created through value; it is not required before value.**

---

# X. AI CONTRACT

## 43. Role of AI

AI is a replaceable intelligence/interface layer over deterministic PIOS data and domain rules.

AI can:

- understand natural-language Needs;
- extract context from user input;
- help identify likely capabilities;
- summarize a Person;
- explain why a result is relevant;
- recommend network paths;
- help capture new context;
- orchestrate approved actions.

AI cannot be the source of truth for:

- identity;
- Relationship state;
- confirmed Capability;
- transaction state;
- payment state;
- dispatch invariants;
- privacy authorization.

Target architecture:

```text
PIOS DOMAIN DATA
       ↑
       │
AI interpretation / recommendation
       │
       ↓
User intent
       ↓
Deterministic domain/application command
```

The model provider must be replaceable.

---

# XI. PRIVACY AND SAFETY CONTRACT

## 44. Core privacy rules

1. Contact import is user-initiated.
2. Imported contacts are not automatically Relationships.
3. Private user notes remain private unless explicitly shared.
4. A user's possession of a contact record does not grant the right to expose another person's private information.
5. External profile enrichment requires supported access and appropriate authorization.
6. Identity linking must preserve confidence/provenance.
7. AI guesses must never silently become facts.
8. Second-degree network discovery must provide introductions/recommendations, not unrestricted access to another person's network.
9. PIOS must not become a surveillance system.
10. Trust/reputation must not become a paid hidden-ranking mechanism.

---

# XII. BUSINESS MODEL PRINCIPLES

## 45. Commercial direction

PIOS should not depend structurally on taking a percentage of every transaction.

The stronger long-term monetization direction is value-added services such as:

- business tools;
- AI/network intelligence;
- automation;
- premium network capabilities;
- B2B functionality;
- infrastructure/services;
- payments where PIOS adds genuine value.

The product must not sell:

- trust itself;
- fake reputation;
- paid personal-network priority;
- ownership of a user's relationships.

Taxi V1 may remain cash-first and commission-free according to existing product decisions.

---

# XIII. ARCHITECTURE BOUNDARY

## 46. Universal Core target

The universal Core should contain or define contracts for:

```text
Identity
Person
Profile
Relationship
Invitation
Capability
Need
Context
Evidence
Provenance
Confidence
Network Search
Opportunity
```

It should not contain Taxi workflows.

---

## 47. Taxi application target

Taxi retains:

```text
Driver
Passenger
Order
Proposal
Assignment
Trip
Availability
Primary Driver
First Refusal
Dispatch
Taxi payment lifecycle
```

The relationship between them is through explicit contracts/mappings/events rather than shared ownership of domain state.

---

## 48. Existing-to-target mapping

| Existing concept | Target disposition | Reason |
|---|---|---|
| `Identity` | **KEEP → DECOUPLE** | Existing authentication foundation; remove Taxi coupling only after safe replacement exists |
| `Identity.driverId` / `drv` | **TRANSITIONAL** | Current Taxi dependency; target is Person + Role |
| `network-management.Person` | **KEEP → EVOLVE** | Correct universal direction, but current model is scaffold |
| `network-management.Profile` | **KEEP → EVOLVE** | Correct universal direction |
| `network-management.Connection` | **EVOLVE → Relationship infrastructure** | Current semantics too narrow for final Relationship |
| `network-management.Invitation` | **KEEP → CORE** | Generic connection/invitation mechanism |
| `passenger-experience.Connection` | **KEEP IN TAXI** | Personal-link/provenance semantics are Taxi-specific |
| `primary_connections` | **KEEP IN TAXI** | Primary Driver is Taxi-specific |
| `primary_driver_records` | **KEEP IN DISPATCH** | Dispatch projection for First Refusal |
| `Order` | **KEEP IN TAXI** | Concrete Taxi request workflow |
| `Proposal` | **KEEP IN TAXI** | Concrete Taxi offer workflow |
| `Assignment` | **KEEP IN TAXI** | Concrete Taxi commitment |
| `Trip` | **KEEP IN TAXI** | Concrete Taxi service result |
| Taxi history | **KEEP / INTEGRATE** | Existing history is useful factual source |
| Generic `Transaction` | **TARGET CONCEPT, no forced migration** | Avoid premature generic abstraction |
| Generic `Action` entity | **NOT REQUIRED V1** | Keep as product abstraction |
| Generic `Network` persistence entity | **OPEN** | Product concept first; storage strategy follows implementation needs |
| Capability | **BUILD IN CORE** | Essential for network intelligence |
| Need | **BUILD IN CORE** | Essential for universal demand representation |
| Opportunity | **BUILD AFTER NEED + CAPABILITY** | Requires stable candidate/search semantics |
| AI orchestration | **BUILD AFTER deterministic contracts** | AI must not own domain truth |

---

# XIV. IMPLEMENTATION STATUS MODEL

## 49. Status vocabulary

Every future PIOS architecture document should classify decisions using:

### EXISTING
Already implemented in the repository/production system.

### RATIFIED
A product/architecture decision already accepted and intended to guide implementation.

### TARGET
Desired future architecture/product behavior that is not yet fully implemented.

### OPEN
Requires an explicit decision before implementation.

### REJECTED
A previously considered approach that must not be implemented.

This vocabulary prevents conceptual designs from being mistaken for current system facts.

---

# XV. IMPLEMENTATION ORDER

## 50. Phase 0 — production safety

Before Core integration work:

- isolate integration-test databases;
- ensure test configuration cannot resolve production databases;
- add safeguards that fail fast on production DB usage from test runtime;
- preserve current production configuration;
- verify no experimental Core code is deployed to production.

**No Network Core integration should proceed past this gate without this safety boundary.**

---

## 51. Phase 1 — identity resolution + Person

Build the canonical path:

```text
Contact Record
      ↓
Normalization
      ↓
Identity Candidate
      ↓
Person
```

Requirements:

- normalized phone/email matching;
- duplicate detection;
- no name-only auto-merge;
- explicit confidence;
- provenance;
- safe linkage to existing PIOS accounts.

---

## 52. Phase 2 — Relationship

Define and implement the universal Relationship semantic contract before connecting it to Taxi.

Must answer:

- who recognizes whom;
- directionality/mutuality;
- how relationship is created;
- lifecycle;
- provenance;
- visibility;
- trust dimensions;
- relationship context;
- relationship termination;
- interaction/history relationship.

Only after this should current Connection models be mapped.

---

## 53. Phase 3 — Contact Discovery + My People

Implement the first user-visible Core value:

```text
Contacts
 ↓
Candidates
 ↓
Recognize person
 ↓
Add / invite
 ↓
Relationship
 ↓
My People
```

This is the first universal PIOS slice.

It must not modify production Taxi behavior.

---

## 54. Phase 4 — Capability

Introduce:

- Capability;
- provenance;
- confidence;
- confirmation;
- progressive enrichment.

Do not attempt to classify all contacts at import time.

---

## 55. Phase 5 — Need + Network Search

Introduce natural-language Need input and deterministic search contracts.

Search order:

```text
Direct network
 ↓
Second-degree network
 ↓
PIOS opportunities
 ↓
Marketplace/open opportunity
```

Confidence and evidence must remain explicit.

---

## 56. Phase 6 — Opportunity

Only after Need and Capability semantics are stable, implement Opportunity resolution.

Opportunity must work for both:

- people with existing networks;
- people with zero networks.

---

## 57. Phase 7 — Taxi integration

Map Core relationships to existing Taxi concepts through an explicit integration layer.

Target:

```text
Core Relationship
       ↓
Taxi Primary Preference
       ↓
Dispatch projection
       ↓
First Refusal
```

Do not merge the existing Connection models blindly.

Before enabling a new First Refusal path in production, enforce existing critical invariants:

- one valid Assignment per accepted Order;
- no competing assignment after acceptance;
- atomic Proposal terminal transition against expiry/accept race;
- idempotent commands;
- safe RabbitMQ redelivery;
- reconciliation for failed Proposal → Assignment progression.

---

## 58. Phase 8 — History and Network Intelligence

Feed factual Taxi and other vertical events into Network Intelligence.

Examples:

> «Ты уже ездил с Виктором 8 раз.»

> «Сергей — человек из твоей сети; вы работали вместе.»

> «У тебя есть несколько клиентов, с которыми давно не было поездок.»

These are examples of intelligence over facts, not autonomous invention of relationship state.

---

## 59. Phase 9 — AI

Add AI after deterministic domain/search contracts are stable.

AI should provide:

- natural-language interpretation;
- contextual recall;
- recommendation explanations;
- assisted capture;
- orchestration of approved commands.

It must remain replaceable.

---

# XVI. NON-GOALS

## 60. PIOS Core v1 must not become

- a messenger;
- a social network;
- an automatic phonebook-to-network importer;
- a surveillance system;
- an AI-only product;
- a generic Taxi rewrite;
- a paid reputation marketplace;
- a hidden dispatch-ranking system;
- a forced CRM data-entry system;
- a closed network requiring existing relationships before value can be received.

---

# XVII. ACCEPTANCE PRINCIPLES

## 61. Product acceptance tests

A future PIOS Core implementation should satisfy the following conceptual tests.

### Test A — Existing network

A user with existing contacts can discover a known person, recognize them, establish/confirm a Relationship, and see them in My People without manually rebuilding a CRM record.

### Test B — Unknown contact

A user can encounter an unfamiliar number and, where authorized evidence exists, obtain useful context without PIOS fabricating facts.

### Test C — Capability search

A user can ask:

> «Мне нужен сварщик.»

and PIOS searches their recognized network before broader opportunity sources.

### Test D — Uncertain evidence

If evidence is weak, PIOS labels it as likely/unknown rather than presenting it as confirmed.

### Test E — Second degree

If no direct person is suitable, PIOS can suggest a network path/introduction without exposing private data.

### Test F — Zero network

A user with no relationships can still obtain an opportunity through the broader PIOS flow.

### Test G — Transaction ≠ relationship

A completed one-off transaction does not silently create a permanent Relationship.

### Test H — Taxi isolation

Taxi-specific concepts do not leak into universal Core semantics.

### Test I — AI separation

AI cannot directly redefine canonical identity, relationship, transaction, payment, or dispatch state.

### Test J — Production safety

Core tests cannot write to production databases.

---

# XVIII. FINAL PRODUCT CONTRACT

## 62. The canonical PIOS chain

The current best-supported universal chain is:

```text
IDENTITY
   ↓
PERSON
   ↓
PROFILE
   ↓
RELATIONSHIP
   ↓
CAPABILITY
   ↕
NEED
   ↓
SEARCH
   ↓
OPPORTUNITY
   ↓
ACTION
   ↓
RESULT / TRANSACTION
   ↓
HISTORY
   ↓
REPEAT / DEVELOPMENT
   ↓
MORE NETWORK UTILITY
```

AI operates across this chain as an interpretation/recommendation/orchestration layer.

Taxi operates as one application consuming this Core:

```text
PIOS CORE
   ↓
Taxi Relationship Context
   ↓
Primary Driver
   ↓
First Refusal
   ↓
Taxi Dispatch
   ↓
Trip
   ↓
History / Network Intelligence
```

---

## 63. The deepest product thesis

PIOS does not win by having more contacts than a phonebook, a larger marketplace than an aggregator, or a smarter model than an AI company.

Its defensible product direction is the combination of:

```text
Personal Identity Context
+
User-controlled Relationship Graph
+
Verified Interaction History
+
Capability Graph
+
Need / Opportunity context
+
Privacy controls
+
Progressive enrichment
+
Network development
```

The resulting product promise is:

> **PIOS помогает человеку строить, сохранять, использовать и развивать собственную сеть людей — и превращать отношения в реальные возможности, результаты и повторный бизнес.**

For the first commercial vertical:

> **«Получай заказы сегодня. Строй свой бизнес на завтра.»**

And the strategic position remains:

> **«В агрегаторе ты работаешь на платформу. В PIOS ты строишь свой бизнес, используя платформу.»**

---

# 64. Decisions that must remain explicit

The following are intentionally **not silently decided by this contract**:

1. Exact universal Relationship schema and lifecycle.
2. Exact directionality/mutuality rules for every relationship type.
3. Exact storage strategy for Network.
4. Exact Capability taxonomy.
5. Exact Need ontology.
6. Exact Opportunity lifecycle/persistence.
7. Exact second-degree privacy/consent mechanics.
8. Exact external-source integrations and their legal/API capabilities.
9. Exact reputation aggregation algorithm.
10. Exact monetization packaging.

These require focused ADR/product decisions before implementation where they materially affect the architecture.

---

# 65. Source-of-truth rule

When this document conflicts with an observed implementation fact, the implementation fact must be recorded as **EXISTING**, while this document defines the **TARGET** unless the target decision is explicitly revised.

When this document conflicts with a ratified ADR, the conflict must be resolved explicitly; developers must not silently choose one interpretation.

No production code should be changed solely because a concept appears in this document. Implementation requires a concrete technical task/ADR with bounded scope, tests, migration strategy, and production-safety assessment.
