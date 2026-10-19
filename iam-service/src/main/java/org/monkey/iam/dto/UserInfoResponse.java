package org.monkey.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.monkey.iam.model.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户信息响应
 *
 * @author monkey
 */
@Data
@Builder
@AllArgsConstructor
public class UserInfoResponse {
    private Long id;
    private String username;
    private String nickname;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private Set<String> roles;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static UserInfoResponse from(User user) {
        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .realName(user.getRealName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .status(user.getStatus() != null ? user.getStatus().getValue() : null)
                .roles(user.getRoleSet().stream().map(Enum::name).collect(Collectors.toSet()))
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }
}
