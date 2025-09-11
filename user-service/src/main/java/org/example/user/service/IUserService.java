package org.example.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.user.DTO.LoginRequest;
import org.example.user.DTO.RegisterRequest;
import org.example.user.entity.User;

public interface IUserService extends IService<User> {
    /**
     * 用户注册
     * @param request 注册请求体
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     * @param request 登录请求体
     * @return 包含 JWT 的响应
     */
    String login(LoginRequest request);
}
