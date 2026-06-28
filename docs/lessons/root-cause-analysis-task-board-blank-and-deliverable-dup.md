# Dev 看板空白 + 交付物重复渲染 根因分析

> Issue: CO-361（看板空白）/ Refs #1269（交付物重复渲染）
> 日期: 2026-06-28
> 排查者: zcode agent
> 修复提交: 6877ffe68（PR #1270）/ 1ae84f831（PR #1271）

本次会话连续修了两个独立 bug，归并到一份沉淀文档，因为它们共享一个元教训：**外部诊断报告给出的根因未必准确，必须自己回到代码与机器真相复核**。

---

## Bug A：Dev 环境任务看板空白（无列无卡片）

### 现场还原

**症状素描**：dev 环境启动后访问任务看板（TaskBoard.vue），看板无列、无卡片，整页空白，但控制台无报错。

**边界划定**：
- e2e profile：正常 ✅
- dev profile：看板空白 ❌
- prod profile：未 seed（历史遗留）

**思维沙箱**：怀疑前端 `columns` 计算逻辑或后端 `/api/task-status-dict` 返回空。

### 剥洋葱：逆向调用链

#### Layer 1 — 入口/视图层

```javascript
// TaskBoard.vue:221 附近
const columns = computed(() => projectStore.taskStatuses.map(...))
```

`taskStatuses` 为空数组时，`columns=[]`，看板无列无卡片。

#### Layer 2 — 状态/数据层

```
projectStore.taskStatuses  ←  /api/task-status-dict 返回 []
                            ←  task_status_dict 表为空
```

#### Layer 3 — 种子/迁移层（根因所在）

```
task_status_dict 表：
  - 结构存在（JPA ddl-auto 自动建表）
  - 数据为空（无 seed）

种子数据来源排查：
  1. db/migration-mysql/V101__task_status_dict.sql   ← 存在，但从未执行
  2. E2eDemoDataInitializer.seedTaskStatuses()       ← 唯一 seed 来源
     但 @Profile("e2e") 仅 e2e profile 跑，dev 不跑
```

**为什么 V101 从未执行？**

```yaml
# application-mysql.yml:22-24
locations: classpath:db/migration-mysql
baseline-on-migrate: true
baseline-version: 1050        # ← baseline 是 1050，不是 1047
```

Flyway 在 `baseline-on-migrate: true` 模式下，**版本号低于 baseline 的迁移脚本一律跳过**。`V101 < 1050`，所以 `V101__task_status_dict.sql` 在 dev/prod 从未执行。表结构由 JPA `ddl-auto` 建出来，但种子数据始终为空。

> ⚠️ **commit message 笔误纠正**：6877ffe68 的提交说明里写"低于 Flyway baseline V1047"，实际 baseline 是 **V1050**（见 `application-mysql.yml:24`）。根因结论正确，但版本号引用有误。本沉淀以代码真相为准。

### 零号病人定位

**第一行错误**：

```java
// E2eDemoDataInitializer.java（修复前）
@Profile("e2e")   // ← 只在 e2e profile 跑，dev 不跑
public class E2eDemoDataInitializer implements ApplicationRunner {
    void seedTaskStatuses() { ... }  // 唯一的字典 seed 来源
}
```

**必然性解释**：
- V101 因低于 baseline 1050 永远不执行 → 表无种子
- 唯一能种入字典的 `seedTaskStatuses()` 被 `@Profile("e2e")` 锁死
- dev profile 既不跑迁移种子、也不跑 Java seed → `task_status_dict` 永远空
- 前端拿到的 `/api/task-status-dict` 是 `[]` → `columns=[]` → 看板空白

### 验证与修复

#### 修复 diff

```diff
- @Profile("e2e")
+ // dev profile 也启用：V101 字典种子迁移因低于 Flyway baseline V1050 从未执行，
+ // task_status_dict 表无种子数据，导致 TaskBoard.vue columns 为空、看板空白（CO-361 排查发现）。
+ // seedTaskStatuses()/ensureSystemRoles() 幂等，dev 重复启动无副作用。
+ @Profile({"e2e", "dev"})
```

#### 影响范围（必须显式声明）

| profile | 修复前 | 修复后 |
|---|---|---|
| e2e | seed 正常 | 行为不变 |
| dev | 字典空 → 看板空白 | 启动自动 seed，看板恢复 |
| prod | 无 seed | 仍无 seed（技术债，后续补 V1106 迁移或扩展 @Profile） |

