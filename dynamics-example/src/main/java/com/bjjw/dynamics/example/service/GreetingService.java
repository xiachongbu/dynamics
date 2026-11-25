package com.bjjw.dynamics.example.service;

import org.springframework.stereotype.Service;

/**
 * 类热替换的演示目标。替换用的新字节码见 resources/patch/GreetingService.java。
 */
@Service
public class GreetingService {

    public String greeting() {
        return "v1";
    }
}