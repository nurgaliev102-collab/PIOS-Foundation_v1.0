/**
 * PIOS Driver Web Push (ADR-083, D-10). Wraps the browser's own
 * `PushManager` subscribe/unsubscribe flow and Dispatch's own
 * `/v1/driver-push-subscriptions` endpoints -- mirrors
 * `features/install/installPrompt.ts`'s own "wrap the browser's real
 * mechanism, never simulate it" discipline.
 *
 * Every exported function here is fully wrapped so a failure anywhere in
 * this flow (permission denied, no service worker registration yet, a
 * network error, an unsupported browser) is a silent no-op for the calling
 * screen -- never a thrown error that blocks it (ADR-083's own frontend
 * scope: "Both must be fully wrapped so any failure is a silent no-op for
 * the app").
 */

import { request, resolveBackendBaseUrl } from '../../api/apiClient'
import { pushSupport } from './pushSupport'

// Dispatch's own local port (INTERFACE_CONTRACTS.md) -- same resolution
// rule every other per-module backend base URL in this frontend follows
// (`api/apiClient.ts`'s own `resolveBackendBaseUrl`; `DriverHome.tsx`'s own
// identically-named constant).
const DISPATCH_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_DISPATCH_BASE_URL, 'http://localhost:8084')

interface PublicKeyResponse {
  publicKey: string
}

/**
 * Converts a URL-safe base64 VAPID public key into the `Uint8Array` form
 * `PushManager.subscribe`'s own `applicationServerKey` option requires --
 * the standard conversion every Web Push client-side integration needs,
 * since the browser API accepts only a raw byte array, never a string.
 */
function urlBase64ToUint8Array(base64: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4)
  const normalized = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = window.atob(normalized)
  const output = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i += 1) {
    output[i] = raw.charCodeAt(i)
  }
  return output
}

/**
 * Fetches the VAPID public key Dispatch currently advertises. Returns
 * `null` on any failure -- `404` (VAPID not configured, fail-closed),
 * `401`/`403`, a network error, or a malformed response -- so a caller
 * (`DriverHome.tsx`'s own opt-in control) can decide not to render
 * anything rather than surface an error.
 */
export async function fetchPushPublicKey(token: string): Promise<string | null> {
  try {
    const response = await request<PublicKeyResponse>('/v1/driver-push-subscriptions/public-key', {
      headers: { Authorization: `Bearer ${token}` },
      baseUrl: DISPATCH_BASE_URL,
    })
    return response.publicKey ?? null
  } catch {
    return null
  }
}

/**
 * The driver's own explicit opt-in act (ADR-083: "NO auto-prompt on mount,
 * ever -- the permission request must only happen from this explicit user
 * action"). Fetches the current VAPID public key, requests browser
 * notification permission, subscribes via `PushManager`, then registers
 * the subscription with Dispatch. Returns `true` only on a fully completed
 * subscribe + register; any failure at any step -- unsupported browser,
 * denied/dismissed permission, no VAPID key configured, a network error --
 * resolves `false` rather than throwing.
 */
export async function enablePush(token: string): Promise<boolean> {
  if (!pushSupport()) {
    return false
  }
  try {
    const publicKey = await fetchPushPublicKey(token)
    if (!publicKey) {
      return false
    }
    const permission = await Notification.requestPermission()
    if (permission !== 'granted') {
      return false
    }
    const registration = await navigator.serviceWorker.ready
    const subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    })
    const json = subscription.toJSON()
    if (!json.endpoint || !json.keys?.p256dh || !json.keys?.auth) {
      return false
    }
    await request('/v1/driver-push-subscriptions', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        endpoint: json.endpoint,
        keys: { p256dh: json.keys.p256dh, auth: json.keys.auth },
      }),
      baseUrl: DISPATCH_BASE_URL,
    })
    return true
  } catch {
    return false
  }
}

/**
 * The driver's own explicit opt-out act, and `DriverHome.tsx`'s own
 * `handleLogout` step (before the session is cleared -- the `DELETE`
 * needs a still-valid token). Removes Dispatch's own copy of the
 * subscription first, then unsubscribes the browser's own `PushManager`
 * -- reversed order from [enablePush] deliberately, since the `DELETE`
 * needs the endpoint the subscription still carries. Never throws; a
 * failing `DELETE` (e.g. an already-expired token during logout) still
 * lets the browser-side `unsubscribe()` proceed, and either way this
 * function resolves without surfacing anything to the caller.
 */
export async function disablePush(token: string): Promise<void> {
  if (!pushSupport()) {
    return
  }
  try {
    const registration = await navigator.serviceWorker.ready
    const subscription = await registration.pushManager.getSubscription()
    if (!subscription) {
      return
    }
    try {
      await request(`/v1/driver-push-subscriptions?endpoint=${encodeURIComponent(subscription.endpoint)}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
        baseUrl: DISPATCH_BASE_URL,
      })
    } catch {
      // Deliberately swallowed -- see this function's own KDoc: logout
      // must complete even if this DELETE fails.
    }
    await subscription.unsubscribe()
  } catch {
    // Fully silent, per this function's own KDoc.
  }
}
