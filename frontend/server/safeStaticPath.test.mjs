import path from 'node:path'
import { expect, test } from 'vitest'
import { safeStaticPath } from './safeStaticPath.mjs'

const distDir = path.resolve('frontend', 'dist')

test('resolves a normal built asset under dist', () => {
  expect(safeStaticPath(distDir, '/assets/app.js')).toBe(path.join(distDir, 'assets', 'app.js'))
})

test('rejects traversal into a sibling whose name begins with dist', () => {
  expect(safeStaticPath(distDir, '/..%2fdist-private%2fsecret.txt')).toBeNull()
  if (path.sep === '\\') {
    expect(safeStaticPath(distDir, '/..\\dist-private\\secret.txt')).toBeNull()
  }
})

test('rejects malformed escaping', () => {
  expect(safeStaticPath(distDir, '/bad%zz')).toBeNull()
})
