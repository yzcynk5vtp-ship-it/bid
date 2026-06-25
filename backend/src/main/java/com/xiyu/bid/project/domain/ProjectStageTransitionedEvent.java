package com.xiyu.bid.project.domain;

import com.xiyu.bid.project.core.ProjectStage;

/**
 * 项目阶段推进领域事件（纯 POJO，不依赖 Spring —— domain purity）。
 * <p>由 {@code ProjectStageService.requestTransition} 发布，audit 模块监听并记录
 * 「从 XX 推进至 YY」的操作日志（含源 + 目标阶段中文名）。
 *
 * <p>CO-324：service 不直接注入 {@code IAuditLogService}（ArchitectureTest RULE 12），
 * 改用事件解耦。Spring {@code ApplicationEventPublisher.publishEvent(Object)} 自 4.2 起
 * 接受任意对象，事件无需继承 {@code ApplicationEvent}，故用纯 record 保持 domain 纯净。
 */
public record ProjectStageTransitionedEvent(
        Long projectId,
        ProjectStage fromStage,
        ProjectStage toStage
) {}
