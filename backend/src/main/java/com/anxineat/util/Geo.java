package com.anxineat.util;

/**
 * 地理距离计算（Haversine）。
 *
 * <p>为什么不用腾讯/高德地图 API：需求文档要求「按定位获取附近门店」，
 * 但接入地图服务需要密钥与配额，且门店直线距离排序用 Haversine 已足够。
 * 真实配送距离与骑手路径规划属于非目标范围。
 */
public final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Geo() {
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
