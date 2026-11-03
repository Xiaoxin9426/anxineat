package com.anxineat.security;

/**
 * 当前请求上下文。使用 ThreadLocal 保存，请求结束后必须清理。
 */
public final class AuthContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<AdminPrincipal> ADMIN = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 获取当前登录用户 ID，未登录时抛业务异常。
     */
    public static long requireUserId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new com.anxineat.common.BizException(com.anxineat.common.ResultCode.UNAUTHORIZED);
        }
        return id;
    }

    public static void setAdmin(AdminPrincipal admin) {
        ADMIN.set(admin);
    }

    public static AdminPrincipal getAdmin() {
        return ADMIN.get();
    }

    public static AdminPrincipal requireAdmin() {
        AdminPrincipal a = ADMIN.get();
        if (a == null) {
            throw new com.anxineat.common.BizException(com.anxineat.common.ResultCode.UNAUTHORIZED);
        }
        return a;
    }

    public static void clear() {
        USER_ID.remove();
        ADMIN.remove();
    }
}
