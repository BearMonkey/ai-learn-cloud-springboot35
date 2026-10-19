package org.monkey.iam.service.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.monkey.iam.config.JwtProperties;
import org.monkey.iam.model.Role;
import org.monkey.iam.service.JwtService;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT 服务实现
 *
 * @author monkey
 */
@Component
public class JwtServiceImpl implements JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtServiceImpl(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = jwtProperties.getExpiration();
    }

    @Override
    public String createToken(Long userId, String username, Set<Role> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .issuer("iam-service")
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles.stream().map(Role::name).collect(Collectors.toList()))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    @Override
    public long getExpirationMillis() { return expirationMillis; }
}