> 注：`@Profile({"e2e","dev"})` 会顺带在 dev 跑 `seedDemoUsers()`，种入 7 个 demo 用户（密码 123456）。本地开发可接受，**但绝不能把 dev 加入 prod 的 @Profile**。

#### 最小验证

1. `E2eDemoDataInitializerTest` 2/2 通过（单测直接调用方法，不依赖 profile）
2. 手动 DB `INSERT` 三态种子后，`/api/task-status-dict` 返回 3 项，看板恢复
3. dev profile 重启后自动 seed，看板恢复

### 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `E2eDemoDataInitializer.java` 的 `@Profile("e2e")` | ✅ |
| 必然性已证明 | V101 < baseline 1050 不执行 + 唯一 seed 被 profile 锁死 | ✅ |
| 最小验证已设计 | 单测 2/2 + 手动 seed 后看板恢复 | ✅ |
| 修复 diff 已提供 | 见上 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

1. dev profile 启动后 `/api/task-status-dict` 返回 3 项
2. `E2eDemoDataInitializerTest` 覆盖 `seedTaskStatuses()` 幂等性

---

## Bug B：交付物重复渲染（只读模式同文件出现两次）

### 现场还原

**症状素描**：任务详情只读模式下，同一份已保存交付物同时出现在两处——`el-upload` 的禁用条目（灰色、不可下载）和 `.deliverable-list` 的可下载链接里，重复且令人困惑。

**边界划定**：
- 编辑/上传模式：正常（el-upload 可上传）✅
- 只读模式（view）：重复渲染 ❌

**思维沙箱**：怀疑 `rebuildFileList()` 把已保存 deliverables 也塞进了 el-upload 的 file-list。

### 剥洋葱：逆向调用链

#### Layer 1 — 模板层

```html
<!-- TaskForm.vue -->
<el-upload :file-list="deliverableFileList" :disabled="readonly">  <!-- 禁用条目 -->
  ...
</el-upload>
<div v-if="readonly && localValue.deliverables?.length" class="deliverable-list">
  <a class="deliverable-link" @click="downloadDeliverable">...</a>   <!-- 可下载链接 -->
</div>
```

只读模式下，`.deliverable-list` 会渲染所有已保存交付物为可下载链接。

#### Layer 2 — composable 层（根因所在）

```javascript
// useTaskDeliveryForm.js（修复前）
function rebuildFileList() {
  const files = localValue.deliverableFiles      // 待上传新文件
  if (files?.length) { ... return }

  const dels = localValue.deliverables           // ← 已保存交付物
  if (dels?.length) {
    const list = dels.map((d, i) => ({ name: d?.name, url: d?.url, id: d?.id }))
    deliverableFileList.value = list             // ← 塞进 el-upload 的 file-list
    return
  }
}
```

`rebuildFileList()` 在只读模式下把已保存的 `deliverables` 也填入 `el-upload` 的 `file-list`，导致同一文件同时出现在：
1. `el-upload` 禁用条目（灰色、不可下载）
2. `.deliverable-list` 可下载链接

### 零号病人定位

**第一行错误**：

```javascript
// useTaskDeliveryForm.js（修复前）
const dels = localValue.deliverables
if (dels?.length) {
  const list = dels.map((d, i) => ({ name: d?.name, url: d?.url, id: d?.id }))
  deliverableFileList.value = list   // ← 已保存交付物不应进 el-upload
}
```

**必然性解释**：
- `el-upload` 的 `:disabled="readonly"` 在只读模式下把 file-list 里的条目渲染为灰色禁用态，且不可点击下载
- 已保存交付物本应只走 `.deliverable-list` 的可下载链接
- 但 `rebuildFileList()` 把它们也塞进了 `deliverableFileList`，于是同一文件出现两次，一次不可下载、一次可下载

### 验证与修复

#### 修复 diff

