package com.bjjw.dynamics.starter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 默认准入实现：用户名必须精确命中 Redis 名单，名单缺失时拒绝所有操作。
 */
@Slf4j
public class RedisWhitelistAuthorizer implements DynamicsAuthorizer {

    private final StringRedisTemplate redisTemplate;
    private final DynamicsUserProvider userProvider;
    private final String authorizedUsersKey;

    public RedisWhitelistAuthorizer(StringRedisTemplate redisTemplate, DynamicsUserProvider userProvider,
                                    String authorizedUsersKey) {
        this.redisTemplate = redisTemplate;
        this.userProvider = userProvider;
        this.authorizedUsersKey = authorizedUsersKey;
    }

    @Override
    public String authorize(String operation) {
        final String username = userProvider.currentUsername();
        if (StringUtils.isBlank(username)) {
            log.warn("[dynamics] 未取到当前用户, 拒绝操作: {}", operation);
            return "未获取到当前用户, 无权执行动态管理操作";
        }
        final String authorizedUsers = redisTemplate.opsForValue().get(authorizedUsersKey);
        if (StringUtils.isBlank(authorizedUsers)) {
            log.error("[dynamics] Redis 未配置授权名单(key: {}), 拒绝操作: {}", authorizedUsersKey, operation);
            return "系统未配置授权用户, 请联系管理员";
        }
        // 逐项精确比对：整串 contains 会让 admin 命中 superadmin
        for (String allowed : authorizedUsers.split(",")) {
            if (allowed.trim().equals(username)) {
                log.info("[dynamics-审计] 用户: {}, 操作: {}", username, operation);
                return null;
            }
        }
        log.warn("[dynamics] 用户 [{}] 不在授权名单内, 拒绝操作: {}", username, operation);
        return "无权限执行此操作, 请联系管理员";
    }
}