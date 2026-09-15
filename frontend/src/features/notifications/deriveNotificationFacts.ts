/**
 * ADR-071 (In-App, Poll-Derived Notification Surface) Part 3/Part 4 —
 * the exact fact list and the single pure derivation function that ADR
 * ratifies. Read that ADR's Part 2/3/4 before changing anything here;
 * this file exists specifically so the rules stated there are followed
 * exactly, not approximated.
 *
 * ## The one seam (Part 4)
 *
 * [deriveNotificationFacts] is the single function boundary every fact
 * passes through. It is pure: given two already-polled snapshots (the
 * previous poll's, and the current one's), it returns the
 * [NotificationFact]s the transition between them honestly supports —
 * nothing computed, weighted, scored, or inferred beyond that (Part 3's
 * own words). Both `DriverHome.tsx` and `RideRequest.tsx` call the same
 * function; each screen's own snapshot naturally carries only the data
 * that screen already polls, and each screen's own hook filters the
 * result to its own `audience` before rendering (a driver never sees a
 * `passenger`-audience fact, and vice versa) — no `audience` parameter is
 * needed on this function itself, since a fact's audience is a property
 * of *which* row of ADR-071's own table produced it, not of who is
 * asking.
 *
 * A single underlying transition can honestly support two different
 * facts for two different audiences at once — `PRICE_PROPOSED →
 * ACCEPTED` is both D2 ("The passenger confirmed your price") for the
 * driver and P2 ("Your ride is confirmed") for the passenger. Both are
 * emitted; the caller's own audience filter is what keeps them apart.
 *
 * ## Correctness rule 1 — transitions only, never terminal state on first load (Part 2)
 *
 * [previous] is `null` on the very first poll after a component mounts.
 * This function returns `[]` immediately in that case — the baseline is
 * seeded silently by the caller (it simply remembers [current] as next
 * call's [previous]), and nothing is derived from it. Only a change
 * observed between two real polls ever produces a fact. This is not an
 * optimization detail; Part 2 gives two independent correctness reasons
 * (attribution of `DECLINED`, and not fabricating a history from a
 * driver's entire lifetime of rides on first load) and states the rule
 * explicitly so it is never "fixed" into a backfill later.
 *
 * ## Correctness rule 2 — never fabricate what cannot be honestly derived (Part 5)
 *
 * Every fact below is read directly off a field an existing aggregate
 * already owns. Two disclosed gaps this function deliberately does NOT
 * paper over (Part 5): **Gap 1**, "no driver was found for your order"
 * — `FallbackDispatchOutcome.NoAvailableDriver` creates no Proposal and
 * no Assignment, so this is indistinguishable from "still searching" by
 * polling alone; no timeout-based guess is invented here (mirrors
 * ADR-070 Part 7 Q9's own explicit prohibition). **Gap 2**, a `DECLINED`
 * first observed in its already-terminal state (i.e. with no matching
 * entry in [previous] at all) is not attributed to anyone — it simply
 * produces nothing, exactly like any other fact this function has no
 * [previous] entry to diff against.
 *
 * `occurredAt` is `null` wherever the wire contract genuinely carries no
 * timestamp for that transition (every proposal-status and order-status
 * fact below) — `ProposalListItem`/`ProposalStatusItem` and
 * `OrderListItem` carry no "status changed at" field, and inventing one
 * would itself be the forbidden approximation. Only assignment-status
 * facts (`statusChangedAt`, when the caller has it) and message facts
 * (`sentAt`) carry a real instant.
 *
 * ## `id` derivation (Part 4)
 *
 * "`id` is derived only from identifiers the server already guarantees
 * unique (`proposalId` + target status, `assignmentId` + target status,
 * `messageId`)." Because the very same transition can honestly produce
 * two different facts for two different audiences (see above), `id` here
 * is `` `${kind}:${...server identifiers...}` `` — `kind` is a fixed,
 * ADR-table-defined discriminator (`D1`..`D7`/`P1`..`P7`), never a
 * client-minted value or anything based on wall-clock order, so this
 * still satisfies Part 4's own requirement (stable across reloads, and a
 * future real backend producing the same fact would compute the same
 * id) while keeping D2 and P2's own two ids distinct.
 */

export type NotificationAudience = 'driver' | 'passenger'

export type NotificationFactKind = 'D1' | 'D2' | 'D3' | 'D4' | 'D5' | 'D6' | 'D7' | 'P1' | 'P2' | 'P3' | 'P4' | 'P5' | 'P6' | 'P7'

export type ProposalStatusValue = 'OPEN' | 'PRICE_PROPOSED' | 'ACCEPTED' | 'DECLINED' | 'LAPSED' | 'WITHDRAWN'
export type AssignmentStatusValue = 'CREATED' | 'ACCEPTED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED'

/** The subset of `ProposalListItem`/`ProposalStatusItem` (both existing screens' own wire shapes) this derivation needs. */
export interface NotificationProposalSnapshot {
  proposalId: string
  orderId: string
  status: ProposalStatusValue
  /** ADR-042: present only once a price is named -- P1's own enabling condition ("with statedPrice present"). */
  statedPrice: string | null
}

