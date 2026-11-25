package com.bjjw.dynamics.example.service;

/**
 * GreetingService 的替换版本：只改方法体，不动类结构（retransform 不允许增删字段/方法或改签名）。
 * 编译后上传即可把运行中的 greeting() 从 v1 换成 v2。
 */
public class GreetingService {

    public String greeting() {
        return "v2-hot-patched";
    }
}