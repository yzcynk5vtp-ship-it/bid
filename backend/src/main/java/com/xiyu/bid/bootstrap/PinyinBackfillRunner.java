// Input: user repository, pinyin utility
// Output: full_name_pinyin backfilled for all existing enabled users
// Pos: Bootstrap - startup backfill for search data completeness
// 维护声明: V1096 迁移只加列不填数，@PrePersist/@PreUpdate 仅对新创建/更新的用户生效。
// 此 runner 在 all profiles 下运行，确保存量用户的拼音可以被按需补齐。
// 它本身是幂等的（WHERE full_name_pinyin IS NULL 过滤），新版部署后自动跳过已填充的用户。
package com.xiyu.bid.bootstrap;

import com.xiyu.bid.common.util.PinyinUtils;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动时回填存量用户 {@code full_name_pinyin} 列。
 * <p>
 * V1096 迁移新增了列但仅依赖 {@code @PrePersist/@PreUpdate} 自动填充，
 * 对于迁移执行前已存在的存量用户，该列为 NULL 导致拼音搜索（LIKE full_name_pinyin）
 * 对存量用户完全无效。此 runner 逐批回填存量用户的拼音，确保搜索功能一致性。
 * <p>
 * 幂等安全：SQL 条件 {@code full_name_pinyin IS NULL} 确保每个用户只处理一次。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PinyinBackfillRunner implements ApplicationRunner {

    /** 单次事务提交的批量大小。 */
    static final int BATCH_SIZE = 500;

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> usersWithoutPinyin = userRepository.findEnabledWithNullPinyin();
        if (usersWithoutPinyin.isEmpty()) {
            log.info("Pinyin backfill: no users with NULL full_name_pinyin found — nothing to do.");
            return;
        }

        int total = usersWithoutPinyin.size();
        log.info("Pinyin backfill: found {} enabled users with NULL full_name_pinyin — starting batch backfill.", total);

        List<User> batch = new ArrayList<>(BATCH_SIZE);
        int processed = 0;

        for (User user : usersWithoutPinyin) {
            if (user.getFullName() != null && !user.getFullName().isBlank()) {
                user.setFullNamePinyin(PinyinUtils.toPinyin(user.getFullName()));
            }
            batch.add(user);

            if (batch.size() >= BATCH_SIZE) {
                userRepository.saveAll(batch);
                processed += batch.size();
                log.info("Pinyin backfill: saved batch {}/{}", processed, total);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            userRepository.saveAll(batch);
            processed += batch.size();
        }

        log.info("Pinyin backfill: COMPLETE — backfilled {} users.", processed);
    }
}