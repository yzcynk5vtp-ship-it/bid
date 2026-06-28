#!/usr/bin/env bash
# Input: FLYWAY_ALLOW_* environment variables set during git commit
# Output: appends bypass events to .runtime/flyway-bypass.log
# Pos: scripts/ — Flyway 逃生阀追踪器（Flyway bypass tracker）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Rule:
#   当开发者使用以下逃生阀绕过 Flyway 检查时，记录到 .runtime/flyway-bypass.log：
#     - FLYWAY_ALLOW_LEGACY_DIR=1      （迁移目录守卫）
#     - FLYWAY_ALLOW_CONFIG_EDIT=1     （配置守卫）
#     - FLYWAY_ALLOW_MARIADB_SYNTAX=1  （语法守卫）
#     - FLYWAY_ALLOW_IMMUTABLE_EDIT=1  （已发布迁移不可变守卫）
#     - FLYWAY_DB_SYNC_SKIP=1          （DB 同步检查）
#     - SKIP_FLYWAY_VALIDATE=1         （部署时 Flyway validate 预检）
#
#   记录内容：时间戳、分支、commit、绕过的检查项、文件列表
#   用途：PR 创建时检查该日志，未说明绕过原因则要求补充
#
# 工程背景（请勿删除）：
# 2026-06-28 V1106 事故复盘发现，Flyway 检查有多条逃生阀（FLYWAY_ALLOW_*），
# 但绕过后是否完成了"PR 说明 + 部署前 repair"完全靠人工自觉，没有自动追踪。
# 本脚本将逃生阀使用事件记录到 .runtime/flyway-bypass.log，供 PR 审查时参考。
#
# 集成方式：
#   在 .githooks/pre-commit 末尾调用（不阻断提交，仅记录）
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '')"
if [ -z "$ROOT_DIR" ]; then
  exit 0
fi
cd "$ROOT_DIR"

script_name="$(basename "$0")"

# 逃生阀环境变量列表
BYPASS_VARS=(
  "FLYWAY_ALLOW_LEGACY_DIR"
  "FLYWAY_ALLOW_CONFIG_EDIT"
  "FLYWAY_ALLOW_MARIADB_SYNTAX"
  "FLYWAY_ALLOW_IMMUTABLE_EDIT"
  "FLYWAY_DB_SYNC_SKIP"
  "SKIP_FLYWAY_VALIDATE"
)

# 检查是否有逃生阀被使用
ACTIVE_BYPASSES=()
for var in "${BYPASS_VARS[@]}"; do
  val="${!var:-0}"
  if [ "$val" = "1" ]; then
    ACTIVE_BYPASSES+=("${var}")
  fi
done

# 无逃生阀使用，无需记录
if [ ${#ACTIVE_BYPASSES[@]} -eq 0 ]; then
  exit 0
fi

# 创建 .runtime 目录（如不存在）
mkdir -p "$ROOT_DIR/.runtime"

LOG_FILE="$ROOT_DIR/.runtime/flyway-bypass.log"

# 获取当前 git 信息
BRANCH=$(git branch --show-current 2>/dev/null || echo "unknown")
COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "pre-commit")
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# 获取 staged 文件列表（简短）
STAGED_FILES=$(git diff --cached --name-only 2>/dev/null | head -10 | tr '\n' ',' | sed 's/,$//')

# 追加日志
{
  echo "---"
  echo "timestamp: ${TIMESTAMP}"
  echo "branch: ${BRANCH}"
  echo "commit: ${COMMIT}"
  echo "bypasses: ${ACTIVE_BYPASSES[*]}"
  echo "files: ${STAGED_FILES}"
} >> "$LOG_FILE"

yellow() { printf '\033[33m%s\033[0m\n' "$*"; }
yellow "⚠ ${script_name}: 检测到 Flyway 逃生阀被使用"
yellow "  绕过的检查: ${ACTIVE_BYPASSES[*]}"
yellow "  已记录到: ${LOG_FILE}"
yellow "  ⚠ 请在 commit message / PR 描述中说明绕过原因，部署前必须跑 flyway validate。"
exit 0
