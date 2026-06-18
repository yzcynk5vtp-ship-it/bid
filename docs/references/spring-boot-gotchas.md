# Spring Boot 陷阱与调试经验

记录开发过程中遇到的 Spring Boot 陷阱、配置问题和调试方法论。

## 1. Bean 名冲突：文件移动未删旧文件（PR794）

> 来源：2026-06-18 标讯创建接口 500 事故复盘（PR794）
> 适用范围：所有 Spring Boot 项目中重命名/移动 Controller、Service、Component 类的场景

### 事故一句话总结

PR792 将 `AdminRoleOssMenuSyncController` 从 `com.xiyu.bid.controller` 包移动到 `com.xiyu.bid.integration.organization.controller` 包，但**未删除旧文件**。Spring 启动时扫描到两个同名类，抛出 `ConflictingBeanDefinitionException` 导致应用启动失败 4 次，服务器被迫回滚到旧版本。

### 根因：Spring 默认用类名首字母小写作为 Bean 名

Spring 对 `@RestController`、`@Service`、`@Component` 等注解的类，默认使用**类名首字母小写**作为 Bean 名（`AnnotationBeanNameGenerator`）。当两个不同包下有同名类时，Bean 名冲突：

```
服务器日志证据：
Caused by: org.springframework.context.annotation.ConflictingBeanDefinitionException:
  Annotation-specified bean name 'adminRoleOssMenuSyncController' for bean class
  [com.xiyu.bid.controller.AdminRoleOssMenuSyncController] conflicts with existing
  bean definition of same name and class
  [com.xiyu.bid.integration.organization.controller.AdminRoleOssMenuSyncController]
```

**关键认知**：
- Spring 的 Bean 名生成策略只看类名，不看包路径
- 两个不同包下的同名类会被注册为同名 Bean，导致冲突
- `@RestController` 默认不会报错，只有在 Spring 扫描到第二个同名 Bean 时才抛异常
- 重构移动类时，IDE 通常会保留旧文件（特别是用 `Copy` 而非 `Move` 操作时）

### 正确修复：删除旧文件

```bash
# ❌ 错误：PR792 移动文件但保留旧文件
# 旧文件：backend/src/main/java/com/xiyu/bid/controller/AdminRoleOssMenuSyncController.java
# 新文件：backend/src/main/java/com/xiyu/bid/integration/organization/controller/AdminRoleOssMenuSyncController.java
# 两个文件内容相同，只是包名不同 → Bean 名冲突

# ✅ 正确：删除旧文件，只保留新位置的文件
rm backend/src/main/java/com/xiyu/bid/controller/AdminRoleOssMenuSyncController.java
```

### 备选方案：显式指定 Bean 名

如果确实需要保留两个同名类（不推荐），可以显式指定不同的 Bean 名：

```java
// 旧位置（不推荐保留）
@RestController("legacyAdminRoleOssMenuSyncController")
public class AdminRoleOssMenuSyncController { ... }

// 新位置
@RestController("adminRoleOssMenuSyncController")
public class AdminRoleOssMenuSyncController { ... }
```

**推荐**：移动类时直接删除旧文件，不要保留两个同名类。

### 诊断方法

1. 应用启动失败，日志出现 `ConflictingBeanDefinitionException`
2. 错误信息中包含两个不同包路径下的同名类
3. 检查是否有重构移动类后未删除旧文件的情况

### 通用规则：类移动/重命名的纪律

1. **移动类时必须删除旧文件**：用 `git mv` 或 IDE 的 `Move` 功能（而非 `Copy`）
2. **PR review 时检查文件数量**：移动类后，旧路径不应再有同名文件
3. **CI 门禁**：可以加一个脚本扫描同名类（不同包下的同名 `@RestController`/`@Service`/`@Component`）

### 排查方法

```bash
# 搜索所有同名类（不同包下的同名 Java 文件）
find backend/src/main/java -name "*.java" -exec basename {} \; | sort | uniq -d

# 搜索所有 @RestController/@Service/@Component 注解的类
grep -rn "@RestController\|@Service\|@Component" backend/src/main/java/ --include="*.java" -l

# 交叉比对：同名类 + Spring 注解 = 潜在 Bean 名冲突
```

### 测试验证

修复后应用启动成功，日志出现：
```
Started XiyuBidApplication in 19.638 seconds (process running for 20.193)
```

## 相关文档

- [jpa-hibernate-lessons.md](./jpa-hibernate-lessons.md) — JPA/Hibernate 教训（含 StackOverflowError）
- [AdminRoleOssMenuSyncController.java](../../backend/src/main/java/com/xiyu/bid/integration/organization/controller/AdminRoleOssMenuSyncController.java) — 新位置（保留）