/** The subset of `AssignmentInfo`/`AssignmentStatusItem` this derivation needs. `statusChangedAt` is `null` when the caller's own screen does not fetch it (honest gap, never fabricated -- see this file's own KDoc). */
export interface NotificationAssignmentSnapshot {
  orderId: string
  status: AssignmentStatusValue
  statusChangedAt: string | null
}

/** The subset of `OrderListItem`/`OrderResponse` this derivation needs -- `status` is the one type-only addition ADR-071 Part 3 names as D5's sole enabler. */
export interface NotificationOrderSnapshot {
  id: string
  status: string
}

/** The subset of `ProposalMessageItem` this derivation needs, across every proposal a screen has loaded messages for. */
export interface NotificationMessageSnapshot {
  id: string
  proposalId: string
  orderId: string
  senderRole: 'PASSENGER' | 'DRIVER'
  sentAt: string
}

export interface NotificationSnapshot {
  proposals: NotificationProposalSnapshot[]
  assignments: NotificationAssignmentSnapshot[]
  orders: NotificationOrderSnapshot[]
  messages: NotificationMessageSnapshot[]
}

/** What a `subject` carries is deliberately minimal -- just enough for a future caller (or a richer UI) to locate the underlying record; never a name, price, or address (those belong to the screen's own already-loaded state, not to this fact). */
export interface NotificationFactSubject {
  orderId: string
  proposalId?: string
  assignmentId?: string
  messageId?: string
}

export interface NotificationFact {
  id: string
  kind: NotificationFactKind
  audience: NotificationAudience
  /** ISO-8601, or `null` when the underlying wire contract carries no real timestamp for this transition -- see this file's own KDoc. Never a client-side `Date.now()` guess. */
  occurredAt: string | null
  actionRequired: boolean
  subject: NotificationFactSubject
}

function findProposal(snapshot: NotificationSnapshot, proposalId: string): NotificationProposalSnapshot | undefined {
  return snapshot.proposals.find((p) => p.proposalId === proposalId)
}

function findAssignment(snapshot: NotificationSnapshot, orderId: string): NotificationAssignmentSnapshot | undefined {
  return snapshot.assignments.find((a) => a.orderId === orderId)
}

function findOrder(snapshot: NotificationSnapshot, orderId: string): NotificationOrderSnapshot | undefined {
  return snapshot.orders.find((o) => o.id === orderId)
}

