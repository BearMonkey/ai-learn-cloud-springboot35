package org.monkey.iam.service;

import org.monkey.iam.model.Role;

import java.util.Set;

/**
 * JWT 服务接口
 *
 * @author monkey
 */
public interface JwtService {
    String createToken(Long userId, String username, Set<Role> roles);
    long getExpirationMillis();
}
