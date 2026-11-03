package com.anxineat.module.point.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.module.point.entity.PointRecord;
import com.anxineat.module.point.mapper.PointRecordMapper;
import com.anxineat.module.system.service.SysConfigService;
import com.anxineat.module.user.entity.User;
import com.anxineat.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 积分服务。
 *
 * <p>积分口径相对原需求文档做了关键修正：原文档验收标准写
 * 「积分余额 = 累计获得 − 累计使用」，却又要求「积分默认年度滚动过期」，
 * 两者矛盾。本项目采用「积分余额 = 累计获得 − 累计使用 − 累计过期」，
 * 并新增 EXPIRE 流水，使余额与流水始终可对账。
 *
 * <p>每条积分入账/扣减都写一条 {@link PointRecord}，且带唯一 biz_no，
 * 保证「消费积分入账（幂等）」「重复签到防重」「兑换发券同事务」等验收项成立。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointService {

    private final PointRecordMapper pointRecordMapper;
    private final UserMapper userMapper;
    private final SysConfigService config;

    public Long balance(Long userId) {
        User u = userMapper.selectById(userId);
        return u == null ? 0L : u.getPointBalance();
    }

    /**
     * 消费积分入账（支付成功后调用，需幂等）。
     *
     * @param levelPointRate 会员等级积分倍率百分比（100 = 1 倍）
     * @return 实际入账积分（已入账则返回 0）
     */
    @Transactional
    public long awardConsume(Long userId, Long orderId, long payAmountCents, int levelPointRate) {
        String bizNo = "ORDER-POINT-" + orderId;
        if (pointRecordMapper.countByBizNo(bizNo) > 0) {
            return 0; // 已入账，幂等返回
        }
        long yuan = payAmountCents / 100L;
        long base = config.pointPerYuan() * yuan;
        long points = base * Math.max(1, levelPointRate) / 100L;
        if (points <= 0) {
            return 0;
        }
        append(userId, PointRecord.EARN_CONSUME, points, bizNo,
                "订单 " + orderId + " 消费赠送");
        return points;
    }

    /**
     * 支付成功后，会员等级可能随累计实付金额升级，积分倍率随之变化；
     * 这里按升级后的倍率重算（若已入账则幂等跳过）。
     */
    @Transactional
    public long awardConsumeWithLevel(Long userId, Long orderId, long payAmountCents, Long newLevelRate) {
        return awardConsume(userId, orderId, payAmountCents,
                newLevelRate == null ? 100 : newLevelRate.intValue());
    }

    /**
     * 活动奖励发放积分（幂等）。已发放（biz_no 命中）则返回 0，不重复入账。
     */
    @Transactional
    public long earnActivity(Long userId, long amount, String bizNo, String remark) {
        if (amount <= 0) {
            return 0;
        }
        if (pointRecordMapper.countByBizNo(bizNo) > 0) {
            return 0;
        }
        append(userId, PointRecord.EARN_ACTIVITY, amount, bizNo, remark);
        return amount;
    }

    /**
     * 活动消耗积分（如 0元兑换的积分门槛），幂等（按 biz_no）。
     * 余额不足抛 POINT_NOT_ENOUGH，由调用方事务整体回滚。
     */
    @Transactional
    public void consumeForActivity(Long userId, long cost, String bizNo, String remark) {
        if (cost <= 0) {
            return;
        }
        if (pointRecordMapper.countByBizNo(bizNo) > 0) {
            return;
        }
        long balance = balance(userId);
        if (balance < cost) {
            throw new BizException(ResultCode.POINT_NOT_ENOUGH,
                    "积分不足，还差 " + (cost - balance) + " 分");
        }
        append(userId, PointRecord.USE_EXCHANGE, -cost, bizNo, remark);
    }

    /**
     * 每日签到。返回本次获得积分；已签到则返回 0 并抛业务异常。
     */
    @Transactional
    public long signin(Long userId) {
        LocalDate today = LocalDate.now();
        int rows = userMapper.markSignin(userId, today);
        if (rows == 0) {
            throw new BizException(ResultCode.SIGNIN_ALREADY, "今日已签到");
        }
        long points = config.signinPoint();
        append(userId, PointRecord.EARN_SIGNIN, points, "SIGNIN-" + userId + "-" + today,
                "每日签到赠送");
        return points;
    }

    /**
     * 积分兑换优惠券时扣减积分（与发券在同一事务内）。
     */
    @Transactional
    public void consumeForExchange(Long userId, long cost, String couponName) {
        long balance = balance(userId);
        if (balance < cost) {
            throw new BizException(ResultCode.POINT_NOT_ENOUGH,
                    "积分不足，还差 " + (cost - balance) + " 分");
        }
        append(userId, PointRecord.USE_EXCHANGE, -cost,
                "EXCHANGE-" + userId + "-" + System.nanoTime(), "兑换优惠券：" + couponName);
    }

    /**
     * 下单时锁定积分抵扣（幂等）。返回实际扣减的积分。
     *
     * <p>幂等键基于 orderId，重复提交订单不会重复扣积分。
     */
    @Transactional
    public long deductForOrder(Long userId, Long orderId, long points) {
        if (points <= 0) {
            return 0;
        }
        String bizNo = "ORDER-POINT-DEDUCT-" + orderId;
        if (pointRecordMapper.countByBizNo(bizNo) > 0) {
            return 0;
        }
        long balance = balance(userId);
        // 退款扣回时若已花掉，允许多扣到 0（余额不为负），差额不再追回
        long real = Math.min(points, balance);
        if (real > 0) {
            append(userId, PointRecord.USE_DEDUCT, -real, bizNo, "订单 " + orderId + " 积分抵现");
        }
        return real;
    }

    /**
     * 退款时扣回消费积分（按规则，可不足额）。幂等。
     */
    @Transactional
    public long rollbackConsume(Long userId, Long orderId, long points) {
        String bizNo = "ORDER-POINT-ROLLBACK-" + orderId;
        if (pointRecordMapper.countByBizNo(bizNo) > 0) {
            return 0;
        }
        if (points <= 0) {
            return 0;
        }
        long balance = balance(userId);
        long real = Math.min(points, balance);
        if (real > 0) {
            append(userId, PointRecord.REFUND_ROLLBACK, -real, bizNo, "订单 " + orderId + " 退款扣回");
        }
        return real;
    }

    /**
     * 退款时退回下单时抵扣的积分（若未过期）。幂等。
     */
    @Transactional
    public long rollbackDeduct(Long userId, Long orderId, long points) {
        if (points <= 0) {
            return 0;
        }
        String bizNo = "ORDER-POINT-DEDUCT-ROLLBACK-" + orderId;
        if (pointRecordMapper.countByBizNo(bizNo) > 0) {
            return 0;
        }
        append(userId, PointRecord.USE_DEDUCT, points, bizNo, "订单 " + orderId + " 退款退回积分");
        return points;
    }

    /**
     * 写入积分流水并更新用户余额（带负余额保护）。
     */
    private void append(Long userId, String type, long amount, String bizNo, String remark) {
        long balanceBefore = balance(userId);
        long balanceAfter = Math.max(0, balanceBefore + amount);
        PointRecord rec = new PointRecord();
        rec.setUserId(userId);
        rec.setType(type);
        rec.setAmount(amount);
        rec.setBalanceAfter(balanceAfter);
        rec.setBizNo(bizNo);
        rec.setRemark(remark);
        try {
            pointRecordMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            // 并发下的最后一道防线：biz_no 唯一索引拦截重复入账
            log.warn("积分流水 biz_no 重复，已忽略：{}", bizNo);
            return;
        }
        // 余额更新（GREATEST 保证不为负）
        userMapper.changePoint(userId, amount);
    }

    public List<PointRecord> records(Long userId, int page, int size) {
        int offset = (Math.max(1, page) - 1) * size;
        return pointRecordMapper.selectByUser(userId, size, offset);
    }

    public long countRecords(Long userId) {
        return pointRecordMapper.countByUser(userId);
    }
}
