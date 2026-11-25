package com.bjjw.dynamics.starter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "dynamics")
public class DynamicsProperties {

    private boolean enabled = true;

    /**
     * Redis 频道；留空则按 {spring.application.name 首段}:dynamics:update:topic 推导。
     * 同一应用的多个实例必须落在同一频道才能同步生效。
     */
    private String topic;

    /**
     * 存放授权用户名单的 Redis key，值为逗号分隔的用户名。未配置该 key 时拒绝所有操作。
     */
    private String authorizedUsersKey = "dynamics:authorized:users";

    /**
     * 请求未指定 sqlSessionFactory 时使用的 Bean 名称。
     */
    private String sqlSessionFactory = "sqlSessionFactory";
}