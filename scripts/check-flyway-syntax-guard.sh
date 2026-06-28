#!/usr/bin/env bash
# Input: staged Flyway migration V*.sql files from git index
# Output: detects MariaDB-only syntax in MySQL 8.0 migrations
# Pos: scripts/ — Flyway 语法守卫（Flyway syntax guard）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Rule (hard block):
#   MySQL 8.0 不支持以下 MariaDB 扩展语法：
#     - ALTER TABLE ... ADD COLUMN IF NOT EXISTS
#     - ALTER TABLE ... DROP COLUMN IF EXISTS
#     - ALTER TABLE ... ADD INDEX IF NOT EXISTS
#     - ALTER TABLE ... DROP INDEX IF EXISTS
#     - CREATE INDEX IF NOT EXISTS
#   使用这些语法会导致 Flyway 迁移失败（success=0），引发启动失败。
#
# 允许的 IF NOT EXISTS 用法（MySQL 8.0 原生支持）：
#   - CREATE TABLE IF NOT EXISTS
#   - CREATE DATABASE IF NOT EXISTS
#   - 存储过程中的 IF NOT EXISTS (SELECT ...)
#
# 工程背景（请勿删除）：
# 2026-06-26 第4次部署事故（详见 docs/release/deploy-report-2026-06-26-4th.md:43-50）：
#   V1101 使用 ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completion_notes TEXT，
#   MySQL 8.0 不支持该语法（MariaDB 扩展），Flyway 记录为 failed（success=0），
#   导致后端启动失败。修复时需要停服 → 删除 flyway_schema_history 中 success=0 记录
#   → 从 JAR 中删除 V1101 文件 → 重启，耗时且高风险。
#   根因（deploy-report-2026-06-26-4th.md:120）当时未查清，本脚本堵住该缺口。
#
# 逃生阀：FLYWAY_ALLOW_MARIADB_SYNTAX=1 可绕过（仅在确认 MariaDB 兼容环境时使用）。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '')"
if [ -z "$ROOT_DIR" ]; then
  echo "flyway-syntax-guard: not in a git repo, skipping."
  exit 0
fi
cd "$ROOT_DIR"

script_name="$(basename "$0")"

red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }
yellow(){ printf '\033[33m%s\033[0m\n' "$*" >&2; }

# 逃生阀
if [ "${FLYWAY_ALLOW_MARIADB_SYNTAX:-0}" = "1" ]; then
  yellow "${script_name}: FLYWAY_ALLOW_MARIADB_SYNTAX=1 set, skipping MariaDB syntax guard."
  yellow "  ⚠ MySQL 8.0 不支持 ALTER ADD/DROP COLUMN IF NOT EXISTS，请确认目标数据库兼容性。"
  exit 0
fi

# 检测 staged 的 V*.sql 文件（A/M/R/C/T 状态都算）
STAGED_MIGRATIONS=$(git diff --cached --name-only --diff-filter=AMRCT -- \
  "backend/src/main/resources/db/migration-mysql/V*.sql" \
  "backend/src/main/resources/db/migration-mysql/B*.sql" 2>/dev/null | \
  grep -vE "^backend/src/main/resources/db/migration-mysql/U" || true)

if [ -z "$STAGED_MIGRATIONS" ]; then
  echo "${script_name}: no V/B migrations staged, skipping."
  exit 0
fi

# MariaDB 扩展语法模式（MySQL 8.0 不支持）
# 匹配大小写不敏感，忽略注释行（-- 开头）
MARIADB_PATTERNS=(
  "ADD[[:space:]]+COLUMN[[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS"
  "DROP[[:space:]]+COLUMN[[:space:]]+IF[[:space:]]+EXISTS"
  "ADD[[:space:]]+INDEX[[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS"
  "DROP[[:space:]]+INDEX[[:space:]]+IF[[:space:]]+EXISTS"
  "CREATE[[:space:]]+INDEX[[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS"
)

TMP_VIOLATIONS=$(mktemp)
trap 'rm -f "$TMP_VIOLATIONS"' EXIT

while IFS= read -r file; do
  [ -z "$file" ] && continue
  [ ! -f "$file" ] && continue

  # 跳过注释行（-- 开头），只检查实际 SQL
  while IFS= read -r linenum; do
    [ -z "$linenum" ] && continue
    line=$(sed -n "${linenum}p" "$file")
    # 跳过注释行
    echo "$line" | grep -qE "^\s*--" && continue

    for pattern in "${MARIADB_PATTERNS[@]}"; do
      if echo "$line" | grep -iqE "$pattern"; then
        echo "  ${file}:${linenum}: ${line}" >> "$TMP_VIOLATIONS"
        echo "    匹配模式: ${pattern}" >> "$TMP_VIOLATIONS"
        break
      fi
    done
  done < <(seq 1 $(wc -l < "$file"))
done <<< "$STAGED_MIGRATIONS"

VIOLATIONS=$(wc -l < "$TMP_VIOLATIONS" | tr -d ' ')

if [ "$VIOLATIONS" -eq 0 ]; then
  echo "${script_name}: no MariaDB-only syntax detected, skipping."
  exit 0
fi

red "❌ ${script_name}: 检测到 MariaDB 扩展语法（MySQL 8.0 不支持）"
red ""
red "以下 V*.sql 文件使用了 MySQL 8.0 不支持的 IF NOT EXISTS 语法："
cat "$TMP_VIOLATIONS" >&2
red ""
red "工程背景："
red "  2026-06-26 第4次部署事故：V1101 使用 ALTER TABLE ... ADD COLUMN IF NOT EXISTS，"
red "  MySQL 8.0 不支持（MariaDB 扩展），Flyway 记录为 failed（success=0），"
red "  导致后端启动失败，需要停服删除 flyway_schema_history 记录才能恢复。"
red ""
red "允许的 IF NOT EXISTS 用法（MySQL 8.0 原生支持）："
red "  - CREATE TABLE IF NOT EXISTS"
red "  - CREATE DATABASE IF NOT EXISTS"
red "  - 存储过程中的 IF NOT EXISTS (SELECT ...)"
red ""
red "修复方案（幂等迁移设计，参考 xiyu-server-deploy skill §7）："
red "  用存储过程 + information_schema 检查替代："
red "    DELIMITER \$\$"
red "    CREATE PROCEDURE IF NOT EXISTS repair_xxx() BEGIN"
red "      IF NOT EXISTS ("
red "        SELECT 1 FROM information_schema.columns"
red "        WHERE table_schema=DATABASE() AND table_name='xxx' AND column_name='yyy'"
red "      ) THEN"
red "        ALTER TABLE xxx ADD COLUMN yyy VARCHAR(255);"
red "      END IF;"
red "    END\$\$"
red "    DELIMITER ;"
red "    CALL repair_xxx();"
red ""
red "  参考实现: V1099__add_users_full_name_pinyin.sql, V1103__repair_personnel_missing_columns.sql"
red ""
red "逃生阀：FLYWAY_ALLOW_MARIADB_SYNTAX=1 可绕过本检查（仅在确认 MariaDB 兼容环境时使用）。"
exit 1
