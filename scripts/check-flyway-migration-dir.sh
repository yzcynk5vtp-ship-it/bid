#!/usr/bin/env bash
# Input: staged Flyway migration files from git index
# Output: detects V*.sql / B*.sql migrations staged under the legacy db/migration/ directory
# Pos: scripts/ — Flyway 迁移目录守卫（migration directory guard）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Rule (hard block):
#   新增/修改的 Flyway 正向迁移（V*.sql / B*.sql）必须放在 db/migration-mysql/，
#   不能放在 db/migration/（历史遗留目录，Flyway 不读取）。
#   放错目录会导致迁移永远不执行，但 Flyway 不会报错（静默跳过），引发生产事故。
#
# 工程背景（请勿删除）：
# 2026-06-28 V1106 事故（详见 docs/release/deploy-report-2026-06-28-10th.md）
# 问题根因：CO-382(commit 0834deb91)添加 tasks.created_by 列时，迁移文件误放至
#   db/migration/V121__add_created_by_to_tasks.sql（历史遗留目录），而 application.yml:31
#   配置 locations: classpath:db/migration-mysql，Flyway 根本不读取 db/migration/。
#   导致生产 DB 缺少 created_by 列，Task.java 实体的 @Column(name="created_by") 触发
#   Hibernate 查询该列，引发 SQLSyntaxErrorException: Unknown column 't1_0.created_by'，
#   /api/tasks/my 返回 500，生产服务中断约 30 分钟。
# 本脚本将"目录正确性"检查前移到 pre-commit，让"放错目录"在提交时就被拦截。
#
# 逃生阀：FLYWAY_ALLOW_LEGACY_DIR=1 可绕过（仅在迁移历史归档等特殊场景使用）。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '')"
if [ -z "$ROOT_DIR" ]; then
  echo "flyway-migration-dir: not in a git repo, skipping."
  exit 0
fi
cd "$ROOT_DIR"

# 历史遗留目录（Flyway 不读取）
LEGACY_DIR="backend/src/main/resources/db/migration"
# 当前生效目录（Flyway 读取）
ACTIVE_DIR="backend/src/main/resources/db/migration-mysql"
script_name="$(basename "$0")"

red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }
yellow(){ printf '\033[33m%s\033[0m\n' "$*" >&2; }

# 逃生阀
if [ "${FLYWAY_ALLOW_LEGACY_DIR:-0}" = "1" ]; then
  yellow "${script_name}: FLYWAY_ALLOW_LEGACY_DIR=1 set, skipping legacy-dir check."
  yellow "  ⚠ 放错目录会导致迁移不执行且无告警，请确认是否真的要放在历史目录。"
  exit 0
fi

# 检测 staged 的 V*.sql / B*.sql 在 legacy 目录下（A/M/R/C/T 状态都算）
# 不检查 U*.sql 回滚脚本（回滚脚本放错目录影响较小，且历史目录有 U115~U120）
STAGED_LEGACY=$(git diff --cached --name-status --diff-filter=AMRCT -- "${LEGACY_DIR}/" | \
  grep -E "/[VB][0-9]+__.*\.sql$" || true)

if [ -z "$STAGED_LEGACY" ]; then
  echo "${script_name}: no V/B migrations staged under legacy dir, skipping."
  exit 0
fi

red "❌ ${script_name}: 检测到 Flyway 正向迁移文件放在历史遗留目录"
red ""
red "以下文件 staged 在 ${LEGACY_DIR}/（Flyway 不读取此目录）："
red ""
printf '%s\n' "$STAGED_LEGACY" | while IFS=$'\t' read -r status path; do
  red "  ${status}  ${path}"
done | sed 's/^/  /'
red ""
red "工程背景："
red "  application.yml 配置 spring.flyway.locations: classpath:db/migration-mysql"
red "  放在 db/migration/ 的迁移文件永远不会被执行，且 Flyway 不会报错（静默跳过）。"
red "  历史事故：2026-06-28 V1106 放错目录导致 /api/tasks/my 500 错误，生产中断 30 分钟。"
red ""
red "修复方案："
red "  将上述文件移到 ${ACTIVE_DIR}/，例如："
red "    git mv ${LEGACY_DIR}/V121__xxx.sql ${ACTIVE_DIR}/V1107__xxx.sql"
red "  （注意：版本号需使用 scripts/next-migration-version.sh 重新分配，避免撞号）"
red ""
red "逃生阀：FLYWAY_ALLOW_LEGACY_DIR=1 可绕过（仅在归档历史迁移等特殊场景使用）。"
exit 1
