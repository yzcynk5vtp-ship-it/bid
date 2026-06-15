# Symphony Smoke Test

<!-- tested by Claude, reviewed by Codex -->

> Linear: [CO-205](https://linear.app/ericforai/issue/CO-205/claudecodex-routing-workflow-test)
> Branch: `agent/symphony/CO-204-routing-test`

## Purpose

Smoke-test artifact for the Claude-exec / Codex-review routing workflow.
Its presence on the task branch proves Symphony claimed CO-205, routed it to
Claude (exec), Claude landed a workspace edit on this file, and Codex (review)
was invoked against the resulting diff — all on a doc-only change with no
hot-path overlap. "Signed off" is an outcome of the review, not something
this artifact can assert on its own; reviewers should consult the Integrator
review state on the PR for the current verdict.

## Verification (reviewer-runnable)

Paste from the repo root after checking out `agent/symphony/CO-204-routing-test`:

```bash
set -e

# 1. Marker line present, verbatim, exactly once. Uses `-Fx` (whole-line,
#    fixed-string) so inline references elsewhere in this doc don't inflate
#    the count.
test "$(grep -Fxc '<!-- tested by Claude, reviewed by Codex -->' docs/symphony-smoke.md)" -eq 1

# 2. Diff footprint is doc-only (rule 1 hot-path gate).
test "$(git diff --name-only origin/main..HEAD)" = "docs/symphony-smoke.md"

# 3. Hot-path blacklist (rule 1) — must match nothing.
! git diff --name-only origin/main..HEAD \
  | grep -E '^(backend/src/main/resources/db/migration-mysql/|backend/src/main/resources/db/rollback/migration-mysql/|backend/src/main/java/com/xiyu/bid/entity/|backend/src/main/resources/application.*\.yml|src/router/index\.js|src/views/Login\.vue|\.github/workflows/|\.githooks/)'

# 4. Branch naming (rule 2) — must start with agent/symphony/.
git rev-parse --abbrev-ref HEAD | grep -q '^agent/symphony/'

# 5. Branch tip is published — local HEAD matches the remote-tracking ref.
#    Uses the explicit `origin/<branch>` ref (not `@{u}`) so it does not
#    depend on per-checkout upstream-tracking config; any reviewer who has
#    fetched the branch has this ref. Catches the "committed but forgot to
#    push" failure mode that the naming-only check above cannot detect.
test "$(git rev-parse HEAD)" = "$(git rev-parse origin/agent/symphony/CO-204-routing-test)"
```

## Acceptance criteria

- [x] The literal line `<!-- tested by Claude, reviewed by Codex -->` exists
      in this file (once).
- [x] Diff footprint is doc-only (`docs/symphony-smoke.md`).
- [x] No `WORKFLOW.md` rule-1 hot path is touched
      (authoritative list: [`WORKFLOW.md` §1](../WORKFLOW.md#1-hot-paths-blacklist--do-not-modify)).
- [x] Commit lands on `agent/symphony/CO-204-routing-test`.
- [x] Branch tip is published to `origin` (local HEAD == `origin/<branch>`).
- [x] Verification block reproduces locally without external services.
