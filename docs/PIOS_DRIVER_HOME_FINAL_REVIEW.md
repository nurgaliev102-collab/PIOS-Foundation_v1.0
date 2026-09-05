# PIOS DriverHome — Final Visual/UX Review

**Scope:** `frontend/src/pages/DriverHome/DriverHome.tsx` and its module CSS/components only, after the information-hierarchy fix (`docs/PIOS_DRIVER_HOME_UX_AUDIT.md` → reorder + COMPLETE confirmation). Read-only: no code was changed to produce this report. No backend, API, authorization, Coordinator, OwnerControlCenter, PassengerLanding, AI Advisor, or First Refusal code was inspected or touched.

**Method:** direct reading of the current `DriverHome.tsx`, `DriverHome.module.css`, `RideStatus.tsx`, `RequestCard.tsx`, `ActiveRidePanel.tsx`, `StatusMessage.tsx`, `Button.tsx`/`.module.css`, and `QRCard.tsx`'s feedback rendering — no code was run, no build/test was executed (not required for a static/visual review and explicitly excluded from this task's scope).

---

## 1. Final verdict

**CLOSE DRIVERHOME.**

The two concrete defects the prior audit named — buried active work/requests, and a ride that vanished on COMPLETE with no acknowledgment — are both verifiably fixed in the current code, at the exact scope the follow-up task authorized. Nothing found during this review rises to a level that should block moving to the next screen. Three non-blocking, pre-existing or explicitly out-of-scope observations are recorded in Section 10 as backlog, not as reasons to keep working on this screen.

---

## 2. OFFLINE

`AvailabilityStatus` is the first substantive content under the page title and the "Как это работает" replay link. When `driver.availability === 'UNAVAILABLE'`, it shows a muted (not red) dot, the driver's own current-state label, a secondary hint line, and a single primary button whose label is the one available action ("Выйти на линию").

- **Is it clear the driver is offline?** Yes — dot color + label are the first thing after the page title.
- **Is it clear how to go online?** Yes — exactly one button, `variant="primary"`, no competing action beside it.
- **Is it clear what's required right now?** Yes — there is nothing else to do; "Ваши заказы" (immediately below) will honestly show "Пока нет заказов" until the driver goes online and one arrives, which is the correct state to show rather than hiding the section entirely.

No issues.

---

## 3. ONLINE / NO WORK

`AvailabilityStatus` flips to the green dot + "Уйти с линии". Directly beneath it, "Ваши заказы" renders its own empty-state line ("Пока нет заказов. Как только клиент оформит поездку, она появится здесь.") — a single sentence, `.status` styling, not a loud empty-state graphic.

- **Is it clear the driver is online?** Yes.
- **Is it clear there's no work yet?** Yes — plain, honest, unhidden.
- **Is it clear what to do next?** Reasonably — growth/QR/install content is now below this, so a driver who wants to do something while waiting (share their link) finds it by scrolling, not by it fighting for attention above the work section. This is the intended trade-off of the reorder and it holds up.

No issues.

---

## 4. NEW REQUEST

`RequestCard` for an `OPEN` proposal is now the **second** thing on the page (immediately after `AvailabilityStatus`), ahead of all growth/QR/install/passenger content — this is the reorder's central fix and it is confirmed in the code (`DriverHome.tsx:1141-1314`, the "Ваши заказы" block closes before the growth fragment opens).

- **Visible without significant scrolling?** Yes, for the normal case of one open request — it sits right under a compact availability card.
- **Obvious a decision is required?** Yes — `RequestCard` is `elevated` (raised shadow) with its own accent-blue left rule, and carries the `RideStatus` badge "Ожидает вашего решения" in `information` tone.
- **Primary action sufficiently prominent?** Yes — "Принять" is `variant="primary"`, "Отклонить" is `variant="secondary"`, so Accept reads as the encouraged action without Decline being hidden or disabled.
- **Any competing CTA?** None inside the card. On the page as a whole, the only actions above it are the availability toggle and "Как это работает" — both already resolved by the time a driver is looking at a new request, not competing with it.

No issues.

---

