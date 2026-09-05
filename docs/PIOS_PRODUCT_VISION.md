# PIOS — Product Vision & Context

**Status:** Foundational context document. Authored by the product owner, 2026-09-05, explicitly to be carried forward into every future session (human or AI) working on this codebase — not derivable from the code alone.

**Purpose:** Every other document in `docs/` describes *what* PIOS does or *how* a specific decision was made. This document describes *why PIOS exists at all* — the intent a purely technical read of the codebase cannot recover on its own. Read this alongside `CLAUDE.md` before making any product- or architecture-level judgment call, not only an implementation one.

---

## 1. What PIOS is

**PIOS is not another taxi aggregator.**

PIOS is a platform that connects a client and a service provider **directly**, and lets the provider gradually build **their own business and their own client network**, using PIOS as infrastructure.

For taxi, the first provider is the driver.

The core idea:

> **On an aggregator, you work for the platform.
> On PIOS, you build your own business, using the platform.**

This is the product's defining difference from everything it superficially resembles.

## 2. What we are explicitly NOT building

PIOS must not become a copy of Uber, Yandex Go, Bolt, a classic taxi aggregator, or BlaBlaCar.

We may borrow the best UX patterns and mechanics from existing products, but not their business model wholesale.

BlaBlaCar is a useful reference in one specific sense: a user can find and interact with a *specific* person/driver, rather than always drawing from an anonymous pool. But **PIOS is not BlaBlaCar**.

## 3. The core user principle

A driver should not just get the ability to receive random orders. They should be able to:

1. register;
2. get their own identity within PIOS;
3. get their own link/entry point for clients;
4. invite their own regular clients;
5. receive orders from them;
6. accept or decline requests;
7. complete rides;
8. build their own client base;
9. bring those clients back again.

This produces a cycle:

**Driver → own link → client → order → ride → returning client → next order.**

This cycle is the foundation of PIOS's entrepreneurial model.

## 4. Why this matters

In a traditional aggregator: **the client belongs to the platform.** The driver receives an order from the platform, completes it, gets paid. The platform controls the relationship between the two sides and typically takes a commission.

PIOS inverts this: **the driver builds the relationship with the client themselves.** PIOS supplies the infrastructure — identity, discovery/connection, requests, dispatch, ride management, statuses, history, client-acquisition tools, trust, a communication layer, and eventually commercial infrastructure.

So PIOS must create **value for the entrepreneur**, not just optimize order distribution.

## 5. The first market: taxi

Taxi is the first practical vertical — not because the end goal is to build yet another taxi service, but because it is a good *first* market: the base transaction is unambiguous —

**Passenger → Order → Driver → Assignment → Ride → Complete.**

This lets the platform's fundamental mechanics be validated against real, physical interaction before generalizing.

## 6. The base ride model (already working)

Already built and confirmed end-to-end:

passenger creates an order → driver sees the request → accepts it → is assigned to the ride → arrives → starts the ride → completes the ride.

PIOS is already able to execute a real ride-state chain from order to completion — not just a UI demo. This is the foundation.

## 7. Roles

### Passenger

The simplest possible path: **open PIOS → find the right driver → create an order → wait for acceptance → take the ride.** Do not make the passenger learn a complex system.

### Driver

For the driver, PIOS must be much more than "an orders screen." The driver's own screen must answer four questions, **in this order**:

1. **Am I working right now?** — AVAILABLE / OFFLINE
2. **Do I have work?** — a new request must be immediately noticeable
3. **Do I have an active ride?** — it must carry maximum visual priority
4. **How am I growing my own business?** — own link, invitations, and client network must be part of the product

The sequencing matters: **the driver's current work comes first; business tools come after.** This is exactly why DriverHome was reordered to: **Availability → current orders/active ride → business/growth → QR/invite → install → passengers.**

## 8. UX principle

PIOS must be simple for someone who is physically **in the middle of a ride**, not sitting at a desk — especially the driver. So: minimum cognitive load, unambiguous states, large touch targets, minimum actions, an active ride always outranks secondary information, and state must be legible without studying the interface.

