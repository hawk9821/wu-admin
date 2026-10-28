import { useUserStore } from '@/store/user'
import type { MenuTreeNode } from '@/types/api'

export type PermissionValue = string | string[]

export interface CheckMenuOptions {
  /** 是否排除按钮级节点（type=3）。页面访问级权限判断应传 true，按钮/接口级判断保持默认 false。 */
  excludeButton?: boolean
}

/**
 * 递归检查菜单树中是否存在目标权限标识
 * @param options.excludeButton 为 true 时跳过 type===3 的按钮节点，仅命中目录(type=1)/页面(type=2)级节点，
 *   用于页面访问权限判断，避免按钮权限被误当作页面访问权限（快捷入口越权根因）。
 */
export function checkPermissionFromMenus(
  menus: MenuTreeNode[] | undefined,
  permission: string,
  options?: CheckMenuOptions,
): boolean {
  if (!menus?.length) return false

  for (const menu of menus) {
    if (options?.excludeButton && menu.type === 3) continue
    if (menu.permission === permission) return true
    if (menu.children?.length && checkPermissionFromMenus(menu.children as MenuTreeNode[], permission, options)) {
      return true
    }
  }
  return false
}

/**
 * 权限判断核心方法
 * 1. 管理员（admin / super_admin 角色）拥有所有权限
 * 2. 具备通配符 '*:*:*' 权限拥有所有权限
 * 3. 匹配 userStore.userInfo.permissions 权限码集合（支持按钮与接口级权限）
 * 4. 匹配 userStore.menus 菜单树（支持页面级权限）
 */
export function hasPermission(permission: PermissionValue): boolean {
  const userStore = useUserStore()
  const roles = userStore.userInfo?.roles || []
  if (roles.includes('admin') || roles.includes('super_admin')) {
    return true
  }

  const codes = userStore.userInfo?.permissions || []
  if (codes.includes('*:*:*')) {
    return true
  }

  const permissionList = Array.isArray(permission) ? permission : [permission]
  return permissionList.some(
    perm => codes.includes(perm) || checkPermissionFromMenus(userStore.menus, perm)
  )
}

/**
 * 从菜单树中判断是否拥有某权限标识（页面访问级）
 * 说明：后端下发的 userStore.menus 同时包含目录/页面与按钮(type=3)节点，
 * 页面访问权限判断会排除 type=3 按钮节点，避免按钮权限被误当作页面访问权限。
 * 若页面级菜单未命中，会自动回退检查 userStore.userInfo.permissions 或超级管理员角色。
 */
export function hasMenuPermission(menus: MenuTreeNode[] | undefined, permission: string): boolean {
  if (checkPermissionFromMenus(menus, permission, { excludeButton: true })) {
    return true
  }
  const userStore = useUserStore()
  const roles = userStore.userInfo?.roles || []
  if (roles.includes('admin') || roles.includes('super_admin')) {
    return true
  }
  const codes = userStore.userInfo?.permissions || []
  return codes.includes('*:*:*') || codes.includes(permission)
}

/**
 * 页面访问级可达性判断（供路由守卫使用）
 * - 管理员（admin / super_admin）或全局通配符 '*:*:*' 拥有全部页面权限；
 * - 其余仅以「用户页面级菜单树」为准（排除 type=3 按钮节点），
 *   不回退 user.permissions 的具体按钮权限码，避免按钮码被当作页面访问权限导致越权。
 */
export function hasPageAccessByUser(
  menus: MenuTreeNode[] | undefined,
  permission: string,
  user?: { roles?: string[]; permissions?: string[] },
): boolean {
  const roles = user?.roles || []
  const codes = user?.permissions || []
  if (roles.includes('admin') || roles.includes('super_admin') || codes.includes('*:*:*')) {
    return true
  }
  return checkPermissionFromMenus(menus, permission, { excludeButton: true })
}

/** 兼容简写：从侧栏菜单树判断页面级权限（与后端 @PreAuthorize 标识一致）。
 *  跳过 type=3 按钮节点，仅命中目录/页面级，确保按钮权限不会被误判为页面访问权限。 */
export function hasMenuPerm(
  menus: { permission?: string; children?: unknown[]; type?: number }[] | undefined,
  perm: string,
): boolean {
  if (!menus?.length) return false
  for (const menu of menus) {
    if (menu.type === 3) continue
    if (menu.permission === perm) return true
    if (menu.children?.length && hasMenuPerm(menu.children as typeof menus, perm)) return true
  }
  return false
}

export function hasMonitorPerm(
  permissions: string[] | undefined,
  menus: { permission?: string; children?: unknown[]; type?: number }[] | undefined,
  perm: string,
): boolean {
  return (permissions || []).includes(perm) || hasMenuPerm(menus, perm)
}

/**
 * 是否可使用企业 IM。
 * 仅以侧栏菜单树为准（与路由鉴权一致）：系统菜单里停用企业 IM 后不会出现在 menus 中，
 * 此时不应请求群聊接口，避免无权限 403 弹窗。
 */
export function hasChatPerm(
  _permissions: string[] | undefined,
  menus: { permission?: string; children?: unknown[]; type?: number }[] | undefined,
): boolean {
  return hasMenuPerm(menus, 'system:chat:list') || hasMenuPerm(menus, 'system:chat:query')
}