## 5. ACTIVE RIDE (ACCEPTED / ARRIVED / IN_PROGRESS)

`ActiveRidePanel` occupies the same "second on page" position as `RequestCard` when the proposal is `ACCEPTED`. It carries a success-green left rule (deliberately different from `RequestCard`'s blue, per its own KDoc) and exactly one primary button, chosen by the assignment's own status: "Прибыл" → "Начать поездку" → "Завершить поездку". No secondary controls compete inside the panel.

- **Immediately clear the ride is active?** Yes — distinct visual treatment from a still-open request, plus the `RideStatus` badge showing the live stage.
- **Next action clear?** Yes — one button, its label always names the very next step, never a generic "Update".
- **Do QR/growth/passengers compete?** No — they are now entirely below "Ваши заказы", both in DOM order and typically off the first viewport while an active ride is showing.
- **Excess text?** No — `ProposalDetails` renders only the fields that exist (pickup/destination/price/ETA), same as before the reorder; nothing was added.
- **Well-differentiated from a request card?** Yes, by design (accent color + absence of Accept/Decline pair).

**One real, unresolved gap** (see Section 10, item 1): `visibleProposals.map(...)` has no priority ordering between an `ACCEPTED` ride and a separate `OPEN` proposal if both exist for the same driver at once — the render order simply follows whatever order the backend returned. The audit's own target hierarchy phrased "current work" as if it were a single slot ("ActiveRidePanel if active, RequestCard if open"), but the code does not enforce that only one appears, nor does it sort an active ride above a newer open request. I did not verify whether Dispatch's own matching rules ever actually produce this concurrent state for one driver — flagged honestly as unverified, not as a confirmed bug.

---

## 6. COMPLETE

Flow: `IN_PROGRESS` → "Завершить поездку" → `respondToAssignment(..., 'complete')` succeeds → `showCompletionFeedback('Поездка завершена')` fires → `<StatusMessage tone="success">` renders at the top of "Ваши заказы" (`DriverHome.tsx:1190`), independent of whether any proposal cards remain → the underlying `visibleProposals` filter (unchanged, ADR-040) drops the now-`COMPLETED` order from the list in the same update.

- **Is the feedback actually visible?** Yes — it renders unconditionally in the "Ваши заказы" section header area, not tied to the card that just disappeared, so it cannot vanish along with the card it refers to.
- **Does it conflict with other feedback/toast states?** No. `QRCard`'s own `feedback` prop (copy/share toast, `QRCard.tsx:91-93`) is a separate `<p>` rendered inside `QRCard` itself, which now lives well below "Ваши заказы" in the growth section — different DOM location, independent state (`completionFeedback`/`completionFeedbackTimeout` vs `feedback`/`feedbackTimeout`), so one can never overwrite or visually collide with the other.
- **Does the screen correctly return to a waiting state?** Yes — if that was the driver's only proposal, "Поездка завершена" and the empty-state line ("Пока нет заказов…") render together for the 3-second window, which reads as a coherent close-out rather than a contradiction, then the confirmation clears on its own and the screen is left in the same ONLINE/NO WORK state reviewed in Section 3.

No issues.

---

## 7. Visual hierarchy

Target (from the audit): AvailabilityStatus → current work → Growth/QR → Passengers → Install/secondary. Actual, as read from `DriverHome.tsx:1141-1385`:

1. Page title + "Как это работает" + `AvailabilityStatus`
2. "Ваши заказы" (empty state / RequestCard / ActiveRidePanel / resolved Card, in whatever order the backend returns them — see Section 5's caveat)
3. Growth card ("Сегодня" / Новых клиентов)
4. `DriverCard` + `QRCard`
5. Install card (conditional on `!isStandalone()`)
6. "Мои пассажиры" (conditional on `connections.length > 0`)
7. "Выйти"

This matches the mandated target order exactly (P0 work above P2/P3 growth content). The one deviation from the audit's *phrasing* — not its *scope* — is the internal ordering within "Ваши заказы" itself when multiple proposals exist (Section 5), which the reorder task never asked to touch.

---

## 8. Mobile UX

- **First viewport:** with zero or one active/pending proposal, everything through "Ваши заказы" (title, availability, section header, one card) comfortably fits a typical phone viewport before any growth/QR content is reached. With two or more concurrent proposals, scrolling further into "Ваши заказы" itself is expected and correct — that reflects real work, not layout noise.
- **Touch targets:** `Button` uses `min-height: var(--pios-touch-target-min)` (`Button.module.css:6`), and the plain `<input>`/legacy fields use `min-height: 44px` (`DriverHome.module.css:76`) — consistent with the ≥44px requirement carried over from Task 8.
- **Visual noise:** the "Сегодня" growth card and "Мои пассажиры" reuse the same `.growthCard` shape — consistent, not duplicated noise.
- **Repeating elements:** none found beyond that intentional shared card shape.
- **Long texts:** the "Ваши заказы" hint paragraph is unchanged from before this task and is a single sentence; no new long text was introduced.
- **Breathing room:** `.content` keeps a consistent `1rem` gap between siblings (`1.25rem` on wider viewports); nothing was tightened or loosened by the reorder itself.
- **"Stack of independent cards" feel:** still present — each section (`AvailabilityStatus`, growth card, install card, passenger list) remains its own visually discrete white box with no shared connecting element beyond consistent spacing/radius. The reorder fixes *what* the driver sees first; it does not change *how* the page feels compositionally. This was already true before the reorder and was explicitly out of this task's scope (no visual-style changes without necessity) — recorded as backlog, not a regression.

---

## 9. PIOS identity

The screen still opens on "Мой бизнес" as its own heading, and the growth/QR/passenger content — now positioned as the *second* priority, after active work rather than competing with it — continues to carry the "manage your own client base" framing (today's new clients, the invitation QR, the named passenger list). Positioning it below "Ваши заказы" arguably strengthens this framing rather than weakening it: a driver first sees "here is your current work," then "here is the business you're building," which is closer to an entrepreneur's own mental model than either section fighting for the top slot.

That said, the core interaction for "Ваши заказы" itself — an incoming offer to Accept or Decline — is still the same request-queue mechanic a generic aggregator app would use. That is a property of the underlying product model (`docs/PRODUCT_DECISION_ENTREPRENEUR_MODEL.md`: "the personal invitation link is the whole of \[the entrepreneur model\]"), not something this reorder task touched or was asked to touch, so this is a description, not a defect.

---

## 10. Remaining issues

1. **No explicit priority ordering within "Ваши заказы" when multiple proposals coexist** (Section 5). If a driver ever has both an `ACCEPTED` active ride and a separate `OPEN` proposal at the same time, `visibleProposals.map(...)` renders them in whatever order the backend returned, not "active ride first." Not confirmed to be a reachable state in practice — worth a Dispatch-side check before deciding whether it needs a fix.
2. **The page still reads as a set of independent cards** rather than one connected surface (Section 8) — a pre-existing, explicitly out-of-scope condition for this reorder task, not introduced or worsened by it.
3. **Two independently-implemented transient-feedback mechanisms** (`feedback`/`feedbackTimeout` for copy/share, `completionFeedback`/`completionFeedbackTimeout` for ride completion) duplicate the same pattern rather than sharing one. They do not conflict at runtime (Section 6), but it is code-level duplication a future pass could consolidate.

None of these block closing this round of work.

---

## 11. Recommendation for next task

Move on to the next screen. Log the three items above as backlog for whenever DriverHome is revisited (not urgent): confirm with Dispatch whether concurrent OPEN+ACCEPTED proposals for one driver are actually reachable before deciding whether item 1 needs a sort; consider a shared feedback/toast primitive in a later design-system pass (item 3) rather than as a DriverHome-specific fix; leave item 2 (card-stack visual cohesion) for an explicitly-scoped visual-design task, since fixing it here would require the visual-style changes this and the prior task were both told to avoid.

---

## 12. Git status before/after

No files were created, modified, or deleted by this review except this report itself (`docs/PIOS_DRIVER_HOME_FINAL_REVIEW.md`, new).

**Before** (`git status --short | wc -l`): 107
**After:** identical working tree plus this one new untracked file. No commit, no push, no reset, no clean was performed.
