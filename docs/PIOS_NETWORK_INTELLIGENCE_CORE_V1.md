# PIOS Network Intelligence Core v1

## Purpose

Turn a user's existing contacts into a progressively useful personal network without requiring manual CRM-style enrichment of every person.

The core user promise is:

> PIOS remembers people, understands what is known about them, and helps the user find the right person from their own network when a real need appears.

## Product rule

Do not treat the phonebook as the network. The phonebook is an input source. A PIOS Relationship is created only by explicit user recognition, an accepted connection/invitation, or a product flow that explicitly asks the user to save/recognize the person for future interaction.

A transaction or interaction is historical evidence; it does not automatically create a permanent Relationship.

Do not fabricate professional attributes. Every inferred attribute must carry provenance and confidence.

## Canonical model

- Person: stable individual reference.
- Profile: presentation/contact representations.
- Relationship: user-recognized relationship; multidimensional, not a linear state machine.
- Capability: what a person can do/offer; many per person.
- Need: what the user needs now.
- Opportunity: a candidate produced from Need + Capability + Context + Availability + Eligibility + Evidence; Relationship may affect relevance/preference when present.
- History: derived from factual interactions/transactions; not mutable relationship state.
- Source: where a fact came from.
- Confidence: confirmed / likely / unknown.

## Source hierarchy

1. Explicit user statement: confirmed.
2. Existing PIOS transaction/interaction fact: confirmed for the relevant fact.
3. User-authorized structured external source: confirmed as source data, but identity linkage must be separately scored.
4. AI inference from weak evidence: likely only; never promoted to confirmed without confirmation.
5. No evidence: unknown.

## Contact enrichment strategy

### Cold import

Import contact records and normalize names, phones, email, organization, title, notes, URLs and available profile identifiers. Deduplicate by normalized phone/email first; name-only matches are suggestions, not automatic merges.

Create Person candidates without automatically creating Relationships.

### Progressive enrichment

Do not ask the user to classify all imported contacts. Enrich a person when:

- the user searches for a capability;
- the user opens a person;
- a new interaction supplies context;
- the user explicitly corrects/labels a person;
- an authorized integration supplies reliable context.

### Natural-language capture

When a user adds a person, accept free-form context such as:

> "Сергей, строитель, познакомились через Рустама, занимается металлоконструкциями."

Extract structured facts while preserving the original source/context.

## Network search

For a Need such as `Мне нужен сварщик` search in this order:

1. Confirmed capabilities among the user's recognized people.
2. Likely capabilities among recognized people, clearly labeled as likely.
3. People in the user's extended network who may satisfy the need.
4. PIOS opportunity/marketplace search only when the personal network is insufficient.

Network-first is a preference for relevance and continuity, not a requirement to exhaustively scan every contact before returning useful results.

Never silently mix confidence levels.

## Second-degree discovery

If no direct candidate exists, search relationships of recognized people for a suitable capability. Return the path:

`User -> Known Person -> Introduced/known Person -> Capability`

The system should offer an introduction/request action rather than implying an existing direct relationship.

Do not expose another user's private address book, hidden relationships, phone numbers, or protected profile data merely because a path exists.

## Person card

A useful person card should prioritize recognition over CRM fields:

- photo when available from an authorized source;
- name;
- why the user knows the person;
- capabilities;
- organizations/roles;
- recent relevant history;
- connected profiles that are authorized and confidently linked;
- confidence/provenance for uncertain facts;
- direct actions: call, message, request, introduce, save/update context.

## AI interaction examples

- `Кто такой Сергей?`
- `Мне нужны сварщики.`
- `Кто у меня связан со строительством?`
- `Кто может знать хорошего юриста?`
- `Кому я могу предложить эту работу?`
- `Запомни: Сергей — строитель, познакомились через Рустама.`

The AI layer is an interpreter/orchestrator over canonical PIOS data. It is replaceable and must not become the source of truth.

## Real-contact baseline from 2026-09-10 test export

A real iPhone vCard export contained 792 contact cards. A deterministic first-pass lexical analysis found clear professional signals in several groups, including 17 welding-related contacts. This is useful for validating the import/indexing pipeline, but lexical matches are not equivalent to verified capabilities.

The test also demonstrates the central problem: most cards contain little or no professional context. Therefore the product must rely on progressive enrichment rather than one-time bulk classification.

## UX principle: magic through action

The first useful experience should not be "organize your contacts". It should be a task:

> `Мне нужен сварщик`

PIOS should immediately search the user's network and explain why each result is relevant. Every clarification the user gives becomes reusable network context.

Old contacts: recover memory when needed.

New contacts: prevent future context loss at creation time.

## Safety/privacy requirements

- Contact import is user-initiated.
- Do not automatically publish imported contacts into the PIOS network.
- Do not expose another person's private data merely because it exists in a user's address book.
- External profile enrichment requires explicit authorization and supported access.
- Identity linking across services must have an auditable confidence/provenance record.
- AI-generated guesses must never silently become facts.

## Implementation sequence

1. Canonical Person/Relationship/Capability data contract.
2. Contact import + normalization + deduplication pipeline.
3. Capability index with provenance/confidence.
4. Person card API/UI.
5. Natural-language Need endpoint and network-first search.
6. Progressive enrichment and confirmation actions.
7. Second-degree network discovery.
8. Integrate the existing Taxi relationship/First Refusal flow through an explicit mapping layer; do not merge existing Connection models blindly.
9. Add transaction/history signals to Network Intelligence.
10. Add AI orchestration after deterministic domain/search contracts are stable.

## Non-goals for v1

- Building a messenger.
- Automatically scraping private WhatsApp/Telegram conversations.
- Automatically importing the entire phonebook as PIOS relationships.
- Guessing professions from names.
- Building a generic social network.
- Replacing the existing Taxi dispatch lifecycle.
