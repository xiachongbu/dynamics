package com.bjjw.dynamics.starter;

import com.bjjw.dynamics.MybatisSqlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@AutoConfiguration
@ConditionalOnClass({RedisConnectionFactory.class, SqlSessionFactory.class})
@ConditionalOnProperty(prefix = "dynamics", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DynamicsProperties.class)
@Slf4j
public class DynamicsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisSqlUtil mybatisSqlUtil() {
        return new MybatisSqlUtil();
    }

    /**
     * 未接入登录态时取不到用户名，配合默认准入实现即拒绝所有操作。
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamicsUserProvider dynamicsUserProvider() {
        return () -> null;
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicsAuthorizer dynamicsAuthorizer(StringRedisTemplate redisTemplate, DynamicsUserProvider userProvider,
                                                 DynamicsProperties properties) {
        return new RedisWhitelistAuthorizer(redisTemplate, userProvider, properties.getAuthorizedUsersKey());
    }

    @Bean
    public DynamicsMessageListener dynamicsMessageListener(MybatisSqlUtil mybatisSqlUtil, ApplicationContext applicationContext,
                                                           DynamicsProperties properties, ObjectMapper objectMapper) {
        return new DynamicsMessageListener(mybatisSqlUtil, applicationContext, properties, objectMapper);
    }

    @Bean
    public RedisMessageListenerContainer dynamicsRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, DynamicsMessageListener listener,
            DynamicsProperties properties, Environment environment) {
        final String topic = resolveTopic(properties, environment);
        final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(new MessageListenerAdapter(listener, "onMessage"), new PatternTopic(topic));
        log.info("[dynamics] 热更新监听已启动, topic: {}", topic);
        return container;
    }

    @Bean
    public DynamicsController dynamicsController(StringRedisTemplate redisTemplate, DynamicsAuthorizer authorizer,
                                                 ObjectMapper objectMapper, DynamicsProperties properties,
                                                 Environment environment) {
        return new DynamicsController(redisTemplate, authorizer, objectMapper, resolveTopic(properties, environment));
    }

    static String resolveTopic(DynamicsProperties properties, Environment environment) {
        if (StringUtils.isNotBlank(properties.getTopic())) {
            return properties.getTopic();
        }
        final String applicationName = environment.getProperty("spring.application.name", "default");
        return StringUtils.defaultIfBlank(StringUtils.substringBefore(applicationName, "-"), applicationName)
                + ":dynamics:update:topic";
    }
}