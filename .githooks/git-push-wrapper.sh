#!/usr/bin/env bash
# Input: git push arguments (may include --no-verify)
# Output: gate pass/fail, then delegates to real git push (with --no-verify filtered)
# Pos: .githooks/ - push 命令别名包装器 (2026-06-09)
#
# 门禁流程：
#   1. 强制跑 pre-push-gate.sh（14 道门禁，不通过则 exit 1）
#   2. 过滤掉 --no-verify / -n 参数
#   3. 调用真实 git push（会触发 .githooks/pre-push 二次门禁）
#   4. push 成功后自动为 agent/* 分支创建 PR（Agent 无需记住）
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

# ── 4. 记下分支名，执行真实 push ──
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"

if ! "$REAL_GIT" push "${ARGS[@]}"; then
  echo "::git-push-wrapper:: git push 失败" >&2
  exit 1
fi

# ── 5. push 成功 → 为 agent/* 分支自动创建 PR ──
# 跳过 --dry-run 等非实际推送场景
DO_DRY_RUN=false
for arg in "${ARGS[@]}"; do
  [[ "$arg" == "--dry-run" ]] && DO_DRY_RUN=true
done

if [[ "$CURRENT_BRANCH" == agent/* ]] && [ "$DO_DRY_RUN" = false ]; then
  # 用最近的 commit message 作为 PR 标题
  PR_TITLE="$(git log -1 --format='%s' 2>/dev/null || echo "")"

  if [ -n "$PR_TITLE" ] && [ -x "$ROOT_DIR/scripts/pr-create.sh" ]; then
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  🔄 推送成功，自动创建 PR...                                 ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    # pr-create.sh 内部会检测远端是否已有同源 PR，有则跳过
    bash "$ROOT_DIR/scripts/pr-create.sh" "$PR_TITLE" <<< ""
  fi
fi
