package org.monkey.iam.service.impl;

import org.monkey.iam.dto.UserInfoResponse;
import org.monkey.iam.model.BusinessException;
import org.monkey.iam.model.User;
import org.monkey.iam.service.TokenBlacklistService;
import org.monkey.iam.service.UserRepository;
import org.monkey.iam.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务实现
 *
 * @author monkey
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;

    public UserServiceImpl(UserRepository userRepository, TokenBlacklistService tokenBlacklistService) {
        this.userRepository = userRepository;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public UserInfoResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        return UserInfoResponse.from(user);
    }

    @Override
    public UserInfoResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        return UserInfoResponse.from(user);
    }

    @Override
    @Transactional
    public void upgradeToSeller(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        user.upgradeToSeller();
        userRepository.save(user);
        // 废弃旧 token（只黑名单当前 token，新 token 不受影响）
        tokenBlacklistService.addToBlacklist(token, 86400000L);
    }
}
