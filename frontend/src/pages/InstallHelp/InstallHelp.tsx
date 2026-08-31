import { Header } from '../../components/Header'
import { InstallPIOS } from '../../features/install'
import styles from './InstallHelp.module.css'

/**
 * PIOS Install v1 (Product Owner exception — see `InstallPIOS.tsx`'s own
 * doc comment). Public, unauthenticated route (`/help/install` in
 * `routes.tsx`) — Section 7's own requirement: a single link an owner or
 * driver can send through Telegram/WhatsApp/SMS, opened by someone who has
 * no PIOS session on this device at all yet. Safe to leave unauthenticated
 * for the same reason every other pre-login screen in this app already is
 * (`DriverHome.tsx`'s welcome screen, `PassengerLanding.tsx`'s invited
 * screen): nothing here reads or writes any account data, and
 * `InstallPIOS.tsx` itself makes no backend call at all (Section 20).
 *
 * No `onClose` is passed to `InstallPIOS` — this is a real, standalone
 * page, not an overlay; there is nothing to return to except the
 * browser's own back button, which already satisfies Section 15's
 * "возможность вернуться назад" here.
 */
export function InstallHelp() {
  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        <InstallPIOS />
      </main>
    </div>
  )
}
