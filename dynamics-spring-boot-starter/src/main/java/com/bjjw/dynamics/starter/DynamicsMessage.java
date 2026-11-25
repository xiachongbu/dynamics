package com.bjjw.dynamics.starter;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * Redis 频道上传递的热更新指令。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DynamicsMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private MessageType type;

    private String messageId;

    private Long timestamp;

    /**
     * 发布方实例标识，便于排查是哪个实例下发的指令。
     */
    private String sender;

    private SqlUpdateParam sqlUpdateParam;

    private ClassUpdateParam classUpdateParam;

    public enum MessageType {
        SQL_UPDATE,
        CLASS_UPDATE
    }

    @Data
    public static class SqlUpdateParam implements Serializable {
        private static final long serialVersionUID = 1L;

        private String sqlStatementId;

        private String sql;

        /**
         * 多数据源场景下指定 SqlSessionFactory 的 Bean 名称，留空取配置默认值。
         */
        private String sqlSessionFactory;
    }

    @Data
    public static class ClassUpdateParam implements Serializable {
        private static final long serialVersionUID = 1L;

        private String className;

        /**
         * class 文件内容，Base64 编码。
         */
        private String classFileContent;
    }
}