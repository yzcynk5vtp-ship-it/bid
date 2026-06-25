# Project 模块 (项目主数据模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
项目模块负责项目主数据的创建、查询与更新，是投标项目生命周期管理的核心入口，承接项目列表、详情与创建流程的后端契约。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ProjectController.java` | Controller | 项目接口 |
| `service/ProjectService.java` | Service | 项目业务编排 |
| `service/ProjectQueryService.java` | Service | 项目列表查询投影，补齐负责人姓名和负责人用户 ID |
| `service/ProjectListEnrichmentSupport.java` | Service helper | 项目列表投影补齐与状态派生辅助逻辑 |
| `service/ProjectExportService.java` | Service | 项目列表 Excel 导出，复用列表负责人 ID 过滤口径 |
| `repository/ProjectLeadAssignmentRepository.java` | Repository | 项目主/副投标负责人分配查询，支持列表批量补齐负责人 ID |
| `dto/ProjectDTO.java` | DTO | 项目视图对象，包含项目/投标负责人姓名与用户 ID |
| `dto/ProjectRequest.java` | DTO | 项目创建/更新请求 |
