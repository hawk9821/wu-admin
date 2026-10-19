import type { App, Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/store/user'
import type { MenuTreeNode } from '@/types/api'

type PermissionValue = string | string[]

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
 * 权限控制指令
 * 使用方式: v-permission="'system:user:create'" 或 v-permission="['system:user:create', 'system:user:update']"
 */
export const permission: Directive<HTMLElement, PermissionValue> = {
  mounted(el, binding) {
    const requiredPermissions = binding.value
    if (!requiredPermissions) return

    if (!hasPermission(requiredPermissions)) {
      el.parentNode?.removeChild(el)
    }
  },
}

/**
 * 从菜单树中判断是否拥有某权限标识
 * 说明：后端下发的 userStore.menus 仅包含目录与页面（已过滤 type=3 按钮），
 * 若菜单树未命中，会自动回退检查 userStore.userInfo.permissions 或超级管理员角色，避免按钮权限被误判为无权限。
 */
export function hasMenuPermission(menus: MenuTreeNode[], permission: string): boolean {
  if (checkPermissionFromMenus(menus, permission)) {
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

function checkPermissionFromMenus(menus: MenuTreeNode[] | undefined, permission: string): boolean {
  if (!menus?.length) return false

  for (const menu of menus) {
    if (menu.permission === permission) return true
    if (menu.children?.length && checkPermissionFromMenus(menu.children, permission)) {
      return true
    }
  }
  return false
}

/**
 * 角色控制指令
 * 使用方式: v-role="'admin'" 或 v-role="['admin', 'editor']"
 */
export const role: Directive<HTMLElement, PermissionValue> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const userRoles = userStore.userInfo.roles || []
    const requiredRoles = binding.value
    if (!requiredRoles) return

    const roleList = Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles]
    const hasRole = roleList.some(r => userRoles.includes(r))

    if (!hasRole) {
      el.parentNode?.removeChild(el)
    }
  },
}

/** 注册全局指令 */
export function setupPermissionDirectives(app: App): void {
  app.directive('permission', permission as Directive)
  app.directive('role', role as Directive)
}

export type PermissionBinding = DirectiveBinding<PermissionValue>
