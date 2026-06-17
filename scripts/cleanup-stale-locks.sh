#!/usr/bin/env bash
# 清理所有 worktree 中的 stale agent 锁文件
# 用法: bash /Users/user/xiyu/worktrees/trae/scripts/cleanup-stale-locks.sh
set -euo pipefail

WORKTREES=(
  /Users/user/xiyu/worktrees/claude
  /Users/user/xiyu/worktrees/codex
  /Users/user/xiyu/worktrees/cursor
  /Users/user/xiyu/worktrees/gemini
  /Users/user/xiyu/worktrees/kimi
  /Users/user/xiyu/worktrees/mimo
  /Users/user/xiyu/worktrees/qoder
  /Users/user/xiyu/xiyu-bid-poc
)

total_files=0
total_yml=0

for wt in "${WORKTREES[@]}"; do
  wt_name=$(basename "$wt")
  locks_dir="$wt/.agent-locks"
  locks_yml="$wt/.agent-locks.yml"

  echo "=== [$wt_name] ==="

  # 1. 删除 per-task 锁文件
  if [ -d "$locks_dir" ]; then
    files=$(find "$locks_dir" -name "*.yml" -not -name ".gitkeep" 2>/dev/null)
    if [ -n "$files" ]; then
      count=$(echo "$files" | wc -l | tr -d ' ')
      echo "  删除 $count 个 per-task 锁文件"
      echo "$files" | while IFS= read -r f; do
        rm -f "$f"
      done
      total_files=$((total_files + count))
    else
      echo "  (无 per-task 锁文件)"
    fi
  fi

  # 2. 清空 .agent-locks.yml 的 stale 条目（保留文件头注释）
  if [ -f "$locks_yml" ]; then
    # 检查是否有 stale 条目
    if grep -q "^- path:" "$locks_yml" 2>/dev/null; then
      # 保留注释头，替换 locks: 段为空
      # 找到 "locks:" 行，删除其后所有条目
      awk '/^locks:/{print "locks: []"; skip=1; next} skip && /^- /{next} skip && /^[^ #-]/{skip=0} skip{next} {print}' "$locks_yml" > "$locks_yml.tmp" && mv "$locks_yml.tmp" "$locks_yml"
      echo "  清空 .agent-locks.yml stale 条目"
      total_yml=$((total_yml + 1))
    else
      echo "  .agent-locks.yml 已是空 (locks: [])"
    fi
  fi
done

echo ""
echo "=== 清理汇总 ==="
echo "删除 per-task 锁文件: $total_files 个"
echo "清空 .agent-locks.yml: $total_yml 个"

# 验证
echo ""
echo "=== 验证结果 ==="
for wt in "${WORKTREES[@]}"; do
  wt_name=$(basename "$wt")
  remaining=$(find "$wt/.agent-locks" -name "*.yml" -not -name ".gitkeep" 2>/dev/null | wc -l | tr -d ' ')
  yml_status=$(grep -q "^- path:" "$wt/.agent-locks.yml" 2>/dev/null && echo "STALE" || echo "OK")
  echo "[$wt_name] per-task=$remaining  .agent-locks.yml=$yml_status"
done
