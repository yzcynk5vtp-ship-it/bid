#!/usr/bin/env bash
# Input: git push arguments (may include --no-verify)
# Output: gate pass/fail, then delegates to real git push (with --no-verify filtered)
# Pos: .githooks/ - push 命令别名包装器 (2026-06-09)
#
# 为什么有这个脚本：
#   通过 git alias 配置为 push 命令的替代入口，确保每次 git push
#   无论是否带 --no-verify 都先跑 pre-push-gate.sh（14 道门禁）。
#
# 门禁流程：
#   1. 强制跑 pre-push-gate.sh（不通过则 exit 1）
#   2. 过滤掉 --no-verify / -n 参数
#   3. 调用真实 git push（会触发 .githooks/pre-push 二次门禁）
#
# 激活方式（由 agent-start-task.sh 自动执行）：
#   git config alias.push '!bash .githooks/git-push-wrapper.sh'

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# ── 1. 强制跑门禁 ──
if ! bash "$ROOT_DIR/scripts/pre-push-gate.sh"; then
  echo ""
  echo "::git-push-wrapper:: 门禁未通过，推送已拒绝。" >&2
  exit 1
fi

# ── 2. 过滤 --no-verify ──
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
  echo "::git-push-wrapper:: 已过滤 --no-verify（门禁已强制跑）" >&2
fi

# ── 3. 找到真实 git ──
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
  echo "::git-push-wrapper:: 找不到真实 git 命令" >&2
  exit 1
fi

# ── 4. 委派给真实 git push ──
exec "$REAL_GIT" push "${ARGS[@]}"
