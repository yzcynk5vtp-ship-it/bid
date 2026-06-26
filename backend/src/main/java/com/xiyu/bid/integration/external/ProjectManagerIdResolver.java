package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CO-333: 按全名解析投标负责人 user_id。
 * 唯一匹配才返回 id，0 个 / 多个（重名）跳过避免误绑，
 * 解析失败不阻断主流程，仅 log warn。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectManagerIdResolver {

    private final UserRepository userRepository;

    /**
     * @param fullName 全名（null/空返回 null）
     * @return 唯一匹配返回 id；否则 null
     */
    public Long resolveByFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        List<User> matches = userRepository.findByFullName(fullName.trim());
        if (matches.size() == 1) {
            return matches.get(0).getId();
        }
        if (matches.isEmpty()) {
            log.warn("CO-333: projectManagerName '{}' 无匹配用户，projectManagerId 保持 null", fullName);
        } else {
            log.warn("CO-333: projectManagerName '{}' 匹配到 {} 个用户（重名），跳过 id 绑定避免误绑",
                    fullName, matches.size());
        }
        return null;
    }
}
