# PIOS Product Decision: Personal Network Value Distribution v1.0

Status: Decided (in this specific scope) — Product Owner-authority decision (`PROJECT_CONSTITUTION.md` Section 7). Recorded 2026-07-29. **File intentionally uncommitted** pending architect (ChatGPT) review, consistent with every other draft artifact produced this session (see `docs/SPRINT_9_PROPOSAL_PERSONAL_NETWORK_ACTIVATION.md`, `docs/ARCHITECTURE_VERIFICATION_REPORT.md`).

This document resolves **exactly** the product-owner statement made in conversation on 2026-07-29, quoted verbatim in Section 1, against the specific open questions it actually answers in `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8, `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 8, and `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 6. It does not answer every open question those documents list, and does not itself authorize any ADR, code, or implementation. Following the same evidence-grading discipline those documents already established:

- **[RATIFIED]** — stated or directly implied by an already-approved document.
- **[DECIDED HERE]** — resolved by this specific product-owner statement, quoted inline.
- **[STILL OPEN]** — not resolved by this statement, explicitly not filled in.

## 1. Source

Product Owner statement, 2026-07-29, quoted in full:

> «PIOS не должен использовать модель владения клиентом. Клиент не является собственностью водителя. Система должна сохранять происхождение связи (кто пригласил), но ценность должна распределяться по фактическому вкладу участников. Первый пригласивший получает признание и приоритет связи, но исполнитель поездки получает оплату. Каждый участник сети может создавать собственные связи.»

## 2. What this decides

### 2.1 Ownership model — **[DECIDED HERE, extending a RATIFIED principle]**

PIOS does not use a client-ownership model. A client is not the property of a driver.

This extends, rather than introduces, `PROJECT_CONSTITUTION.md` Section 3/18's existing **Relationship Protection** law: *"Durable, recognized relationships between a driver and the passengers or corporate customers they recurrently serve are a first-class product concern, never owned or captured by **the platform**..."* **[RATIFIED]**. That law addresses the platform's own relationship to the data. It does not, on its own, say whether a *driver* may treat a client as exclusive property in a network sense. Today's statement closes that specific gap: ownership is excluded both ways — the platform does not own it, and a driver does not own it either.

This also directly answers `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1's earlier **[RATIFIED — it is NOT ownership]** finding, reused and confirmed, not contradicted.

### 2.2 Origin must be preserved — **[DECIDED HERE]**

The system must retain who originated a connection ("происхождение связи — кто пригласил"). This answers part of `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8's first open question ("How is the relationship created — declared by the driver, by the passenger, mutually, or inferred?") only to this extent: **whoever issued the invitation is the recorded origin.** It does not decide confirmation (must both sides agree?), expiration, or delegation — those remain **[STILL OPEN]**, per Part 8.

### 2.3 Value follows contribution, not origin — **[DECIDED HERE]**

"Ценность должна распределяться по фактическому вкладу участников" — value is distributed by actual contribution, not by who originated the chain.

### 2.4 Recognition and "connection priority" go to the first inviter — **[DECIDED HERE, mechanism STILL OPEN]**

The first inviter receives **recognition** and **priority of the connection** ("признание и приоритет связи"). Recognition is a clear, attribution-only claim: the system should record and surface who originated a relationship.

**"Priority of the connection" is decided only as a named concept, not as a mechanism.** This document does **not** decide:

- Whether "priority of connection" means anything inside Dispatch's assignment decision at all, or is purely a network-graph/attribution property with no effect on order routing.
- If it does affect assignment: whether it is exclusivity, first-refusal, a ranking weight, or something else.
- How it interacts with Fair Dispatch (`PROJECT_CONSTITUTION.md` Section 3) if it ever does reach Dispatch.

This is a deliberate, not an accidental, gap. Three separate, already-open questions in the existing decision chain each ask a version of this and remain open:

- `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Section 4/8: *"Whether a Personal-Client-directed order is routed through Dispatch's assignment decision at all... Personal Client priority rules — whether, when, and how a personal-client relationship takes precedence over shared dispatch."* **[STILL OPEN]**
- `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 6: *"Does Personal Client Relationship Influence Opportunity Priority? [OPEN] — unchanged, not resolved here."* **[STILL OPEN]**
- `ADR-034` Part 2: Personal Client Relationship remains a **future candidate input**, never a ratified one, to the Assignment Policy port. **[STILL OPEN]**

Today's statement gives those three open questions a *name* ("priority of connection") and a *principle* (it belongs to the first inviter, not the platform or the executor) but not an *answer* to whether or how that name ever reaches an actual Assignment Policy implementation. A future ADR extending ADR-034 would still need to decide that, informed by this document, not replaced by it.

### 2.5 Recognition/priority is separate from payment — **[DECIDED HERE]**

"Исполнитель поездки получает оплату" — whoever actually performs a given ride is paid for that ride, regardless of who originated the connection that led to it. This cleanly separates two things the earlier documents were careful not to conflate:

- **Attribution/priority** (who gets recognized, and whatever "priority of connection" eventually turns out to mean) — governed by Section 2.4 above.
- **Payment for service actually rendered** — always goes to the executing driver.

This is consistent with, and does not touch, `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8's separate, already-ratified constraint that payment to PIOS may never purchase priority — a different mechanism (buying rank) from what this document addresses (who gets paid for a ride they drove).

