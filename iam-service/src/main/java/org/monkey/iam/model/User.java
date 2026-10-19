package org.monkey.iam.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User 聚合根
 *
 * @author monkey
 */
@Data
public class User {

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private UserStatus status;
    private Set<UserRole> roles = new HashSet<>();
    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static User create(String username, String encodedPassword, String nickname) {
        User user = new User();
        user.username = username;
        user.password = encodedPassword;
        user.nickname = nickname;
        user.status = UserStatus.ACTIVE;
        user.roles.add(new UserRole(Role.BUYER));
        user.createTime = LocalDateTime.now();
        user.updateTime = LocalDateTime.now();
        return user;
    }

    public static User reconstitute(Long id, String username, String password, String nickname,
                                     String realName, String email, String phone, String avatar,
                                     UserStatus status, Long createBy, Long updateBy,
                                     LocalDateTime createTime, LocalDateTime updateTime,
                                     Set<UserRole> roles) {
        User user = new User();
        user.id = id;
        user.username = username;
        user.password = password;
        user.nickname = nickname;
        user.realName = realName;
        user.email = email;
        user.phone = phone;
        user.avatar = avatar;
        user.status = status;
        user.createBy = createBy;
        user.updateBy = updateBy;
        user.createTime = createTime;
        user.updateTime = updateTime;
        if (roles != null) {
            user.roles = new HashSet<>(roles);
        }
        return user;
    }

    public void upgradeToSeller() {
        if (hasRole(Role.SELLER)) {
            throw new BusinessException("用户已是卖家角色");
        }
        this.roles.add(new UserRole(Role.SELLER));
    }

    public boolean hasRole(Role role) {
        return roles.stream().anyMatch(r -> r.getRole() == role);
    }

    public Set<Role> getRoleSet() {
        return roles.stream().map(UserRole::getRole).collect(Collectors.toUnmodifiableSet());
    }

    public enum UserStatus {
        ACTIVE(1),
        DISABLED(0);
        private final int value;
        UserStatus(int value) { this.value = value; }
        public int getValue() { return value; }
        public static UserStatus fromValue(int value) {
            for (UserStatus s : values()) {
                if (s.value == value) return s;
            }
            throw new IllegalArgumentException("未知的用户状态值: " + value);
        }
    }
}
