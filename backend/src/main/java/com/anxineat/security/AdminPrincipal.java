package com.anxineat.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 后台登录主体。
 */
@Data
@AllArgsConstructor
public class AdminPrincipal {

    private Long adminId;
    private String role;
    /** 店员绑定的门店；超管为 null 表示可跨门店 */
    private Long storeId;

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }

    /**
     * 校验是否有权操作指定门店的数据。
     * 需求文档验收标准：店员只能看到并操作本门店数据；超管可切换门店。
     */
    public void assertStoreAccess(Long targetStoreId) {
        if (isSuperAdmin()) {
            return;
        }
        if (targetStoreId == null || !targetStoreId.equals(storeId)) {
            throw new com.anxineat.common.BizException(com.anxineat.common.ResultCode.ADMIN_FORBIDDEN);
        }
    }

    /**
     * 返回实际作用于查询的门店 ID：店员强制为本门店，超管可用传入值。
     */
    public Long effectiveStoreId(Long requestedStoreId) {
        if (isSuperAdmin()) {
            return requestedStoreId;
        }
        return storeId;
    }
}
