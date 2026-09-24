export const PRODUCTION_PUBLIC_ORIGIN = 'https://piosapp.ru'

export function resolvePublicOrigin(configuredOrigin = process.env.PUBLIC_ORIGIN) {
  const value = configuredOrigin?.trim()
  return value || PRODUCTION_PUBLIC_ORIGIN
}
