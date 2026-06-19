# Spring Boot Actuator 健康检查陷阱

> 主题：业务 API 正常，但 `/actuator/health` 返回 `OUT_OF_SERVICE`（503）
> 日期: 2026-06-19
> 排查者: kimi

---

## 现象

服务重启后，业务接口（如 `/api/notifications/unread-count`）返回 200，但：

```bash
curl -s http://172.16.38.78:8080/actuator/health
# {"status":"OUT_OF_SERVICE","groups":["liveness","readiness"]}
```

Nginx 或负载均衡若仅依赖 actuator health 做健康检查，可能误判服务不可用。

---

## 根因

项目自定义了 `JwtHealthIndicator`，校验 `JWT_SECRET` 环境变量长度：

```java
// backend/src/main/java/com/xiyu/bid/config/JwtHealthIndicator.java
public Health health() {
    if (jwtSecret == null || jwtSecret.length() < MIN_SECRET_LENGTH) {
        return Health.down()
                .withDetail("reason", "JWT secret too short for secure HMAC-SHA256")
                .build();
    }
    return Health.up().build();
}
```

服务器环境变量 `JWT_SECRET` 实际长度只有 **2 个字符**，远低于 `MIN_SECRET_LENGTH = 32`，导致 health indicator DOWN，整体 health 状态为 `OUT_OF_SERVICE`。

---

## 验证方法

```bash
# 1. 查看业务接口是否正常
ssh jetty@172.16.38.78 'curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/notifications/unread-count'
# 期望：200

# 2. 查看 actuator health 详情
ssh jetty@172.16.38.78 'curl -s http://127.0.0.1:8080/actuator/health'
# 若 show-details=never，只能看到 OUT_OF_SERVICE

# 3. 检查 JWT_SECRET 长度
ssh jetty@172.16.38.78 'echo "JWT_SECRET length: $(echo -n "$JWT_SECRET" | wc -c)"'
# 期望：>= 32；本次实际：2
```

---

## 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| health 503 但业务正常 | actuator 健康状态不等于业务可用性，需区分 liveness/readiness/business | 部署后同时检查 health 和业务接口 |
| JWT_SECRET 过短 | 环境变量配置与代码健康检查要求不匹配 | 部署脚本中校验 `JWT_SECRET` 长度 >= 32 |
| health details 默认隐藏 | 只看到 OUT_OF_SERVICE 时无法定位具体 indicator | 排查时临时开启 `management.endpoint.health.show-details=always` |

---

## 修复方案

1. **治标**：在服务器环境配置文件 `/etc/xiyu-bid/backend.env` 中设置长度 >= 32 的 `JWT_SECRET`，重启服务。
2. **治本**：在部署脚本中加入环境变量预检查，避免带无效 secret 启动：

```bash
# deploy-check.sh
SECRET_LEN=${#JWT_SECRET}
if [ "$SECRET_LEN" -lt 32 ]; then
    echo "ERROR: JWT_SECRET length $SECRET_LEN < 32"
    exit 1
fi
```

---

## 相关代码

- `backend/src/main/java/com/xiyu/bid/config/JwtHealthIndicator.java`
- 服务环境文件：`/etc/xiyu-bid/backend.env`
