package org.monkey.iam.service;

import org.monkey.iam.dto.LoginRequest;
import org.monkey.iam.dto.LoginResponse;
import org.monkey.iam.dto.RegisterRequest;

/**
 * 认证服务接口
 *
 * @author monkey
 */
public interface AuthService {
    LoginResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
}
