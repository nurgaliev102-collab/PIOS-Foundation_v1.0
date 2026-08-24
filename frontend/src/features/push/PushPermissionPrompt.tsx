import { useState } from 'react'
import { enablePushNotifications, isPushSupported } from './pushSubscription'

/** Explicit user-interaction gate for Web Push. It never requests permission on mount. */
export function PushPermissionPrompt() {
  const [state, setState] = useState<'idle' | 'working' | 'enabled' | 'error'>('idle')

  if (!isPushSupported() || state === 'enabled') {
    return null
  }

  async function enable() {
    setState('working')
    try {
      const enabled = await enablePushNotifications()
      setState(enabled ? 'enabled' : 'idle')
    } catch {
      setState('error')
    }
  }

  return (
    <div style={{ position: 'fixed', right: 16, bottom: 16, zIndex: 1000, maxWidth: 360, padding: 12, borderRadius: 12, background: 'Canvas', color: 'CanvasText', boxShadow: '0 6px 24px rgb(0 0 0 / 18%)' }}>
      <strong>Получать уведомления PIOS?</strong>
      <p style={{ margin: '6px 0 10px' }}>PIOS сможет сообщать о новых событиях, даже когда вкладка не открыта.</p>
      <button type="button" onClick={enable} disabled={state === 'working'}>
        {state === 'working' ? 'Подключаем…' : 'Включить уведомления'}
      </button>
      {state === 'error' ? <span role="alert" style={{ marginLeft: 8 }}>Не удалось включить</span> : null}
    </div>
  )
}
