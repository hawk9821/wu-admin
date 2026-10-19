<template>
  <view class="module-form-page">
    <ModuleDarkHero
      :title="isCreate ? '新增角色' : '编辑角色'"
      :subtitle="isCreate ? '创建角色并配置权限标识' : '修改角色名称与状态'"
      icon="shield-o"
      theme="role"
    />

    <ListLoading v-if="loading" />

    <template v-else>
      <view class="form-section card--elevated">
        <view class="form-section__head">
          <ModuleIcon icon="shield-o" theme="role" size="sm" />
          <view class="form-section__intro">
            <text class="form-section__title">基本信息</text>
          </view>
        </view>

        <view class="form-fields">
          <FormCell
            v-model="form.name"
            label="角色名称"
            editable
            boxed
            placeholder="请输入角色名称"
          />
          <FormCell
            v-if="isCreate"
            v-model="form.code"
            label="角色编码"
            editable
            boxed
            placeholder="请输入角色编码"
          />
          <FormCell v-else label="角色编码" boxed :display-value="form.code" muted last />
        </view>
      </view>

      <view class="form-section card--elevated">
        <view class="form-section__head">
          <ModuleIcon icon="shield-o" theme="role" size="sm" />
          <view class="form-section__intro">
            <text class="form-section__title">数据权限</text>
            <text class="form-section__desc">控制该角色可查看的数据范围</text>
          </view>
        </view>

        <view class="form-fields">
          <FormCell
            label="数据范围"
            clickable
            boxed
            arrow
            :last="form.dataScope !== 2"
            @click="pickDataScope"
          >
            <text class="picker-value">{{ dataScopeLabel }}</text>
          </FormCell>
          <FormCell
            v-if="form.dataScope === 2"
            label="可见部门"
            clickable
            boxed
            arrow
            last
            @click="pickDept"
          >
            <text
              class="picker-value"
              :class="{ 'picker-value--muted': !form.dataScopeDeptIds.length }"
            >
              {{ selectedDeptLabel }}
            </text>
          </FormCell>
        </view>
      </view>

      <view class="form-section card--elevated">
        <view class="form-section__head">
          <ModuleIcon icon="setting-o" theme="role" size="sm" />
          <view class="form-section__intro">
            <text class="form-section__title">状态与备注</text>
          </view>
        </view>

        <view class="form-fields">
          <FormCell v-model="sortText" label="排序" editable boxed input-type="number" placeholder="0" />
          <FormCell label="状态" clickable boxed arrow @click="pickStatus">
            <text class="picker-value">{{ statusOptions[statusIndex]?.label }}</text>
          </FormCell>
          <FormCell v-model="form.remark" label="备注" editable boxed placeholder="选填" last />
        </view>
      </view>

      <PageFooter>
        <button class="page-footer__btn" :loading="saving" @click="save(roleId)">
          {{ isCreate ? '创建角色' : '保存' }}
        </button>
      </PageFooter>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ModuleDarkHero from '@/components/common/ModuleDarkHero/index.vue'
import ModuleIcon from '@/components/common/ModuleIcon/index.vue'
import ListLoading from '@/components/common/ListLoading/index.vue'
import FormCell from '@/components/common/FormCell/index.vue'
import PageFooter from '@/components/common/PageFooter/index.vue'
import { useRoleForm } from '@/composables/useRoleForm'
import { useEditPageGuard } from '@/composables/useEditPageGuard'
import { setTreeSelectDeptIds } from '@/utils/tree-select-init'
import { consumePagePickerResult } from '@/utils/page-picker-result'
import { appendNavFromParam } from '@/utils/nav-from'

const roleId = ref(0)
const {
  loading,
  saving,
  isCreate,
  form,
  statusOptions,
  statusIndex,
  dataScopeOptions,
  dataScopeLabel,
  selectedDeptLabel,
  initCreate,
  loadRole,
  onStatusChange,
  onDataScopeChange,
  setDeptSelection,
  save,
} = useRoleForm()

const { resetBaseline } = useEditPageGuard(
  () => ({
    name: form.name,
    code: form.code,
    sort: form.sort,
    remark: form.remark,
    statusIndex: statusIndex.value,
    dataScope: form.dataScope,
    dataScopeDeptIds: [...form.dataScopeDeptIds].sort((a, b) => a - b),
  }),
  { loading },
)

const sortText = computed({
  get: () => String(form.sort),
  set: (v) => {
    form.sort = Number(v) || 0
  },
})

function pickStatus() {
  uni.showActionSheet({
    itemList: statusOptions.map((s) => s.label),
    success: (res) => onStatusChange({ detail: { value: res.tapIndex } }),
  })
}

function pickDataScope() {
  uni.showActionSheet({
    itemList: dataScopeOptions.map((item) => item.label),
    success: (res) => onDataScopeChange({ detail: { value: res.tapIndex } }),
  })
}

function pickDept() {
  setTreeSelectDeptIds([...form.dataScopeDeptIds])
  uni.navigateTo({
    url: appendNavFromParam(
      `/pages-sub/system/tree-select?type=dept-multi&title=${encodeURIComponent('选择可见部门')}`,
    ),
  })
}

function applyPickerResults() {
  const result = consumePagePickerResult('dept-multi')
  if (!result) return
  setDeptSelection(result.ids || [], result.labelText || '')
}

onShow(applyPickerResults)

onLoad((options) => {
  if (options?.mode === 'create') {
    initCreate().then(() => resetBaseline())
    return
  }
  roleId.value = Number(options?.id)
  if (roleId.value) loadRole(roleId.value)
})
</script>

<style lang="scss" scoped>
@use '@/styles/common.scss' as *;
@use '@/styles/module-form-page.scss' as *;
</style>
