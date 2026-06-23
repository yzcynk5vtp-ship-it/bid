# 西域给泊冉权限接口 - 根据工号列表查询用户角色

> 接口编号：26325
> 接口名称：根据工号列表查询用户角色
> 接口路径：`POST /oss/admin-web/v1/output/data/getUserJobListByJobNumberList`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oss/admin-web/v1/output/data/getUserJobListByJobNumberList`
> 备注：根据工号列表批量查询用户角色信息

---

## 请求参数

### Headers

| 参数名称     | 参数值           | 是否必须 | 示例 | 备注 |
| ------------ | ---------------- | -------- | ---- | ---- |
| Content-Type | application/json | 是       |      |      |

### Body

| 名称 | 类型     | 是否必须 | 默认值 | 备注     |
| ---- | -------- | -------- | ------ | -------- |
| data | string[] | 非必须   |        | 工号列表 |

**请求示例**：

```json
{
  "data": ["08402", "08640"]
}
```

---

## 返回数据

### 返回示例

```json
{
  "msg": "操作成功",
  "code": 0,
  "data": {
    "08402": {
      "jobName": "Java开发工程师",
      "sysRoleList": [
        {
          "id": 18,
          "roleName": "员工测试",
          "status": 0,
          "isDefault": 0,
          "createAt": "2024-07-17 16:32:42",
          "createBy": "08384",
          "updateAt": "2024-07-17 16:32:42",
          "updateBy": "08384",
          "del": false
        },
        {
          "id": 2,
          "roleName": "管理员",
          "status": 1,
          "isDefault": 1,
          "createAt": "2024-07-10 10:15:04",
          "createBy": "06669",
          "updateAt": "2024-07-10 10:15:04",
          "updateBy": "06669",
          "del": false
        },
        {
          "id": 3,
          "roleName": "默认角色",
          "status": 1,
          "isDefault": 0,
          "createAt": "2024-07-10 10:15:04",
          "createBy": "",
          "updateAt": "2024-07-10 10:15:04",
          "updateBy": "",
          "del": false
        }
      ],
      "employeeStatus": 3,
      "jobNumber": "08402",
      "username": "张锡臣",
      "status": 1
    },
    "08640": {
      "jobName": "运输管理专员",
      "sysRoleList": [],
      "employeeStatus": 8,
      "jobNumber": "08640",
      "username": "范子文",
      "status": 0
    }
  },
  "timestamp": 1747292370541
}
```

### 响应字段

| 名称              | 类型     | 是否必须 | 默认值 | 备注                                       |
| ----------------- | -------- | -------- | ------ | ------------------------------------------ |
| msg               | string   | 非必须   |        | 返回信息                                   |
| code              | number   | 非必须   |        | 状态码，0 成功，其他失败                   |
| data              | object   | 非必须   |        | 键为工号，值为该工号对应的用户信息         |
| ├ `<jobNumber>`   | object   | 非必须   |        | 单个工号的用户信息                         |
| ├ ├ jobName       | string   | 非必须   |        | 任职角色 / 岗位名称                        |
| ├ ├ jobNumber     | string   | 非必须   |        | 工号                                       |
| ├ ├ username      | string   | 非必须   |        | 姓名                                       |
| ├ ├ employeeStatus| string   | 非必须   |        | 员工状态：1待入职；2试用；3正式；4调出；5待调入；6退休；8离职；12非正式 |
| ├ ├ status        | string   | 非必须   |        | 状态：0 无效；1 有效                       |
| ├ ├ sysRoleList   | object[] | 非必须   |        | 系统角色列表                               |
| ├ ├ ├ id          | string   | 非必须   |        | 角色 id                                    |
| ├ ├ ├ roleName    | string   | 非必须   |        | 角色名称                                   |
| ├ ├ ├ isDefault   | string   | 非必须   |        | 是否默认角色                               |
| ├ ├ ├ status      | string   | 非必须   |        | 状态：0 无效；1 有效                       |
| timestamp         | number   | 非必须   |        | 时间戳                                     |
