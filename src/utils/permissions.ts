import type { Role } from '../types/domain'
export const roleLabels: Record<Role, string> = {
  ADMIN: '系统管理员', OPERATOR: '运营人员', FINANCE: '财务人员', AUDITOR: '审计人员', USER: '居民用户',
}
export const managementRoles: Role[] = ['ADMIN', 'OPERATOR', 'FINANCE', 'AUDITOR']
export const operationsRoles: Role[] = ['ADMIN', 'OPERATOR']
export const financeRoles: Role[] = ['ADMIN', 'FINANCE']
export function homeFor(role: Role) { return role === 'USER' ? '/user/home' : '/admin/dashboard' }
