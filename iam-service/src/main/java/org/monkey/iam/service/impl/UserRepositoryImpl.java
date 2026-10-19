package org.monkey.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.monkey.iam.entity.UserEntity;
import org.monkey.iam.entity.UserRoleEntity;
import org.monkey.iam.mapper.UserMapper;
import org.monkey.iam.mapper.UserRoleMapper;
import org.monkey.iam.model.User;
import org.monkey.iam.service.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户仓储实现
 *
 * @author monkey
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserEntityMapper mapper;

    public UserRepositoryImpl(UserMapper userMapper, UserRoleMapper userRoleMapper, UserEntityMapper mapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(Long id) {
        UserEntity entity = userMapper.selectById(id);
        if (entity == null) return Optional.empty();
        List<UserRoleEntity> roles = userRoleMapper.selectList(
                new QueryWrapper<UserRoleEntity>().eq("user_id", id));
        return Optional.of(mapper.toDomain(entity, roles.stream().collect(Collectors.toSet())));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        UserEntity entity = userMapper.selectOne(
                new QueryWrapper<UserEntity>().eq("username", username));
        if (entity == null) return Optional.empty();
        List<UserRoleEntity> roles = userRoleMapper.selectList(
                new QueryWrapper<UserRoleEntity>().eq("user_id", entity.getId()));
        return Optional.of(mapper.toDomain(entity, roles.stream().collect(Collectors.toSet())));
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.selectCount(
                new QueryWrapper<UserEntity>().eq("username", username)) > 0;
    }

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        if (entity.getId() == null) {
            userMapper.insert(entity);
        } else {
            userMapper.updateById(entity);
            userRoleMapper.delete(new QueryWrapper<UserRoleEntity>().eq("user_id", entity.getId()));
        }
        if (user.getRoles() != null) {
            for (var domainRole : user.getRoles()) {
                userRoleMapper.insert(new UserRoleEntity(entity.getId(), domainRole.getRole().name()));
            }
        }
        return findById(entity.getId()).orElseThrow();
    }
}
