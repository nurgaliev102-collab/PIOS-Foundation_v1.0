import styles from './Header.module.css'

/**
 * App-wide top bar. "PIOS" is the application's own name, not mock or
 * injectable data — unlike every other component in this sprint, this
 * one takes no props, since it has nothing participant-specific to show
 * yet.
 */
export function Header() {
  return (
    <header className={styles.header}>
      <span className={styles.brand}>PIOS</span>
    </header>
  )
}
