# 事件库 SDK 环境变量清单

> 本文档记录启用事件库 SDK（organization event-sdk）所需的全部环境变量。
>
> 参考配置源：`backend/src/main/resources/application.yml` 中 `xiyu.integrations.organization.event-sdk` 配置段（约 145-156 行和 275-277 行）。

## 环境变量一览

| 环境变量 | 用途 | 默认值 | 必填 |
|---|---|---|---|
| `XIYU_ORG_EVENT_SDK_ENABLED` | 启用事件库 SDK 的总开关 | `false` | 是 |
| `XIYU_ORG_EVENT_BROKER_SERVER_LIST` | Kafka broker 地址 | （空） | 是（启用时） |
| `XIYU_ORG_EVENT_BROKER_ZK_SERVERS` | ZooKeeper 地址 | （空） | 是（启用时） |
| `XIYU_ORG_EVENT_BROKER_ENV` | 环境标识：`test` / `staging` / `prod` | `test` | 是 |
| `XIYU_ORG_EVENT_SERVICE_NAME` | 服务注册名 | （空） | 是（启用注册时） |
| `XIYU_ORG_EVENT_SERVER_REGISTER_URL` | 服务注册 URL | （空） | 是（启用注册时） |
| `XIYU_ORG_EVENT_ENABLE_REGISTER` | 是否启用服务注册 | `true` | 否 |
| `XIYU_ORG_EVENT_RENEWAL_INITIAL_DELAY` | 续约初始延迟（秒） | `3` | 否 |
| `XIYU_ORG_EVENT_RENEWAL_PERIOD` | 续约周期（秒） | `3` | 否 |
| `XIYU_ORG_EVENT_RENEWAL_DURATION_MS` | 续约持续时间（毫秒） | `3000` | 否 |
| `XIYU_ORG_EVENT_CONSUMER_GROUP` | 消费者组名 | `bms` | 否 |

## 配置段对应关系

### 1. event-sdk 启用开关与消费者组（application.yml 约 275-277 行）

```yaml
event-sdk:
  enabled: ${XIYU_ORG_EVENT_SDK_ENABLED:false}
  consumer-group: ${XIYU_ORG_EVENT_CONSUMER_GROUP:bms}
```

- `XIYU_ORG_EVENT_SDK_ENABLED`：默认 `false`，仅在需要消费组织事件时设为 `true`。
- `XIYU_ORG_EVENT_CONSUMER_GROUP`：默认 `bms`，多实例共享同一消费者组以实现负载均衡。

### 2. client.register 服务注册（application.yml 约 143-147 行）

```yaml
client:
  register:
    serviceName: ${XIYU_ORG_EVENT_SERVICE_NAME:}
    serverRegisterUrl: ${XIYU_ORG_EVENT_SERVER_REGISTER_URL:}
    enableRegister: ${XIYU_ORG_EVENT_ENABLE_REGISTER:true}
```

- `XIYU_ORG_EVENT_SERVICE_NAME`：服务在注册中心的注册名，启用注册时必填。
- `XIYU_ORG_EVENT_SERVER_REGISTER_URL`：服务注册中心 URL，启用注册时必填。
- `XIYU_ORG_EVENT_ENABLE_REGISTER`：默认 `true`，设为 `false` 可关闭服务注册（仅消费不注册）。

### 3. client.renewal 续约配置（application.yml 约 148-151 行）

```yaml
renewal:
  initialDelay: ${XIYU_ORG_EVENT_RENEWAL_INITIAL_DELAY:3}
  period: ${XIYU_ORG_EVENT_RENEWAL_PERIOD:3}
  renewalDuration: ${XIYU_ORG_EVENT_RENEWAL_DURATION_MS:3000}
```

- `XIYU_ORG_EVENT_RENEWAL_INITIAL_DELAY`：续约任务启动后的初始延迟，默认 `3` 秒。
- `XIYU_ORG_EVENT_RENEWAL_PERIOD`：续约任务执行周期，默认 `3` 秒。
- `XIYU_ORG_EVENT_RENEWAL_DURATION_MS`：单次续约持续时间，默认 `3000` 毫秒。

### 4. broker.configure Kafka 配置（application.yml 约 152-156 行）

```yaml
broker:
  configure:
    serverList: ${XIYU_ORG_EVENT_BROKER_SERVER_LIST:}
    zkServers: ${XIYU_ORG_EVENT_BROKER_ZK_SERVERS:}
    env: ${XIYU_ORG_EVENT_BROKER_ENV:test}
```

- `XIYU_ORG_EVENT_BROKER_SERVER_LIST`：Kafka broker 地址列表，启用 SDK 时必填。
- `XIYU_ORG_EVENT_BROKER_ZK_SERVERS`：ZooKeeper 地址列表，启用 SDK 时必填。
- `XIYU_ORG_EVENT_BROKER_ENV`：环境标识，用于区分 `test` / `staging` / `prod`，默认 `test`。

## 启用示例

在 `.env.api` 或部署环境变量中配置：

```bash
# 启用开关
XIYU_ORG_EVENT_SDK_ENABLED=true

# Kafka 配置（按实际环境填写）
XIYU_ORG_EVENT_BROKER_SERVER_LIST=kafka-broker1:9092,kafka-broker2:9092
XIYU_ORG_EVENT_BROKER_ZK_SERVERS=zk1:2181,zk2:2181
XIYU_ORG_EVENT_BROKER_ENV=test

# 服务注册（如需注册）
XIYU_ORG_EVENT_SERVICE_NAME=xiyu-bid
XIYU_ORG_EVENT_SERVER_REGISTER_URL=http://register-center:8080

# 以下为可选项，不填使用默认值
# XIYU_ORG_EVENT_ENABLE_REGISTER=true
# XIYU_ORG_EVENT_RENEWAL_INITIAL_DELAY=3
# XIYU_ORG_EVENT_RENEWAL_PERIOD=3
# XIYU_ORG_EVENT_RENEWAL_DURATION_MS=3000
# XIYU_ORG_EVENT_CONSUMER_GROUP=bms
```

## 注意事项

1. **生产环境必须覆盖默认值**：`XIYU_ORG_EVENT_BROKER_ENV` 在生产环境必须设为 `prod`，不得使用默认的 `test`。
2. **关闭注册场景**：若仅需消费事件而不需被其他服务发现，可设置 `XIYU_ORG_EVENT_ENABLE_REGISTER=false`，此时 `SERVICE_NAME` 和 `SERVER_REGISTER_URL` 可不填。
3. **消费者组隔离**：不同业务系统应使用不同的 `XIYU_ORG_EVENT_CONSUMER_GROUP`，避免消息被其他系统消费；默认 `bms` 适用于本平台。
4. **默认关闭**：`XIYU_ORG_EVENT_SDK_ENABLED` 默认为 `false`，未显式启用时事件库 SDK 不生效，不影响其他功能。
