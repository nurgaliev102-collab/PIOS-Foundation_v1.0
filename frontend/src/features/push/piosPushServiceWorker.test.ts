import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { runInNewContext } from 'node:vm'
import { isValidElement } from 'react'
import { matchRoutes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { routes } from '../../app/routes'
import { DriverHome } from '../../pages/DriverHome'

describe('driver push notification click', () => {
  it('opens the existing DriverHome route when the app has no open window', async () => {
    const handlers = new Map<string, (event: unknown) => void>()
    const showNotification = vi.fn(async (_title: string, _options: { data: { url: string } }) => undefined)
    const matchAll = vi.fn(async () => [])
    const openWindow = vi.fn(async (_url: string) => undefined)
    const script = readFileSync(resolve('public/pios-push-sw.js'), 'utf8')

    runInNewContext(script, {
      self: {
        addEventListener: (type: string, handler: (event: unknown) => void) => handlers.set(type, handler),
        registration: { showNotification },
        clients: { matchAll, openWindow },
      },
    })

    const pending: Promise<unknown>[] = []
    const waitUntil = (promise: Promise<unknown>) => { pending.push(promise) }
    handlers.get('push')?.({
      data: { json: () => ({ kind: 'N1', tag: 'proposal-1:OPEN', url: '/' }) },
      waitUntil,
    })
    await Promise.all(pending.splice(0))

    expect(showNotification).toHaveBeenCalledOnce()
    const notification = { data: showNotification.mock.calls[0][1].data, close: vi.fn() }
    handlers.get('notificationclick')?.({ notification, waitUntil })
    await Promise.all(pending)

    expect(notification.close).toHaveBeenCalledOnce()
    expect(matchAll).toHaveBeenCalledWith({ type: 'window', includeUncontrolled: true })
    expect(openWindow).toHaveBeenCalledExactlyOnceWith('/')
    const matchedRoute = matchRoutes(routes, openWindow.mock.calls[0][0])?.at(-1)?.route
    expect(matchedRoute?.path).toBe('/')
    expect(isValidElement(matchedRoute?.element) && matchedRoute.element.type === DriverHome).toBe(true)
  })
})
