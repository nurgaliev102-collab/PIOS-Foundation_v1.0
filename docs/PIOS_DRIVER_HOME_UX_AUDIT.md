# PIOS DriverHome UX Audit

**Date:** 2026-09-05. **Scope:** read-only. No code, CSS, or component was modified. `git status --short` recorded before and after this audit — both counts identical (105 entries), confirming the working tree is unchanged; see the end of this report for the exact comparison. No `npm run build`/`test` was executed (both could rewrite build artifacts; not needed for a read-only UX review). No backend, API, authorization, `Coordinator`, `OwnerControlCenter`, `PassengerLanding`, AI Advisor, or First Refusal code was read with intent to change anything.

---

## 1. Executive Verdict

**NEEDS REWORK.**

Not because the component-level migration (Task 8: `AvailabilityStatus`, `RideStatus`, `RequestCard`, `ActiveRidePanel`) was done badly — it demonstrably fixed the two concrete gaps it targeted (an `OPEN` proposal now reads as "needs a decision," an active ride now reads as visually distinct from a pending one). The problem is one level up, at the screen's own information architecture, which this task's own scope explicitly, deliberately left untouched pending confirmation. The actionable content — new requests, and critically, an **active, in-progress ride** — still sits at the very bottom of the page, below five blocks of business/growth/invitation content. A driver mid-ride, glancing at their phone for two seconds to confirm their next step (exactly the moment the design brief itself singles out as needing to be frictionless), must scroll past all of it first. A component redesign cannot fix a structural ordering problem; only reordering can, and that step was correctly deferred, not executed.

## 2. First 5 Seconds

What actually renders, top to bottom, the instant the "ready" screen paints (`DriverHome.tsx:1111-1196`, then `:1199-1329`):

1. "Мой бизнес" (page title)
2. "Как это работает" (onboarding replay link)
3. `AvailabilityStatus` — status + toggle
4. "Сегодня / Новых клиентов" (growth counter)
5. `DriverCard` — name + availability, again
6. `QRCard` — a 336px-wide QR code image + link + copy/share
7. A hint sentence
8. Install-PIOS card (first-time/non-standalone only)
9. "Мои пассажиры" (conditional passenger list)
10. **"Ваши заказы"** — the actual requests/active ride

**Can the driver instantly tell:**
- **Their own status?** Yes — `AvailabilityStatus` is third on the page, correctly high, dot + text + button, no reading required.
- **Whether there's work?** **No, not without scrolling.** Whether a new request exists, or a ride is already in progress, is not visible in the first viewport on a real phone — it is the tenth thing on the page, after four to five cards of business/growth content.
- **A new proposal?** Same answer — buried below the fold.
- **An active ride?** Same answer, and this is the more serious case (see Section 5) — an `ACCEPTED` ride, mid-trip, is exactly as buried as an untouched `OPEN` request, despite the brief's own explicit instruction that this moment specifically needs to be fast and minimal.
- **What to do next?** Not obvious without scrolling. `AvailabilityStatus`'s own action (toggle online/offline) is instantly clear; but "do I have a ride to handle" is not answerable in the first five seconds.

## 3. Current Information Hierarchy

| Block | Current position | Priority | Problem |
|---|---|---|---|
| `AvailabilityStatus` | 1st (after title/replay link) | **P0** | None — correctly placed |
| "Сегодня / Новых клиентов" (growth) | 2nd | P2 | Useful, but ranked above the actual work |
| `DriverCard` | 3rd | P3 | **Redundant** — re-shows availability (`DriverCard.tsx:76`, `"На линии"/"Не на линии"`) that `AvailabilityStatus`, one block above it, already shows; the only new fact is the driver's own name, which they already know |
| `QRCard` + hint | 4th | P1 | Product-critical (Section 6), but not urgent on every single app open — currently ranked as if it were |
| Install card | 5th (conditional) | P3 | Fine as low priority, correctly conditional |
| "Мои пассажиры" | 6th (conditional) | P2 | Useful, correctly below the QR/invite mechanism it stems from |
| "Ваши заказы" (`RequestCard`/`ActiveRidePanel`/resolved) | **Last**, 10th | **P0** | The screen's actual job — buried under every P1/P2/P3 block above it |

