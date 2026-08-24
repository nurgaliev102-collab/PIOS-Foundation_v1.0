import { clientsClaim } from 'workbox-core'
import { cleanupOutdatedCaches, precacheAndRoute } from 'workbox-precaching'

declare let self: ServiceWorkerGlobalScope & typeof globalThis

type PushPayload = {
  type?: string
  id?: string
}

self.skipWaiting()
clientsClaim()
cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST)

self.addEventListener('push', (event) => {
  const payload: PushPayload = event.data?.json?.() ?? {}
  const type = payload.type ?? 'pios.event'
  const id = payload.id ?? ''
  const title = type === 'driver.order-assigned'
    ? 'PIOS: новый заказ'
    : type === 'passenger.assignment-accepted'
      ? 'PIOS: заказ принят'
      : type === 'passenger.assignment-arrived'
        ? 'PIOS: водитель прибыл'
        : 'PIOS: новое событие'

  event.waitUntil(
    self.registration.showNotification(title, {
      body: 'Откройте PIOS, чтобы посмотреть актуальное состояние.',
      tag: id ? `${type}:${id}` : type,
      data: { type, id },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
      const existing = clients.find((client) => 'focus' in client)
      if (existing) {
        return existing.focus()
      }
      return self.clients.openWindow('/')
    }),
  )
})
