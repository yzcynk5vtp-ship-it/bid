# 提交立项报错日期格式解析失败 根因分析

> Issue: CO-279
> 日期: 2026-06-19
> 排查者: kimi
> 修复 PR: `agent/kimi/co279-project-init-date-parse`

---

## 现场还原

**症状素描**：前端点击「提交立项」后，后端返回 400 Bad Request，报错 `Failed to load resource: the server responded with a status of 400 (Bad Request)`，请求路径 `POST /api/projects/{id}/initiation`。

**边界划定**：
- 立项表单中其他字段填写正常 ✅
- 开标时间 `bidOpenTime` 前端传值为 `2026-06-20 10:00:00` ❌
- 直接调用后端接口可复现 ❌

---

## 剥洋葱：逆向调用链

### Layer 1 — 入口/参数层

`InitiationController` 接收 `InitiationDto`：

```java
// backend/src/main/java/com/xiyu/bid/project/controller/InitiationController.java
@PostMapping("/api/projects/{projectId}/initiation")
public ApiResponse<InitiationResult> submit(@PathVariable Long projectId,
                                            @RequestBody @Valid InitiationDto dto) { ... }
```

### Layer 2 — 反序列化层

`InitiationDto` 中 `bidOpenTime` 字段类型为 `LocalDateTime`：

```java
// backend/src/main/java/com/xiyu/bid/project/dto/InitiationDto.java
private LocalDateTime bidOpenTime;
```

Jackson 默认的 `JavaTimeModule` 对 `LocalDateTime` 只接受 **ISO-8601 格式**（如 `2026-06-20T10:00:00`），而前端按业务习惯传的是 `yyyy-MM-dd HH:mm:ss`（带空格）。

**零号病人定位**：

```java
// 修复前：InitiationDto.java
private LocalDateTime bidOpenTime;   // 无 @JsonDeserialize
```

### Layer 3 — 数据层

反序列化失败发生在 `DispatcherServlet` 调用 controller 之前，请求体尚未进入业务校验层，因此 `InitiationFieldPolicy` 等校验逻辑未被执行。

---

## 必然性解释

- 前端 `bidOpenTime` 组件返回带空格的字符串格式
- Jackson `LocalDateTime` 默认反序列化器不认空格格式
- 请求在反序列化阶段直接抛 `InvalidFormatException` → Spring 包装为 400

**状态变迁图**：

```
用户点击提交立项
  → 前端 POST /api/projects/{id}/initiation
  → 请求体 bidOpenTime="2026-06-20 10:00:00"
  → Jackson 反序列化 LocalDateTime 失败
  → Spring 返回 400 Bad Request
```

---

## 验证与修复

### 修复 diff

新增宽容日期反序列化器，兼容 ISO-8601、`yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd HH:mm` 三种格式：

```java
// backend/src/main/java/com/xiyu/bid/project/dto/LenientLocalDateTimeDeserializer.java
public class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    };

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {}
        }
        throw new InvalidFormatException(p, "无法解析日期时间: " + value, value, LocalDateTime.class);
    }
}
```

在 `InitiationDto.bidOpenTime` 上应用：

```java
// backend/src/main/java/com/xiyu/bid/project/dto/InitiationDto.java
@JsonDeserialize(using = LenientLocalDateTimeDeserializer.class)
private LocalDateTime bidOpenTime;
```

### 最小验证

1. 单元测试覆盖三种格式：

```java
// backend/src/test/java/com/xiyu/bid/project/dto/LenientLocalDateTimeDeserializerTest.java
@Test
void parses_iso_local_date_time() { ... }

@Test
void parses_space_separated_seconds() { ... }

@Test
void parses_space_separated_minutes() { ... }
```

2. 前端重新提交立项，`bidOpenTime` 传 `2026-06-20 10:00:00`，接口返回 200。

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `InitiationDto.bidOpenTime` 未配置自定义反序列化器 | ✅ |
| 必然性已证明 | 前端空格格式 ≠ Jackson ISO-8601 默认格式 → 400 | ✅ |
| 最小验证已设计 | 三种格式单测 + 前端提交验证 | ✅ |
| 修复 diff 已提供 | 见上文 | ✅ |
| 防复发测试已设计 | `LenientLocalDateTimeDeserializerTest` | ✅ |

**Verdict**: ✅ **PASS**

---

## 为什么之前没有提前发现

1. **后端单元测试未覆盖日期格式边界**：原有测试使用 `LocalDateTime.now()` 直接构造 DTO，未经过 JSON 反序列化路径。
2. **前端组件输出格式与后端默认契约不一致**：业务上习惯用 `yyyy-MM-dd HH:mm:ss`，但后端按 ISO-8601 约定实现，缺乏契约对齐。
3. **接口契约文档未明确日期格式**：`InitiationDto` 没有文档或注解说明接受哪些格式。

---

## 防复发规范

1. 前端日期时间组件向后端传字符串时，必须在 API 契约文档或后端字段注解中声明可接受格式。
2. 新增 `LocalDateTime` / `LocalDate` 字段时，默认使用项目统一的宽容反序列化器，避免依赖 Jackson 默认行为。
3. 对接口字段的单元测试应包含「真实 JSON 请求体 → DTO」路径，而不仅是直接构造 DTO。
