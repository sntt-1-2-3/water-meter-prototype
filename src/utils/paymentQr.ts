/** QR links carry no token, customer identity or trusted amount; the server authorizes every request. */
export function paymentUrl(id: number, origin: string, configuredBase?: string) {
  if (!Number.isSafeInteger(id) || id < 1) throw new Error('账单编号无效')
  const base = new URL(configuredBase?.trim() || origin)
  if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.search || base.hash)
    throw new Error('支付页面地址必须是无凭据、无参数的 HTTP(S) 地址')
  // This app uses history routing at the domain root.
  if (base.pathname !== '/') throw new Error('VITE_PUBLIC_APP_URL 请配置网站根地址，不要包含子路径')
  return new URL('/pay/' + id, base).href
}
export function isLoopbackUrl(url: string) {
  return ['localhost', '127.0.0.1', '[::1]'].includes(new URL(url).hostname)
}
