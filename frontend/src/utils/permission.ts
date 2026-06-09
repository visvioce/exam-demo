/**
 * 角色权限工具
 * 角色层级：ADMIN 拥有 TEACHER 的全部权限（管理员 = 教师 + 用户管理 + 轮播图管理）
 */

/**
 * 判断用户是否拥有指定角色权限（含角色层级）
 */
export function hasRolePermission(userRole: string | undefined | null, requiredRoles: string[]): boolean {
  const role = userRole || ''
  if (role === 'ADMIN' && requiredRoles.includes('TEACHER')) {
    return true
  }
  return requiredRoles.includes(role)
}