import { describe, expect, it } from 'vitest'
import { deriveNotificationFacts, type NotificationSnapshot } from './deriveNotificationFacts'

function emptySnapshot(): NotificationSnapshot {
  return { proposals: [], assignments: [], orders: [], messages: [] }
}

describe('deriveNotificationFacts', () => {
  // --- Correctness rule 1 (ADR-071 Part 2): baseline seeding ---

  it('returns nothing on the first poll after mount (previous is null), even for an already-OPEN proposal', () => {
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
    }

    expect(deriveNotificationFacts(null, current)).toEqual([])
  })

  it('returns nothing when nothing changed between two identical polls', () => {
    const snapshot: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
    }

    expect(deriveNotificationFacts(snapshot, snapshot)).toEqual([])
  })

  // --- D1 ---

  it('D1: a proposalId not previously seen, now OPEN', () => {
    const previous = emptySnapshot()
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
    }

    const facts = deriveNotificationFacts(previous, current)

    expect(facts).toEqual([
      {
        id: 'D1:p1:OPEN',
        kind: 'D1',
        audience: 'driver',
        occurredAt: null,
        actionRequired: true,
        subject: { orderId: 'o1', proposalId: 'p1' },
      },
    ])
  })

  // --- Gap 2 (Part 5): a DECLINED first observed cold is never attributed ---

  it('does not attribute a DECLINED proposal first observed in its terminal state to anyone', () => {
    const previous = emptySnapshot()
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'DECLINED', statedPrice: null }],
    }

    expect(deriveNotificationFacts(previous, current)).toEqual([])
  })

  // --- D2 / P2 ---

  it('D2 and P2: PRICE_PROPOSED -> ACCEPTED produces both audiences from the one transition', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'PRICE_PROPOSED', statedPrice: '350' }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'ACCEPTED', statedPrice: '350' }],
    }

    const facts = deriveNotificationFacts(previous, current)

    expect(facts.find((f) => f.kind === 'D2')).toMatchObject({ id: 'D2:p1:ACCEPTED', audience: 'driver', actionRequired: true })
    expect(facts.find((f) => f.kind === 'P2')).toMatchObject({ id: 'P2:p1:ACCEPTED', audience: 'passenger', actionRequired: false })
    expect(facts).toHaveLength(2)
  })

  // --- D3 vs P3 (Part 2's own worked attribution example) ---

  it('D3: PRICE_PROPOSED -> DECLINED is the passenger declining a named price -- driver-only', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'PRICE_PROPOSED', statedPrice: '350' }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'DECLINED', statedPrice: '350' }],
    }

    const facts = deriveNotificationFacts(previous, current)

    expect(facts).toEqual([
      {
        id: 'D3:p1:DECLINED',
        kind: 'D3',
        audience: 'driver',
        occurredAt: null,
        actionRequired: false,
        subject: { orderId: 'o1', proposalId: 'p1' },
      },
    ])
  })

  it('P3: OPEN -> DECLINED is the driver declining before naming a price -- passenger-only', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'DECLINED', statedPrice: null }],
    }

    const facts = deriveNotificationFacts(previous, current)

    expect(facts).toEqual([
      {
        id: 'P3:p1:DECLINED',
        kind: 'P3',
        audience: 'passenger',
        occurredAt: null,
        actionRequired: true,
        subject: { orderId: 'o1', proposalId: 'p1' },
      },
    ])
  })

  // --- D4, D7 ---

  it('D4: OPEN -> WITHDRAWN (ADR-053, passenger cancelled before the driver answered)', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'WITHDRAWN', statedPrice: null }],
    }

    expect(deriveNotificationFacts(previous, current)).toEqual([
      {
        id: 'D4:p1:WITHDRAWN',
        kind: 'D4',
        audience: 'driver',
        occurredAt: null,
        actionRequired: false,
        subject: { orderId: 'o1', proposalId: 'p1' },
      },
    ])
  })

  it('D7: OPEN -> LAPSED (ADR-052)', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'LAPSED', statedPrice: null }],
    }

    expect(deriveNotificationFacts(previous, current)).toEqual([
      {
        id: 'D7:p1:LAPSED',
        kind: 'D7',
        audience: 'driver',
        occurredAt: null,
        actionRequired: false,
        subject: { orderId: 'o1', proposalId: 'p1' },
      },
    ])
  })

  // --- P1 ---

  it('P1: OPEN -> PRICE_PROPOSED with a stated price', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'PRICE_PROPOSED', statedPrice: '400' }],
    }

    expect(deriveNotificationFacts(previous, current)).toEqual([
      {
        id: 'P1:p1:PRICE_PROPOSED',
        kind: 'P1',
        audience: 'passenger',
        occurredAt: null,
        actionRequired: true,
        subject: { orderId: 'o1', proposalId: 'p1' },
      },
    ])
  })

  it('does not fire P1 for OPEN -> PRICE_PROPOSED with no stated price (never fabricate a price that is not there)', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'PRICE_PROPOSED', statedPrice: null }],
    }

    expect(deriveNotificationFacts(previous, current)).toEqual([])
  })

  // --- P4, P5, P6 ---

  it('P4/P5/P6: assignment status transitions, carrying a real occurredAt when the caller has one', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      assignments: [{ orderId: 'o1', status: 'ACCEPTED', statusChangedAt: null }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      assignments: [{ orderId: 'o1', status: 'ARRIVED', statusChangedAt: '2026-09-15T09:00:00Z' }],
    }

    expect(deriveNotificationFacts(previous, current)).toEqual([
      {
        id: 'P4:o1:ARRIVED',
        kind: 'P4',
        audience: 'passenger',
        occurredAt: '2026-09-15T09:00:00Z',
        actionRequired: true,
        subject: { orderId: 'o1' },
      },
    ])
  })

  it('P6 carries occurredAt: null when the caller does not fetch statusChangedAt (honest gap, not fabricated)', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      assignments: [{ orderId: 'o1', status: 'IN_PROGRESS', statusChangedAt: null }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      assignments: [{ orderId: 'o1', status: 'COMPLETED', statusChangedAt: null }],
    }

    expect(deriveNotificationFacts(previous, current)).toEqual([
      {
        id: 'P6:o1:COMPLETED',
        kind: 'P6',
        audience: 'passenger',
        occurredAt: null,
        actionRequired: false,
        subject: { orderId: 'o1' },
      },
    ])
  })

  // --- D5 ---

  it('D5: the order behind an ACCEPTED proposal of this driver\'s becomes CANCELLED', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'ACCEPTED', statedPrice: '300' }],
      orders: [{ id: 'o1', status: 'SUBMITTED' }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'ACCEPTED', statedPrice: '300' }],
      orders: [{ id: 'o1', status: 'CANCELLED' }],
    }

    expect(deriveNotificationFacts(previous, current)).toEqual([
      {
        id: 'D5:o1:CANCELLED',
        kind: 'D5',
        audience: 'driver',
        occurredAt: null,
        actionRequired: true,
        subject: { orderId: 'o1', proposalId: 'p1' },
      },
    ])
  })

  it('does not fire D5 when no proposal for that order ever reached ACCEPTED', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
      orders: [{ id: 'o1', status: 'SUBMITTED' }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'WITHDRAWN', statedPrice: null }],
      orders: [{ id: 'o1', status: 'CANCELLED' }],
    }

    const facts = deriveNotificationFacts(previous, current)

    expect(facts.some((f) => f.kind === 'D5')).toBe(false)
  })

  // --- D6 / P7 ---

  it('D6: a new message from the passenger', () => {
    const previous = emptySnapshot()
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      messages: [{ id: 'm1', proposalId: 'p1', orderId: 'o1', senderRole: 'PASSENGER', sentAt: '2026-09-15T10:00:00Z' }],
    }

    expect(deriveNotificationFacts(previous, current)).toEqual([
      {
        id: 'D6:m1',
        kind: 'D6',
        audience: 'driver',
        occurredAt: '2026-09-15T10:00:00Z',
        actionRequired: true,
        subject: { orderId: 'o1', proposalId: 'p1', messageId: 'm1' },
      },
    ])
  })

  it('P7: a new message from the driver, and an already-seen message id never fires twice', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      messages: [{ id: 'm1', proposalId: 'p1', orderId: 'o1', senderRole: 'DRIVER', sentAt: '2026-09-15T10:00:00Z' }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      messages: [
        { id: 'm1', proposalId: 'p1', orderId: 'o1', senderRole: 'DRIVER', sentAt: '2026-09-15T10:00:00Z' },
        { id: 'm2', proposalId: 'p1', orderId: 'o1', senderRole: 'DRIVER', sentAt: '2026-09-15T10:05:00Z' },
      ],
    }

    const facts = deriveNotificationFacts(previous, current)

    expect(facts).toEqual([
      {
        id: 'P7:m2',
        kind: 'P7',
        audience: 'passenger',
        occurredAt: '2026-09-15T10:05:00Z',
        actionRequired: true,
        subject: { orderId: 'o1', proposalId: 'p1', messageId: 'm2' },
      },
    ])
  })

  // --- id stability (Part 4) ---

  it('produces the same fact id for the same transition every time (stable across recomputation)', () => {
    const previous: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'OPEN', statedPrice: null }],
    }
    const current: NotificationSnapshot = {
      ...emptySnapshot(),
      proposals: [{ proposalId: 'p1', orderId: 'o1', status: 'PRICE_PROPOSED', statedPrice: '400' }],
    }

    const first = deriveNotificationFacts(previous, current)
    const second = deriveNotificationFacts(previous, current)

    expect(first).toEqual(second)
  })
})
