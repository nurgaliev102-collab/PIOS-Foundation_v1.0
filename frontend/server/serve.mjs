#!/usr/bin/env node
/**
 * Growth Loops TZ v1, Phase 1 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 1) --
 * a minimal replacement for `vite preview` in production. `vite preview`
 * cannot inject per-route `<meta>` tags, and a driver's personal invitation
 * link (`/i/:driverCode`) needs exactly that: WhatsApp/Telegram/VK read raw
 * HTML and do not execute JavaScript, so without this, every shared link
 * shows a bare URL instead of the driver's name.
 *
 * Does three things, in this order for every request:
 *   1. `/i/:driverCode` or `/i/:driverCode/request` -> fetch the driver's
 *      name server-side and serve a copy of index.html with driver-specific
 *      <meta> tags injected (injectMeta.mjs). Real browsers still load the
 *      identical SPA underneath -- this only changes what a crawler sees.
 *   2. A path matching one of `BACKEND_ROUTES` -> reverse-proxied to that
 *      backend module, unchanged, so the app's own runtime API calls keep
 *      working exactly as they did under `vite preview`'s own `preview.proxy`
 *      (the two now share one source of truth, see backendRoutes.mjs).
 *   3. Everything else -> served as a static file from `dist/` if it
 *      exists; otherwise falls back to `dist/index.html` unmodified (the
 *      same SPA-fallback behavior `vite preview` already provides for
 *      client-side routes it doesn't recognize as a file).
 *
 * Deliberately zero new npm dependencies -- only Node's own `http`/`fs`,
 * kept small enough to read in one sitting rather than pulling in Express
 * for what amounts to a few dozen lines of routing.
 */
import http from 'node:http'
import fs from 'node:fs'
import fsp from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { resolveBackendTarget, BACKEND_ROUTES } from './backendRoutes.mjs'
import { clientIpForBackend } from './clientIp.mjs'
import { safeStaticPath } from './safeStaticPath.mjs'
import { injectDriverPreview } from './injectMeta.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DIST_DIR = path.resolve(__dirname, '..', 'dist')
const INDEX_HTML_PATH = path.join(DIST_DIR, 'index.html')

const HOST = process.env.HOST ?? '0.0.0.0'
const PORT = Number(process.env.PORT ?? 4173)
// Used to build the absolute og:url / og:image a crawler needs -- these
// tags must be absolute URLs, a relative one is invalid per the Open Graph
// spec. Defaults to this machine's own public Tailscale Funnel hostname
// (docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md), overridable for local testing.
const PUBLIC_ORIGIN = process.env.PUBLIC_ORIGIN ?? 'https://home-pc.tail385153.ts.net'

const DRIVER_CODE_ROUTE = /^\/i\/([^/]+)(?:\/request)?\/?$/

const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.webmanifest': 'application/manifest+json',
  '.woff2': 'font/woff2',
}

let indexHtmlCache = null
async function readIndexHtml() {
  if (indexHtmlCache) {
    return indexHtmlCache
  }
  indexHtmlCache = await fsp.readFile(INDEX_HTML_PATH, 'utf-8')
  return indexHtmlCache
}

// 60s TTL: WhatsApp/Telegram/VK routinely re-fetch a link several times
// while a card is being composed/re-shared -- there is no reason to hit
// Driver Management once per crawler fetch for a name that essentially
// never changes minute-to-minute.
const DRIVER_NAME_CACHE_TTL_MS = 60_000
const driverNameCache = new Map()

async function fetchDriverName(driverCode) {
  const cached = driverNameCache.get(driverCode)
  if (cached && Date.now() - cached.fetchedAt < DRIVER_NAME_CACHE_TTL_MS) {
    return cached.name
  }
  const driverManagementBaseUrl = resolveBackendTarget('/v1/drivers')
  const response = await fetch(`${driverManagementBaseUrl}/v1/drivers/${encodeURIComponent(driverCode)}`)
  if (!response.ok) {
    driverNameCache.set(driverCode, { name: null, fetchedAt: Date.now() })
    return null
  }
  const driver = await response.json()
  const name = driver.displayName ?? null
  driverNameCache.set(driverCode, { name, fetchedAt: Date.now() })
  return name
}

