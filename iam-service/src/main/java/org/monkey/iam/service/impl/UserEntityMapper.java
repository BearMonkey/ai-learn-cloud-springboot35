package org.monkey.iam.service.impl;

import org.monkey.iam.entity.UserEntity;
import org.monkey.iam.entity.UserRoleEntity;
import org.monkey.iam.model.Role;
import org.monkey.iam.model.User;
import org.monkey.iam.model.UserRole;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entity ↔ Model 转换器
 *
 * @author monkey
 */
@Component
public class UserEntityMapper {

    public UserEntity toEntity(User domain) {
        if (domain == null) return null;
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setUsername(domain.getUsername());
        entity.setPassword(domain.getPassword());
        entity.setNickname(domain.getNickname());
        entity.setRealName(domain.getRealName());
        entity.setEmail(domain.getEmail());
        entity.setPhone(domain.getPhone());
        entity.setAvatar(domain.getAvatar());
        entity.setStatus(domain.getStatus() != null ? domain.getStatus().getValue() : null);
        entity.setCreateBy(domain.getCreateBy());
        entity.setUpdateBy(domain.getUpdateBy());
        entity.setCreateTime(domain.getCreateTime());
        entity.setUpdateTime(domain.getUpdateTime());
        return entity;
    }

    public User toDomain(UserEntity entity, Set<UserRoleEntity> roleEntities) {
        if (entity == null) return null;
        Set<UserRole> roles = null;
        if (roleEntities != null) {
            roles = roleEntities.stream()
                    .map(r -> new UserRole(Role.valueOf(r.getRole())))
                    .collect(Collectors.toSet());
        }
        return User.reconstitute(
                entity.getId(), entity.getUsername(), entity.getPassword(), entity.getNickname(),
                entity.getRealName(), entity.getEmail(), entity.getPhone(), entity.getAvatar(),
                entity.getStatus() != null ? User.UserStatus.fromValue(entity.getStatus()) : null,
                entity.getCreateBy(), entity.getUpdateBy(),
                entity.getCreateTime(), entity.getUpdateTime(), roles);
    }
}
