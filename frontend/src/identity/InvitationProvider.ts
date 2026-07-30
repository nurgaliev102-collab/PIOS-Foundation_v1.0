/**
 * ADR-038, Этап 4/5: wraps the invitation-link mechanism that already
 * exists and already works (`currentDriver.ts::invitationLinkFor`,
 * `DriverHome.tsx`'s Copy/Share handlers) behind an interface, per this
 * ADR's own "use the new model where possible without changing behavior"
 * instruction — this is the one piece of Этап 5 where full integration
 * *was* possible today, unlike [ContactProvider], since nothing about
 * sharing a link depends on the not-yet-authorized `network-management`
 * wiring.
 *
 * [LocalInvitationProvider] below is a straight extraction of
 * `DriverHome.tsx`'s prior inline logic — same `window.location.origin`
 * link, same `navigator.clipboard`/`navigator.share` calls, same fallback
 * order. Behavior is unchanged; only where the logic lives has changed.
 * Swapping this for a provider backed by `network-management`'s real
 * `Invitation` aggregate (ADR-037) later touches only this file.
 */
export interface InvitationProvider {
  linkFor(driverId: string): string
  copy(link: string): Promise<void>
  share(link: string): Promise<void>
}

export class LocalInvitationProvider implements InvitationProvider {
  linkFor(driverId: string): string {
    return `${window.location.origin}/i/${driverId}`
  }

  async copy(link: string): Promise<void> {
    await navigator.clipboard.writeText(link)
  }

  async share(link: string): Promise<void> {
    if (navigator.share) {
      await navigator.share({ title: 'PIOS', text: 'Вас пригласили в PIOS', url: link })
      return
    }
    // Fallback for browsers without the Web Share API: copy instead.
    await navigator.clipboard.writeText(link)
  }
}
