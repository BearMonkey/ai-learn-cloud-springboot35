package org.monkey.iam.service;

import org.monkey.iam.dto.UserInfoResponse;

/**
 * 用户服务接口
 *
 * @author monkey
 */
public interface UserService {
    UserInfoResponse getCurrentUser(Long userId);
    UserInfoResponse getUserById(Long id);
    /**
     * 升级为卖家角色，并废弃当前 token
     *
     * @param userId 用户ID
     * @param token  当前 JWT（不含 "Bearer " 前缀），升级后加入黑名单
     */
    void upgradeToSeller(Long userId, String token);
}
