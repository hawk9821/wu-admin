<template>
  <div class="app-container module-page security-log-page">
    <!-- 顶部概览 Hero 卡片 -->
    <el-card class="search-card module-hero-card" shadow="never">
      <div class="module-hero-row">
        <div class="module-hero-text">
          <div class="module-hero-title">
            <ModulePageIcon :icon="MODULE_PAGE_ICON.securityLog" />
            <span>安全风控</span>
          </div>
          <p class="module-hero-desc">
            全链路反爬虫嗅探、防重放攻击、HMAC-SM3 国密验签拦截与动态行为风控闭环
          </p>
        </div>
        <div class="security-stats-group">
          <div class="stat-pill stat-pill-primary">
            <div class="stat-num">{{ stats.todayTotal }}</div>
            <div class="stat-label">今日拦截</div>
          </div>
          <div class="stat-pill stat-pill-warning">
            <div class="stat-num">{{ stats.todayUaBlock }}</div>
            <div class="stat-label">爬虫拦截</div>
          </div>
          <div class="stat-pill stat-pill-danger">
            <div class="stat-num">{{ stats.todayReplayBlock }}</div>
            <div class="stat-label">重放攻击</div>
          </div>
          <div class="stat-pill stat-pill-banned">
            <div class="stat-num">{{ stats.activeBlockedCount }}</div>
            <div class="stat-label">当前封禁 IP</div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 标签页导航 -->
    <el-card class="content-card" shadow="never">
      <el-tabs v-model="activeTab" class="security-tabs" @tab-change="handleTabChange">
        <!-- 标签页 1：安全拦截审计事件 -->
        <el-tab-pane label="安全拦截事件" name="events">
          <!-- 检索表单 -->
          <el-form :model="queryParams" inline class="module-search-form">
            <el-form-item label="客户端 IP">
              <el-input v-model="queryParams.ipaddr" placeholder="请输入 IP 地址" clearable style="width: 170px" />
            </el-form-item>
            <el-form-item label="事件类型">
              <el-select v-model="queryParams.eventType" placeholder="全部类型" clearable style="width: 160px">
                <el-option label="自动化爬虫 UA" value="UA_BLOCK" />
                <el-option label="重放攻击 (Nonce)" value="ANTI_REPLAY" />
                <el-option label="数字签名篡改" value="SIGN_FAIL" />
                <el-option label="时间戳过期" value="TIMESTAMP_INVALID" />
                <el-option label="风控自动封禁" value="RISK_BLOCK" />
              </el-select>
            </el-form-item>
            <el-form-item label="处置动作">
              <el-select v-model="queryParams.action" placeholder="全部动作" clearable style="width: 140px">
                <el-option label="直接阻断 (403)" value="BLOCKED" />
                <el-option label="触发封禁" value="FORBIDDEN" />
                <el-option label="安全告警" value="WARNING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
              <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <!-- 工具栏 -->
          <div class="table-toolbar">
            <span class="toolbar-title">拦截事件审计明细</span>
            <div class="toolbar-actions">
              <el-button
                type="danger"
                plain
                :icon="Delete"
                v-permission="'system:security:clear'"
                @click="handleClearEvents"
              >
                清空事件
              </el-button>
              <el-button :icon="RefreshRight" @click="loadEventData">刷新</el-button>
            </div>
          </div>

          <!-- 事件数据表格 -->
          <el-table
            :data="eventList"
            v-loading="eventLoading"
            border
            stripe
            class="security-table"
          >
            <el-table-column prop="id" label="ID" width="70" align="center" />
            <el-table-column prop="ipaddr" label="客户端 IP" width="140" align="center">
              <template #default="{ row }">
                <el-link type="primary" :underline="false" @click="filterByIp(row.ipaddr)">
                  {{ row.ipaddr }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column prop="eventType" label="事件类型" width="150" align="center">
              <template #default="{ row }">
                <el-tag :type="getEventTypeTagType(row.eventType)" effect="light">
                  {{ formatEventTypeName(row.eventType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="触发规则与原因" min-width="200" show-overflow-tooltip />
            <el-table-column label="请求端点" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="method-badge">{{ row.requestMethod }}</span>
                <span class="uri-text">{{ row.requestUri }}</span>
              </template>
            </el-table-column>
            <el-table-column label="风险分值" width="130" align="center">
              <template #default="{ row }">
                <span class="risk-delta">+{{ row.riskDelta }}</span>
                <span class="risk-total">({{ row.totalRiskScore }}分)</span>
              </template>
            </el-table-column>
            <el-table-column prop="action" label="处置结果" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.action === 'FORBIDDEN' ? 'danger' : 'warning'" size="small">
                  {{ row.action === 'FORBIDDEN' ? '触发封禁' : '已阻断(403)' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="拦截时间" width="170" align="center" />
            <el-table-column label="操作" width="140" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-buttons">
                  <el-button type="primary" link size="small" @click="showDetail(row)">详情</el-button>
                  <el-button
                    type="danger"
                    link
                    size="small"
                    v-permission="'system:security:block'"
                    @click="quickBlockIp(row.ipaddr)"
                  >
                    拉黑
                  </el-button>
                  <el-button
                    type="danger"
                    link
                    size="small"
                    v-permission="'system:security:delete'"
                    @click="handleDeleteEvent(row)"
                  >
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <el-pagination
            v-model:current-page="queryParams.pageNo"
            v-model:page-size="queryParams.pageSize"
            :total="eventTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            class="table-pagination"
            @size-change="loadEventData"
            @current-change="loadEventData"
          />
        </el-tab-pane>

        <!-- 标签页 2：风险名单管控与一键解封 -->
        <el-tab-pane label="风险封禁名单" name="risk">
          <div class="table-toolbar">
            <span class="toolbar-title">当前处于系统限制与封禁中的风险主体（共 {{ riskList.length }} 个）</span>
            <div class="toolbar-actions">
              <el-button
                type="primary"
                :icon="Plus"
                v-permission="'system:security:block'"
                @click="openManualBlockDialog"
              >
                手动封禁 IP
              </el-button>
              <el-button :icon="RefreshRight" @click="loadRiskData">刷新名单</el-button>
            </div>
          </div>

          <el-table
            :data="riskList"
            v-loading="riskLoading"
            border
            stripe
            class="security-table"
          >
            <el-table-column prop="principal" label="风险主体 IP" width="180" align="center">
              <template #default="{ row }">
                <span class="font-mono font-bold">{{ row.principal }}</span>
              </template>
            </el-table-column>
            <el-table-column label="风险分与警戒等级" width="220">
              <template #default="{ row }">
                <div class="risk-progress-wrap">
                  <el-progress
                    :percentage="Math.min(100, Math.round((row.riskScore / 120) * 100))"
                    :status="row.riskScore >= 120 ? 'exception' : (row.riskScore >= 80 ? 'warning' : '')"
                    :stroke-width="8"
                  />
                  <span class="risk-score-text">{{ row.riskScore }} / 120 分</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="封禁原因" min-width="220" show-overflow-tooltip />
            <el-table-column label="剩余封禁时长" width="180" align="center">
              <template #default="{ row }">
                <el-tag type="danger" effect="plain" class="countdown-tag">
                  {{ formatRemainingSeconds(row.remainingSeconds) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="管控操作" width="130" fixed="right" align="center">
              <template #default="{ row }">
                <el-button
                  type="success"
                  size="small"
                  :icon="CircleCheck"
                  v-permission="'system:security:unblock'"
                  @click="handleUnblock(row.principal)"
                >
                  一键解封
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="当前暂无处于封禁状态的风险 IP，系统运行平稳" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 事件详情抽屉 Drawer -->
    <el-drawer v-model="detailVisible" title="安全拦截事件详情" size="520px">
      <div v-if="activeEvent" class="detail-container">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="事件 ID">{{ activeEvent.id }}</el-descriptions-item>
          <el-descriptions-item label="事件类型">
            <el-tag :type="getEventTypeTagType(activeEvent.eventType)">
              {{ formatEventTypeName(activeEvent.eventType) }} ({{ activeEvent.eventType }})
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客户端 IP">{{ activeEvent.ipaddr }}</el-descriptions-item>
          <el-descriptions-item label="请求端点">
            {{ activeEvent.requestMethod }} {{ activeEvent.requestUri }}
          </el-descriptions-item>
          <el-descriptions-item label="风险分值">
            本次增量 +{{ activeEvent.riskDelta }}，当前总计 {{ activeEvent.totalRiskScore }} 分
          </el-descriptions-item>
          <el-descriptions-item label="处置动作">
            {{ activeEvent.action === 'FORBIDDEN' ? '自动临时封禁' : '直接 403 阻断' }}
          </el-descriptions-item>
          <el-descriptions-item label="拦截时间">{{ activeEvent.createTime }}</el-descriptions-item>
          <el-descriptions-item label="详细触发原因">
            <span class="text-danger font-bold">{{ activeEvent.reason }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="完整 User-Agent">
            <div class="ua-block">{{ activeEvent.userAgent || '无 User-Agent 头' }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <!-- 手动封禁对话框 Dialog -->
    <el-dialog v-model="blockDialogVisible" title="手动封禁风险主体 IP" width="460px">
      <el-form :model="blockForm" label-width="100px">
        <el-form-item label="目标 IP" required>
          <el-input v-model="blockForm.ip" placeholder="例如：192.168.1.100" />
        </el-form-item>
        <el-form-item label="封禁时长">
          <el-select v-model="blockForm.duration" style="width: 100%">
            <el-option label="1 小时" :value="3600" />
            <el-option label="6 小时" :value="21600" />
            <el-option label="24 小时 (1天)" :value="86400" />
            <el-option label="7 天" :value="604800" />
            <el-option label="30 天" :value="2592000" />
          </el-select>
        </el-form-item>
        <el-form-item label="封禁原因">
          <el-input v-model="blockForm.reason" placeholder="例如：恶意扫描 / 批量刷接口" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="blockDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmManualBlock">确认封禁</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Refresh,
  RefreshRight,
  Delete,
  Plus,
  CircleCheck,
} from '@element-plus/icons-vue'
import ModulePageIcon from '@/components/ModulePageIcon.vue'
import { MODULE_PAGE_ICON } from '@/constants/module-page-icons'
import {
  getSecurityEvents,
  getSecurityStats,
  getRiskList,
  unblockRiskSubject,
  blockRiskSubject,
  deleteSecurityEvent,
  clearSecurityEvents,
  type SecurityEventVO,
  type SecurityEventPageQuery,
  type RiskSubjectVO,
  type SecurityStatsVO,
} from '@/api/system/security'

const activeTab = ref('events')

// 大盘指标数据
const stats = reactive<SecurityStatsVO>({
  todayTotal: 0,
  todayBlocked: 0,
  todayUaBlock: 0,
  todayReplayBlock: 0,
  activeBlockedCount: 0,
})

// 拦截事件查询参数
const queryParams = reactive<SecurityEventPageQuery>({
  pageNo: 1,
  pageSize: 10,
  ipaddr: '',
  eventType: '',
  action: '',
})

const eventLoading = ref(false)
const eventList = ref<SecurityEventVO[]>([])
const eventTotal = ref(0)

// 风险名单
const riskLoading = ref(false)
const riskList = ref<RiskSubjectVO[]>([])

// 详情抽屉
const detailVisible = ref(false)
const activeEvent = ref<SecurityEventVO | null>(null)

// 手动封禁弹窗
const blockDialogVisible = ref(false)
const blockForm = reactive({
  ip: '',
  duration: 3600,
  reason: '管理员手动封禁',
})

// 格式化事件类型名称
function formatEventTypeName(type: string): string {
  switch (type) {
    case 'UA_BLOCK':
      return '爬虫工具拦截'
    case 'ANTI_REPLAY':
      return '重放攻击拦截'
    case 'SIGN_FAIL':
      return '数字签名篡改'
    case 'TIMESTAMP_INVALID':
      return '时间戳超窗'
    case 'RISK_BLOCK':
      return '风控累计封禁'
    default:
      return type || '未知事件'
  }
}

// 事件类型标签样式
function getEventTypeTagType(type: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' | undefined {
  switch (type) {
    case 'UA_BLOCK':
      return 'warning'
    case 'ANTI_REPLAY':
      return 'danger'
    case 'SIGN_FAIL':
      return 'danger'
    case 'TIMESTAMP_INVALID':
      return 'info'
    case 'RISK_BLOCK':
      return 'danger'
    default:
      return undefined
  }
}

// 格式化剩余秒数
function formatRemainingSeconds(sec: number): string {
  if (!sec || sec <= 0) return '已到期'
  if (sec < 60) return `${sec} 秒`
  const minutes = Math.floor(sec / 60)
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const remMinutes = minutes % 60
  if (hours < 24) return `${hours} 小时 ${remMinutes} 分`
  const days = Math.floor(hours / 24)
  const remHours = hours % 24
  return `${days} 天 ${remHours} 小时`
}

// 加载大盘统计
async function loadStats() {
  try {
    const res = await getSecurityStats()
    if (res.data) {
      Object.assign(stats, res.data)
    }
  } catch {}
}

// 加载事件列表
async function loadEventData() {
  eventLoading.value = true
  try {
    const res = await getSecurityEvents(queryParams)
    if (res.data) {
      eventList.value = res.data.list || []
      eventTotal.value = res.data.total || 0
    }
  } catch (err) {
    console.error('加载安全事件失败', err)
  } finally {
    eventLoading.value = false
  }
}

// 加载风险名单
async function loadRiskData() {
  riskLoading.value = true
  try {
    const res = await getRiskList()
    if (res.data) {
      riskList.value = res.data || []
      stats.activeBlockedCount = riskList.value.length
    }
  } catch (err) {
    console.error('加载风险名单失败', err)
  } finally {
    riskLoading.value = false
  }
}

function handleTabChange(tab: string | number) {
  if (tab === 'events') {
    loadEventData()
  } else if (tab === 'risk') {
    loadRiskData()
  }
}

function handleQuery() {
  queryParams.pageNo = 1
  loadEventData()
}

function resetQuery() {
  queryParams.ipaddr = ''
  queryParams.eventType = ''
  queryParams.action = ''
  queryParams.pageNo = 1
  loadEventData()
}

function filterByIp(ip: string) {
  queryParams.ipaddr = ip
  handleQuery()
}

function showDetail(row: SecurityEventVO) {
  activeEvent.value = row
  detailVisible.value = true
}

// 一键解封
async function handleUnblock(ip: string) {
  try {
    await ElMessageBox.confirm(`确认立即解除对 IP【${ip}】的限制与封禁吗？解除后该 IP 可正常访问。`, '确认解封', {
      type: 'warning',
      confirmButtonText: '立即解封',
      cancelButtonText: '取消',
    })
    await unblockRiskSubject(ip)
    ElMessage.success(`IP【${ip}】已成功解封！`)
    loadRiskData()
    loadStats()
  } catch {}
}

// 快速拉黑 IP
function quickBlockIp(ip: string) {
  blockForm.ip = ip
  blockForm.duration = 3600
  blockForm.reason = '管理员从事件明细中手动拉黑'
  blockDialogVisible.value = true
}

function openManualBlockDialog() {
  blockForm.ip = ''
  blockForm.duration = 3600
  blockForm.reason = '管理员手动封禁'
  blockDialogVisible.value = true
}

async function confirmManualBlock() {
  if (!blockForm.ip.trim()) {
    ElMessage.warning('请输入待封禁的 IP 地址')
    return
  }
  try {
    await blockRiskSubject(blockForm.ip.trim(), blockForm.duration, blockForm.reason)
    ElMessage.success(`已成功将 IP【${blockForm.ip}】加入封禁黑名单！`)
    blockDialogVisible.value = false
    loadRiskData()
    loadStats()
  } catch (err: any) {
    ElMessage.error(err?.message || '封禁操作失败')
  }
}

// 删除单条事件
async function handleDeleteEvent(row: SecurityEventVO) {
  try {
    await ElMessageBox.confirm(`确认删除此条安全拦截事件记录吗？`, '提示', {
      type: 'warning',
    })
    await deleteSecurityEvent(row.id)
    ElMessage.success('删除成功')
    loadEventData()
    loadStats()
  } catch {}
}

// 清空所有事件
async function handleClearEvents() {
  try {
    await ElMessageBox.confirm('确认清空所有的安全拦截事件审计日志吗？清空后不可恢复！', '高危警告', {
      type: 'error',
      confirmButtonText: '确认清空',
      cancelButtonText: '取消',
    })
    await clearSecurityEvents()
    ElMessage.success('安全事件日志已清空')
    loadEventData()
    loadStats()
  } catch {}
}

onMounted(() => {
  loadStats()
  loadEventData()
})
</script>

<style scoped>
.security-log-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.security-stats-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.stat-pill {
  min-width: 104px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  text-align: center;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}

.stat-pill:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.25);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.15), 0 6px 18px rgba(0, 0, 0, 0.25);
}

.stat-pill-primary .stat-num {
  color: #38bdf8;
  text-shadow: 0 0 14px rgba(56, 189, 248, 0.35);
}

.stat-pill-warning .stat-num {
  color: #fbbf24;
  text-shadow: 0 0 14px rgba(251, 191, 36, 0.35);
}

.stat-pill-danger .stat-num {
  color: #f87171;
  text-shadow: 0 0 14px rgba(248, 113, 113, 0.35);
}

.stat-pill-banned {
  background: rgba(239, 68, 68, 0.14);
  border-color: rgba(239, 68, 68, 0.32);
}

.stat-pill-banned:hover {
  background: rgba(239, 68, 68, 0.2);
  border-color: rgba(239, 68, 68, 0.5);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.15), 0 6px 18px rgba(239, 68, 68, 0.3);
}

.stat-pill-banned .stat-num {
  color: #f87171;
  text-shadow: 0 0 14px rgba(239, 68, 68, 0.45);
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.5px;
  font-feature-settings: 'tnum';
}

.stat-label {
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.72);
  margin-top: 4px;
  letter-spacing: 0.3px;
}

.content-card {
  border-radius: 8px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.toolbar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.method-badge {
  display: inline-block;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 700;
  border-radius: 4px;
  background: var(--el-fill-color-darker);
  color: var(--el-text-color-regular);
  margin-right: 6px;
}

.uri-text {
  font-family: monospace;
  font-size: 12px;
}

.risk-delta {
  font-weight: 700;
  color: var(--el-color-danger);
  margin-right: 4px;
}

.risk-total {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.risk-progress-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.risk-score-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.countdown-tag {
  font-family: monospace;
  font-weight: 600;
}

.ua-block {
  font-family: monospace;
  font-size: 12px;
  background: var(--el-fill-color-light);
  padding: 8px;
  border-radius: 4px;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 150px;
  overflow-y: auto;
}

.font-mono {
  font-family: monospace;
}
.font-bold {
  font-weight: 700;
}
</style>
