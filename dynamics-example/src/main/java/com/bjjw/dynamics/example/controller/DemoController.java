package com.bjjw.dynamics.example.controller;

import com.bjjw.dynamics.example.entity.User;
import com.bjjw.dynamics.example.mapper.UserMapper;
import com.bjjw.dynamics.example.service.GreetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 观察热替换效果的只读接口。
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    private final UserMapper userMapper;
    private final GreetingService greetingService;

    public DemoController(UserMapper userMapper, GreetingService greetingService) {
        this.userMapper = userMapper;
        this.greetingService = greetingService;
    }

    @GetMapping("/user/{id}")
    public User user(@PathVariable Long id) {
        return userMapper.selectById(id);
    }

    @GetMapping("/greeting")
    public String greeting() {
        return greetingService.greeting();
    }
}