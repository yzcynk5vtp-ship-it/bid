# User 模块 (分配候选人统一查询模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
用户模块负责在 task / tender 两种业务场景下，统一对外提供"可分配候选人"查询能力。模块按 FP-Java 分层组织：纯核心（core）承载过滤排序逻辑、不依赖框架；编排层（service）组装调用链；控制器（controller）仅做 HTTP 入参校验与响应封装。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/AssignmentCandidateController.java` | Controller | 候选人查询 HTTP 入口，暴露 `/api/users/assignable-candidates`；做 context 合法性校验，编排委托 AppService |
| `core/AssignmentCandidatePolicy.java` | Core | 纯核心过滤排序策略：按部门可见性、deptCode、roleCode 过滤，按 departmentCode → roleName → fullName 排序；无 Spring 依赖 |
| `core/AssignmentContext.java` | Core | 纯核心值对象（record），封装 contextType / deptCode / roleCode，提供 `isValidContextType()` 校验 task/tender |
| `service/AssignmentCandidateAppService.java` | Service | 编排层（Imperative Shell）：调用 UserRepository + RoleProfileService + ProjectAccessScopeService 收集输入，再委托 AssignmentCandidatePolicy 执行纯过滤 |
| `dto/AssignmentCandidateDTO.java` | DTO | 候选人视图对象（record），用于跨层返回查询结果 |

## FP-Java 分层说明
- **core/**：纯核心，无 Spring 依赖、无 IO、无状态。`AssignmentCandidatePolicy` 接收调用方注入的全部输入，仅做过滤与排序；`AssignmentContext` 为不可变 record，仅承载上下文与校验。
- **service/**：命令式外壳（Imperative Shell）。`AssignmentCandidateAppService` 负责调用 `UserRepository.findByEnabledTrue()`、`RoleProfileService.hasGlobalAccess()`、`ProjectAccessScopeService.getAllowedDepartmentCodes()` 收集真实数据，再委托纯核心执行业务计算。
- **controller/**：HTTP 入口。`AssignmentCandidateController` 仅做参数校验（context 非空、合法）与统一响应封装，不承载业务逻辑。
- **dto/**：跨层数据载体，纯 record，不依赖框架。

## 调用链
```
HTTP GET /api/users/assignable-candidates?context=task&deptCode=&roleCode=
  └─ AssignmentCandidateController.findCandidates
       ├─ AssignmentContext.of(context, deptCode, roleCode)   // core: 上下文构造
       ├─ ctx.isValidContextType()                              // core: 校验
       └─ AssignmentCandidateAppService.findCandidates(ctx, currentUser)
            ├─ UserRepository.findByEnabledTrue()               // 外部依赖
            ├─ RoleProfileService.hasGlobalAccess(currentUser)  // 外部依赖
            ├─ ProjectAccessScopeService.getAllowedDepartmentCodes(currentUser)
            └─ AssignmentCandidatePolicy.filter(...)           // core: 纯过滤排序
                 └─ List<AssignmentCandidateDTO>
```
