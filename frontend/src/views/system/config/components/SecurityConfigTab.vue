<template>
  <div class="security-config-pane">
    <el-form :model="draft" label-width="140px" class="config-form security-form">
      <el-divider content-position="left">前端安全</el-divider>
      <el-form-item label="禁止前端调试">
        <el-switch :model-value="draft.disableDevtool" :disabled="!canEdit" @update:model-value="(v) => draft.disableDevtool = !!v" />
        <span class="unit">开启后将限制打开开发者工具（F12），降低随意查看源码与调试的风险</span>
      </el-form-item>
      <el-divider content-position="left">会话安全</el-divider>
      <el-form-item label="禁止多端同时在线">
        <el-switch :model-value="forbidConcurrentLogin" :disabled="!canEdit" @update:model-value="(v) => $emit('update:forbidConcurrentLogin', !!v)" />
        <span class="unit">开启后，同一账号再次登录会先踢掉之前的会话，只保留最新一次登录</span>
      </el-form-item>
      <el-divider content-position="left">API 安全防线与防重放</el-divider>
      <el-form-item label="SM2 密码加密">
        <el-switch :model-value="draft.sm2EncryptEnabled ?? true" :disabled="!canEdit" @update:model-value="(v) => draft.sm2EncryptEnabled = !!v" />
        <span class="unit">是否启用登录/注册/重置密码非对称加密传输（国密 SM2 椭圆公钥加密，防止明文口令嗅探，默认开启）</span>
      </el-form-item>
      <el-form-item label="SM4 数据加密">
        <el-switch :model-value="draft.sm4EncryptEnabled ?? false" :disabled="!canEdit" @update:model-value="(v) => draft.sm4EncryptEnabled = !!v" />
        <span class="unit">是否启用接口请求/响应数据加密（国密 SM4-GCM 认证加密，IV[12] + 密文 + Tag[16]）</span>
      </el-form-item>
      <el-form-item label="数字签名验签">
        <el-switch :model-value="draft.sm2SignEnabled ?? false" :disabled="!canEdit" @update:model-value="(v) => draft.sm2SignEnabled = !!v" />
        <span class="unit">是否启用接口签名验签（国密 HMAC-SM3 高性能签名防篡改）</span>
      </el-form-item>
      <el-form-item label="时间戳校验">
        <el-switch :model-value="draft.timestampEnabled ?? true" :disabled="!canEdit" @update:model-value="(v) => draft.timestampEnabled = !!v" />
        <span class="unit">校验请求时间，防止过期请求（5 分钟时间窗口）</span>
      </el-form-item>
      <el-form-item label="Nonce 校验">
        <el-switch :model-value="draft.nonceEnabled ?? true" :disabled="!canEdit" @update:model-value="(v) => draft.nonceEnabled = !!v" />
        <span class="unit">校验随机数，防止高频重放攻击（Redis 单次随机数查重）</span>
      </el-form-item>
      <el-divider content-position="left">风险分自动封禁</el-divider>
      <el-form-item label="临时封禁阈值">
        <el-input-number :model-value="draft.riskBlockScore ?? 80" :min="20" :max="1000" :disabled="!canEdit" @update:model-value="(v) => draft.riskBlockScore = Number(v) || 80" />
        <span class="unit">风险分达到后临时封禁 1 小时，默认 80</span>
      </el-form-item>
      <el-form-item label="24 小时封禁阈值">
        <el-input-number :model-value="draft.riskBanScore ?? 120" :min="40" :max="2000" :disabled="!canEdit" @update:model-value="(v) => draft.riskBanScore = Number(v) || 120" />
        <span class="unit">风险分达到后封禁 24 小时，必须高于临时封禁阈值，默认 120</span>
      </el-form-item>
    </el-form>
    <el-alert type="info" :closable="false" show-icon title="保存全部后立即生效：接口安全开关将即时作用于系统所有 REST 接口；时间戳与 Nonce 建议保持开启。" class="security-alert" />
  </div>
</template>

<script setup lang="ts">
import type { ConfigGroupMap } from '@/types/config'

defineProps<{
  draft: ConfigGroupMap['security']
  canEdit: boolean
  forbidConcurrentLogin: boolean
}>()

defineEmits<{
  'update:forbidConcurrentLogin': [value: boolean]
}>()
</script>

<style scoped>
.config-form.security-form {
  max-width: 980px;
}

.security-form :deep(.el-form-item__content) {
  flex-wrap: nowrap;
}

.security-form .unit {
  margin-left: 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary, #909399);
  white-space: nowrap;
  flex-shrink: 0;
}

.security-alert {
  max-width: 980px;
  margin-top: 16px;
}
</style>
