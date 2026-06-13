<template>
  <div class="workflow-designer-page">
    <header class="designer-header">
      <div>
        <p class="eyebrow">Workflow Forms</p>
        <h1>流程表单配置</h1>
      </div>
      <div class="header-actions">
        <el-button :loading="loading.templates" @click="activeSource === 'workflow' ? loadTemplates() : loadFormDefinitions()">刷新</el-button>
        <el-button v-if="activeSource === 'workflow'" type="primary" @click="newTemplate">新建表单</el-button>
      </div>
    </header>

    <main class="designer-shell">
      <!-- 表单来源切换 -->
      <div class="source-tabs">
        <button class="source-tab" :class="{ active: activeSource === 'workflow' }" @click="onSourceChange('workflow')">OA 表单</button>
        <button class="source-tab" :class="{ active: activeSource === 'formengine' }" @click="onSourceChange('formengine')">独立表单</button>
      </div>

      <!-- 左侧列表 -->
      <aside class="template-list">
        <template v-if="activeSource === 'workflow'">
          <button v-for="template in templates" :key="template.templateCode" class="template-row" :class="{ active: template.templateCode === draft.templateCode }" type="button" @click="selectTemplate(template)">
            <strong>{{ template.name }}</strong>
            <span>{{ template.templateCode }} · v{{ template.version || 0 }} · {{ template.status }}</span>
          </button>
          <div v-if="selectedTemplateVersions.length > 0" class="version-list">
            <div class="version-list-title">历史版本</div>
            <div v-for="version in selectedTemplateVersions" :key="`${version.templateCode}-${version.version}`" class="version-row">
              <div><p>v{{ version.version }}</p><span>{{ version.publishedAt || '-' }}</span></div>
              <el-button type="primary" size="small" :disabled="version.version === draft.version" @click="rollback(version.version)">回滚</el-button>
            </div>
          </div>
        </template>
        <template v-else-if="activeSource === 'formengine'">
          <div v-if="formEngineLoading.list" class="list-loading"><el-icon class="is-loading" :size="16"><Loading /></el-icon><span>加载中...</span></div>
          <button v-for="def in formDefinitions" :key="def.id" class="template-row" :class="{ active: def.scope === formEngineDraft.scope }" type="button" @click="selectFormDefinition(def)">
            <strong>{{ def.scopeLabel }}</strong>
            <span>{{ def.scope }} · v{{ def.version || 1 }} · {{ def.enabled ? '已启用' : '已禁用' }}</span>
          </button>
          <div v-if="!formEngineLoading.list && formDefinitions.length === 0" class="list-empty">暂无独立表单</div>
        </template>
      </aside>

      <!-- 主编辑区 -->
      <section class="designer-main">
        <div class="form-grid">
          <el-form label-width="96px" class="template-form">
            <el-form-item label="模板编码"><el-input v-model="draft.templateCode" placeholder="例如 SEAL_APPLY" /></el-form-item>
            <el-form-item label="表单名称"><el-input v-model="draft.name" placeholder="例如 用章申请" /></el-form-item>
            <el-form-item label="业务类型">
              <el-select v-model="draft.businessType"><el-option v-for="type in businessTypes" :key="type" :label="type" :value="type" /></el-select>
            </el-form-item>
            <el-form-item label="启用"><el-switch v-model="draft.enabled" /></el-form-item>
          </el-form>
          <section class="oa-panel">
            <h2>OA 流程绑定</h2>
            <el-input v-model="oa.workflowCode" placeholder="泛微流程 ID / workflowCode" />
            <el-input v-model="oa.provider" placeholder="Provider" />
            <el-button @click="autoMapping">按字段生成映射</el-button>
          </section>
        </div>

        <!-- 编辑区 + 实时预览 并排 -->
        <div class="editor-preview-layout">
          <div class="editor-col">
            <el-tabs v-model="activeTab" class="field-editor-tabs">
              <el-tab-pane label="字段配置" name="fields">
                <DesignerFieldList :fields="draft.schema.fields" :field-types="fieldTypes" @add-field="addField" @delete-field="deleteField" @copy-field="copyField" @new-template="newTemplate" @normalize-field="normalizeField" @get-enum-options="getEnumOptions" />
              </el-tab-pane>
              <el-tab-pane label="规则配置" name="rules">
                <DesignerRulePanel :visibility-rules="visibilityRules" :cross-field-rules="crossFieldRules" :tenant-overrides="tenantOverrides" :available-fields="availableFields" @add-visibility="addVisibilityRule" @remove-visibility="removeVisibilityRule" @add-cross-field="addCrossFieldRule" @remove-cross-field="removeCrossFieldRule" @add-tenant-override="addTenantOverride" @remove-tenant-override="removeTenantOverride" />
              </el-tab-pane>
            </el-tabs>
          </div>
          <div class="preview-col">
            <DesignerPreview v-model="previewModel" v-model:role="previewRole" :schema="normalizedSchema" :trial-payload="trialPayload" :trial-loading="loading.trial" @open-full-preview="previewVisible = true" @trial-submit="trialSubmit" />
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="action-bar">
          <el-alert v-if="operationError" :title="operationError" type="error" show-icon :closable="false" style="margin-bottom: 12px" />
          <el-button :loading="loading.save" type="primary" @click="saveAll">保存草稿</el-button>
          <el-button :loading="loading.publish" type="success" @click="publish">发布</el-button>
        </div>
      </section>
    </main>

    <!-- 全屏预览弹窗 -->
    <el-drawer v-model="previewVisible" title="表单预览" size="600px">
      <div class="preview-role-badge">预览角色：<strong>{{ previewRole }}</strong></div>
      <DynamicWorkflowForm :schema="normalizedSchema" v-model="previewModel" />
    </el-drawer>
  </div>
