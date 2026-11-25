package com.bjjw.dynamics.example.mapper;

import com.bjjw.dynamics.example.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);
}