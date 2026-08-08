# PIOS Product Decision: Circle of Trust v1.0

Status: Decided — Product Owner authority (PROJECT_CONSTITUTION.md Section 7), 2026-08-08. This document resolves specific open questions [PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md](PRODUCT_DECISION_PERSONAL_CLIENT_RELATIONSHIP.md) ("PCR") Part 8 left unresolved, for pilot scope only. It does not redefine PCR itself (Part 1–7 of that document stand unchanged), does not touch Assignment Policy (ADR-034), and does not give Dispatch any new role.

## Why now

PCR Part 8 lists eight open questions and states plainly: *"resolution of any Part 8 question requires a Product Owner decision... before any lower-authority document — including a future Assignment Policy implementation — may act on it."* The pilot ("MY BUSINESS + CIRCLE OF TRUST" Sprint) needs four of those eight answered to let a passenger see and manage more than one recognized driver relationship. This document answers exactly those four, in the Product Owner's own words, and leaves the rest exactly as open as PCR left them.

## Decisions (resolving PCR Part 8)

1. **"Can multiple drivers hold a personal-client relationship with the same passenger?"** — **Yes.** A passenger's set of recognized drivers is their **circle of trust**. No cap is set for the pilot.
2. **"Can it be exclusive (precluding a relationship with another driver)?"** — **No.** Holding a relationship with one driver never precludes another. Exactly one relationship may additionally be marked **primary** at any time — primary is a passenger-chosen designation *within* the circle, not exclusivity of the relationship itself.
3. **"How is the relationship created?"** — **By the passenger's own explicit action only** (opening a driver's invitation link and confirming, or an equivalent explicit "add" action). It is never inferred from completing a ride with a driver, and never created or changed by the driver's own action (a driver cannot make themselves anyone's primary or add themselves to a circle — see Rule 7 below).
4. **"Can it be delegated — for example, to a Fleet?"** — **Not resolved here.** PCR Part 8 itself notes Fleet "has no ratified domain owner yet." This Sprint deliberately does not build any driver-team/fallback-delegation mechanism (see the accompanying ADR's own explicit exclusion). A future Sprint proposing one must first resolve this question as its own Product Decision.

The remaining PCR Part 8 questions (confirmation by both sides, expiration from inactivity, and Dispatch-assignment weight) are **not** resolved by this document and remain exactly as open as PCR left them. In particular:

## Explicit non-decision: Dispatch stays uninvolved

PCR Part 2 already establishes Dispatch has **[RATIFIED absence]** of any role regarding Personal Client Relationship. This document does not change that. The circle of trust is read and written entirely within Passenger Experience (see the accompanying ADR for the technical placement); Dispatch's `POST /v1/proposals` continues to receive a single `driverId` chosen by the frontend exactly as it does today (`RideRequest.tsx` already sends `driverId: driverCode` from the invitation-link route param) — the circle of trust only changes *which driver the passenger's own client offers and remembers*, never anything Dispatch itself decides or is told about the relationship.

## Business rules ratified by this document

Restated from the Sprint brief, now carrying Product Owner authority as ratified product rules (not implementation detail):

- A passenger always freely chooses and changes their own primary driver; only the passenger's own explicit action changes it.
- Completing a ride with a non-primary trusted driver never changes who is primary, and never on its own creates a new circle-of-trust membership beyond what the passenger already explicitly holds.
- A driver cannot make themselves anyone's primary, and cannot add themselves to a passenger's circle of trust.
- A passenger may remove any driver (primary or not) from their circle of trust at any time.
- A specific ride's chosen driver, the driver who actually completed a ride, the passenger's primary driver, and (if ever built) a driver's team membership are four distinct facts and must never be collapsed into one another in code or in UI copy.

## Files Changed

`docs/PRODUCT_DECISION_CIRCLE_OF_TRUST.md` (new). No code, database, event, or API is created or modified by this document itself — implementation follows in the accompanying ADR and Sprint changes.

## Traceability

| Decision | PCR Part 8 question resolved |
| --- | --- |
| Multiple drivers per passenger | "Can multiple drivers hold a personal-client relationship with the same passenger?" |
| Non-exclusive + optional primary | "Can it be exclusive?" |
| Passenger-explicit-action-only creation | "How is the relationship created?" |
| Fleet delegation deliberately deferred | "Can it be delegated — for example, to a Fleet?" |
