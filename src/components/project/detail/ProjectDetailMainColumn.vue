<template>
  <el-main class="main-content">
    <ProjectBasicInfoCard :project="ctx.project" />
    <ProjectStageTimeline
      v-if="ctx.project?.id"
      ref="timelineRef"
      :project-id="ctx.project.id"
      @stage-click="handleStageClick"
      @snapshot="handleSnapshot"
    />

    <div class="stage-tabs-container">
      <el-tabs v-model="activeStageTab" class="custom-stage-tabs">
        <el-tab-pane label="项目立项" name="INITIATED">
          <InitiationStage
            v-if="ctx.project?.id"
            ref="initiationStageRef"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @updated="handleStageUpdated"
          />
        </el-tab-pane>
        <el-tab-pane label="标书制作" name="DRAFTING">
          <div class="drafting-tab-content">
            <ProjectTaskBoardCard
              :tasks="ctx.project?.tasks || []"
              :project-id="ctx.project?.id"
              :show-submit-button="false"
              :can-manage-project-tasks="ctx.canManageProjectTasks"
              :is-demo-mode="ctx.isDemoMode"
              @add-task="ctx.handleAddTask"
              @reset-tasks="ctx.handleResetTasks"
              @task-click="ctx.handleTaskClick"
              @save-task="ctx.handleSaveTask"
              @status-change="ctx.handleTaskStatusChange"
              @generate-tasks="ctx.handleGenerateTasks"
              @open-score-parse="scoreParseRef?.open()"
              @open-decompose="taskDecomposeRef?.open()"
              @add-deliverable="ctx.handleAddDeliverable"
              @remove-deliverable="ctx.handleRemoveDeliverable"
              @submit-to-document="ctx.handleSubmitToDocument"
            />
            <ScoreParseDrawer ref="scoreParseRef" :project-id="ctx.project?.id" />
            <TaskDecomposeDialog ref="taskDecomposeRef" :project-id="ctx.project?.id" />
            <DraftingStage
              v-if="ctx.project?.id"
              ref="draftingStageRef"
              :key="ctx.project.id"
              :project-id="ctx.project.id"
              @advanced="handleStageUpdated"
            />
          </div>
        </el-tab-pane>
        <el-tab-pane label="评标中" name="EVALUATING">
          <EvaluationStage
            v-if="ctx.project?.id"
            ref="evaluationStageRef"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @advanced="handleStageUpdated"
            @switch-tab="handleSwitchTab"
          />
        </el-tab-pane>
        <el-tab-pane label="结果确认" name="RESULT_PENDING">
          <ResultConfirmStage
            v-if="ctx.project?.id"
            ref="resultConfirmStageRef"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @registered="handleStageUpdated"
            @switch-tab="handleSwitchTab"
          />
        </el-tab-pane>
        <el-tab-pane label="项目复盘" name="RETROSPECTIVE">
          <RetrospectiveStage
            v-if="ctx.project?.id"
            ref="retrospectiveStageRef"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @submitted="onRetrospectiveSubmitted"
            :result-type="resultType"
          />
        </el-tab-pane>
        <el-tab-pane label="项目结项" name="CLOSED">
          <ClosureStage
            v-if="ctx.project?.id"
            ref="closureStageRef"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @closed="handleStageUpdated"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <ProjectApprovalStatusCard
      :approval-history="ctx.approvalHistory"
      :project-status="ctx.project?.status"
      :can-approve-current="ctx.canApproveCurrent"
      @quick-approve="ctx.handleQuickApprove"
      @quick-reject="ctx.handleQuickReject"
    />

    <!-- 项目文档（暂时隐藏）
    <el-card class="document-card">
      ...
    </el-card>
    -->

    <!-- 协作讨论（暂时隐藏）
    <ProjectCollaborationCard :project-id="ctx.project?.id" />
    -->
  </el-main>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
// import { Document, DocumentChecked, Folder, Upload } from '@element-plus/icons-vue' // 暂时隐藏项目文档
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'
import { useProjectStore } from '@/stores/project'
import ProjectApprovalStatusCard from '@/components/project/ProjectApprovalStatusCard.vue'
import ProjectBasicInfoCard from '@/components/project/ProjectBasicInfoCard.vue'
import ProjectStageTimeline from '@/components/project/stage/ProjectStageTimeline.vue'
import ProjectTaskBoardCard from '@/components/project/ProjectTaskBoardCard.vue'
// import ProjectCollaborationCard from '@/components/project/ProjectCollaborationCard.vue' // 暂时隐藏
import InitiationStage from '@/views/Project/stages/InitiationStage.vue'
import DraftingStage from '@/views/Project/stages/DraftingStage.vue'
import EvaluationStage from '@/views/Project/stages/EvaluationStage.vue'
import ResultConfirmStage from '@/views/Project/stages/ResultConfirmStage.vue'
import ScoreParseDrawer from '@/views/Project/stages/components/ScoreParseDrawer.vue'
import TaskDecomposeDialog from '@/views/Project/stages/components/TaskDecomposeDialog.vue'
import RetrospectiveStage from '@/views/Project/stages/RetrospectiveStage.vue'
import ClosureStage from '@/views/Project/stages/ClosureStage.vue'

