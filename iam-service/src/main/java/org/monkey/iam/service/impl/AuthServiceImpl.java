package org.monkey.iam.service.impl;

import org.monkey.iam.dto.LoginRequest;
import org.monkey.iam.dto.LoginResponse;
import org.monkey.iam.dto.RegisterRequest;
import org.monkey.iam.model.BusinessException;
import org.monkey.iam.model.User;
import org.monkey.iam.service.AuthService;
import org.monkey.iam.service.JwtService;
import org.monkey.iam.service.PasswordEncoder;
import org.monkey.iam.service.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现
 *
 * @author monkey
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = User.create(request.getUsername(), encodedPassword, request.getNickname());
        user = userRepository.save(user);
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRoleSet());
        return new LoginResponse(token, user.getId(), user.getNickname());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException(403, "用户已被禁用");
        }
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRoleSet());
        return new LoginResponse(token, user.getId(), user.getNickname());
    }
}
