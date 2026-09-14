import styles from './MessageBubble.module.css'

export interface MessageBubbleProps {
  /** True when the current viewer is this message's own sender — styled right-aligned, accent-filled, matching every native chat surface's own convention. */
  own: boolean
  /**
   * The exact, already-composed "{label}: {body}" string
   * (`DriverHome.tsx`/`RideRequest.tsx` already build this themselves —
   * `"Вы: ..."`, `"Пассажир: ..."`, `"Водитель: ..."`). Rendered here as a
   * single, unmodified text node so the existing test assertions that
   * match it exactly (`DriverHome.test.tsx`/`RideRequest.test.tsx`,
   * `findByText('Вы: ...')`) keep passing unchanged — this component only
   * adds bubble chrome (alignment, background, timestamp) around that same
   * text, never splits or restructures it.
   */
  text: string
  /** ISO-8601, `ProposalMessage.sentAt` (Dispatch) — rendered as a local HH:MM, same convention `DriverHome.tsx`'s own `formatOrderTime` already uses. Omitted (not a fallback string) when it fails to parse. */
  sentAt: string
}

function formatMessageTime(sentAt: string): string | null {
  const parsed = new Date(sentAt)
  if (Number.isNaN(parsed.getTime())) {
    return null
  }
  return parsed.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
}

/**
 * PIOS UI/UX Redesign 2.0, Phase 4 ("Messaging"): a minimal, native-feeling
 * conversation bubble for Minimal In-Ride Messaging — previously
 * `DriverHome.tsx` and `RideRequest.tsx` each rendered a plain, identical
 * `<Text role="body">` line with no visual distinction between sender and
 * recipient and no timestamp at all. Shared between both screens (the same
 * `ProposalMessage` thread, read from either side) so the two can never
 * visually drift into two different conversation languages.
 *
 * Deliberately presentation-only: takes the already-composed display
 * string and the raw `sentAt` instant, and nothing else — no message
 * fetching, no send logic, no read-state. Both callers keep their own
 * existing state/handlers entirely unchanged (`messagesByProposal`,
 * `handleSendMessage`, `isMessagingOpen`, …) — this component only changes
 * how a message already resolved by that logic is drawn.
 */
export function MessageBubble({ own, text, sentAt }: MessageBubbleProps) {
  const time = formatMessageTime(sentAt)
  return (
    <div className={`${styles.row} ${own ? styles.rowOwn : styles.rowOther}`}>
      <div className={`${styles.bubble} ${own ? styles.bubbleOwn : styles.bubbleOther}`}>
        <p className={styles.text}>{text}</p>
      </div>
      {time && (
        <span className={styles.time} aria-hidden="true">
          {time}
        </span>
      )}
    </div>
  )
}