### 2.6 Network participation is not driver-exclusive — **[DECIDED HERE, with an architectural implication flagged, not resolved]**

"Каждый участник сети может создавать собственные связи" — any participant in the network, not only a driver, may create their own connections (e.g., Регина, once connected, may herself invite her mother).

**This has a direct architectural implication this document does not resolve, but must flag clearly**, because it changes which existing concept the whole scenario actually maps to:

- `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 restricts participants to exactly **Driver** and **Passenger/Corporate Customer**, jointly owned by Driver Management and Passenger Experience — a two-role, driver-centered model.
- A network where *any* participant can originate further connections is a general graph, not a two-role pair — which is exactly what `network-management`'s already-implemented, currently-unused `Person`/`Profile`/`Connection`/`Invitation` model was built for (ADR-037: *"`Person` is not a merger of `Driver` and `Passenger`; it is a new, independent identity concept"*).

**Implication, stated as an open architecture question, not decided here:** this statement suggests the scenario in `docs/SPRINT_9_PROPOSAL_PERSONAL_NETWORK_ACTIVATION.md` §4.2 belongs more naturally to `network-management`'s existing (but dormant) model than to Passenger Experience's `Connection` or to Personal Client Relationship. That module/ownership choice is exactly the kind of decision ADR-037's own Consequences section reserved for a future ADR — this document does not make that choice, only surfaces that today's product-owner statement points toward it.

## 3. What this does NOT decide (explicitly carried forward as open)

Everything from `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 8 not addressed above remains open:

- Must both sides confirm a connection for it to be recognized?
- Can it expire from inactivity?
- Can it be exclusive (precluding the same passenger from a personal-client relationship with another driver)?
- Can it be delegated (e.g., to a Fleet)?
- The exact mechanism of "priority of connection" (Section 2.4 above) — named, not designed.
- Whether Dispatch gains any ratified contract to this information at all — `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2's *"Dispatch. No ratified role at all"* is unchanged by this document.
- Which module (`network-management` vs. an extension of the existing Driver Management/Passenger Experience joint model) owns this concept — Section 2.6 above only surfaces the question.

## 4. Effect on `docs/SPRINT_9_PROPOSAL_PERSONAL_NETWORK_ACTIVATION.md`

Blocker #3 in that document's Section 5 ("Артур получает приоритет requires a Product Owner decision") is **now partially resolved**: the ownership model (2.1), origin tracking (2.2), value-vs-contribution principle (2.3), and payment-vs-recognition split (2.5) are decided. The *mechanism* of "priority of connection" — the part that would actually change Dispatch's behavior — remains open per Section 2.4 above, so Sprint 9 Section 4.3 still cannot proceed to an implementation plan without a further decision on that specific mechanism, and Blockers #1 (evidence log), #2 (ADR extending ADR-037), #4 (Dispatch's missing contract), and now the module-ownership question (Section 2.6 above) all remain open exactly as that document already stated.

## Traceability

| Claim | Source |
|---|---|
| Relationship Protection law (platform does not own relationships) | `PROJECT_CONSTITUTION.md` Sections 3, 18 |
| Personal Client Relationship is not ownership | `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 1 |
| PCR-as-priority-input remains open in three independent places | `PRODUCT_DECISION_DISPATCH_PHILOSOPHY.md` Sections 4, 8; `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 6; ADR-034 Part 2 |
| Payment may never purchase priority (distinct mechanism, not contradicted) | `PRODUCT_DECISION_FAIR_OPPORTUNITY_POLICY.md` Section 8 |
| Dispatch has no ratified contract to this information | `PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md` Part 2 |
| `network-management`'s generic Person/Connection model, and that cross-module wiring needs its own ADR | `docs/ADR/ADR-037-Network-Management-Module-Bounded-Context-Extension.md` |

Where this document finds no evidence in the product-owner statement itself, it states so explicitly rather than filling the gap; resolution of any remaining open item (Section 3) requires either a further Product Owner decision (business rule) or an ADR (architecture/module ownership), per `PROJECT_CONSTITUTION.md` Section 7, before any implementation task may act on it.
