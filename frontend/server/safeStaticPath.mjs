import path from 'node:path'

/** Resolve a request path only when it remains inside the built frontend directory. */
export function safeStaticPath(distDir, pathname) {
  let decoded
  try {
    decoded = decodeURIComponent(pathname)
  } catch {
    return null
  }
  const filePath = path.join(distDir, decoded)
  const relative = path.relative(distDir, filePath)
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    return null
  }
  return filePath
}
