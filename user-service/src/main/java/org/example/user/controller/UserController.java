package org.example.user.controller;

import jakarta.annotation.Resource;
import org.example.user.DTO.LoginRequest;
import org.example.user.DTO.LoginResponse;
import org.example.user.DTO.RegisterRequest;
import org.example.user.service.IUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;



    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        try {
            userService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }



    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            String token = userService.login(request);
            return ResponseEntity.ok(new LoginResponse(token, "Login successful."));
        } catch (Exception e) {
            // 捕获 UsernameNotFoundException, BadCredentialsException 等
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new LoginResponse(null, e.getMessage()));
        }
    }
}

