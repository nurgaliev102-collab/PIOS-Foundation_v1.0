# PIOS Product Decision: Opportunity Before Assignment v1.0

Status: Decided — Product Owner-authority decision (PROJECT_CONSTITUTION.md Section 7). This document ratifies the existence of a business fact distinct from Assignment, without deciding its architectural form. It is not an ADR, not a domain model change, and authorizes no aggregate, entity, event, or implementation.

Derived from PROJECT_CONSTITUTION.md, DOMAIN_MODEL.md, and PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md, following an architecture research process ("Domain Analysis — Is Assignment the Correct Aggregate Before Driver Acceptance?") that examined DOMAIN_MODEL.md's own wording and the conclusions PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md had already reached.

---

## Ratified

Между выбором водителя системой и возникновением обязательства водителя существует самостоятельный бизнес-факт.

Этот факт является частью предметной области PIOS и считается официально признанным.

## Deliberately NOT Ratified

Настоящим решением не утверждается, каким образом данный бизнес-факт должен быть представлен в доменной модели.

В частности, настоящим решением не принимается никаких выводов о том, что этот факт обязан быть:

- отдельным Aggregate;
- отдельной Entity;
- Opportunity;
- Domain Event;
- расширением Assignment;
- либо любой другой архитектурной конструкцией.

Это решение сознательно откладывается.

## Rationale

Настоящее Product Decision определяет исключительно бизнес-семантику.

Выбор архитектурной формы относится к компетенции архитектуры системы и должен приниматься отдельно после анализа возможных вариантов моделирования.

## Status Update

Архитектурная форма этого факта впоследствии определена [ADR-035: Pre-Commitment Business Fact — Aggregate Boundary](ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md): отдельный Aggregate Root внутри Dispatch, без изменения границ владения ADR-002/005/009/019. Название, identity, lifecycle, states, invariants, commands, events и repository contract этого агрегата остаются нератифицированными в точности так, как установлено выше.

## Files Changed

`docs/PRODUCT_DECISION_OPPORTUNITY_BEFORE_ASSIGNMENT.md` (new). `docs/README.md` receives one minimal traceability pointer under its existing "Product" heading, consistent with the precedent set for every preceding Product Decision. `docs/ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md` (new, separate decision) records the architectural form this document deliberately leaves open.

## References

- [PROJECT_CONSTITUTION.md](PROJECT_CONSTITUTION.md), Section 7 (Product Owner authority)
- [DOMAIN_MODEL.md](DOMAIN_MODEL.md), Section 4 (Assignment), Section 6 (Acceptance)
- [PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md](PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md), Section 8, Section 10, Unresolved Decisions #1
- [ADR-035: Pre-Commitment Business Fact — Aggregate Boundary](ADR/ADR-035-Pre-Commitment-Business-Fact-Aggregate-Boundary.md)
