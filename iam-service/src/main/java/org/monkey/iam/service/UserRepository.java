package org.monkey.iam.service;

import org.monkey.iam.model.User;

import java.util.Optional;

/**
 * 用户仓储接口
 *
 * @author monkey
 */
public interface UserRepository {

    Optional<User> findById(Long id);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    User save(User user);
}
