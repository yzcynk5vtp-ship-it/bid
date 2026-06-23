# 西域给泊冉权限接口 - 根据 token 获取员工信息接口

> 接口编号：23358
> 接口名称：根据 token 获取员工信息接口
> 接口路径：`GET /oauth/getUserInfo`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/getUserInfo`
> 备注：根据 token 获取员工的用户信息

---

## 请求参数

### Headers

| 参数名称      | 参数值                                      | 是否必须 | 示例 | 备注 |
| ------------- | ------------------------------------------- | -------- | ---- | ---- |
| Content-Type  | application/x-www-form-urlencoded           | 是       |      |      |
| Authorization | Bearer `<access_token>`                     | 是       |      | 登录接口返回的 access_token |

### Body

无。token 通过 `Authorization` Header 传递。

---

## 返回数据

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": {
    "id": 5549,
    "userId": 185793021,
    "username": "09118",
    "nickName": "覃超颖",
    "phone": "15000022861",
    "email": "eva_qin@ehsy.com",
    "deptId": 2279832,
    "deptName": "项目一组",
    "deptPath": "900109664/997091/997093/997267/3264067/2279832",
    "jobId": 4948,
    "jobName": "高级产品经理",
    "isManager": true,
    "createAt": "2024-07-31 13:43:36",
    "updateAt": "2024-08-01 14:27:17",
    "status": 1,
    "deptTree": null,
    "roleList": null
  }
}
```

### 响应字段

| 名称     | 类型    | 是否必须 | 默认值 | 备注         |
| -------- | ------- | -------- | ------ | ------------ |
| code     | number  | 非必须   |        |              |
| message  | string  | 非必须   |        |              |
| trace    | null    | 非必须   |        |              |
| data     | object  | 非必须   |        |              |
| ├ id        | number  | 非必须   |        |              |
| ├ userId    | number  | 非必须   |        | 用户 id      |
| ├ username  | string  | 非必须   |        | 用户登录账号 |
| ├ nickName  | string  | 非必须   |        | 用户名       |
| ├ phone     | string  | 非必须   |        |              |
| ├ email     | string  | 非必须   |        |              |
| ├ deptId    | number  | 非必须   |        | 部门 id      |
| ├ deptName  | string  | 非必须   |        | 部门名称     |
| ├ deptPath  | string  | 非必须   |        | 部门路径     |
| ├ jobId     | number  | 非必须   |        | 岗位 id      |
| ├ jobName   | string  | 非必须   |        | 岗位名       |
| ├ isManager | boolean | 非必须   |        | 是否管理员   |
| ├ createAt  | string  | 非必须   |        |              |
| ├ updateAt  | string  | 非必须   |        |              |
| ├ status    | number  | 非必须   |        | 账号状态     |
| ├ deptTree  | null    | 非必须   |        |              |
| ├ roleList  | null    | 非必须   |        |              |
