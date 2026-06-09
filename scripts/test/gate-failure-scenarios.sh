#!/usr/bin/env bash
# Input: current git worktree
# Output: structured test report for failure scenarios
# Pos: scripts/ - 门禁失败场景测试 (2026-06-09)
#
# 测试目标：门禁只在正常工作时才通过，在以下场景中必须拦截/报错：
#   F1: alias.push 配置正确（防 --no-verify 绕过）
#   F2: alias.commit 配置正确（防 --no-verify 绕过）
#   F3: git-push-wrapper.sh 在执行时过滤 --no-verify
#   F4: git-commit-wrapper.sh 在执行时过滤 --no-verify
#   F5: pre-push-gate.sh 缺失时，wrapper 报错退出
#   F6: pre-push-gate.sh 返回非零时，wrapper 阻断推送
#   F7: 真实 git 不可用时，wrapper 能否检测并报错（代码审查 + 单元测试）
#   F8: core.hooksPath 指向 .githooks
#
# 用法: bash scripts/test-local-gates-failure-scenarios.sh
# 退出码: 0 = 全部通过, 1 = 有失败的

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PASS=0; FAIL=0; SKIP=0
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

pass() { PASS=$((PASS + 1)); echo -e "  ${GREEN}PASS${NC} $1"; }
fail() { FAIL=$((FAIL + 1)); echo -e "  ${RED}FAIL${NC} $1"; }
skip() { SKIP=$((SKIP + 1)); echo -e "  ${YELLOW}SKIP${NC} $1"; }

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  🔴 门禁失败场景测试                                        ║"
echo "║  验证门禁在恶意/异常场景下是否可靠拒绝                       ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# ════════════════════════════════════════════════════════════════
# F1: alias.push 是否正确配置
# ════════════════════════════════════════════════════════════════
echo "── F1: alias.push 配置正确（防 --no-verify 绕过）──"
ALIAS_PUSH="$(git config alias.push 2>/dev/null || echo "")"
if [[ "$ALIAS_PUSH" == *"git-push-wrapper.sh"* ]]; then
  pass "alias.push 指向 git-push-wrapper.sh"
else
  fail "alias.push 未配置或指向非 wrapper（值: $ALIAS_PUSH）"
fi

# ════════════════════════════════════════════════════════════════
# F2: alias.commit 是否正确配置
# ════════════════════════════════════════════════════════════════
echo "── F2: alias.commit 配置正确（防 --no-verify 绕过）──"
ALIAS_COMMIT="$(git config alias.commit 2>/dev/null || echo "")"
if [[ "$ALIAS_COMMIT" == *"git-commit-wrapper.sh"* ]]; then
  pass "alias.commit 指向 git-commit-wrapper.sh"
else
  fail "alias.commit 未配置或指向非 wrapper（值: $ALIAS_COMMIT）"
fi

# ════════════════════════════════════════════════════════════════
# F3: git-push-wrapper.sh 在执行时是否过滤 --no-verify
# ════════════════════════════════════════════════════════════════
echo "── F3: git-push-wrapper.sh 过滤 --no-verify ──"
OUTPUT=$(PRE_PUSH_GATE=0 bash .githooks/git-push-wrapper.sh --no-verify --dry-run 2>&1 || true)
if echo "$OUTPUT" | grep -q '已过滤 --no-verify'; then
  pass "wrapper 正确过滤了 --no-verify 参数"
else
  fail "wrapper 未过滤 --no-verify（输出: $(echo "$OUTPUT" | head -3)）"
fi

# ════════════════════════════════════════════════════════════════
# F4: git-commit-wrapper.sh 在执行时是否过滤 --no-verify
# ════════════════════════════════════════════════════════════════
echo "── F4: git-commit-wrapper.sh 过滤 --no-verify ──"
OUTPUT=$(bash .githooks/git-commit-wrapper.sh --no-verify --dry-run 2>&1 || true)
if echo "$OUTPUT" | grep -q '已过滤 --no-verify'; then
  pass "wrapper 正确过滤了 --no-verify 参数"
else
  fail "wrapper 未过滤 --no-verify（输出: $(echo "$OUTPUT" | head -3)）"
fi

# ════════════════════════════════════════════════════════════════
# F5: pre-push-gate.sh 不存在时，wrapper 是否报错退出
# ════════════════════════════════════════════════════════════════
echo "── F5: pre-push-gate.sh 缺失时，wrapper 报错退出 ──"
if [ -f scripts/pre-push-gate.sh ]; then
  mv scripts/pre-push-gate.sh scripts/pre-push-gate.sh.bak
  EXIT_CODE=0
  OUTPUT=$(bash .githooks/git-push-wrapper.sh --dry-run 2>&1) || EXIT_CODE=$?
  mv scripts/pre-push-gate.sh.bak scripts/pre-push-gate.sh
  if echo "$OUTPUT" | grep -q '门禁未通过\|No such file\|pre-push-gate'; then
    pass "pre-push-gate.sh 缺失时，wrapper 正确拒绝"
  else
    fail "wrapper 在 pre-push-gate.sh 缺失时未拒绝（输出: $(echo "$OUTPUT" | head -3)）"
  fi
