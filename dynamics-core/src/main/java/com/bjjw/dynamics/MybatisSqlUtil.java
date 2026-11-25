package com.bjjw.dynamics;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLIncludeTransformer;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Field;

/**
 * 运行期替换 MyBatis MappedStatement 的 SQL，无需重启应用。
 */
@Slf4j
public class MybatisSqlUtil {

    public void dynamicReplaceSqlStatement(Configuration configuration, String sql, String sqlStatementId) {
        if (StringUtils.isBlank(sql)) {
            return;
        }
        final String script = "<script>" + sql + "</script>";
        final MappedStatement mappedStatement = configuration.getMappedStatement(sqlStatementId);
        final SqlSource sqlSource = mappedStatement.getSqlSource();
        if (!(sqlSource instanceof DynamicSqlSource) && !(sqlSource instanceof RawSqlSource)) {
            throw new IllegalStateException("该 statement 的 SqlSource 不支持替换: "
                    + sqlSource.getClass().getName() + ", statementId=" + sqlStatementId);
        }
        log.info("replace sqlStatementId:{}, replace sql:{}", sqlStatementId, script);

        final String fullStatementId = mappedStatement.getId();
        final XPathParser parser = new XPathParser(script, false, configuration.getVariables(), new XMLMapperEntityResolver());
        final XNode scriptNode = parser.evalNode("/script");
        final MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, fullStatementId);
        assistant.setCurrentNamespace(fullStatementId.substring(0, fullStatementId.lastIndexOf('.')));
        // <include> 由 XMLStatementBuilder 在 mapper 解析期展开，运行期拼 <script> 不经过该步骤，需手工补一次
        new XMLIncludeTransformer(configuration, assistant).applyIncludes(scriptNode.getNode());

        final SqlSource newSqlSource = configuration.getDefaultScriptingLanguageInstance().createSqlSource(
                configuration, scriptNode, sqlSource instanceof DynamicSqlSource ? null : Object.class);
        try {
            final Field field = mappedStatement.getClass().getDeclaredField("sqlSource");
            field.setAccessible(true);
            field.set(mappedStatement, newSqlSource);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("替换 sqlSource 失败: " + sqlStatementId, e);
        }
    }
}