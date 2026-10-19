import { get, post, del } from '@/utils/request'
import type { PageResult } from '@/types/api'

export interface SecurityEventVO {
  id: number
  eventType: string
  principal: string
  ipaddr: string
  requestUri: string
  requestMethod: string
  userAgent: string
  riskDelta: number
  totalRiskScore: number
  action: string
  reason: string
  createTime: string
}

export interface SecurityEventPageQuery {
  pageNo?: number
  pageSize?: number
  ipaddr?: string
  eventType?: string
  action?: string
}

export interface RiskSubjectVO {
  principal: string
  riskScore: number
  blocked: boolean
  remainingSeconds: number
  reason: string
}

export interface SecurityStatsVO {
  todayTotal: number
  todayBlocked: number
  todayUaBlock: number
  todayReplayBlock: number
  activeBlockedCount: number
}

/** 分页获取安全拦截事件 */
export function getSecurityEvents(params: SecurityEventPageQuery) {
  return get<PageResult<SecurityEventVO>>('/system/security/events', params)
}

/** 获取安全拦截统计数据 */
export function getSecurityStats() {
  return get<SecurityStatsVO>('/system/security/stats')
}

/** 获取当前被封禁的风险名单 */
export function getRiskList() {
  return get<RiskSubjectVO[]>('/system/security/risk-list')
}

/** 一键解除风险封禁 */
export function unblockRiskSubject(principal: string) {
  return post<boolean>('/system/security/unblock', null, {
    params: { principal },
  })
}

/** 手动封禁风险主体 */
export function blockRiskSubject(
  principal: string,
  durationSeconds = 3600,
  reason = '管理员手动封禁',
) {
  return post<boolean>('/system/security/block', null, {
    params: { principal, durationSeconds, reason },
  })
}

/** 删除单条安全事件 */
export function deleteSecurityEvent(id: number) {
  return del<boolean>(`/system/security/events/${id}`)
}

/** 清空安全事件日志 */
export function clearSecurityEvents() {
  return del<boolean>('/system/security/events/clear')
}
