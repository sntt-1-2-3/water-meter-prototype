import type { UserSession } from '../types/domain'

const TOKEN_KEY = 'water-token'
const SESSION_KEY = 'water-session'
export const authExpiredEvent = 'water-auth-expired'
localStorage.removeItem('water-registered-users')
export function readToken() { return localStorage.getItem(TOKEN_KEY) || '' }
export function readSession(): UserSession | null {
  try {
    const value = JSON.parse(localStorage.getItem(SESSION_KEY) || 'null') as UserSession | null
    return value && ['ADMIN', 'OPERATOR', 'FINANCE', 'AUDITOR', 'USER'].includes(value.role) ? value : null
  } catch { return null }
}
export function persistSession(session: UserSession, token?: string) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  localStorage.removeItem('water-registered-users')
}
export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(SESSION_KEY)
  localStorage.removeItem('water-registered-users')
}
