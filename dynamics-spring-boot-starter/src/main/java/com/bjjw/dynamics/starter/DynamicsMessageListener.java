package com.bjjw.dynamics.starter;

import com.bjjw.dynamics.ClassService;
import com.bjjw.dynamics.MybatisSqlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 消费 Redis 上的热更新指令，使集群内每个实例都在本地执行替换。
 */
@Slf4j
public class DynamicsMessageListener implements MessageListener {

    private static final int MAX_PROCESSED_IDS = 1000;

    private final MybatisSqlUtil mybatisSqlUtil;
    private final ApplicationContext applicationContext;
    private final DynamicsProperties properties;
    private final ObjectMapper objectMapper;
    private final ClassService classService = ClassService.getInstance();

    /** 监听容器是多线程的，用同步 LRU 保证并发安全并淘汰最旧的消息 ID */
    private final Set<String> processedMessageIds = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<String, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_PROCESSED_IDS;
                }
            }));

    public DynamicsMessageListener(MybatisSqlUtil mybatisSqlUtil, ApplicationContext applicationContext,
                                   DynamicsProperties properties, ObjectMapper objectMapper) {
        this.mybatisSqlUtil = mybatisSqlUtil;
        this.applicationContext = applicationContext;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        final String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("[dynamics] 收到热更新指令: {}", body);
        try {
            final DynamicsMessage dynamicsMessage = objectMapper.readValue(body, DynamicsMessage.class);
            if (!processedMessageIds.add(dynamicsMessage.getMessageId())) {
                log.info("[dynamics] 指令已处理, 跳过: {}", dynamicsMessage.getMessageId());
                return;
            }
            switch (dynamicsMessage.getType()) {
                case SQL_UPDATE:
                    handleSqlUpdate(dynamicsMessage.getSqlUpdateParam());
                    break;
                case CLASS_UPDATE:
                    handleClassUpdate(dynamicsMessage.getClassUpdateParam());
                    break;
                default:
                    log.warn("[dynamics] 未知的指令类型: {}", dynamicsMessage.getType());
            }
        } catch (Exception e) {
            log.error("[dynamics] 执行热更新指令失败: {}", body, e);
        }
    }

    private void handleSqlUpdate(DynamicsMessage.SqlUpdateParam param) {
        final String beanName = StringUtils.defaultIfBlank(param.getSqlSessionFactory(), properties.getSqlSessionFactory());
        final SqlSessionFactory sqlSessionFactory = applicationContext.getBean(beanName, SqlSessionFactory.class);
        mybatisSqlUtil.dynamicReplaceSqlStatement(sqlSessionFactory.getConfiguration(), param.getSql(), param.getSqlStatementId());
        log.info("[dynamics] SQL 替换完成: {}", param.getSqlStatementId());
    }

    private void handleClassUpdate(DynamicsMessage.ClassUpdateParam param) throws IOException {
        final byte[] bytecode = Base64.getDecoder().decode(param.getClassFileContent());
        final String className = param.getClassName();
        final File classFile = new File(System.getProperty("java.io.tmpdir"),
                "dynamics_" + className.substring(className.lastIndexOf('.') + 1) + ".class");
        Files.write(classFile.toPath(), bytecode);
        classFile.deleteOnExit();
        log.info("[dynamics] 待替换字节码已落盘: {} ({} bytes)", classFile.getAbsolutePath(), bytecode.length);

        classService.updateClass(className, classFile.getAbsolutePath());
        log.info("[dynamics] 类替换完成: {}", className);
    }
}