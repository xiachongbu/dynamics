package com.bjjw.dynamics.starter;

/**
 * 提供当前操作者用户名，默认实现返回 null（即拒绝所有操作），需接入方按自己的登录态实现。
 */
@FunctionalInterface
public interface DynamicsUserProvider {

    String currentUsername();
}