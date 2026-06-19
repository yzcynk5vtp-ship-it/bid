# 构建工具陷阱与调试经验

记录构建工具（Maven、git-commit-id-plugin、worktree 等）的陷阱和调试方法。

---

## 1. git-commit-id-maven-plugin 在 worktree 中读取主仓库 HEAD，git.properties 元数据失真

### 问题

在 git worktree 中执行 `mvn package` 构建后，jar 内的 `git.properties` 显示的 commit id **不是当前 worktree 分支的 HEAD**，而是主仓库（main worktree）的 HEAD。

```bash
# 在 worktree agent/zcode/co-277 中构建
$ git log -1 --format='%H'   # 当前 worktree HEAD
9c25985ff...

$ unzip -p target/bid-poc-1.0.3.jar git.properties | grep commit
git.commit.id=1e8d18e...   # ❌ 这是主仓库 HEAD，不是当前分支！
```

### 根因

`git-commit-id-maven-plugin` 通过 `.git` 目录读取 git 信息。在 worktree 中，`.git` 是一个**指向主仓库 git 目录的文件**（不是目录），plugin 读取的是主仓库的 HEAD（通常是 main），而非当前 worktree 的分支 HEAD。

```bash
$ cat .git
gitdir: /Users/user/xiyu/worktrees/zcode/.git/worktrees/zcode   # 指向主仓库

# plugin 读取的是主仓库 HEAD（main 分支），不是当前 worktree 的 HEAD
```

### 危害

部署后用 `git.properties` 验证"jar 是否含本次修复"会得到**错误结论**——git.properties 显示旧 commit，但 jar 内的 class 文件其实是新代码。这会导致：

1. 误判"部署失败/回退"，重复部署浪费时间
2. 误判"代码没编译进去"，反复检查编译流程
3. 排查方向跑偏（去查 CI/CD 流程，而非验证 class 内容）

### 正确做法：用 class 文件内容验证，而非 git.properties

```bash
# ✅ 正确：直接验证 jar 内 class 文件是否含本次修复的符号/字符串
$ unzip -p target/bid-poc-1.0.3.jar \
    BOOT-INF/classes/com/xiyu/bid/integration/external/CrmTenderLinkService.class \
    | strings | grep -E "tryParseChanceId|findProjectLeaderByChanceId"
findProjectLeaderByChanceId   # ✅ 命中，说明新代码已编译进 jar
tryParseChanceId

# ❌ 错误：信任 git.properties（worktree 下会失真）
$ unzip -p target/bid-poc-1.0.3.jar git.properties | grep commit
git.commit.id=1e8d18e...   # 误导：显示主仓库 HEAD，非当前分支
```

服务器端验证同理：

```bash
# 服务器上验证部署的 jar 含新代码
$ sudo unzip -p /opt/xiyu-bid/shared/backend/app.jar \
    BOOT-INF/classes/com/xiyu/bid/integration/external/CrmTenderLinkService.class \
    | strings | grep "tryParseChanceId"
tryParseChanceId   # ✅
```

### 通用规则

1. **worktree 构建的 jar，git.properties 不可信**——它读取主仓库 HEAD，不是当前分支
2. **验证 jar 含某次修复，用 class 文件 `strings` 查特征符号**（方法名、常量字符串），这是编译产物的直接证据
3. **不要用 git.properties 作为部署验证依据**，尤其在 worktree 开发流程下
4. 选择特征符号时挑**本次新增的、唯一的**（如新方法名 `tryParseChanceId`），避免命中旧代码

### 相关命令

```bash
# 查 jar 内 class 是否含某符号
unzip -p <jar> BOOT-INF/classes/<path>.class | strings | grep "<symbol>"

# 查 git.properties（仅供参考，worktree 下不可信）
unzip -p <jar> git.properties | grep -E "commit|branch"

# 查 worktree 的 .git 指向
cat .git
```