Examples:
- **OPEN** → "Ожидает вашего решения"
- **ACCEPTED / ACTIVE** → active ride
- **COMPLETE** → "Поездка завершена"

We deliberately moved away from visual chaos and must not drift back into it.

## 9. The entrepreneurial model

One of the most important elements of PIOS. The driver should not perceive themselves as a PIOS employee or an aggregator's resource — they are an **independent provider/entrepreneur**. PIOS helps them: acquire clients, retain clients, get repeat orders, manage relationships, do the work, and gradually grow their own client base.

The QR/invite mechanism is therefore **not** a decorative growth card — it is a core part of the product model. But it must never outrank the driver's active work (Section 7).

## 10. The wider vision

Taxi is only the first vertical. Long-term, PIOS is a **platform for entrepreneurs and providers across different industries**:

**Entrepreneur/provider ↕ PIOS ↕ their clients / other entrepreneurs / suppliers / partners**

PIOS potentially becomes a **network of trusted participants** who can find each other and transact.

## 11. Beyond a client list

Longer-term, PIOS's value must extend past "here's my link, order from me." A stronger model: **a PIOS participant gets their own economic identity and network of relationships.** They can find clients, find providers, find suppliers, negotiate, get special terms, offer special terms, exchange value, and build recurring commercial relationships — e.g. one entrepreneur extending another a discount or special service because they're both part of the same trusted network. Potentially, PIOS becomes a **business network**, not just taxi software.

**This is explicitly future scope — Section 14 governs what to build right now.**

## 12. Architecture must be free to grow into this

The codebase already has real bounded contexts: **Order Management, Dispatch, Driver Management, Identity, Passenger Experience.** `Assignment` is already its own aggregate inside Dispatch, referencing `OrderReference`/`DriverReference`. This must not be broken for the sake of a quick UI fix — the model needs room to scale beyond taxi.

## 13. Vision vs. reality today

It matters to separate **vision** from **shipped state**. As of the last pilot deployment, PIOS can: register and authenticate a user, create an order, create a driver, set a driver AVAILABLE, create a proposal, accept it, create an assignment, run it through its ride states, complete it, cancel an order, and enforce authorization boundaries. Last deployment: **12/12 E2E steps PASS.** The fundamental transactional machine already works.

## 14. What NOT to do right now

Do not simultaneously try to: turn PIOS into a full marketplace; build dozens of verticals; build a complex social network; build a large CRM; add AI for its own sake; rewrite a working backend; change architecture for its own sake; or copy Uber/BlaBlaCar's interfaces. That is dilution.

Right now, the thing worth proving is:

> **Can a real driver get a real client through PIOS and actually complete a ride for them, start to finish?**

If the answer holds up consistently, there is a foundation for a business.

## 15. The product's main test

Not "is the app pretty" and not "do we have 50 features," but:

> **Can a driver bring their own client into PIOS, get an order, accept it, complete the ride, and be able to work with that same client again through PIOS?**

If that cycle works, PIOS has product meaning. If it doesn't, everything else is secondary.

## 16. The strategic formula

> **PIOS = infrastructure that lets an independent provider build their own client and commercial network, and lets a client directly find and order the specific provider they want.**

Taxi is the first implementation of this model. The driver is the first entrepreneur. The passenger is the first client. The ride is the first transaction. The link/QR is the first mechanism for building a provider's own network. Dispatch is the first infrastructure for connecting the two sides. The further goal: **a network of entrepreneurs and clients able to interact and transact with each other through PIOS.**

## 17. The most important instruction for whoever works on this next

**Do not optimize PIOS toward the status quo.** If a decision makes PIOS look more like an ordinary aggregator, stop and check it against this vision.

The test:

> **PIOS should strengthen a provider's independence, not their dependence on PIOS.**

And simultaneously:

> **PIOS should make client↔provider interaction simpler, safer, and more convenient than if they arranged it entirely by themselves.**

This is the construction we have been building toward from the start.
