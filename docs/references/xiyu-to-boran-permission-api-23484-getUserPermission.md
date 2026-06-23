# 西域给泊冉权限接口 - 根据 token 获取用户系统权限接口

> 接口编号：23484
> 接口名称：根据 token 获取用户系统权限接口
> 接口路径：`GET /oauth/getUserPermission`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/getUserPermission`
> 备注：根据 token 获取用户在各系统中的菜单权限

---

## 请求参数

### Headers

| 参数名称      | 参数值                  | 是否必须 | 示例 | 备注 |
| ------------- | ----------------------- | -------- | ---- | ---- |
| Authorization | Bearer `<access_token>` | 是       |      | 登录接口返回的 access_token |

### Query

| 参数名称   | 是否必须 | 示例 | 备注     |
| ---------- | -------- | ---- | -------- |
| systemName | 否       | SMS  | 系统名称 |

---

## 返回数据

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": {
    "OPC": [
      "sale_log",
      "sale_order"
    ],
    "SMS": [
      "sys_org",
      "sys_user"
    ]
  }
}
```

### 响应字段

| 名称    | 类型     | 是否必须 | 默认值 | 备注                     |
| ------- | -------- | -------- | ------ | ------------------------ |
| code    | number   | 非必须   |        |                          |
| message | string   | 非必须   |        |                          |
| trace   | null     | 非必须   |        |                          |
| data    | object   | 非必须   |        | 键为系统名，值为菜单路径数组 |
| ├ OPC   | string[] | 非必须   |        | 示例系统：OPC 菜单路径   |
| ├ SMS   | string[] | 非必须   |        | 示例系统：SMS 菜单路径   |
