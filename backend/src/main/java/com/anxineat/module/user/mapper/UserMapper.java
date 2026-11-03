package com.anxineat.module.user.mapper;

import com.anxineat.module.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM user WHERE open_id = #{openId}")
    User selectByOpenId(String openId);

    @Select("SELECT * FROM user WHERE phone = #{phone}")
    User selectByPhone(String phone);

    /**
     * 积分变更（条件更新保证余额不为负）。
     *
     * <p>注意：退款扣回积分时如果用户已把积分花掉，余额可能不足。
     * 这里允许扣到 0（不出现负余额），差额不再追回 —— 见需求文档
     * 「退款处理」的修订说明。
     */
    @Update("""
            UPDATE user
               SET point_balance = GREATEST(0, point_balance + #{delta}),
                   point_total   = point_total + CASE WHEN #{delta} > 0 THEN #{delta} ELSE 0 END,
                   updated_at    = NOW()
             WHERE id = #{userId}
            """)
    int changePoint(@Param("userId") Long userId, @Param("delta") long delta);

    @Update("UPDATE user SET total_amount = total_amount + #{delta}, updated_at = NOW() WHERE id = #{userId}")
    int addTotalAmount(@Param("userId") Long userId, @Param("delta") long delta);

    /**
     * 标记签到，返回受影响行数。
     *
     * <p>条件 {@code date > COALESCE(last_signin_date, '1970-01-01')} 保证同一自然日
     * 只能成功更新一次：重复签到时 rows = 0，由 Service 转为「今日已签到」。
     * 相比「先查后改」，条件更新在并发点击下不会产生重复发分。
     * （这里刻意避免 SQL 中出现小于号，以防 MyBatis 注解 SQL 的 XML 解析歧义。）
     */
    @Update("UPDATE user SET last_signin_date = #{date}, updated_at = NOW() "
            + "WHERE id = #{userId} AND #{date} > COALESCE(last_signin_date, '1970-01-01')")
    int markSignin(@Param("userId") Long userId, @Param("date") java.time.LocalDate date);
}
