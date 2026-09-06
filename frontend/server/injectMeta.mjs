/**
 * Growth Loops TZ v1, Phase 1 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 1):
 * pure HTML transform that gives a driver's personal invitation link
 * (`/i/:driverCode`) a real link-preview card in WhatsApp/Telegram/VK
 * instead of a bare URL — those crawlers read raw HTML and do not execute
 * JavaScript, so this has to exist before React ever mounts.
 *
 * Deliberately name-only: `GET /v1/drivers/{id}` (driver-management's own
 * `DriverResponse`) carries no photo and no rating today — inventing either
 * in this card would be fabricating data the domain doesn't have. Kept as a
 * pure string→string function (no HTTP, no fs) so it is unit-testable
 * without spinning up the real server.
 */

const HEAD_CLOSE = '</head>'

export function buildDriverPreviewMeta({ driverName, canonicalUrl, iconUrl }) {
  const title = `${driverName} — личный водитель в PIOS`
  const description = `Заказывайте поездки напрямую у ${driverName}, без звонков и поиска номера.`
  const escape = (value) =>
    value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  return [
    `<title>${escape(title)}</title>`,
    `<meta name="description" content="${escape(description)}" />`,
    `<meta property="og:type" content="website" />`,
    `<meta property="og:title" content="${escape(title)}" />`,
    `<meta property="og:description" content="${escape(description)}" />`,
    `<meta property="og:url" content="${escape(canonicalUrl)}" />`,
    `<meta property="og:image" content="${escape(iconUrl)}" />`,
    `<meta name="twitter:card" content="summary" />`,
    `<meta name="twitter:title" content="${escape(title)}" />`,
    `<meta name="twitter:description" content="${escape(description)}" />`,
  ].join('\n    ')
}

/**
 * Replaces the static `<title>PIOS</title>` this app's own `index.html`
 * ships with the driver-specific block above, and appends the rest of the
 * `og:*`/`twitter:*` tags just before `</head>` — leaves every other tag
 * (fonts, favicon, viewport) untouched, and leaves `index.html` on disk
 * itself unmodified: this only ever transforms an in-memory copy of it, for
 * this one route, at request time.
 */
export function injectDriverPreview(html, { driverName, canonicalUrl, iconUrl }) {
  const metaBlock = buildDriverPreviewMeta({ driverName, canonicalUrl, iconUrl })
  const withoutStaticTitle = html.replace(/<title>.*?<\/title>/, '')
  if (!withoutStaticTitle.includes(HEAD_CLOSE)) {
    // No </head> found -- return the original document unmodified rather
    // than producing broken HTML real users would then receive.
    return html
  }
  return withoutStaticTitle.replace(HEAD_CLOSE, `    ${metaBlock}\n  ${HEAD_CLOSE}`)
}
