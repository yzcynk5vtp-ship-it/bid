package com.xiyu.bid.webhook.infrastructure;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WebhookDeliveryTaskRepository extends JpaRepository<WebhookDeliveryTask, Long> {
    /**
     * 取可投递任务：新入队的 PENDING（nextRetryAt 可为 NULL）或到期的 PENDING_RETRY。
     * <p>JPQL 显式处理 NULL nextRetryAt —— 派生查询 {@code NextRetryAtLessThanEqual} 会把 NULL 行过滤掉，
     * 导致入队后从未被扫到。与 {@code NotificationDeliveryTaskRepository} 保持一致。
     */
    @Query("""
            select t from WebhookDeliveryTask t
            where (t.status = com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus.PENDING
                   or (t.status = com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus.PENDING_RETRY
                       and (t.nextRetryAt is null or t.nextRetryAt <= :now)))
            order by t.createdAt asc
            """)
    List<WebhookDeliveryTask> findRunnableTasksPaged(@Param("now") LocalDateTime now, Pageable pageable);

    default List<WebhookDeliveryTask> findRunnableTasks(LocalDateTime now, int batchSize) {
        return findRunnableTasksPaged(now, PageRequest.of(0, batchSize));
    }
}