/** See this file's own top-of-file KDoc for the full set of rules this implements. */
export function deriveNotificationFacts(
  previous: NotificationSnapshot | null,
  current: NotificationSnapshot
): NotificationFact[] {
  // Correctness rule 1 (ADR-071 Part 2): the first poll after mount seeds
  // the baseline silently. Nothing is derived from it.
  if (previous === null) {
    return []
  }

  const facts: NotificationFact[] = []

  // --- Proposal-status transitions (D1-D4, D7, P1-P3) ---
  for (const proposal of current.proposals) {
    const before = findProposal(previous, proposal.proposalId)

    // D1: a proposalId not previously seen at all, now OPEN. `OPEN` is a
    // Proposal's only entry status (Proposal.kt's own state machine never
    // returns to it), so "not previously seen" + OPEN is both necessary
    // and sufficient -- never a false positive for a proposal this screen
    // already knew about in some other status.
    if (!before && proposal.status === 'OPEN') {
      facts.push({
        id: `D1:${proposal.proposalId}:OPEN`,
        kind: 'D1',
        audience: 'driver',
        occurredAt: null,
        actionRequired: true,
        subject: { orderId: proposal.orderId, proposalId: proposal.proposalId },
      })
    }

    if (!before) {
      // No prior state to diff a transition against for anything below --
      // per Gap 2 (Part 5), a fact first observed already in a terminal
      // status is never attributed to anyone; it is silently skipped.
      continue
    }

    // D2 / P2: PRICE_PROPOSED -> ACCEPTED, one transition, two audiences.
    if (before.status === 'PRICE_PROPOSED' && proposal.status === 'ACCEPTED') {
      facts.push({
        id: `D2:${proposal.proposalId}:ACCEPTED`,
        kind: 'D2',
        audience: 'driver',
        occurredAt: null,
        actionRequired: true,
        subject: { orderId: proposal.orderId, proposalId: proposal.proposalId },
      })
      facts.push({
        id: `P2:${proposal.proposalId}:ACCEPTED`,
        kind: 'P2',
        audience: 'passenger',
        occurredAt: null,
        actionRequired: false,
        subject: { orderId: proposal.orderId, proposalId: proposal.proposalId },
      })
    }

    // D3: PRICE_PROPOSED -> DECLINED (the passenger declined a named price).
    if (before.status === 'PRICE_PROPOSED' && proposal.status === 'DECLINED') {
      facts.push({
        id: `D3:${proposal.proposalId}:DECLINED`,
        kind: 'D3',
        audience: 'driver',
        occurredAt: null,
        actionRequired: false,
        subject: { orderId: proposal.orderId, proposalId: proposal.proposalId },
      })
    }

    // P3: OPEN -> DECLINED (the driver declined before naming a price).
    if (before.status === 'OPEN' && proposal.status === 'DECLINED') {
      facts.push({
        id: `P3:${proposal.proposalId}:DECLINED`,
        kind: 'P3',
        audience: 'passenger',
        occurredAt: null,
        actionRequired: true,
        subject: { orderId: proposal.orderId, proposalId: proposal.proposalId },
      })
    }

    // D4: OPEN -> WITHDRAWN (ADR-053, the passenger cancelled before the driver answered).
    if (before.status === 'OPEN' && proposal.status === 'WITHDRAWN') {
      facts.push({
        id: `D4:${proposal.proposalId}:WITHDRAWN`,
        kind: 'D4',
        audience: 'driver',
        occurredAt: null,
        actionRequired: false,
        subject: { orderId: proposal.orderId, proposalId: proposal.proposalId },
      })
    }

    // D7: OPEN -> LAPSED (ADR-052).
    if (before.status === 'OPEN' && proposal.status === 'LAPSED') {
      facts.push({
        id: `D7:${proposal.proposalId}:LAPSED`,
        kind: 'D7',
        audience: 'driver',
        occurredAt: null,
        actionRequired: false,
        subject: { orderId: proposal.orderId, proposalId: proposal.proposalId },
      })
    }

    // P1: OPEN -> PRICE_PROPOSED, only once a price is actually named.
    if (before.status === 'OPEN' && proposal.status === 'PRICE_PROPOSED' && proposal.statedPrice) {
      facts.push({
        id: `P1:${proposal.proposalId}:PRICE_PROPOSED`,
        kind: 'P1',
        audience: 'passenger',
        occurredAt: null,
        actionRequired: true,
        subject: { orderId: proposal.orderId, proposalId: proposal.proposalId },
      })
    }
  }

  // --- Assignment-status transitions (P4-P6) ---
  for (const assignment of current.assignments) {
    const before = findAssignment(previous, assignment.orderId)
    if (!before) {
      continue
    }
    if (before.status !== 'ARRIVED' && assignment.status === 'ARRIVED') {
      facts.push({
        id: `P4:${assignment.orderId}:ARRIVED`,
        kind: 'P4',
        audience: 'passenger',
        occurredAt: assignment.statusChangedAt,
        actionRequired: true,
        subject: { orderId: assignment.orderId },
      })
    }
    if (before.status !== 'IN_PROGRESS' && assignment.status === 'IN_PROGRESS') {
      facts.push({
        id: `P5:${assignment.orderId}:IN_PROGRESS`,
        kind: 'P5',
        audience: 'passenger',
        occurredAt: assignment.statusChangedAt,
        actionRequired: false,
        subject: { orderId: assignment.orderId },
      })
    }
    if (before.status !== 'COMPLETED' && assignment.status === 'COMPLETED') {
      facts.push({
        id: `P6:${assignment.orderId}:COMPLETED`,
        kind: 'P6',
        audience: 'passenger',
        occurredAt: assignment.statusChangedAt,
        actionRequired: false,
        subject: { orderId: assignment.orderId },
      })
    }
  }

  // --- Order-status transition (D5 -- the one type-only addition ADR-071 names) ---
  for (const order of current.orders) {
    const before = findOrder(previous, order.id)
    if (!before || before.status === 'CANCELLED' || order.status !== 'CANCELLED') {
      continue
    }
    // "a ride you had accepted" (Part 3's own wording) -- scoped to a
    // proposal of this driver's that had actually reached ACCEPTED for
    // this order, not merely any proposal that ever existed for it.
    const acceptedProposal = current.proposals.find((p) => p.orderId === order.id && p.status === 'ACCEPTED')
    if (!acceptedProposal) {
      continue
    }
    facts.push({
      id: `D5:${order.id}:CANCELLED`,
      kind: 'D5',
      audience: 'driver',
      occurredAt: null,
      actionRequired: true,
      subject: { orderId: order.id, proposalId: acceptedProposal.proposalId },
    })
  }

  // --- New messages (D6, P7) ---
  const previousMessageIds = new Set(previous.messages.map((m) => m.id))
  for (const message of current.messages) {
    if (previousMessageIds.has(message.id)) {
      continue
    }
    if (message.senderRole === 'PASSENGER') {
      facts.push({
        id: `D6:${message.id}`,
        kind: 'D6',
        audience: 'driver',
        occurredAt: message.sentAt,
        actionRequired: true,
        subject: { orderId: message.orderId, proposalId: message.proposalId, messageId: message.id },
      })
    } else {
      facts.push({
        id: `P7:${message.id}`,
        kind: 'P7',
        audience: 'passenger',
        occurredAt: message.sentAt,
        actionRequired: true,
        subject: { orderId: message.orderId, proposalId: message.proposalId, messageId: message.id },
      })
    }
  }

  return facts
}
