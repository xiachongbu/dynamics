package com.bjjw.dynamics.example.config;

import com.bjjw.dynamics.starter.DynamicsUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 示例用请求头 X-Dynamics-User 充当登录态；真实项目请改为从自己的登录上下文取用户名。
 */
@Configuration
public class DynamicsUserConfig {

    @Bean
    public DynamicsUserProvider dynamicsUserProvider(ObjectFactory<HttpServletRequest> requestFactory) {
        return () -> requestFactory.getObject().getHeader("X-Dynamics-User");
    }
}