import { describe, expect, it } from 'vitest'
import { hasMenuPerm, hasMonitorPerm, hasChatPerm } from '@/utils/hasMenuPerm'

const menus = [
  {
    permission: 'system:user:list',
    children: [
      { permission: 'system:user:create' },
    ],
  },
  {
    permission: 'system:role:list',
    children: [],
  },
]

/** 按钮级节点（type=3）不应被当作页面访问权限 */
const menusWithButton = [
  {
    type: 2,
    permission: 'system:config:list',
    children: [],
  },
  {
    type: 3,
    permission: 'system:config:edit',
    children: [],
  },
  {
    type: 3,
    permission: 'system:approval:list',
    children: [],
  },
]

describe('hasMenuPerm', () => {
  it('returns false for empty menus', () => {
    expect(hasMenuPerm(undefined, 'system:user:list')).toBe(false)
    expect(hasMenuPerm([], 'system:user:list')).toBe(false)
  })

  it('matches permission on root node', () => {
    expect(hasMenuPerm(menus, 'system:role:list')).toBe(true)
  })

  it('matches permission on nested child', () => {
    expect(hasMenuPerm(menus, 'system:user:create')).toBe(true)
  })

  it('returns false when permission not found', () => {
    expect(hasMenuPerm(menus, 'system:dept:list')).toBe(false)
  })

  it('ignores button-level (type=3) nodes for page-level permission', () => {
    // 仅存在按钮级节点时，不作为页面访问权限（快捷入口越权根因）
    expect(hasMenuPerm(menusWithButton, 'system:approval:list')).toBe(false)
  })

  it('still matches page-level (type=2) nodes', () => {
    expect(hasMenuPerm(menusWithButton, 'system:config:list')).toBe(true)
  })

  it('keeps matching nodes without type field for backward compatibility', () => {
    expect(hasMenuPerm(menus, 'system:user:create')).toBe(true)
  })
})

describe('hasMonitorPerm', () => {
  it('returns true when permission array contains perm', () => {
    expect(hasMonitorPerm(['monitor:cache:query'], [], 'monitor:cache:query')).toBe(true)
  })

  it('falls back to menu tree when not in permission array', () => {
    expect(hasMonitorPerm([], menus, 'system:user:list')).toBe(true)
  })

  it('returns false when neither source has perm', () => {
    expect(hasMonitorPerm([], menus, 'monitor:server:query')).toBe(false)
  })
})

describe('hasChatPerm', () => {
  const chatMenus = [{ permission: 'system:chat:list', children: [] }]

  it('returns true when chat menu is in tree', () => {
    expect(hasChatPerm([], chatMenus)).toBe(true)
  })

  it('returns false when chat menu is absent', () => {
    expect(hasChatPerm(['system:chat:query'], [])).toBe(false)
    expect(hasChatPerm(['system:user:list'], menus)).toBe(false)
  })
})
