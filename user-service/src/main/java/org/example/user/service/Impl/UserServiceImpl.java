package org.example.user.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.example.user.DTO.LoginRequest;
import org.example.user.DTO.RegisterRequest;
import org.example.user.entity.User;
import org.example.user.mapper.UserMapper;
import org.example.user.service.IUserService;
import org.example.user.util.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    // 注意：JwtUtil 将在下一步骤 2.1.3 中创建


    @Override
    public void register(RegisterRequest request) {
        // 1. 检查用户名是否已存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", request.getUsername());
        if (baseMapper.exists(queryWrapper)) {
            throw new IllegalArgumentException("Username already exists.");
        }

        // 2. 创建新用户实体
        User user = new User();
        user.setUsername(request.getUsername());
        // 3. 使用 BCrypt 加密密码
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhone());
        // 设置默认值
//        user.setStatus(1); // 1-正常

        // 4. 插入数据库
        baseMapper.insert(user);
    }

    @Override
    public String login(LoginRequest request) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", request.getUsername());
        User user = baseMapper.selectOne(queryWrapper);

        if (user == null) {
            throw new UsernameNotFoundException("User not found with username: " + request.getUsername());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid password.");
        }

        // *** 关键改动 ***
        // 使用 JwtUtil 生成真实的 JWT
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }
}
