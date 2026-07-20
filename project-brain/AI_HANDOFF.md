# AI Handoff — PIOS Project Brain

**Read this file first. Every time. Before touching code, docs, or answering a question about PIOS.**

You are an AI agent — possibly with no memory of any prior PIOS conversation — who has just been given access to this repository. This file is your entry point. It does not tell you what PIOS is (that is `PIOS_MASTER_CONTEXT.md`); it tells you how to find out safely, without inventing anything or silently overwriting history.

---

## START HERE — Compact Protocol

1. **Read the Project Brain, in this order**, before doing anything else:
   1. `AI_HANDOFF.md` (this file)
   2. `PIOS_MASTER_CONTEXT.md` — what PIOS is, why, for whom
   3. `PIOS_DECISION_LEDGER.md` — chronological history of what was actually decided
   4. `PIOS_PRODUCT_MODEL.md` — actors, flows, NOW/NEXT/LATER
   5. `PIOS_CURRENT_STATE.md` — what is actually built, verified against current code
   6. `PIOS_MVP_V01.md` — candidate next-build direction (not an authorization)
   7. `PIOS_OPEN_QUESTIONS.md` — what remains unresolved, and why
2. **Then, before claiming anything about implementation status**, independently inspect `backend/` yourself (Rule 4 below). Do not trust this Project Brain's own `PIOS_CURRENT_STATE.md` as permanently current — it is a snapshot as of the commit named at its top; code may have moved since.
3. **Then, and only then**, act on whatever task you were actually given.

---

## Authority Hierarchy (You Do Not Get to Reorder This)

This repository has its own, older, and higher authority than this Project Brain. In descending order:

```
docs/PROJECT_CONSTITUTION.md                    (highest — overrides everything)
    |
docs/PRODUCT_DECISION_*.md                       (Product Owner decisions)
    |
docs/ADR/ADR-*.md                                (Architecture Decision Records)
    |
docs/*_ARCHITECTURE.md, MODULE_STRUCTURE.md, etc. (Architecture layer)
    |
docs/DOMAIN_MODEL.md, EVENT_CATALOG.md            (Domain layer)
    |
docs/API_SPECIFICATION.md, INTERFACE_CONTRACTS.md (API layer)
    |
docs/DATABASE_DESIGN.md, LOGICAL_DATA_MODEL.md    (Database layer)
    |
backend/ (actual code)                            (Implementation)
```

**This Project Brain (`project-brain/`) is not in that hierarchy. It is a portable index and consolidation layer sitting alongside it, not above or below it.** Where this Project Brain summarizes or paraphrases something `docs/` already states, `docs/` is authoritative if the two ever diverge — treat any such divergence as a sign this Project Brain has gone stale, not as a sign `docs/` is wrong. Report the divergence; do not silently pick one.

**A separate, orthogonal category exists: Current Product Direction.** Some content in this Project Brain (marked `[CURRENT DIRECTION]` or `[HYPOTHESIS]` throughout) is newer product thinking supplied directly by the Product Owner, that does **not yet** appear in any `docs/PRODUCT_DECISION_*.md` or ADR. It sits *outside* the hierarchy above entirely — it is not yet Constitutional, not yet a Product Decision, not architecture. Never treat it as if it had passed through the process the hierarchy above requires. See "Prohibition on Silently Promoting Direction" below.

---

## How to Resolve a Conflict

1. Higher layer in the hierarchy above always wins over a lower one.
2. Within the same layer, a later-dated, still-`Status: Decided`/`Status: Proposed`/not-superseded document wins over an earlier one — but **an ADR is never implicitly superseded by silence**; it must be explicitly marked superseded (PROJECT_CONSTITUTION.md Section 7). If you cannot find an explicit supersession, assume both still hold and report the tension rather than picking one.
3. `[CURRENT DIRECTION]` or `[HYPOTHESIS]` content **never** outranks anything in the hierarchy, regardless of how recent or how emphatically stated. It can motivate a *future* Product Decision or ADR; it is not one itself.
4. If you find two genuinely authoritative sources that disagree and neither has been superseded, this is a `[CONFLICT / NEEDS DECISION]` — say so explicitly, to the human you're working with, and stop rather than picking a side. Do not resolve it by inventing a tiebreaker.

---

## Non-Negotiable Rules

