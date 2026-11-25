package com.bjjw.dynamics.starter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 热更新入口：只负责准入判定与下发指令，实际替换由各实例的监听器执行。
 */
@RestController
@RequestMapping("/api/dynamics")
@Slf4j
public class DynamicsController {

    private final StringRedisTemplate redisTemplate;
    private final DynamicsAuthorizer authorizer;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String instanceId;

    public DynamicsController(StringRedisTemplate redisTemplate, DynamicsAuthorizer authorizer,
                              ObjectMapper objectMapper, String topic) {
        this.redisTemplate = redisTemplate;
        this.authorizer = authorizer;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.instanceId = resolveInstanceId();
    }

    @PostMapping("/mybatis/replace-sql")
    public R<Map<String, Object>> replaceSql(
            @RequestParam(required = false) String sqlSessionFactory,
            @RequestParam String sqlStatementId,
            @RequestParam String sql) {
        final String denied = authorizer.authorize("SQL替换: " + sqlStatementId);
        if (denied != null) {
            return R.fail(denied);
        }

        final DynamicsMessage.SqlUpdateParam param = new DynamicsMessage.SqlUpdateParam();
        param.setSqlStatementId(sqlStatementId);
        param.setSql(sql);
        param.setSqlSessionFactory(sqlSessionFactory);

        final DynamicsMessage message = new DynamicsMessage();
        message.setType(DynamicsMessage.MessageType.SQL_UPDATE);
        message.setSqlUpdateParam(param);
        final long receivers = publish(message);
        if (receivers == 0) {
            return R.fail("当前没有实例订阅频道 " + topic + ", 指令未送达");
        }

        final Map<String, Object> data = new HashMap<>();
        data.put("message", "SQL 替换指令已发布, 集群所有实例将异步执行");
        data.put("sqlStatementId", sqlStatementId);
        data.put("messageId", message.getMessageId());
        data.put("receivers", receivers);
        data.put("instanceId", instanceId);
        return R.ok(data);
    }

    @PostMapping("/class/update-by-upload")
    public R<Map<String, Object>> updateClassByUpload(
            @RequestParam String className,
            @RequestParam("file") MultipartFile file) throws IOException {
        final String denied = authorizer.authorize("类替换: " + className);
        if (denied != null) {
            return R.fail(denied);
        }

        final byte[] bytecode = file.getBytes();
        if (bytecode.length < 4 || bytecode[0] != (byte) 0xCA || bytecode[1] != (byte) 0xFE
                || bytecode[2] != (byte) 0xBA || bytecode[3] != (byte) 0xBE) {
            return R.fail("上传的文件不是有效的 class 文件");
        }

        final DynamicsMessage.ClassUpdateParam param = new DynamicsMessage.ClassUpdateParam();
        param.setClassName(className);
        param.setClassFileContent(Base64.getEncoder().encodeToString(bytecode));

        final DynamicsMessage message = new DynamicsMessage();
        message.setType(DynamicsMessage.MessageType.CLASS_UPDATE);
        message.setClassUpdateParam(param);
        final long receivers = publish(message);
        if (receivers == 0) {
            return R.fail("当前没有实例订阅频道 " + topic + ", 指令未送达");
        }

        final Map<String, Object> data = new HashMap<>();
        data.put("message", "类替换指令已发布, 集群所有实例将异步执行");
        data.put("className", className);
        data.put("messageId", message.getMessageId());
        data.put("receivers", receivers);
        data.put("instanceId", instanceId);
        return R.ok(data);
    }

    /**
     * @return Redis PUBLISH 回报的订阅者数量；0 表示指令没有送达任何实例
     */
    private long publish(DynamicsMessage message) {
        message.setMessageId(UUID.randomUUID().toString());
        message.setTimestamp(System.currentTimeMillis());
        message.setSender(instanceId);
        final byte[] rawChannel = topic.getBytes(StandardCharsets.UTF_8);
        final byte[] rawMessage;
        try {
            rawMessage = objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("热更新指令序列化失败", e);
        }
        // convertAndSend 在 spring-data-redis 2.x 返回 void，拿不到订阅者数，故直接走 connection.publish
        final Long receivers = redisTemplate.execute(
                (RedisCallback<Long>) connection -> connection.publish(rawChannel, rawMessage));
        log.info("[dynamics] 指令已发布, topic: {}, messageId: {}, type: {}, 订阅者: {}",
                topic, message.getMessageId(), message.getType(), receivers);
        return receivers == null ? 0L : receivers;
    }

    private static String resolveInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}