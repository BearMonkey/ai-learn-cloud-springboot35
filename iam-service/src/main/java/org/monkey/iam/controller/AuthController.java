package org.monkey.iam.controller;

import jakarta.validation.Valid;
import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.dto.Result;
import org.monkey.iam.dto.LoginRequest;
import org.monkey.iam.dto.LoginResponse;
import org.monkey.iam.dto.RegisterRequest;
import org.monkey.iam.service.AuthService;
import org.monkey.iam.service.UserService;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 *
 * @author monkey
 */
@RestController
@RequestMapping("/iam/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PutMapping("/upgrade-to-seller")
    public Result<Void> upgradeToSeller(@RequestHeader("Authorization") String authHeader) {
        Long userId = UserContextHolder.getUserId();
        // 提取 Bearer token，传给 service 以废弃旧 token
        String token = authHeader.substring(7);
        userService.upgradeToSeller(userId, token);
        return Result.success();
    }
}
