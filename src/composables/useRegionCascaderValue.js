// Input: 总部所在地字符串（getter）+ 字符串写入器（setter）+ emptyValue 选项
// Output: el-cascader v-model 双向绑定 computed（路径 ↔ 归一字符串）
// Pos: src/composables/ - 总部所在地 cascader 通用绑定（被 ManualTenderDialog / TenderBasicInfoTab / TenderSearchCard 共 3 处复用）
// 维护声明: 若 cascader 组件或 chinaRegionData.js 工具函数接口变化，需同步更新本文件。

import { computed } from 'vue'
import {
  normalizeHeadquartersRegionPath,
  regionValueToCascaderPath,
} from '@/components/common/chinaRegionData.js'

/**
 * 总部所在地（region）字符串与 el-cascader 选中路径之间的双向绑定。
 *
 * 用法示例：
 *   const regionCascaderValue = useRegionCascaderValue(
 *     () => props.form.region,
 *     (v) => { props.form.region = v },
 *     { emptyValue: '' }, // 搜索过滤场景传 null
 *   )
 *
 * - get: 字符串 → cascader path（via regionValueToCascaderPath，兼容历史"省+市" / 直辖市带区 / 纯省名）
 * - set: cascader path → 归一字符串（via normalizeHeadquartersRegionPath，直辖市/港澳台仅存本级行政区名）
 * - 空值时写入 emptyValue（默认 ''），用于 reset cascader
 *
 * 满足 RULES.md §2.6.1 提取条件（3 处引用、>80 行价值、消 39 行重复）。
 * 命名函数 export（避免 §2.6.2 Vite 异步 chunk 内联 + tree-shaking 截断函数体的问题）。

 * 配套导出 REGION_CASCADER_PROPS：3 处调用方共享同一 cascader 配置，
 * 避免 `:props="{ expandTrigger: 'hover', ... }"` 在模板里重复 3 次。
 */
export const REGION_CASCADER_PROPS = Object.freeze({
  expandTrigger: 'hover',
  label: 'name',
  value: 'name',
  checkStrictly: true,
  emitPath: true,
})

export function useRegionCascaderValue(getter, setter, options = {}) {
  const emptyValue = 'emptyValue' in options ? options.emptyValue : ''

  return computed({
    get: () => regionValueToCascaderPath(readValue(getter)),
    set: (val) => {
      if (!val) {
        setter(emptyValue)
        return
      }
      if (Array.isArray(val)) {
        setter(normalizeHeadquartersRegionPath(val))
      } else {
        setter(val)
      }
    },
  })
}

function readValue(getter) {
  // getter 由调用方提供，返回 string 或 null/undefined（未选）；统一归一为 string
  return getter() ?? ''
}
