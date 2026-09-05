import styles from './Divider.module.css'

/** PIOS design-system foundation Divider — docs/PIOS_DESIGN_SYSTEM.md Section 5: "a single, thin, --pios-color-border rule; no decorative dividers." */
export function Divider() {
  return <hr className={styles.divider} />
}
