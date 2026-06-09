#!/usr/bin/env bash
# Input: git commit arguments (may include --no-verify)
# Output: delegates to real git commit (with --no-verify filtered)
# Pos: .githooks/ - commit 命令别名包装器 (2026-06-09)
#
# 为什么有这个脚本：
#   通过 git alias 配置为 commit 命令的替代入口，确保每次 git commit
#   无论是否带 --no-verify 都先跑 pre-commit hook。
#
# 门禁流程：
#   1. 过滤掉 --no-verify / -n 参数（让 pre-commit hook 始终触发）
#   2. 调用真实 git commit（会触发 .githooks/pre-commit 跑所有门禁）
#
# 激活方式（由 agent-start-task.sh 自动执行）：
#   git config alias.commit '!bash .githooks/git-commit-wrapper.sh'

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# ── 过滤 --no-verify ──
ARGS=()
HAS_NO_VERIFY=0
for arg in "$@"; do
  if [[ "$arg" == "--no-verify" || "$arg" == "-n" ]]; then
    HAS_NO_VERIFY=1
    continue
  fi
  ARGS+=("$arg")
done

if [ "$HAS_NO_VERIFY" = "1" ]; then
  echo "::git-commit-wrapper:: 已过滤 --no-verify（pre-commit hook 将正常触发）" >&2
fi

# ── 找到真实 git ──
REAL_GIT=""
for p in /usr/bin/git /usr/local/bin/git /opt/homebrew/bin/git; do
  if [ -x "$p" ]; then
    REAL_GIT="$p"
    break
  fi
done
if [ -z "$REAL_GIT" ]; then
  REAL_GIT="$(command -v git 2>/dev/null || echo "")"
fi
if [ -z "$REAL_GIT" ]; then
  echo "::git-commit-wrapper:: 找不到真实 git 命令" >&2
  exit 1
fi

# ── 委派给真实 git commit ──
exec "$REAL_GIT" commit "${ARGS[@]}"