```diff
 function rebuildFileList() {
-  // 编辑/上传优先用 deliverableFiles (raw File objects)
+  // 仅填充待上传的新文件（raw File objects）。
+  // 已保存的交付物（deliverables）交给 TaskForm 模板的 .deliverable-list 渲染为可下载链接，
+  // 不再塞入 el-upload 的 file-list —— 否则只读模式下同一交付物会同时出现在
+  // el-upload 的禁用条目（不可下载）和 deliverable-list（可下载）里，重复且令人困惑。
   const files = localValue.deliverableFiles
   if (files?.length) { ... return }
-  // 查看模式用 deliverables (backend DTO)
-  const dels = localValue.deliverables
-  if (dels?.length) { ... return }
   if (deliverableFileList.value.length) deliverableFileList.value = []
 }

 rebuildFileList()
-watch(() => [localValue.deliverableFiles, localValue.deliverables], rebuildFileList, { deep: true })
+watch(() => localValue.deliverableFiles, rebuildFileList, { deep: true })
```

#### 关键设计原则

**职责分离**：
- `el-upload` 的 `deliverableFileList` 只负责"待上传的新文件"（raw File objects）
- 已保存交付物（backend DTO）的渲染完全交给模板的 `.deliverable-list`
- 两者数据源不重叠，避免同一份数据被两个 UI 容器同时消费

#### 最小验证

新增 2 个测试用例（`TaskForm.spec.js` 21/21 通过）：
1. view 模式下已保存交付物渲染为 `.deliverable-link`，`ElUpload` 的 `fileList` 为空
2. 点击 `.deliverable-link` 触发 `downloadDeliverable`

### 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `useTaskDeliveryForm.rebuildFileList()` 把 deliverables 塞进 el-upload | ✅ |
| 必然性已证明 | `:disabled="readonly"` 使 el-upload 条目不可下载 + deliverable-list 可下载 → 重复 | ✅ |
| 最小验证已设计 | 2 个用例覆盖只读渲染 + 点击下载 | ✅ |
| 修复 diff 已提供 | 见上 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

1. 只读模式下 `ElUpload` 的 `fileList` 必须为空（已保存交付物不进 el-upload）
2. 只读模式下已保存交付物渲染为 `.deliverable-link`
3. 点击 `.deliverable-link` 触发下载

---

## 元教训（跨两个 bug）

### 1. 外部诊断报告的根因必须自己复核

Bug A 排查时，外部诊断报告称根因为"V1223 三态收口迁移"——但 **V1223 根本不存在**（迁移最大才 V1105）。实际根因是 V101 因低于 baseline 从未执行。

> 教训：任何"别人给的根因"都是假设，必须回到代码与机器真相（迁移文件、baseline 配置、表数据）自己验证一遍。

### 2. baseline-on-migrate 陷阱：低于 baseline 的迁移静默跳过

`baseline-on-migrate: true` + `baseline-version: 1050` 会让所有 `V<1050` 的迁移**静默跳过**，不报错、不告警。这是 Flyway 的高危静默行为，容易让人以为"迁移文件存在 = 已执行"。

> 教训：存在迁移文件 ≠ 已执行。低于 baseline 的脚本只是"历史档案"，不会灌入数据。

### 3. @Profile 是数据可见性的隐形开关

`@Profile("e2e")` 把唯一的 seed 来源锁死在 e2e profile，dev 看不到。这类"profile 限定 + 唯一数据源"组合是隐蔽的空表根因。

> 教训：当一个数据源被 `@Profile` 限定时，必须问"其他 profile 从哪获取这份数据"——如果答案是"没有"，就是潜在空表。

### 4. 同一数据被两个 UI 容器消费 → 必然重复

Bug B 的本质是已保存交付物同时被 `el-upload`（file-list）和 `.deliverable-list` 两个容器渲染。一个数据源只能有一个"渲染责任人"。

> 教训：UI 容器的数据源必须互斥。已保存数据走展示容器（可下载链接），待上传数据走上传容器（el-upload），不能混用 file-list。

---

## 相关文档与代码

- 提交：6877ffe68（PR #1270）/ 1ae84f831（PR #1271）
- `backend/src/main/java/com/xiyu/bid/config/E2eDemoDataInitializer.java` — Bug A 修复点
- `backend/src/main/resources/application-mysql.yml:22-24` — Flyway baseline 配置（根因证据）
- `backend/src/main/resources/db/migration-mysql/V101__task_status_dict.sql` — 因低于 baseline 从未执行的种子迁移
- `src/components/project/useTaskDeliveryForm.js` — Bug B 修复点
- `src/components/project/TaskForm.vue:83-88` — `.deliverable-list` 渲染容器
- `docs/lessons/lessons-learned.md §22` — 元教训提炼
