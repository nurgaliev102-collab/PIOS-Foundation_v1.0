/**
 * PIOS Driver Web Push (ADR-083, D-10) -- imported into the
 * `vite-plugin-pwa`-generated service worker via `workbox.importScripts`
 * (`vite.config.ts`). Plain static JS, no build step, no bundler.
 *
 * Holds the two approved, driver-facing copy pairs -- verbatim, exactly as
 * ratified by the Product Owner (ADR-083):
 *   N1 -- «Новый заказ» / «Откройте приложение, чтобы ответить»
 *   N2 -- «Цена подтверждена» / «Пассажир подтвердил вашу цену»
 *
 * No business logic beyond mapping `kind` to its fixed copy pair. No other
 * event listener. Issues no `fetch` call and triggers no poll -- the
 * existing 3-second in-app polling (`DriverHome.tsx`) is completely
 * unaffected by anything in this file.
 */

const PIOS_PUSH_COPY = {
  N1: { title: 'Новый заказ', body: 'Откройте приложение, чтобы ответить' },
  N2: { title: 'Цена подтверждена', body: 'Пассажир подтвердил вашу цену' },
}

self.addEventListener('push', (event) => {
  let payload = null
  try {
    payload = event.data ? event.data.json() : null
  } catch {
    payload = null
  }
  if (!payload || !payload.kind || !PIOS_PUSH_COPY[payload.kind]) {
    // A malformed/empty payload shows nothing -- never a generic placeholder.
    return
  }
  const copy = PIOS_PUSH_COPY[payload.kind]
  event.waitUntil(
    self.registration.showNotification(copy.title, {
      body: copy.body,
      tag: payload.tag,
      renotify: false,
      data: { url: payload.url },
    })
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = (event.notification.data && event.notification.data.url) || '/'
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client) {
          return client.focus()
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(url)
      }
      return undefined
    })
  )
})
