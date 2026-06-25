# Implementation Notes - UserPicker 远程搜索修复

## 背景

"新增任务"对话框中"选择任务执行人"下拉框在输入文字后无法显示搜索结果，但打开下拉时（未输入文字）能正常显示候选人列表。

## 根因：Element Plus `<el-select>` slot 选项注册时机 Bug

Element Plus 的 `<el-select>` 使用 slot 渲染的 `<el-option>` 通过 `onOptionCreate`/`onOptionDestroy` 注册到内部 `states.options Map`。远程搜索时：

1. 用户输入 → `remote-method` 触发 → API 返回新数据
2. 旧 `<el-option>` 组件 unmount（调用 `onOptionDestroy`）→ 选项从 Map 移除
3. 新 `<el-option>` 组件 mount（调用 `onOptionCreate`）→ 选项注册到 Map

但 Element Plus 2.5.0-2.14.2 的 `<el-select>` 源码中有这样一个 watch：

```js
// select.vue (simplified)
watch(
  () => [slots.default(), props.modelValue],
  () => {
    if (props.persistent || expanded.value) return // ← BUG
    states.options.clear()
    // reprocess slot children...
  },
  { immediate: true }
)
```

当下拉框**展开时**（`expanded.value === true`），这个 watch 跳过重新处理 slot 子节点。远程搜索返回数据后，旧选项已被销毁但新选项的注册被跳过，导致 `states.options` 为空。

## 之前的错误尝试（`<el-select>` + `:options` prop）

第一次修复尝试在 `<el-select>` 上加了 `:options="selectOptions"` prop。但经过深入分析 Element Plus 2.5.0 源码后发现：

- **`<el-select>` 没有 `:options` prop** — 它的 Props 定义中没有 `options` 字段
- 虽然模板中传了 `:options`，但 Vue 只会把它当作 unknown attribute，组件内部完全不处理
- 这解释了为什么热修后页面毫无变化

## 最终修复方案：改用 `<el-select-v2>`

将 `<el-select>` 替换为 `<el-select-v2>`，这是 Element Plus 内置的**数据驱动版本**的选择器：

```vue
<!-- 修复前 -->
<el-select ... remote ...>
  <el-option v-for="user in mergedOptions" ... />
</el-select>

<!-- 修复后 -->
<el-select-v2 ... :options="selectOptions" :value-key="valueField" ...>
  <template #empty>无匹配用户</template>
</el-select-v2>
```

### 为什么 `<el-select-v2>` 能解决问题

- **`<el-select-v2>` 是纯数据驱动的** — 不依赖 slot 子组件注册机制
- 它有一个 `options` prop（类型 `Array`，**required**）作为数据源
- `remote` / `remote-method` 模式完美支持——远程结果直接改变 `options` 数组，el-select-v2 内部能正确检测变化并更新下拉选项
- 没有 `persistent || expanded` 守卫逻辑

### 技术细节

- 新增 `selectOptions` 计算属性，将 `mergedOptions` 转换为 `{ value, label }` 格式
- `value-key` prop 指定用于标识选项的字段（默认 `"id"`）
- 所有现有 props（`modelValue`, `multiple`, `disabled`, `clearable` 等）在两个组件间兼容
- 服务端不需要额外配置——`<el-select-v2>` 已作为 ElementPlus 插件的一部分被全局注册

## 服务器热修回滚

- 服务器之前的错误补丁（element-plus-D2Kidr9s.js 和 UserPicker-D1HKIaA8.js）已从旧版本的 release 中恢复原始文件
- 最终修复在代码层面（UserPicker.vue），通过正规 PR + 构建部署

## 测试更新

- `UserPicker.spec.js`：将 `ElSelect` stub 替换为 `ElSelectV2` stub，添加 `options` 和 `valueKey` prop 验证
- 其他引用 UserPicker 的 spec 使用 `shallowMount` + `UserPicker: true` 不受影响