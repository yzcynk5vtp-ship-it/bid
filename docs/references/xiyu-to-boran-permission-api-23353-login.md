# 西域给泊冉权限接口 - 登录接口

> 接口编号：23353
> 接口名称：登录接口
> 接口路径：`POST /oauth/login`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/login`
> 备注：登录接口，在操作日志表中记录登录信息（`sys_login_info`）

---

## 请求参数

### Headers

| 参数名称     | 参数值                            | 是否必须 | 示例 | 备注 |
| ------------ | --------------------------------- | -------- | ---- | ---- |
| Content-Type | application/x-www-form-urlencoded | 是       |      |      |

### Body

| 参数名称 | 参数类型 | 是否必须 | 示例   | 备注 |
| -------- | -------- | -------- | ------ | ---- |
| username | 文本     | 是       | 00481  |      |
| password | 文本     | 是       | 123456 |      |
| system   | 文本     | 是       | HOME   |      |

---

## 返回数据

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOlsiYXBpIl0sImV4cCI6MTcxODMzMzgzNCwidXNlcl9uYW1lIjoiMDY2NjkiLCJqdGkiOiJRalBqQUpJSl9RSEFka1d5T1paZDRBNWJ2MGsiLCJjbGllbnRfaWQiOiJ3ZWIiLCJzY29wZSI6WyJhbGwiXX0.co_2ug27DG72ie9ItYZXSHcDLQeWAijHCxq8jUmt6Xk",
    "token_type": "bearer",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOlsiYXBpIl0sInVzZXJfbmFtZSI6IjA2NjY5Iiwic2NvcGUiOlsiYWxsIl0sImF0aSI6IlFqUGpBSklKX1FIQWRrV3lPWlpkNEE1YnYwayIsImV4cCI6MTcxODMzOTgzNCwianRpIjoiMFJqV0EyZmJWNi16SXF4QXVyVFJBdmJ1T01ZIiwiY2xpZW50X2lkIjoid2ViIn0.zl9FZzd_0u1IWjOR3GYfUSLg_pjMh7k4_7lAPx9_P8o",
    "expires_in": 5998,
    "scope": "all",
    "jti": "QjPjAJIJ_QHAdkWyOZZd4A5bv0k"
  }
}
```

### 响应字段

| 名称           | 类型       | 是否必须 | 默认值 | 备注                   |
| -------------- | ---------- | -------- | ------ | ---------------------- |
| code           | number     | 非必须   |        |                        |
| message        | string     | 非必须   |        |                        |
| trace          | null       | 非必须   |        |                        |
| data           | object     | 非必须   |        |                        |
| ├ access_token | string     | 非必须   |        | 访问令牌               |
| ├ token_type   | string     | 非必须   |        | 令牌类型，示例：bearer |
| ├ refresh_token| string     | 非必须   |        | 刷新令牌               |
| ├ expires_in   | number     | 非必须   |        | 有效期（秒）           |
| ├ scope        | string     | 非必须   |        |                        |
| ├ permissions  | string[]   | 非必须   |        | 权限列表               |
| ├ id           | string     | 非必须   |        |                        |
| ├ username     | string     | 非必须   |        |                        |
| ├ jti          | string     | 非必须   |        |                        |
