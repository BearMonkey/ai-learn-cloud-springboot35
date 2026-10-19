package org.monkey.iam.service;

/**
 * 密码加密器接口
 *
 * @author monkey
 */
public interface PasswordEncoder {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
