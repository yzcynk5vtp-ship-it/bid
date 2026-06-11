/**
 * check-inlined-composables.js
 *
 * 构建后校验：扫描异步 chunk 中被内联的 composable 函数是否完整。
 * 原理：
 *   - 找到所有异步 chunk（非 vendor/element-plus/echarts 的 .js 文件）
 *   - 查找模式：function \w+\(e,o\){...} （被内联的 composable 通常接收 props, emit）
 *   - 检查函数体内是否包含 ref( / reactive( / return { 等必需调用
 *   - 如果函数体极短（< 80 字符）且调用处解构了多个变量名，则告警
 *
 * 用法：node scripts/check-inlined-composables.cjs [dist-dir]
 * 默认 dist-dir：dist/assets/
 */

const fs = require('fs')
const path = require('path')

const DIST_DIR = process.argv[2] || (() => {
  const candidates = ['dist/assets', 'dist']
  for (const d of candidates) {
    if (fs.existsSync(d)) return d
  }
  return 'dist/assets'
})()

const VENDOR_KEYWORDS = ['vendor-', 'element-plus-', 'echarts-', 'vue-vendor-']
const MIN_FUNC_BODY_LEN = 80
const SUSPECT_PATTERN = /function\s+(\w+)\s*\(([^)]*)\)\s*\{([^}]*)\}/g

function isVendorChunk(filename) {
  return VENDOR_KEYWORDS.some(k => filename.includes(k))
}

function checkChunk(filePath, filename) {
  if (!filename.endsWith('.js')) return []
  if (isVendorChunk(filename)) return []

  const content = fs.readFileSync(filePath, 'utf-8')
  const warnings = []

  SUSPECT_PATTERN.lastIndex = 0

  let match
  while ((match = SUSPECT_PATTERN.exec(content)) !== null) {
    const [, funcName, params, body] = match
    const bodyLen = body.length

    if (bodyLen > 200) continue

    const hasRef = /ref\s*\(/.test(body)
    const hasReactive = /reactive\s*\(/.test(body)
    const hasComputed = /computed\s*\(/.test(body)
    const hasReturn = /\breturn\s+/.test(body)

    if (/^e,o$/.test(params.trim()) && !hasReturn && bodyLen < MIN_FUNC_BODY_LEN) {
      warnings.push({
        file: filename,
        funcName,
        params: params.trim(),
        bodyLength: bodyLen,
        hasRef,
        hasReactive,
        hasComputed,
        hasReturn,
        severity: 'ERROR'
      })
    }
  }

  return warnings
}

function main() {
  console.log(`\n\u{1F50D} 检查异步 chunk 内联 composable 完整性...`)
  console.log(`   扫描目录: ${DIST_DIR}\n`)

  let totalFiles = 0
  let totalWarnings = 0

  const files = fs.readdirSync(DIST_DIR)
  for (const f of files) {
    const filePath = path.join(DIST_DIR, f)
    if (!fs.statSync(filePath).isFile()) continue
    totalFiles++
    const warnings = checkChunk(filePath, f)
    for (const w of warnings) {
      totalWarnings++
      const flag = w.severity === 'ERROR' ? '\u274C' : '\u26A0\uFE0F'
      console.log(`  ${flag} ${w.file}`)
      console.log(`     函数: ${w.funcName}(${w.params})`)
      console.log(`     体长: ${w.bodyLength} 字符 (阈值: ${MIN_FUNC_BODY_LEN})`)
      console.log(`     含 ref: ${w.hasRef}, reactive: ${w.hasReactive}, computed: ${w.hasComputed}, return: ${w.hasReturn}`)
      console.log()
    }
  }

  console.log(`   扫描文件数: ${totalFiles}`)
  console.log(`   问题: ${totalWarnings > 0 ? `\u274C ${totalWarnings} 个可疑内联函数` : '\u2705 无'}`)

  if (totalWarnings > 0) {
    console.log(`\n\u{1F4A1} 提示: 检查上述文件中被内联的 composable 函数体是否完整。`)
    console.log(`   通常需要包含 ref() / reactive() / return {} 等关键调用。`)
    console.log(`   如果函数体被截断，应将 composable 内联到调用组件中。`)
    process.exit(1)
  }
}

main()