async function serveDriverPreview(req, res, driverCode) {
  const [rawHtml, driverName] = await Promise.all([readIndexHtml(), fetchDriverName(driverCode)])
  if (!driverName) {
    // Unknown or nameless driver code: same document a real browser would
    // get anyway (PassengerLanding itself renders the "not-found" state) --
    // no crawler-specific card to build without a name.
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
    res.end(rawHtml)
    return
  }
  const html = injectDriverPreview(rawHtml, {
    driverName,
    canonicalUrl: `${PUBLIC_ORIGIN}${req.url}`,
    iconUrl: `${PUBLIC_ORIGIN}/pios-icon.svg`,
  })
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
  res.end(html)
}

function proxyRequest(req, res, targetBaseUrl) {
  const target = new URL(req.url, targetBaseUrl)
  const headers = { ...req.headers, host: target.host }
  // Never relay a caller-supplied identity for the guest-creation limiter.
  // cloudflared reaches this server through a loopback socket and supplies
  // CF-Connecting-IP; direct external clients are keyed by their socket IP.
  delete headers['x-pios-client-ip']
  const clientIp = clientIpForBackend(req.socket.remoteAddress, req.headers['cf-connecting-ip'])
  delete headers['cf-connecting-ip']
  delete headers['x-forwarded-for']
  if (clientIp) headers['x-pios-client-ip'] = clientIp
  const proxyReq = http.request(
    target,
    { method: req.method, headers },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode ?? 502, proxyRes.headers)
      proxyRes.pipe(res)
    }
  )
  proxyReq.on('error', () => {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('Bad gateway')
  })
  req.pipe(proxyReq)
}

// Root cause of a whole session's worth of "old design" reports (2026-09-07):
// this server never sent a Cache-Control header at all, leaving every
// response -- including index.html itself -- to each browser's own
// caching heuristics. index.html is the one file that must never be
// cached, since it is what points a returning visitor at the current
// build's hashed asset filenames; a mobile browser (observed: iOS Safari,
// both as a plain tab and as an installed PWA) caching it long-term is
// exactly what makes a real redeploy invisible to a real device while
// every server-side check keeps passing. Only `/assets/*` (Vite's own
// content-hashed output -- a new build always gets new filenames) is safe
// to cache aggressively; everything else gets `no-store`.
function cacheControlFor(pathname) {
  return pathname.startsWith('/assets/') ? 'public, max-age=31536000, immutable' : 'no-store'
}

async function serveStaticOrFallback(req, res, pathname) {
  const filePath = safeStaticPath(DIST_DIR, pathname)
  // A string-prefix check is insufficient: dist-private is a sibling, not
  // a child of dist. The helper uses path.relative and rejects that case.
  if (!filePath) {
    res.writeHead(400)
    res.end()
    return
  }
  const hasExtension = path.extname(pathname) !== ''
  if (hasExtension && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const contentType = CONTENT_TYPES[path.extname(pathname)] ?? 'application/octet-stream'
    res.writeHead(200, { 'Content-Type': contentType, 'Cache-Control': cacheControlFor(pathname) })
    fs.createReadStream(filePath).pipe(res)
    return
  }
  if (hasExtension) {
    res.writeHead(404)
    res.end('Not found')
    return
  }
  // No extension: an SPA client-side route (e.g. `/me`, `/coordinator`) --
  // same fallback `vite preview` already provides.
  const html = await readIndexHtml()
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
  res.end(html)
}

const server = http.createServer((req, res) => {
  let pathname
  try {
    pathname = new URL(req.url, 'http://localhost').pathname
  } catch {
    res.writeHead(400)
    res.end('Bad request')
    return
  }

  const driverCodeMatch = pathname.match(DRIVER_CODE_ROUTE)
  if (driverCodeMatch && req.method === 'GET') {
    serveDriverPreview(req, res, driverCodeMatch[1]).catch((error) => {
      console.error('[pios-frontend] driver preview failed:', error)
      res.writeHead(500)
      res.end('Internal error')
    })
    return
  }

  const backendTarget = resolveBackendTarget(pathname)
  if (backendTarget) {
    proxyRequest(req, res, backendTarget)
    return
  }

  serveStaticOrFallback(req, res, pathname).catch((error) => {
    console.error('[pios-frontend] static serve failed:', error)
    res.writeHead(500)
    res.end('Internal error')
  })
})

server.listen(PORT, HOST, () => {
  console.log(`[pios-frontend] serving ${DIST_DIR} on http://${HOST}:${PORT}`)
  console.log(`[pios-frontend] proxying ${Object.keys(BACKEND_ROUTES).length} backend route prefixes`)
})
