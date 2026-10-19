package org.monkey.common.core.context;

import java.util.Collections;
import java.util.List;

/**
 * 用户上下文 ThreadLocal 持有者
 *
 * <p>Filter 层调用 {@link #set(UserInfo)} 存入，请求结束后调用 {@link #clear()} 清理。
 * Service 层任意位置调用 {@link #get()} 或便捷方法即可获取当前用户信息。
 *
 * <p>使用示例：
 * <pre>{@code
 * Long userId = UserContextHolder.getUserId();
 * UserInfo user = UserContextHolder.get();
 * }</pre>
 *
 * @author monkey
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {}

    /** 存入当前用户 */
    public static void set(UserInfo userInfo) {
        CONTEXT.set(userInfo);
    }

    /** 获取当前用户完整信息 */
    public static UserInfo get() {
        return CONTEXT.get();
    }

    /** 获取当前用户ID */
    public static Long getUserId() {
        UserInfo user = CONTEXT.get();
        return user != null ? user.getUserId() : null;
    }

    /** 获取当前用户角色列表 */
    public static List<String> getRoles() {
        UserInfo user = CONTEXT.get();
        return user != null ? user.getRoles() : Collections.emptyList();
    }

    /** 清理（请求结束后由 Filter 调用） */
    public static void clear() {
        CONTEXT.remove();
    }
}
