import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'

const IDENTITY_BASE_URL = import.meta.env.VITE_IDENTITY_BASE_URL ?? 'http://localhost:8086'
const VAPID_PUBLIC_KEY = import.meta.env.VITE_VAPID_PUBLIC_KEY ?? ''

const identityProvider = new BackendIdentityProvider()

export interface PushSubscriptionPayload {
  endpoint: string
  p256dh: string
  auth: string
  userAgent: string | null
}

function urlBase64ToUint8Array(value: string): Uint8Array {
  const padding = '='.repeat((4 - (value.length % 4)) % 4)
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(base64)
  return Uint8Array.from(raw, (character) => character.charCodeAt(0))
}

function isSupported(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window && VAPID_PUBLIC_KEY.length > 0
}

export function isPushSupported(): boolean {
  return isSupported()
}

export async function enablePushNotifications(): Promise<boolean> {
  if (!isSupported()) {
    return false
  }
  const identity = identityProvider.getStoredIdentity()
  if (!identity) {
    return false
  }

  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    return false
  }

  const registration = await navigator.serviceWorker.ready
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
  })
  const json = subscription.toJSON()
  if (!json.endpoint || !json.keys?.p256dh || !json.keys.auth) {
    return false
  }

  const payload: PushSubscriptionPayload = {
    endpoint: json.endpoint,
    p256dh: json.keys.p256dh,
    auth: json.keys.auth,
    userAgent: navigator.userAgent || null,
  }

  const response = await fetch(`${IDENTITY_BASE_URL}/v1/identities/push-subscriptions`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${identity.token}`,
    },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    throw new Error(`Push subscription registration failed: ${response.status}`)
  }
  return true
}

export async function disablePushNotifications(): Promise<void> {
  if (!('serviceWorker' in navigator)) {
    return
  }
  const identity = identityProvider.getStoredIdentity()
  const registration = await navigator.serviceWorker.ready
  const subscription = await registration.pushManager.getSubscription()
  if (!subscription) {
    return
  }
  if (identity) {
    await fetch(`${IDENTITY_BASE_URL}/v1/identities/push-subscriptions`, {
      method: 'DELETE',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        Authorization: `Bearer ${identity.token}`,
      },
      body: JSON.stringify({ endpoint: subscription.endpoint }),
    })
  }
  await subscription.unsubscribe()
}
