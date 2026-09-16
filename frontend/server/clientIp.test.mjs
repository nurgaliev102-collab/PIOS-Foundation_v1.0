import { expect, test } from 'vitest'
import { clientIpForBackend } from './clientIp.mjs'

test('ignores forged Cloudflare IP on an external socket', () => {
  expect(clientIpForBackend('198.51.100.8', '203.0.113.44')).toBe('198.51.100.8')
})

test('accepts a valid Cloudflare IP only from the local tunnel', () => {
  expect(clientIpForBackend('::1', '203.0.113.44')).toBe('203.0.113.44')
  expect(clientIpForBackend('::ffff:127.0.0.1', '2001:db8::2')).toBe('2001:db8::2')
})

test('rejects non-address strings from the tunnel', () => {
  expect(clientIpForBackend('127.0.0.1', 'spoofed-customer')).toBe('127.0.0.1')
})