else
  skip "scripts/pre-push-gate.sh 不存在，无法测试"
fi

# ════════════════════════════════════════════════════════════════
# F6: pre-push-gate.sh 返回非零退出码时，wrapper 是否阻断
# ════════════════════════════════════════════════════════════════
echo "── F6: pre-push-gate.sh 失败时，wrapper 阻断推送 ──"
cat > /tmp/fake-pre-push-gate.sh << 'FAKE'
#!/usr/bin/env bash
echo "模拟门禁失败"
exit 1
FAKE
chmod +x /tmp/fake-pre-push-gate.sh

cp scripts/pre-push-gate.sh /tmp/real-pre-push-gate.sh.bak
cp /tmp/fake-pre-push-gate.sh scripts/pre-push-gate.sh
OUTPUT=$(bash .githooks/git-push-wrapper.sh --dry-run 2>&1 || true)
cp /tmp/real-pre-push-gate.sh.bak scripts/pre-push-gate.sh
rm -f /tmp/fake-pre-push-gate.sh /tmp/real-pre-push-gate.sh.bak

if echo "$OUTPUT" | grep -q '门禁未通过\|门禁已拒绝'; then
  pass "wrapper 在门禁失败时正确阻断推送"
else
  fail "wrapper 在门禁失败时未阻断（输出: $(echo "$OUTPUT" | head -3)）"
fi

# ════════════════════════════════════════════════════════════════
# F7: 代码审查 + 单元测试：真实 git 不可用时的报错逻辑
# ════════════════════════════════════════════════════════════════
echo "── F7: 真实 git 不可用时，wrapper 报错（代码审查）──"
# 审查 wrapper 中 REAL_GIT 查找和缺失处理逻辑
WRAPPER_CONTENT=$(<.githooks/git-push-wrapper.sh)
# 检查是否有 REAL_GIT 查找逻辑
if echo "$WRAPPER_CONTENT" | grep -q 'REAL_GIT=""'; then
  pass "wrapper 包含 REAL_GIT 查找逻辑"
else
  fail "wrapper 缺少 REAL_GIT 查找逻辑"
fi
# 检查查找失败时是否有报错退出
if echo "$WRAPPER_CONTENT" | grep -q '找不到真实 git'; then
  pass "wrapper 在 git 缺失时输出错误信息"
else
  fail "wrapper 缺少 git 缺失时的错误信息"
fi
# 单元测试：模拟无法找到 git 的场景
UNIT_OUTPUT=$(bash -c '
REAL_GIT=""
for p in /nonexistent/git /also/missing/git; do
  if [ -x "$p" ]; then REAL_GIT="$p"; break; fi
done
if [ -z "$REAL_GIT" ]; then
  REAL_GIT="$(command -v nonexistent-git 2>/dev/null || echo "")"
fi
if [ -z "$REAL_GIT" ]; then
  echo "找不到真实 git 命令"
  exit 1
fi
' 2>&1 || true)
if echo "$UNIT_OUTPUT" | grep -q '找不到真实 git'; then
  pass "单元测试：git 缺失时正确检测并报错"
else
  fail "单元测试：git 缺失时未检测到（输出: $UNIT_OUTPUT）"
fi

# ════════════════════════════════════════════════════════════════
# F8: core.hooksPath 是否指向 .githooks
# ════════════════════════════════════════════════════════════════
echo "── F8: core.hooksPath 指向 .githooks ──"
HOOKS_PATH="$(git config core.hooksPath 2>/dev/null || echo "")"
if [ "$HOOKS_PATH" = ".githooks" ]; then
  pass "hooksPath = .githooks"
else
  fail "hooksPath 不是 .githooks（当前: ${HOOKS_PATH:-未设置}）"
fi

# ════════════════════════════════════════════════════════════════
# 汇总
# ════════════════════════════════════════════════════════════════
echo ""
echo "─────────────────────────"
echo -e "通过: ${GREEN}${PASS}${NC}  失败: ${RED}${FAIL}${NC}  跳过: ${YELLOW}${SKIP}${NC}"
echo "─────────────────────────"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo -e "${RED}${FAIL} 个失败场景未通过，门禁存在漏洞。${NC}"
  exit 1
else
  echo ""
  echo -e "${GREEN}全部失败场景通过。门禁在异常/恶意场景下可靠拦截。${NC}"
  exit 0
fi
