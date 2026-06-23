# 西域给泊冉权限接口 - 登出接口

> 接口编号：23370
> 接口名称：登出接口
> 接口路径：`POST /oauth/logout`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/logout`
> 备注：登出接口，在操作日志表中记录登出信息（`sys_login_info`）

---

## 请求参数

### Headers

| 参数名称      | 参数值                            | 是否必须 | 示例 | 备注 |
| ------------- | --------------------------------- | -------- | ---- | ---- |
| Content-Type  | application/x-www-form-urlencoded | 是       |      |      |
| Authorization | Bearer `<access_token>`           | 是       |      | 登录接口返回的 access_token |

### Body

无。

---

## 返回数据

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": "退出成功!"
}
```

### 响应字段

| 名称    | 类型   | 是否必须 | 默认值 | 备注 |
| ------- | ------ | -------- | ------ | ---- |
| code    | number | 非必须   |        |      |
| message | string | 非必须   |        |      |
| trace   | null   | 非必须   |        |      |
| data    | string | 非必须   |        |      |
