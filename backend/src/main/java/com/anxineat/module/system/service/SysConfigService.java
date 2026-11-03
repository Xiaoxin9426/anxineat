package com.anxineat.module.system.service;

import com.anxineat.module.system.entity.SysConfig;
import com.anxineat.module.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置服务。
 *
 * <p>需求文档把「签到积分每日数值」「积分兑换比例」「会员等级阈值」等列为
 * 「开放问题（需产品确认后定稿）」。与其把这些数值硬编码进业务代码，
 * 不如统一落到 sys_config 表，使运营后台可随时调整且无需重启。
 *
 * <p>配置项在进程内缓存，后台修改后调用 {@link #refresh()} 生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        List<SysConfig> list = sysConfigMapper.selectAll();
        Map<String, String> fresh = new LinkedHashMap<>();
        list.forEach(c -> fresh.put(c.getConfigKey(), c.getConfigValue()));
        cache.clear();
        cache.putAll(fresh);
        log.info("系统配置已加载，共 {} 项", cache.size());
    }

    public String get(String key, String defaultValue) {
        return cache.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String v = get(key, null);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 [{}] 不是整数，回退为默认值 {}", key, v, defaultValue);
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String v = get(key, null);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 [{}] 不是长整数，回退为默认值 {}", key, v, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBool(String key) {
        return "1".equals(get(key, "0")) || "true".equalsIgnoreCase(get(key, ""));
    }

    public void update(String key, String value) {
        SysConfig cfg = sysConfigMapper.selectByKey(key);
        if (cfg == null) {
            cfg = new SysConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            sysConfigMapper.insert(cfg);
        } else {
            cfg.setConfigValue(value);
            sysConfigMapper.updateById(cfg);
        }
        cache.put(key, value);
    }

    public Map<String, String> all() {
        return new LinkedHashMap<>(cache);
    }

    // ---------- 常用配置的语义化读取 ----------

    /** 每实付 1 元获得多少积分 */
    public int pointPerYuan() {
        return getInt("point.consume.per_yuan", 1);
    }

    /** 每日签到赠送积分 */
    public int signinPoint() {
        return getInt("point.signin.amount", 10);
    }

    /** 1 积分可抵扣多少分钱（金钱单位） */
    public int pointDeductCentsPerPoint() {
        return getInt("point.deduct.cents_per_point", 1);
    }

    /** 积分抵现起用门槛 */
    public int pointDeductMinPoint() {
        return getInt("point.deduct.min_point", 100);
    }

    /** 积分最多可抵扣商品金额的百分比 */
    public int pointDeductMaxPercent() {
        return getInt("point.deduct.max_percent", 50);
    }

    /** 待支付订单自动取消时长（分钟） */
    public int payExpireMinutes() {
        return getInt("order.pay.expire_minutes", 15);
    }
}
