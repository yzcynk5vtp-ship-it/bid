# 西域 CRM 客户接口 - 根据公司名称模糊查询

> 接口编号：25338
> 接口名称：根据公司名称模糊查询符合条件的前二十条公司
> 接口路径：`POST /company/getCompanyNameByLikeName`
> Mock 地址：`https://yapi.ehsy.com/mock/509/company/getCompanyNameByLikeName`
> YApi 地址：<https://yapi.ehsy.com/project/509/interface/api/25338>
> BaseUrl：`https://cac-test.ehsy.com`（测试）/ 生产由 `CrmProperties` 配置
> 备注：公司名称长度排序，越小靠前

---

## 业务场景

CO-302 标讯自动分配 — CRM 反查路径的**第一步**：

```
标讯招标主体名称
    │
    ▼
[25338] 模糊查询公司 → 取出公司 id（本接口）
    │
    ▼
[25259] 根据公司 id 查客户负责人（下一步）
    │
    ▼
写入标讯的"项目负责人"字段
```

详见 Linear CO-302 §5.3「CRM 反查的查询路径」。

---

## 请求参数

### Headers

| 参数名称     | 参数值            | 是否必须 | 示例 | 备注 |
| ------------ | ----------------- | -------- | ---- | ---- |
| Content-Type | application/json  | 是       |      |      |
| Authorization| `Bearer <token>`  | 是       |      | 通过 CRM 登录接口 23352 获取 |

### Body

| 名称             | 类型   | 是否必须 | 默认值 | 备注                       |
| ---------------- | ------ | -------- | ------ | -------------------------- |
| pageIndex        | number | 非必须   |        | 页码（从 0 开始）          |
| pageSize         | number | 非必须   |        | 页大小（接口上限 20）      |
| body             | object | 非必须   |        | 查询条件                   |
| ├ name           | string | 非必须   |        | 公司名称关键字（模糊匹配）|
| ├ complianceFlag | number | 非必须   |        | 合规标记                   |
| ├ hasInvalidFlag | number | 非必须   |        | 是否含无效                 |
| └ hasVirtualFlag | number | 非必须   |        | 是否含虚拟公司             |

### 请求示例

```json
{
  "pageIndex": 0,
  "pageSize": 20,
  "body": {
    "name": "上海",
    "complianceFlag": 1,
    "hasInvalidFlag": 0,
    "hasVirtualFlag": 0
  }
}
```

---

## 返回数据

### 返回示例

```json
{
  "pageIndex": 0,
  "code": 0,
  "msg": "success",
  "pageSize": 20,
  "totalCount": 1,
  "dataList": [
    {
      "id": 123456,
      "pid": 100001,
      "name": "上海某某有限公司",
      "groupName": "上海某某集团",
      "isAccurate": "1",
      "virtualFlag": "0"
    }
  ]
}
```

### 响应字段

| 名称                | 类型      | 是否必须 | 默认值 | 备注                          |
| ------------------- | --------- | -------- | ------ | ----------------------------- |
| pageIndex           | integer   | 必须     |        | 页码                          |
| code                | integer   | 必须     |        | 0 成功，非 0 失败             |
| msg                 | string    | 必须     |        | 信息                          |
| pageSize            | integer   | 必须     |        | 页大小                        |
| totalCount          | integer   | 必须     |        | 记录总数                      |
| dataList            | object[]  | 必须     |        | 公司列表                      |
| ├ id                | integer   | 必须     |        | **公司 Id**（CO-302 反查下一步要用） |
| ├ pid               | integer   | 必须     |        | 集团公司 id                   |
| ├ name              | string    | 必须     |        | 公司名称                      |
| ├ groupName         | string    | 必须     |        | 集团                          |
| ├ isAccurate        | string    | 必须     |        | 是否准确查询                  |
| └ virtualFlag       | string    | 必须     |        | 是否虚拟（0 否 / 1 是）       |

### 业务成功条件

`code == 0 && dataList.length > 0`

> 注意：本接口 `code` 是 **integer 类型** `0`，与 25259 的 string 类型 `"0"` 不同，反序列化时需要兼容处理。

---

## CO-302 反查路径中的使用方式

1. **输入**：标讯的 `purchaserName`（招标主体）作为 `body.name` 传入
2. **筛选**：建议 `pageSize=20`，按名称长度升序（接口默认排序）取**第一条**结果
3. **精确匹配优先**：检查返回的 `dataList[0].name` 是否与 `purchaserName` 完全相等，相等则视为精确匹配
4. **降级策略**：模糊匹配命中多条时，按 issue 5.3 规则——视为"未查到"，不阻塞流程
5. **取公司 id**：将 `dataList[0].id` 作为下一步接口 25259 的输入

> ⚠️ CO-302 当前实现（`CrmCustomerLeaderQueryService.findLeaderByGroupName`）走的是商机接口 `/customer-chance/page-list`，**未走本接口**。如需切换到 issue 5.3 描述的"客户名→客户ID→客户负责人"两步路径，需要：
> 1. 新增 `CrmCompanySearchService.searchByName(name)` 调用本接口
> 2. 新增 `CrmCustomerLeaderLookupService.findByCompanyId(companyId)` 调用接口 25259
> 3. 在 `TenderAutoAssignmentService.tryAutoAssignFromCrm` 中替换为两步链路
> 4. 注意类型差异：本接口返回 `code` 为 integer，25259 返回 `code` 为 string

---

## 相关接口

| 顺序 | 接口编号 | 接口名称             | 接口路径                                                       | 状态      |
| ---- | -------- | -------------------- | -------------------------------------------------------------- | --------- |
| 1    | 25338    | 模糊查询公司         | `POST /company/getCompanyNameByLikeName`                       | ✅ 本文档 |
| 2    | 25259    | 按 companyId 查负责人| `POST /customerManager/getCustomerManagerListByCompanyId`      | ✅ 已就绪 |

---

## 关联文档

- [CRM 对接规范（索引页）](../../.wiki/pages/integration-oa-crm.md) §1.3 接口清单 + §1.3.1 CO-302 反查路径
- [接口 25259 - 按 companyId 查客户负责人](xiyu-crm-customer-api-25259-getCustomerManagerListByCompanyId.md) — CO-302 反查路径第二步
- [西域CRM商机对接接口](../integration/西域CRM商机对接接口.md) — 商机接口 `/customer-chance/page-list`（当前 CO-302 实现所走路径）
- Linear CO-302 §5.3 CRM 反查的查询路径
