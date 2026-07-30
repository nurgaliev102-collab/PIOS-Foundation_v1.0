import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import styles from './NotFound.module.css'

/**
 * UX audit (pilot readiness): before this, an unmatched URL (a mistyped
 * link, a stale bookmark, a route from a future sprint) rendered nothing
 * at all — `useRoutes` returns `null` when no route matches, so the app
 * looked broken (a blank white screen) rather than telling the person
 * anything. This is a plain, styled fallback consistent with every other
 * screen — no routing logic of its own, wired in `routes.tsx` as the
 * catch-all `path: '*'` entry.
 */
export function NotFound() {
  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        <h1 className={styles.title}>Страница не найдена</h1>
        <p className={styles.text}>Возможно, ссылка устарела или введена с ошибкой.</p>
        <div className={styles.actionRow}>
          <ActionButton label="На главную" variant="primary" onClick={() => window.location.assign('/')} />
        </div>
      </main>
    </div>
  )
}
