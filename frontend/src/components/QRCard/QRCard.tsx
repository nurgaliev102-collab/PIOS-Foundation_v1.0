import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import QRCode from 'qrcode'
import { Button } from '../Button'
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
  /**
   * PIOS Install v1: this component's own label defaulted to "Ссылка для
   * клиентов" (Driver Home's own invitation-link caption) since that was
   * its only caller until now — wrong copy for `InstallPIOS.tsx`'s desktop
   * fallback, where the code points at `/help/install`, not an invitation.
   * Optional, defaulting to the original text unchanged, so Driver Home's
   * own existing usage is unaffected.
   */
  label?: string
}

/**
 * Displays an invitation QR code and link, with Copy/Share actions.
 *
 * Pilot readiness fix: the QR graphic used to be a static, decorative
 * placeholder that encoded nothing (Sprint 1's own "QR: placeholder only"
 * scope) — a real defect once this screen became the thing a driver hands
 * a real client. It now renders a real, scannable code for [invitationLink]
 * itself, generated client-side (`qrcode` package, SVG-to-data-URL, no
 * network call — nothing about the invitation ever leaves the device to
 * produce it). Regenerates whenever [invitationLink] changes.
 */
export function QRCard({ invitationLink, linkTo, onCopy, onShare, feedback, label = 'Ссылка для клиентов' }: QRCardProps) {
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    // Dark/light kept as literal hex, not tokens: a QR code needs a fixed,
    // maximum-contrast pair to stay scannable regardless of the viewer's
    // light/dark theme -- unlike UI chrome, this must not follow the
    // design system's own color tokens. Matches --pios-color-text-primary's
    // near-black in spirit, not its exact value.
    QRCode.toDataURL(invitationLink, { margin: 1, width: 336, color: { dark: '#211d16', light: '#ffffff' } })
      .then((url) => {
        if (active) {
          setQrDataUrl(url)
        }
      })
      .catch(() => {
        // Best-effort: the link itself (Copy/Share, and the text below)
        // remains fully usable even if QR generation fails for some reason.
        if (active) {
          setQrDataUrl(null)
        }
      })
    return () => {
      active = false
    }
  }, [invitationLink])

  return (
    <section className={styles.card}>
      <div className={styles.qrPlaceholder} role="img" aria-label="QR-код ссылки-приглашения">
        {qrDataUrl && <img src={qrDataUrl} alt="" className={styles.qrImage} />}
      </div>

      <p className={styles.linkLabel}>{label}</p>
      {linkTo ? (
        <Link to={linkTo} className={styles.link}>
          {invitationLink}
        </Link>
      ) : (
        <p className={styles.link}>{invitationLink}</p>
      )}

      <div className={styles.actions}>
        <Button label="Копировать" onClick={onCopy} variant="secondary" />
        <Button label="Поделиться" onClick={onShare} variant="primary" />
      </div>

      {feedback && (
        <p className={styles.feedback} role="status" aria-live="polite">
          {feedback}
        </p>
      )}
    </section>
  )
}