</template>

<script setup>
import DynamicWorkflowForm from '@/components/common/DynamicWorkflowForm.vue'
import { useWorkflowFormDesigner } from './workflow-form-designer/useWorkflowFormDesigner.js'
import './workflow-form-designer/workflow-form-designer.css'
import { Loading } from '@element-plus/icons-vue'
import { ref, computed } from 'vue'
import DesignerFieldList from './workflow-form-designer/components/DesignerFieldList.vue'
import DesignerRulePanel from './workflow-form-designer/components/DesignerRulePanel.vue'
import DesignerPreview from './workflow-form-designer/components/DesignerPreview.vue'

const {
  activeSource, formDefinitions, formEngineDraft, formEngineLoading,
  addField, autoMapping, deleteField, draft, fieldTypes,
  loadFormDefinitions, loadTemplates, move, newTemplate, normalizeField,
  onSourceChange, operationError, oa, businessTypes,
  previewModel, previewVisible, publish, rollback, normalizedSchema,
  selectFormDefinition, trialPayload, trialSubmit, templates,
  selectedTemplateVersions, loading, saveAll,
  visibilityRules, crossFieldRules, tenantOverrides,
} = useWorkflowFormDesigner()

const activeTab = ref('fields')
const previewRole = ref('admin')

const availableFields = computed(() => (draft.schema?.fields || [])
  .filter(f => f.key && !['section', 'divider', 'info'].includes(f.type))
  .map(f => ({ key: f.key, label: f.label || f.key }))
)

function getEnumOptions(type) {
  if (type === 'tender_source') return '招标公告=bidding\n比选公告=selection\n竞争性谈判=negotiation\n单一来源=single_source'
  if (type === 'project_status') return '进行中=in_progress\n已暂停=suspended\n已结项=closed\n已取消=cancelled'
  if (type === 'qualification_type') return '营业执照=business_license\n资质证书=qualification_cert\n安全生产许可证=safety_cert\nISO认证=iso_cert'
  return ''
}

