<template>
  <div class="rule-panel">
    <!-- 可见性规则 -->
    <div class="rule-section">
      <div class="section-title">
        <h2>字段可见性规则</h2>
        <el-button size="small" @click="addVisibilityRule">+ 添加规则</el-button>
      </div>
      <div v-if="visibilityRules.length === 0" class="empty-tip">暂无可见性规则。点击上方按钮添加。</div>
      <div v-for="(rule, ri) in visibilityRules" :key="ri" class="rule-card">
        <div class="rule-card-header">
          <span class="rule-index">#{{ ri + 1 }}</span>
          <el-button type="danger" size="small" text @click="removeVisibilityRule(ri)">删除</el-button>
        </div>
        <div class="rule-card-body">
          <div class="rule-field-group">
            <label>触发字段</label>
            <el-select v-model="rule.sourceField" placeholder="选择字段" clearable>
              <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
            </el-select>
          </div>
          <div class="rule-field-group">
            <label>条件</label>
            <el-select v-model="rule.operator" placeholder="操作符">
              <el-option label="等于" value="eq" />
              <el-option label="不等于" value="neq" />
              <el-option label="包含" value="contains" />
              <el-option label="为空" value="empty" />
            </el-select>
            <el-input v-model="rule.targetValue" placeholder="目标值" style="width: 140px" clearable />
          </div>
          <div class="rule-field-group">
            <label>目标字段</label>
            <el-select v-model="rule.targetField" placeholder="选择字段" clearable>
              <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
            </el-select>
          </div>
          <div class="rule-field-group">
            <label>执行动作</label>
            <el-select v-model="rule.action" placeholder="动作">
              <el-option label="显示" value="show" />
              <el-option label="隐藏" value="hide" />
              <el-option label="只读" value="readonly" />
            </el-select>
          </div>
          <div class="rule-field-group">
            <label>角色</label>
            <el-input v-model="rule.rolePattern" placeholder="留空=所有人" clearable />
          </div>
        </div>
      </div>
    </div>

    <!-- 跨字段验证规则 -->
    <div class="rule-section">
      <div class="section-title">
        <h2>跨字段验证规则</h2>
        <el-button size="small" @click="addCrossFieldRule">+ 添加验证规则</el-button>
      </div>
      <div v-if="crossFieldRules.length === 0" class="empty-tip">
        暂无跨字段验证规则。
        <span class="tip-sub">支持：金额比较、日期先后、互斥必填、求和校验等。</span>
      </div>
      <div v-for="(rule, ri) in crossFieldRules" :key="ri" class="rule-card cross-field">
        <div class="rule-card-header">
          <span class="rule-index">#{{ ri + 1 }}</span>
          <el-input-number v-model="rule.priority" :min="0" :max="999" placeholder="优先级" size="small" style="width: 80px" />
          <el-button type="danger" size="small" text @click="removeCrossFieldRule(ri)">删除</el-button>
        </div>
        <div class="rule-card-body">
          <div class="rule-field-group">
            <label>字段A</label>
            <el-select v-model="rule.fieldA" placeholder="选择字段" clearable>
              <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
            </el-select>
          </div>
          <div class="rule-field-group">
            <label>操作符</label>
            <el-select v-model="rule.operator" placeholder="操作符">
              <el-option label="小于" value="less_than" />
              <el-option label="大于" value="greater_than" />
              <el-option label="等于" value="equals" />
              <el-option label="不等于" value="not_equals" />
              <el-option label="求和等于" value="sum_equals" />
              <el-option label="至少填一个" value="one_filled" />
              <el-option label="必须都填" value="both_filled" />
              <el-option label="不晚于" value="not_after" />
            </el-select>
          </div>
          <div v-if="!['one_filled','both_filled'].includes(rule.operator)" class="rule-field-group">
            <label>字段B</label>
            <el-select v-model="rule.fieldB" placeholder="选择字段" clearable>
              <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
            </el-select>
            <el-input v-if="rule.fieldB == null" v-model="rule.targetValue" placeholder="目标值" style="width: 120px" />
          </div>
          <div class="rule-field-group">
            <label>错误提示</label>
            <el-input v-model="rule.errorMessage" placeholder="错误提示信息" />
          </div>
        </div>
      </div>
    </div>

    <!-- 租户字段覆盖 -->
    <div class="rule-section">
      <div class="section-title">
        <h2>租户字段覆盖</h2>
        <el-button size="small" @click="addTenantOverride">+ 添加覆盖</el-button>
      </div>
      <div v-if="tenantOverrides.length === 0" class="empty-tip">暂无租户覆盖。</div>
      <div v-for="(ov, oi) in tenantOverrides" :key="oi" class="rule-card override">
        <div class="rule-card-header">
          <span class="rule-index">#{{ oi + 1 }}</span>
          <el-button type="danger" size="small" text @click="removeTenantOverride(oi)">删除</el-button>
        </div>
        <div class="rule-card-body">
          <div class="rule-field-group">
            <label>字段</label>
            <el-select v-model="ov.fieldKey" placeholder="选择字段" clearable>
              <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
            </el-select>
          </div>
          <div class="rule-field-group">
            <label>覆盖类型</label>
            <el-select v-model="ov.overrideType" placeholder="类型">
              <el-option label="标签" value="label" />
              <el-option label="必填" value="required" />
              <el-option label="默认值" value="default_value" />
              <el-option label="选项" value="options" />
              <el-option label="隐藏" value="hidden" />
              <el-option label="只读" value="readonly" />
            </el-select>
          </div>
          <div class="rule-field-group">
            <label>覆盖值</label>
            <el-input v-model="ov.overrideValue" placeholder="覆盖值" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  visibilityRules: { type: Array, required: true },
  crossFieldRules: { type: Array, required: true },
  tenantOverrides: { type: Array, required: true },
  availableFields: { type: Array, required: true },
})

const emit = defineEmits(['add-visibility', 'remove-visibility', 'add-cross-field', 'remove-cross-field', 'add-tenant-override', 'remove-tenant-override'])

function addVisibilityRule() { emit('add-visibility') }
function removeVisibilityRule(i) { emit('remove-visibility', i) }
function addCrossFieldRule() { emit('add-cross-field') }
function removeCrossFieldRule(i) { emit('remove-cross-field', i) }
function addTenantOverride() { emit('add-tenant-override') }
function removeTenantOverride(i) { emit('remove-tenant-override', i) }
</script>

<style scoped>
.rule-section { margin-bottom: 24px; }
.section-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.section-title h2 { margin: 0; font-size: 15px; font-weight: 600; }
.rule-card { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; margin-bottom: 8px; background: var(--el-fill-color-blank); overflow: hidden; }
.rule-card.cross-field { background: var(--el-fill-color-light); }
.rule-card.override { border-style: dashed; }
.rule-card-header { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--el-fill-color-light); border-bottom: 1px solid var(--el-border-color-lighter); }
.rule-index { font-size: 12px; font-weight: 600; color: var(--el-text-color-secondary); min-width: 24px; }
.rule-card-body { display: flex; flex-wrap: wrap; gap: 12px; padding: 12px; }
.rule-field-group { display: flex; align-items: center; gap: 6px; }
.rule-field-group label { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; min-width: 48px; }
.empty-tip { padding: 20px; text-align: center; color: var(--el-text-color-secondary); font-size: 13px; background: var(--el-fill-color-light); border-radius: 6px; }
.tip-sub { display: block; margin-top: 6px; font-size: 12px; color: var(--el-text-color-placeholder); }
</style>
