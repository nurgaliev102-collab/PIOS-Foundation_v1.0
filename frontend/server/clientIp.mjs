import { isIP } from 'node:net'

function isLoopback(address) {
  return address === '::1' || /^127\./.test(address) || /^::ffff:127\./i.test(address)
}

/** The tunnel connects locally; only that socket may supply Cloudflare's client IP. */
export function clientIpForBackend(remoteAddress, cloudflareHeader) {
  const remote = typeof remoteAddress === 'string' && isIP(remoteAddress) ? remoteAddress : null
  const cloudflare = typeof cloudflareHeader === 'string' && isIP(cloudflareHeader) ? cloudflareHeader : null
  if (remote && isLoopback(remote) && cloudflare) return cloudflare
  return remote
}
