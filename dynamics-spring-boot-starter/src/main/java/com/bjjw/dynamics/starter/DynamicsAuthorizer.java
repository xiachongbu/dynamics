package com.bjjw.dynamics.starter;

/**
 * 热更新操作的准入判定。替换为自有实现即可对接任意登录态与权限体系。
 */
public interface DynamicsAuthorizer {

    /**
     * @param operation 操作描述，用于审计日志
     * @return null 表示放行；非 null 为拒绝原因，会直接返回给调用方
     */
    String authorize(String operation);
}