package com.anxineat.module.store.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.module.order.enums.OrderType;
import com.anxineat.module.store.entity.Store;
import com.anxineat.module.store.entity.StoreBusinessHours;
import com.anxineat.module.store.mapper.StoreBusinessHoursMapper;
import com.anxineat.module.store.mapper.StoreMapper;
import com.anxineat.util.Geo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门店服务。
 *
 * <p>营业状态判定是最容易出错的地方：需求文档同时存在「business_status 手动开关」
 * 与「营业时间」两套控制，但未定义二者关系。本项目约定为「与」关系：
 * 手动休息优先，其次看当前时刻是否落在当天营业时段内（支持跨零点）。
 */
@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreMapper storeMapper;
    private final StoreBusinessHoursMapper hoursMapper;

    public Store getById(Long id) {
        Store s = storeMapper.selectById(id);
        if (s == null) {
            throw new BizException(ResultCode.NOT_FOUND, "门店不存在");
        }
        return s;
    }

    /** 同 getById 但不抛异常，找不到返回 null（订单详情等可选场景用） */
    public Store getByIdSafe(Long id) {
        return id == null ? null : storeMapper.selectById(id);
    }

    /** 门店营业时段 */
    public List<StoreBusinessHours> hours(Long storeId) {
        return hoursMapper.selectByStore(storeId);
    }

    public List<Store> listAll() {
        return storeMapper.selectAllAvailable(null);
    }

    /**
     * 门店列表。传入经纬度时按距离升序，否则按 sort。
     *
     * <p>营业状态由服务端统一计算后随列表返回，前端不做二次判断，
     * 避免出现「前端算的营业状态和服务端算的不一致」这一类经典问题。
     */
    public List<Store> nearby(BigDecimal lat, BigDecimal lng, String city, String keyword) {
        List<Store> stores;
        if (keyword != null && !keyword.isBlank()) {
            stores = storeMapper.searchByKeyword(keyword.trim());
        } else {
            stores = storeMapper.selectAllAvailable((city == null || city.isBlank()) ? null : city);
        }

        Map<Long, List<StoreBusinessHours>> hoursMap = hoursMapper.selectList(null)
                .stream()
                .collect(Collectors.groupingBy(StoreBusinessHours::getStoreId));
        LocalDateTime now = LocalDateTime.now();

        List<Store> result = new ArrayList<>();
        for (Store s : stores) {
            if (lat != null && lng != null && s.getLat() != null && s.getLng() != null) {
                double km = Geo.distanceKm(
                        lat.doubleValue(), lng.doubleValue(),
                        s.getLat().doubleValue(), s.getLng().doubleValue());
                s.setDistanceKm(BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP).doubleValue());
            }
            result.add(s);
        }

        if (lat != null && lng != null) {
            result.sort(Comparator.comparing(s -> s.getDistanceKm() == null ? Double.MAX_VALUE : s.getDistanceKm()));
        } else {
            result.sort(Comparator.comparing(Store::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Store::getId));
        }
        return result;
    }

    /**
     * 计算门店当前营业状态。
     *
     * <p>规则：businessStatus = 0（手动休息）一律不营业；
     * 否则检查当天营业时段，支持跨零点（如 22:00-02:00）；
     * 未配置任何营业时段时视为全天营业。
     */
    public OpenInfo computeOpenInfo(Store store, List<StoreBusinessHours> hours, LocalDateTime now) {
        if (store.getBusinessStatus() == null || store.getBusinessStatus() == 0) {
            return OpenInfo.closed("门店休息中", "");
        }
        if (hours == null || hours.isEmpty()) {
            return OpenInfo.open("全天营业");
        }

        int today = toDayOfWeekValue(now.getDayOfWeek());
        int yesterday = (today + 6) % 7;
        LocalTime time = now.toLocalTime();

        String todayHours = "";
        boolean inRange = false;

        for (StoreBusinessHours h : hours) {
            if (h.getDayOfWeek() == null) {
                continue;
            }
            // 收集今天的营业时段文案
            if (h.getDayOfWeek() == today && todayHours.isEmpty()) {
                todayHours = fmt(h.getOpenTime()) + "-" + fmt(h.getCloseTime());
            }
            // 今天该时段内
            if (h.getDayOfWeek() == today && inPeriod(time, h)) {
                inRange = true;
            }
            // 昨日跨零点时段延伸到今天凌晨（如 22:00-02:00）
            if (h.getDayOfWeek() == yesterday && isCrossDay(h) && !time.isAfter(h.getCloseTime())) {
                inRange = true;
            }
        }

        if (inRange) {
            return OpenInfo.open(todayHours);
        }
        String hint = nextOpenHint(hours, today, time);
        return OpenInfo.closed("不在营业时间内", todayHours, hint);
    }

    /**
     * 判断当前时刻是否落在某个时段内。
     * 跨零点时段（如 22:00-02:00）拆成「当日 22:00 之后」与「次日 02:00 之前」两段判断，
     * 后者由调用方用昨日时段覆盖。
     */
    private boolean inPeriod(LocalTime time, StoreBusinessHours h) {
        LocalTime open = h.getOpenTime();
        LocalTime close = h.getCloseTime();
        if (open == null || close == null) {
            return false;
        }
        if (isCrossDay(h)) {
            return !time.isBefore(open);           // 当日 22:00 之后即算营业
        }
        return !time.isBefore(open) && time.isBefore(close);
    }

    private boolean isCrossDay(StoreBusinessHours h) {
        return Integer.valueOf(1).equals(h.getCrossDay());
    }

    private String nextOpenHint(List<StoreBusinessHours> hours, int today, LocalTime time) {
        String next = hours.stream()
                .filter(h -> h.getDayOfWeek() != null && h.getDayOfWeek() == today)
                .filter(h -> h.getOpenTime() != null && time.isBefore(h.getOpenTime()))
                .map(h -> fmt(h.getOpenTime()))
                .sorted()
                .findFirst()
                .orElse(null);
        return next == null ? "今日已打烊" : "今日 " + next + " 开始营业";
    }

    /** 与库内 day_of_week 约定一致：0=周日，1=周一 ... 6=周六 */
    private static int toDayOfWeekValue(DayOfWeek dow) {
        return dow == DayOfWeek.SUNDAY ? 0 : dow.getValue();
    }

    private static String fmt(LocalTime t) {
        return t == null ? "" : String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    /**
     * 校验门店是否可接指定类型的订单，不满足时抛出业务异常。
     */
    public void assertOrderable(Store store, OrderType orderType) {
        List<StoreBusinessHours> hours = hoursMapper.selectByStore(store.getId());
        OpenInfo info = computeOpenInfo(store, hours, LocalDateTime.now());
        if (!info.open()) {
            throw new BizException(ResultCode.STORE_CLOSED, info.reason());
        }
        if (orderType == OrderType.PICKUP && !Integer.valueOf(1).equals(store.getSupportPickup())) {
            throw new BizException(ResultCode.STORE_CHANNEL_DISABLED, "该门店暂不支持到店自取");
        }
        if (orderType == OrderType.DELIVERY && !Integer.valueOf(1).equals(store.getSupportDelivery())) {
            throw new BizException(ResultCode.STORE_CHANNEL_DISABLED, "该门店暂不支持外卖配送");
        }
    }

    /**
     * 营业状态与提示文案。
     */
    public record OpenInfo(boolean open, String todayHours, String reason, String hint) {

        static OpenInfo open(String todayHours) {
            return new OpenInfo(true, todayHours, "", "");
        }

        static OpenInfo closed(String reason, String todayHours) {
            return new OpenInfo(false, todayHours, reason, "");
        }

        static OpenInfo closed(String reason, String todayHours, String hint) {
            return new OpenInfo(false, todayHours, reason, hint);
        }
    }
}
