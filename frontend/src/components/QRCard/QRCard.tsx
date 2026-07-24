import { Link } from 'react-router-dom'
import { ActionButton } from '../ActionButton'
import styles from './QRCard.module.css'

export interface QRCardProps {
  invitationLink: string
  /**
   * Sprint 2 — Driver Invitation Flow: optional in-app route the
   * displayed link should navigate to (e.g. `/i/ILDAR001`). This exists
   * purely so the link is testable locally without a real `pios.local`
   * domain; when omitted, the link renders as plain text, unchanged from
   * Sprint 1. This component still does not know what an "invitation"
   * is — it only renders whatever path it is given.
   */
  linkTo?: string
  onCopy?: () => void
  onShare?: () => void
  /**
   * Sprint 2: transient status text (e.g. "Copied") shown beneath the
   * actions. The caller owns when it appears and clears — this component
   * only renders it.
   */
  feedback?: string | null
}

/**
 * Displays an invitation QR code and link, with Copy/Share actions.
 *
 * The QR graphic rendered here is a **static, decorative placeholder** —
 * not a real, scannable QR code encoding [invitationLink]. No QR
 * generation library is used, consistent with Sprint 1's own "QR:
 * placeholder only" scope. Swapping in a real, generated QR code later
 * only touches this component's own markup, not its props or any caller.
 */
export function QRCard({ invitationLink, linkTo, onCopy, onShare, feedback }: QRCardProps) {
  return (
    <section className={styles.card}>
      <div className={styles.qrPlaceholder} role="img" aria-label="Invitation QR code placeholder">
        <PlaceholderQrGraphic />
      </div>

      <p className={styles.linkLabel}>Invitation link</p>
      {linkTo ? (
        <Link to={linkTo} className={styles.link}>
          {invitationLink}
        </Link>
      ) : (
        <p className={styles.link}>{invitationLink}</p>
      )}

      <div className={styles.actions}>
        <ActionButton label="Copy" onClick={onCopy} variant="secondary" />
        <ActionButton label="Share" onClick={onShare} variant="primary" />
      </div>

      {feedback && (
        <p className={styles.feedback} role="status" aria-live="polite">
          {feedback}
        </p>
      )}
    </section>
  )
}

/**
 * A fixed, hand-authored pattern resembling a QR code's finder squares
 * and module grid — purely visual, deterministic (same on every render),
 * encodes nothing.
 */
function PlaceholderQrGraphic() {
  return (
    <svg viewBox="0 0 100 100" width="100%" height="100%" aria-hidden="true">
      <rect width="100" height="100" fill="#ffffff" />
      {[
        [4, 4],
        [72, 4],
        [4, 72],
      ].map(([x, y]) => (
        <g key={`${x}-${y}`} transform={`translate(${x} ${y})`}>
          <rect width="24" height="24" fill="#111827" />
          <rect x="4" y="4" width="16" height="16" fill="#ffffff" />
          <rect x="8" y="8" width="8" height="8" fill="#111827" />
        </g>
      ))}
      {[
        36, 44, 52, 60, 68, 4, 12, 20, 28, 36, 44, 52, 60, 68, 76, 84, 92,
      ].map((x, index) => (
        <rect key={`h-${x}-${index}`} x={x} y={36 + (index % 3) * 8} width="6" height="6" fill="#111827" />
      ))}
      {[36, 44, 52, 60, 68, 76, 84, 92].map((y, index) => (
        <rect key={`v-${y}-${index}`} x={36 + (index % 3) * 8} y={y} width="6" height="6" fill="#111827" />
      ))}
    </svg>
  )
}
