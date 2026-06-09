package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 仓库导入任务的查询服务 — 任务列表/详情读取。
 * 从 WarehouseImportAppService 拆出以降低责任域（避免 Page/Pageable 跨域依赖）。
 */
@Service
@RequiredArgsConstructor
public class WarehouseImportQueryService {

    private final WarehouseImportTaskRepository importTaskRepo;

    public Page<WarehouseImportTaskEntity> listTasks(Long userId, Pageable pageable) {
        return importTaskRepo.findByCreatedByOrderByCreatedAtDesc(userId, pageable);
    }

    public WarehouseImportTaskEntity getTask(Long taskId, Long userId) {
        WarehouseImportTaskEntity task = importTaskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("导入任务不存在: " + taskId));
        if (!task.getCreatedBy().equals(userId)) {
            throw new SecurityException("无权查看该导入任务");
        }
        return task;
    }
}
