package org.monkey.common.core.context;

import java.util.Collections;
import java.util.List;

/**
 * 当前请求用户信息 —— 由网关 JWT 解析后通过 Header 注入，Filter 层提取并存入 ThreadLocal
 *
 * <p>后续可扩展字段：用户名、语言、时区、租户ID 等，只改此类和 Filter，不影响 Service 调用方
 *
 * @author monkey
 */
public class UserInfo {

    /** 用户ID（来自 JWT subject） */
    private final Long userId;

    /** 角色列表（来自 JWT roles claim） */
    private final List<String> roles;

    // 后续扩展字段预留：
    // private String username;
    // private String language;
    // private String timezone;
    // private Long tenantId;

    public UserInfo(Long userId, List<String> roles) {
        this.userId = userId;
        this.roles = roles != null ? List.copyOf(roles) : Collections.emptyList();
    }

    public Long getUserId() {
        return userId;
    }

    public List<String> getRoles() {
        return roles;
    }

    /** 是否拥有指定角色 */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /** 是否为卖家 */
    public boolean isSeller() {
        return hasRole("SELLER");
    }

    /** 是否为买家 */
    public boolean isBuyer() {
        return hasRole("BUYER");
    }
}
