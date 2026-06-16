#!/usr/bin/env node
// Input: AGENTS.md and referenced files
// Output: validation report for cross-file references
// Pos: scripts/ - Structure validator for AGENTS.md navigation map
// 维护声明: 修改 AGENTS.md 引用的文件时，运行此脚本验证引用完整性。

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..')

// Files that AGENTS.md references
const agentsMdPath = path.join(repoRoot, 'AGENTS.md')
const referencedFiles = [
  'ARCHITECTURE.md',
  'SECURITY.md',
  'RELIABILITY.md',
  'PLANS.md',
  'DESIGN.md',
  'FRONTEND.md',
  'PRODUCT_SENSE.md',
  'QUALITY_SCORE.md',
  'CLAUDE.md',
  'RULES.md',
  'docs/generated/db-schema.md',
  'docs/exec-plans/README.md',
  'docs/references/ehsy-client-sdk.md',
  'docs/references/wangeditor-notes.md',
  'docs/references/markitdown-sidecar-notes.md',
]

function checkFileExists(relPath) {
  return fs.existsSync(path.join(repoRoot, relPath))
}

function extractFileTree(content) {
  // Extract file tree from AGENTS.md (between ``` markers)
  const treeMatch = content.match(/```\n([\s\S]*?)\n```/)
  if (!treeMatch) return []
  
  const tree = treeMatch[1]
  const files = []
  const lines = tree.split('\n')
  
  for (const line of lines) {
    // Match lines like "├── ARCHITECTURE.md" or "│   └── docs/"
    const match = line.match(/[├└│]\s*(.+)/)
    if (match) {
      const name = match[1].trim()
      if (name && !name.endsWith('/')) {
        files.push(name)
      }
    }
  }
  
  return files
}

function main() {
  console.log('=== AGENTS.md Reference Validator ===\n')

  if (!fs.existsSync(agentsMdPath)) {
    console.error('✗ AGENTS.md not found')
    process.exit(1)
  }

  const content = fs.readFileSync(agentsMdPath, 'utf8')
  const violations = []

  // Check referenced files exist
  console.log('Checking referenced files...')
  for (const file of referencedFiles) {
    if (checkFileExists(file)) {
      console.log(`  ✓ ${file}`)
    } else {
      console.log(`  ✗ ${file} (MISSING)`)
      violations.push(`Referenced file missing: ${file}`)
    }
  }

  // Check file tree references
  console.log('\nChecking file tree references...')
  const treeFiles = extractFileTree(content)
  for (const file of treeFiles) {
    // Skip directory entries and placeholders
    if (file.includes('←') || file.includes('...')) continue
    
    if (checkFileExists(file)) {
      console.log(`  ✓ ${file}`)
    } else {
      console.log(`  ✗ ${file} (MISSING)`)
      violations.push(`File tree entry missing: ${file}`)
    }
  }

  // Check navigation table references
  console.log('\nChecking navigation table...')
  const navMatches = content.matchAll(/\|\s*`([^`]+)`\s*\|/g)
  for (const match of navMatches) {
    const ref = match[1]
    // Skip code examples and non-file references
    if (ref.startsWith('§') || ref.includes(' ') || ref.startsWith('npm')) continue
    
    if (checkFileExists(ref)) {
      console.log(`  ✓ ${ref}`)
    } else if (ref.includes('/')) {
      // Could be a directory reference
      const dirPath = path.join(repoRoot, ref)
      if (fs.existsSync(dirPath) && fs.statSync(dirPath).isDirectory()) {
        console.log(`  ✓ ${ref}/ (directory)`)
      } else {
        console.log(`  ✗ ${ref} (MISSING)`)
        violations.push(`Navigation table reference missing: ${ref}`)
      }
    }
  }

  // Summary
  console.log('\n=== Summary ===')
  if (violations.length === 0) {
    console.log('✓ All references are valid')
    process.exit(0)
  } else {
    console.log(`✗ Found ${violations.length} violation(s):\n`)
    for (const v of violations) {
      console.log(`  • ${v}`)
    }
    process.exit(1)
  }
}

main()
