export function money(value: number | string | null | undefined) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', minimumFractionDigits: 2 }).format(Number(value || 0))
}
export function number(value: number | string | null | undefined) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(Number(value || 0))
}
export function errorMessage(error: unknown) { return error instanceof Error ? error.message : '请求失败，请稍后重试' }
export function dateTime(value?: string | null) { return value ? value.replace('T', ' ').slice(0, 19) : '—' }
export function paymentKey(id: number) {
  const key = 'water-payment-key-' + id
  let value = sessionStorage.getItem(key)
  if (!value) {
    value = typeof crypto.randomUUID === 'function' ? crypto.randomUUID()
      : Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
    sessionStorage.setItem(key, value)
  }
  return value
}