- **Inspect actual code before claiming implementation status.** Never say "X is implemented" or "X works" because a document (including this Project Brain) says so. Grep, read, and run the actual `backend/` code, or state plainly that you have not verified it. `PIOS_CURRENT_STATE.md` shows you how this was done as of its own commit — repeat the method, don't just trust the conclusion, if any time has passed or any commit has landed since.
- **Distinguish Product Direction from Ratified Product Decision, always.** "The Product Owner wants X" and "X is a ratified Product Decision" are different facts with different authority. Every claim in this Project Brain is tagged with which one it is (see tag legend below). Preserve that tagging in anything you write; do not flatten it.
- **Never silently fill an `[OPEN]` decision.** If `PIOS_OPEN_QUESTIONS.md` or any `docs/PRODUCT_DECISION_*.md` marks something open, it stays open until a human with Product Owner authority resolves it and records that resolution as a new or amended Product Decision (PROJECT_CONSTITUTION.md Section 7, Section 9). You may *describe* candidate answers already on record; you may not *choose* one on the project's behalf.
- **Never rewrite history.** `PIOS_DECISION_LEDGER.md` is chronological and additive. When something is superseded, reframed, or narrowed, the ledger records that as a new entry pointing back to what it changed — it does not delete or silently edit the earlier entry, and neither does any ADR (Constitution Section 7: "ADRs are never deleted").
- **Preserve traceability.** Every non-trivial claim you add anywhere in this repository should be traceable to a specific document, ADR, commit, or explicit human instruction — never to "it seemed right" or to an earlier AI's own unstated inference.
- **Follow `.ai/EXECUTION_PROTOCOL.md` and `CLAUDE.md`** for how work in this repository is actually executed (scope discipline, documentation-before-implementation, when to stop and ask). This Project Brain explains *what the project is*; those two files explain *how to work in it*.

---

## Tag Legend (Used Throughout `project-brain/`)

| Tag | Meaning |
| --- | --- |
| `[RATIFIED]` | Explicitly approved by an authoritative repository document (Constitution, Product Decision, ADR) or directly observable in current code. |
| `[CURRENT CODE]` | Actually implemented in current `backend/` HEAD, independently verified. |
| `[CURRENT DIRECTION]` | Latest intended product direction from the Product Owner, not yet ratified into an authoritative document. |
| `[HYPOTHESIS]` | Something intended to be tested or still genuinely uncertain — including most of the Network Pilot's own H1/H2. |
| `[OPEN]` | A decision that has not been made by anyone with the authority to make it. |
| `[SUPERSEDED]` | An older interpretation or direction later narrowed, expanded, or replaced — the earlier entry is kept, not deleted. |
| `[DEFERRED]` | Intentionally postponed, with a stated reason, not simply unbuilt. |
| `[CONFLICT / NEEDS DECISION]` | Two authoritative or current sources genuinely disagree; a human decision is required. |

---

## What This Project Brain Deliberately Does Not Contain

- **No chat archive.** The raw conversation history that produced this Project Brain is not available to whoever wrote it and is not fabricated here. If you need it, ask the human; do not invent a plausible-sounding summary of a conversation you cannot see.
- **No implementation authorization.** Nothing in `PIOS_MVP_V01.md` or anywhere else in this directory authorizes writing code. Every implementation task in this repository still requires its own explicit commission, per `.ai/EXECUTION_PROTOCOL.md`.
- **No resolved business rules beyond what `docs/` already ratifies.** Where this Project Brain describes a candidate mechanism (an invitation system, a team/network model, an electronic-dispatcher MVP flow), it is describing Product Owner direction or a hypothesis, not a rule you may build against without a real Product Decision or ADR first.

---

## Where To Go Next

- Building something? Read `PIOS_CURRENT_STATE.md` and `PIOS_MVP_V01.md`, then stop and get explicit task authorization — this file and its siblings are context, not a work order.
- Asked "what is PIOS?" — answer from `PIOS_MASTER_CONTEXT.md`, not from your own priors or from the most recent thing said to you.
- Asked "what's already decided about X?" — check `PIOS_DECISION_LEDGER.md` first, then the cited `docs/` source directly; never answer from memory of a conversation you cannot see.
- Asked "can we build X?" — check `PIOS_OPEN_QUESTIONS.md` for whether X depends on something still open, before answering.
