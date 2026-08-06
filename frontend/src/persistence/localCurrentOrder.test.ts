import { beforeEach, describe, expect, it } from 'vitest'
import { clearCurrentOrderId, getCurrentOrderId, saveCurrentOrderId } from './localCurrentOrder'

// P0-1 (`docs/SPRINT_PILOT_BLOCKERS.md`): `clearCurrentOrderId` is the
// function that used to not exist at all -- these tests are its own
// coverage, plus the specific acceptance criterion this fix exists for:
// clearing one driver's entry must never touch another driver's.
describe('localCurrentOrder', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('clears the stored order id for the given driver', () => {
    saveCurrentOrderId('driver-1', 'order-1')

    clearCurrentOrderId('driver-1')

    expect(getCurrentOrderId('driver-1')).toBeNull()
  })

  it('does not affect a different driver\'s stored order id', () => {
    saveCurrentOrderId('driver-1', 'order-1')
    saveCurrentOrderId('driver-2', 'order-2')

    clearCurrentOrderId('driver-1')

    expect(getCurrentOrderId('driver-1')).toBeNull()
    expect(getCurrentOrderId('driver-2')).toBe('order-2')
  })

  it('does nothing when no order was ever stored for that driver', () => {
    clearCurrentOrderId('driver-never-ordered')

    expect(getCurrentOrderId('driver-never-ordered')).toBeNull()
  })

  it('does nothing when storage was never written to at all', () => {
    clearCurrentOrderId('driver-1')

    expect(getCurrentOrderId('driver-1')).toBeNull()
  })

  it('allows a new order id to be saved for the same driver after clearing', () => {
    saveCurrentOrderId('driver-1', 'order-1')
    clearCurrentOrderId('driver-1')

    saveCurrentOrderId('driver-1', 'order-2')

    expect(getCurrentOrderId('driver-1')).toBe('order-2')
  })
})
