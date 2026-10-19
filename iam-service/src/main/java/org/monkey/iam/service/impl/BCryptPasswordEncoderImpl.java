package org.monkey.iam.service.impl;

import org.monkey.iam.service.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 密码加密器
 *
 * @author monkey
 */
@Component
public class BCryptPasswordEncoderImpl implements PasswordEncoder {

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder delegate;

    public BCryptPasswordEncoderImpl() {
        this.delegate = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Override
    public String encode(String rawPassword) { return delegate.encode(rawPassword); }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) { return delegate.matches(rawPassword, encodedPassword); }
}