The existing order is **not** correct merely because it exists — it inverts P0 (open requests, active ride) below P1/P2/P3 content almost entirely.

## 4. Recommended Information Hierarchy

Not implemented — a proposed order only, based strictly on the priorities in Section 3, no new content invented:

1. Page title / onboarding-replay link (unchanged)
2. `AvailabilityStatus` (unchanged position — already correct)
3. **"Ваши заказы"** (`RequestCard`/`ActiveRidePanel`/resolved cards) — moved directly under availability
4. Growth card ("Сегодня / Новых клиентов")
5. `QRCard` + hint (the entrepreneur-model mechanism, Section 6 — still prominent, just no longer ahead of active work)
6. "Мои пассажиры"
7. Install card (unchanged, already low and conditional)
8. `DriverCard` — candidate for removal entirely (Section 8/9), not merely reordering, since its one non-redundant fact (the driver's own name) could be folded into `AvailabilityStatus` or the page header instead of a fifth standalone card

This is the same P1 finding the prior audit already raised and which this task's own instructions again say not to implement without separate confirmation — restated here with the added, more specific evidence Section 5 below surfaces (the active-ride case, not just the general request case).

## 5. Driver Journey

`OFFLINE → ONLINE → REQUEST → ACCEPT → ARRIVE → START → COMPLETE`

| State | What the driver sees | Next action | Obvious? | Extra elements? | Error-prone? | Visual continuity |
|---|---|---|---|---|---|---|
| **OFFLINE** | `AvailabilityStatus`: muted dot, "Сегодня не работаю," button "Выйти на линию" | Tap the one button | Yes | No | No | — |
| **ONLINE, no request** | Dot turns green, "Я на линии"; "Ваши заказы" (once scrolled to) reads "Пока нет заказов…" | Wait | Yes, once scrolled that far | The 5 business-content blocks between availability and this message | No | Empty state has a specific, honest message (`DriverHome.tsx:1221`) — good |
| **REQUEST arrives (OPEN)** | A new `RequestCard`: accent-ruled, elevated, `RideStatus` badge "Ожидает вашего решения," passenger/pickup/destination, price/ETA inputs, Принять/Отклонить | Accept or decline | Yes, **once the driver scrolls to it** — nothing above the fold signals a new request exists at all (no badge, no count, no notification) | Optional price/ETA fields sit between the details and the buttons — reasonable, not clutter | Low — Button's own `loading` state disables it during submission | `RequestCard`'s accent rule is new and consistent |
| **ACCEPT** | Card becomes `ActiveRidePanel`: success-green rule, `RideStatus` "Вы приняли," one button "Прибыл" (or none yet, if the Assignment hasn't loaded) | Drive to pickup, tap "Прибыл" on arrival | Yes, once the panel is visible | None | Low | Clear visual hand-off from `RequestCard` (blue rule) to `ActiveRidePanel` (green rule) — this specific transition is good |
| **ARRIVE** | `RideStatus` "Прибыл" (assignment status), one button "Начать поездку" | Tap when passenger is in the car | Yes | None | Low | Continuous |
| **START** | `RideStatus` "IN_PROGRESS," one button "Завершить поездку" | Tap on drop-off | Yes | None | Low | Continuous |
| **COMPLETE** | Card disappears entirely (`visibleProposals` filters out `COMPLETED`, `DriverHome.tsx:1090`) | None — driver is returned to whatever else is on the page | N/A | — | No | **No confirmation moment at all** — brief §6 ("Completed ride: Confirms the outcome... no interstitial platform messaging inserted between 'ride done' and 'back to my business'") asks for *a confirmation*, not *silence*; this screen currently gives silence, which is arguably under-shooting that requirement in the other direction |

**The one journey-wide problem, restated precisely:** every state from REQUEST through COMPLETE is genuinely well-designed *once visible* — the individual `RequestCard`→`ActiveRidePanel` hand-off is the single cleanest thing this audit found. The failure is that none of these states are visible without scrolling past unrelated content first, and this applies with the most real-world consequence exactly to ACCEPT/ARRIVE/START — the states brief §6 explicitly says the driver is "often not looking at their phone" during, and which therefore most need to be instantly findable in the two seconds they do glance at it.

## 6. PIOS Business Model Alignment

This section scores noticeably better than the operational sections above — and the evidence needs stating precisely, because it changes what "fix" actually means here.

Per `docs/PRODUCT_DECISION_ENTREPRENEUR_MODEL.md` ("**[RATIFIED — read directly]**"): *"The personal invitation link is the whole of \[the entrepreneur model\]... not from a pool, not from a queue, not from any platform selection."* The QR/invite mechanism on `DriverHome` is not decorative growth content bolted onto a ride-hailing screen — per this project's own ratified product decision, **it is the single existing implementation of PIOS's entire stated business model.** Its prominence on this screen is therefore correct, not bloat.

- **Does the driver see their own customers?** Yes — "Мои пассажиры" (`DriverHome.tsx:1181-1195`), named where a name is known, honestly labelled "Пассажир по вашей ссылке" where it isn't (no fabricated name — matches `passengerNamesByReference`'s own KDoc discipline, `:277`).
- **Repeat-ride value?** Not directly surfaced — the passenger list shows *who*, not *how many times*, matching brief §7's own "people are named, not counted" — a deliberate absence, not a gap, per the brief's own anti-metric stance.
- **Growth over time?** Only "today" (`todaysNewClientCount`, `:294`) — no trend, no history. Consistent with this Sprint's own documented, narrow scope (H6, "Driver Growth Snapshot" — a same-day glance, not a report, per its own KDoc), not a defect this audit should invent a fix for.
- **QR/referral/growth understandable?** Yes — `QRCard` is a real, scannable code with copy/share, plain hint text.
- **Does growth functionality compete with the ride itself?** **Yes, positionally** — not conceptually. The content itself is correct and product-critical; its *rank ahead of the driver's own active work* is the actual problem (Sections 2-5), not its existence or prominence in general.

**Verdict for this level, specifically: the content is right; the order is wrong.** Recommendation Section 4 reflects that precisely — it moves "Ваши заказы" up, it does not move the QR/growth/passenger content down past a token afterthought position.

## 7. Mobile UX

- **Vertical hierarchy:** Poor for the reasons above — five stacked cards before the operationally critical content.
- **CTA size:** Fine — `Button`/`Input`/`Select` all carry `--pios-touch-target-min: 44px` (`tokens.css:142`), inherited automatically by every new component; no ad-hoc small tap targets found.
- **Touch targets:** Fine, same token.
- **Visual noise:** High for a screen meant to be checked quickly and often — `AvailabilityStatus`, growth card, `DriverCard`, `QRCard`, (conditionally) install card, (conditionally) passenger list, all as separate stacked `Card`-shaped blocks before a single request is visible.
- **Repetition:** Confirmed, concrete — `DriverCard` re-renders the exact same availability fact `AvailabilityStatus` already showed one block above it (Section 3).
- **Scrolling:** Substantial on a real phone — `QRCard` alone renders a 336px-wide QR image (`QRCard.tsx:47`); combined with the other four blocks, "Ваши заказы" is realistically two-plus screen-heights down on a typical device.
- **Fixed/sticky elements:** Only `Header` (`Header.module.css:8`, `position: sticky`) — the generic PIOS branding bar. `AvailabilityStatus` itself is not sticky; once scrolled down to view or act on a request, the driver's own online/offline state is no longer visible without scrolling back up. Minor on its own, compounds with the ordering problem.
- **Active-ride readability without reading:** The `ActiveRidePanel`'s own internal design is good — one status badge, one button, minimal text, exactly matching brief §6's "kept minimal" instruction. The problem is reaching it at all without scrolling, addressed above; this audit does **not** recommend adding any interaction affordance for use while driving — none was found, and none should be proposed.
- **Reading in motion:** No screen text is excessively long; `RequestCard`/`ActiveRidePanel` detail lines are short, single-fact lines (`ProposalDetails`, `:226-265`) — this part is already good.

## 8. Critical UX Problems

**P0**
- Active-ride and open-request content is not visible without scrolling past five unrelated blocks — directly contradicts brief §6's own "kept minimal, since this is a moment the driver is often not looking at their phone" for the single state that matters most.
- No visible signal, above the fold, that a new request or an active ride exists at all — a driver has no reason to scroll down unless they already suspect something is there.

**P1**
- `DriverCard` duplicates `AvailabilityStatus`'s own availability fact one block below it, adding visual weight with no new information beyond the driver's own already-known name.
- No confirmation moment on ride completion (Section 5) — the card simply vanishes; brief §6 asks for a confirmation, not silence.

**P2**
- `AvailabilityStatus` is not sticky/persistently visible while scrolled down into "Ваши заказы."
- Growth counter and passenger list, while correctly scoped (Section 6), still outrank the actual work positionally.

**P3**
- Onboarding-replay link ("Как это работает") and page title share visual weight with no clear typographic hierarchy between them (both plain, unstyled-by-design-system text — this page still uses raw `<h1 className={styles.pageTitle}>`/`<button className={styles.linkAction}>`, not `Heading`/`Text`, for these two elements specifically).

## 9. Recommended Changes

UX only, no code:

1. Move "Ваши заказы" (open requests + active ride) directly beneath `AvailabilityStatus`, ahead of the growth/QR/install/passenger content — the change Section 4 already specifies, still pending separate confirmation per this task's own standing constraint.
2. Remove `DriverCard` from this screen, or reduce it to name-only (no availability re-statement) — a decision for whoever owns this reorder, not a redesign of `DriverCard` itself (it remains correct and unchanged for `Coordinator`'s own use).
3. Add a brief, honest confirmation moment when a ride completes, before the card disappears — matching brief §6's own explicit instruction, not inventing a new one.
4. Consider a persistent (sticky or otherwise always-visible) availability indicator once scrolled past it — lower priority, only worth doing once item 1 is settled, since a shorter page reduces how often this actually matters.

None of the four items above is a new product feature — all are ordering/redundancy/confirmation fixes to content that already exists.

## 10. What NOT to Change

- **`AvailabilityStatus`'s own design** — dot + text + one button, first on the page, muted (not red) for "not working" — already correct, matches brief §6 and the already-established `DriverTrustIndicator` convention. Keep.
- **`RequestCard` vs `ActiveRidePanel`'s own visual distinction** — the accent-blue vs success-green left rule, elevated `Card`, `RideStatus` badge — this is the one genuinely well-executed piece of this whole screen. Keep exactly as built.
- **The QR/invite mechanism's prominence in general** (not its rank relative to "Ваши заказы," which Section 9 does address) — per the ratified Entrepreneur Model decision, this is the actual product, not decoration. Do not shrink, hide, or deprioritize it wholesale.
- **"Мои пассажиры"'s honest, non-fabricated naming** (`passengerNamesByReference`) — a passenger with no order yet reads as "Пассажир по вашей ссылке," never a guessed name. Keep.
- **The empty-state and error-state copy** — specific, honest messages (`DriverHome.tsx:1221`, `:1100-1101`, `:1210-1211`), no generic fallback string. Keep.
- **Touch target sizing, loading-state spinner convention, focus rings** — all correctly inherited from the design-system tokens/components as of Task 8. Keep.
- **The `assignment && (...)` gating on which action button appears** — correct, deliberate, unchanged business logic; no UX fix should touch this.

## 11. Proposed Next Implementation Step

**Reorder `DriverHome`'s top-level blocks per Section 4** — move "Ваши заказы" to directly follow `AvailabilityStatus`, ahead of the growth/QR/install/passenger content. This is the single change Sections 1, 2, 5, and 8 all converge on as the one structural fix the component-level work (Task 8) could not itself deliver, and it is already fully specified (Section 4) pending the separate confirmation this task's own instructions require before any implementation begins.

---

## Working-tree safety

`git status --short` before this audit: 105 entries. `git status --short` after this audit: 105 entries, identical. No file was created, modified, staged, or committed by this task other than this report itself. No `npm` command was run.
