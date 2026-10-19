package org.monkey.iam.service;

/**
 * Token 黑名单服务接口
 *
 * @author monkey
 */
public interface TokenBlacklistService {
    /**
     * 将指定 token 加入黑名单
     *
     * @param token    JWT 原始字符串
     * @param ttlMillis 过期时间（毫秒），应与 JWT 剩余有效期一致
     */
    void addToBlacklist(String token, long ttlMillis);
}