function copyField(index) {
  const original = draft.schema.fields[index]
  const copy = { ...JSON.parse(JSON.stringify(original)), key: original.key + '_copy' }
  draft.schema.fields.splice(index + 1, 0, copy)
}

function addVisibilityRule() { visibilityRules.value.push({ sourceField: '', operator: 'eq', targetValue: '', targetField: '', action: 'hide', rolePattern: '' }) }
function removeVisibilityRule(i) { visibilityRules.value.splice(i, 1) }
function addCrossFieldRule() { crossFieldRules.value.push({ fieldA: '', operator: 'less_than', fieldB: null, targetValue: '', errorMessage: '', priority: crossFieldRules.value.length }) }
function removeCrossFieldRule(i) { crossFieldRules.value.splice(i, 1) }
function addTenantOverride() { tenantOverrides.value.push({ fieldKey: '', overrideType: 'label', overrideValue: '' }) }
function removeTenantOverride(i) { tenantOverrides.value.splice(i, 1) }
</script>

<style scoped>
.workflow-designer-page { display: flex; flex-direction: column; height: 100%; }
.designer-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 24px; border-bottom: 1px solid var(--el-border-color-light); background: var(--el-fill-color-blank); }
.designer-header h1 { margin: 0; font-size: 20px; font-weight: 600; }
.designer-shell { display: flex; flex: 1; overflow: hidden; }
.source-tabs { display: flex; border-bottom: 1px solid var(--el-border-color-light); background: var(--el-fill-color-blank); padding: 0 16px; }
.source-tab { padding: 10px 16px; border: none; background: none; cursor: pointer; font-size: 14px; color: var(--el-text-color-secondary); border-bottom: 2px solid transparent; transition: all 0.2s; }
.source-tab.active { color: var(--el-color-primary); border-bottom-color: var(--el-color-primary); font-weight: 600; }
.template-list { width: 240px; border-right: 1px solid var(--el-border-color-light); background: var(--el-fill-color-light); overflow-y: auto; padding: 8px; }
.template-row { display: block; width: 100%; padding: 10px 12px; margin-bottom: 4px; border: 1px solid var(--el-border-color-light); border-radius: 6px; background: var(--el-fill-color-blank); text-align: left; cursor: pointer; transition: all 0.2s; }
.template-row:hover { border-color: var(--el-color-primary); background: var(--el-fill-color-light); }
.template-row.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.template-row strong { display: block; font-size: 14px; color: var(--el-text-color-primary); }
.template-row span { font-size: 12px; color: var(--el-text-color-secondary); }
.version-list { margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--el-border-color-lighter); }
.version-list-title { font-size: 12px; font-weight: 600; color: var(--el-text-color-secondary); padding: 4px 8px; }
.version-row { display: flex; align-items: center; justify-content: space-between; padding: 6px 8px; font-size: 12px; color: var(--el-text-color-regular); }
.list-loading { display: flex; align-items: center; gap: 6px; padding: 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.list-empty { padding: 24px; text-align: center; color: var(--el-text-color-secondary); font-size: 13px; }
.designer-main { flex: 1; overflow-y: auto; padding: 20px 24px; }
.form-grid { display: flex; gap: 24px; margin-bottom: 16px; }
.template-form { flex: 1; }
.oa-panel { flex: 1; border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 16px; background: var(--el-fill-color-light); }
.oa-panel h2 { margin: 0 0 12px; font-size: 14px; font-weight: 600; }
.editor-preview-layout { display: flex; gap: 16px; margin-bottom: 16px; }
.editor-col { flex: 3; min-width: 0; }
.preview-col { flex: 2; min-width: 300px; position: sticky; top: 0; align-self: flex-start; }
.field-editor-tabs :deep(.el-tabs__header) { margin-bottom: 12px; }
.action-bar { padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.preview-role-badge { padding: 8px 12px; margin-bottom: 12px; background: var(--el-fill-color-light); border-radius: 6px; font-size: 13px; }
</style>