// Import API
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

const ctx = useProjectDetailContext()
const projectStore = useProjectStore()

const activeStageTab = ref('INITIATED')
const timelineRef = ref(null)
const scoreParseRef = ref(null)
const taskDecomposeRef = ref(null)
const resultType = ref('')

// CO-214: 各 stage 组件引用，用于切换 tab 时主动触发数据刷新
const initiationStageRef = ref(null)
const draftingStageRef = ref(null)
const evaluationStageRef = ref(null)
const resultConfirmStageRef = ref(null)
const retrospectiveStageRef = ref(null)
const closureStageRef = ref(null)

const stageRefMap = {
  INITIATED: () => initiationStageRef.value,
  DRAFTING: () => draftingStageRef.value,
  EVALUATING: () => evaluationStageRef.value,
  RESULT_PENDING: () => resultConfirmStageRef.value,
  RETROSPECTIVE: () => retrospectiveStageRef.value,
  CLOSED: () => closureStageRef.value,
}

async function loadResultType() {
  if (!ctx.project?.id) return
  try {
    const res = await projectLifecycleApi.getResult(ctx.project.id)
    resultType.value = res?.data?.resultType || res?.resultType || ''
  } catch (e) {
    console.warn('[ProjectDetailMainColumn] loadResultType failed', e)
    resultType.value = ''
  }
}

// Watch project ID to load result type
watch(() => ctx.project?.id, (newId) => {
  resultType.value = ''
  if (newId) {
    loadResultType()
  }
}, { immediate: true })

// Sync with timeline click events and snapshot events
function handleStageClick(stage) {
  activeStageTab.value = stage.code
}

// CO-214: 切换 tab 时主动调用目标 stage 的 load()，避免显示旧数据。
// 元素 el-tabs 默认非懒渲染，所有 stage 在首次进入详情页时已完成 onMounted；
// 后续切换 tab 不会再次触发 onMounted，因此需要父组件在 tab 变更时主动刷新。
watch(activeStageTab, async (newTab) => {
  if (!newTab) return
  // 等待 tab 切换引发的 DOM 更新完成，确保目标 stage ref 已绑定
  await nextTick()
  const target = stageRefMap[newTab]?.()
  if (target?.load) {
    try {
      await target.load()
    } catch (e) {
      // 加载失败时静默处理（各 stage 内部已有 ElMessage 错误提示）
      console.warn(`[ProjectDetailMainColumn] stage "${newTab}" load failed`, e)
    }
  }
})

const snapshotLock = ref(false)

function handleSnapshot(snapshot) {
  if (snapshotLock.value) return
  if (snapshot?.currentStage) {
    activeStageTab.value = snapshot.currentStage
  }
}

function handleSwitchTab(v) {
  snapshotLock.value = true
  activeStageTab.value = v
  setTimeout(() => { snapshotLock.value = false }, 2000)
}

async function handleStageUpdated() {
  if (timelineRef.value?.reload) {
    await timelineRef.value.reload()
  }
  // 仅当快照锁未激活时（即未通过 @switch-tab 等事件切换），才用快照覆盖 activeStageTab。
  // 避免 @switch-tab + @advanced/@registered 先后触发时的时序竞争：
  // switch-tab 已将 tab 设为目标阶段，handleStageUpdated 不应再通过滞后快照回退。
  if (timelineRef.value?.snapshot?.currentStage && !snapshotLock.value) {
    activeStageTab.value = timelineRef.value.snapshot.currentStage
  }
  await loadResultType()
  if (ctx.project?.id) {
    await projectStore.getProjectById(ctx.project.id)
  }
}

async function onRetrospectiveSubmitted() {
  // 在 handleStageUpdated 之前设锁，防止时间线快照回退 activeStageTab
  snapshotLock.value = true
  await handleStageUpdated()
  activeStageTab.value = 'CLOSED'
  setTimeout(() => { snapshotLock.value = false }, 300)
}
</script>
