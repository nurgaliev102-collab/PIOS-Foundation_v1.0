import { describe, expect, it } from 'vitest'
import { injectDriverPreview } from './injectMeta.mjs'

const BASE_HTML = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <title>PIOS</title>
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>
`

describe('injectDriverPreview', () => {
  it('replaces the static title with the driver-specific one', () => {
    const result = injectDriverPreview(BASE_HTML, {
      driverName: 'Александр',
      canonicalUrl: 'https://home-pc.tail385153.ts.net/i/driver-123',
      iconUrl: 'https://home-pc.tail385153.ts.net/pios-icon.svg',
    })
    expect(result).toContain('<title>Александр — личный водитель в PIOS</title>')
    expect(result).not.toContain('<title>PIOS</title>')
  })

  it('adds og: and twitter: tags with the driver name and canonical url', () => {
    const result = injectDriverPreview(BASE_HTML, {
      driverName: 'Мария',
      canonicalUrl: 'https://home-pc.tail385153.ts.net/i/driver-456',
      iconUrl: 'https://home-pc.tail385153.ts.net/pios-icon.svg',
    })
    expect(result).toContain('property="og:title" content="Мария — личный водитель в PIOS"')
    expect(result).toContain('property="og:url" content="https://home-pc.tail385153.ts.net/i/driver-456"')
    expect(result).toContain('property="og:image" content="https://home-pc.tail385153.ts.net/pios-icon.svg"')
    expect(result).toContain('name="twitter:card" content="summary"')
  })

  it('escapes a driver name containing HTML-significant characters', () => {
    const result = injectDriverPreview(BASE_HTML, {
      driverName: 'Аня "Такси" & Co <script>',
      canonicalUrl: 'https://home-pc.tail385153.ts.net/i/driver-789',
      iconUrl: 'https://home-pc.tail385153.ts.net/pios-icon.svg',
    })
    expect(result).not.toContain('<script>')
    expect(result).toContain('&quot;Такси&quot;')
    expect(result).toContain('&lt;script&gt;')
  })

  it('preserves every other existing tag untouched', () => {
    const result = injectDriverPreview(BASE_HTML, {
      driverName: 'Сергей',
      canonicalUrl: 'https://home-pc.tail385153.ts.net/i/driver-999',
      iconUrl: 'https://home-pc.tail385153.ts.net/pios-icon.svg',
    })
    expect(result).toContain('<meta charset="UTF-8" />')
    expect(result).toContain('<div id="root"></div>')
  })

  it('returns the original document unmodified if </head> is missing', () => {
    const brokenHtml = '<html><body>no head here</body></html>'
    const result = injectDriverPreview(brokenHtml, {
      driverName: 'Игорь',
      canonicalUrl: 'https://home-pc.tail385153.ts.net/i/driver-1',
      iconUrl: 'https://home-pc.tail385153.ts.net/pios-icon.svg',
    })
    expect(result).toBe(brokenHtml)
  })
})
