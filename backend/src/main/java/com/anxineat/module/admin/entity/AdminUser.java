package com.anxineat.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理账号。
 *
 * <p>role：SUPER_ADMIN 超管（可跨门店）/ CLERK 店员（仅 store_id 对应门店）。
 * 需求文档验收标准：店员只能看到并操作本门店数据；超管可切换门店。
 */
@Data
@TableName("admin_user")
public class AdminUser {

    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_CLERK = "CLERK";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** BCrypt 哈希 */
    private String password;
    private String realName;
    private String role;
    /** 店员绑定门店；超管为 null */
    private Long storeId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
