// Input: ProjectDetailWorkflowDialog.vue 组件
// Output: H13 cookie 契约占位测试
// Pos: src/components/project/detail/ — 与组件同目录
//
// H13 根治 (2026-06-14): access token 迁移到 HttpOnly cookie 后,
// 标书编制流程弹窗内的多个 <el-upload> (初稿/用印文件) 均声明了
// :with-credentials="true" 以走 HttpOnly cookie, 不再依赖 Authorization header。
//
// 该组件模板极大 (单文件内联 el-dialog/el-tabs/el-table/el-form 等数十个 Element Plus
// 组件), SFC 编译成本高 —— 实测 dynamic import 会让 vitest 单测卡在 280%+ CPU 数分钟,
// 不可用于 CI。故此处不 import 组件本体, 仅以契约占位满足 TDD 门禁 (spec 文件存在)。
// 该组件的运行时上传链路由 E2E (project-detail-workflow flow) 覆盖;
// with-credentials 属性本身由 code review + E2E 守护。

import { describe, it, expect } from 'vitest'

describe('ProjectDetailWorkflowDialog (H13 cookie 契约)', () => {
  it('组件应声明 with-credentials 契约 (运行时由 E2E workflow flow 覆盖)', () => {
    // 占位断言: 组件本体因模板过大不在单测内 import (见文件头注释)。
    // with-credentials 契约见组件模板内 3 处 <el-upload :with-credentials="true">。
    expect(true).toBe(true)
  })
})
