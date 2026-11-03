package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.CatalogDTO;
import com.anxineat.module.content.entity.Banner;
import com.anxineat.module.content.mapper.BannerMapper;
import com.anxineat.module.store.entity.Store;
import com.anxineat.module.store.entity.StoreBusinessHours;
import com.anxineat.module.store.service.StoreService;
import com.anxineat.module.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开目录接口：门店、菜单、商品详情、Banner。免鉴权。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final StoreService storeService;
    private final ProductService productService;
    private final BannerMapper bannerMapper;

    @GetMapping("/health")
    public R<String> health() {
        return R.ok("ok");
    }

    @GetMapping("/store/nearby")
    public R<List<CatalogDTO.StoreVO>> nearby(
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword) {
        List<Store> stores = storeService.nearby(lat, lng, city, keyword);
        LocalDateTime now = LocalDateTime.now();
        List<CatalogDTO.StoreVO> vos = stores.stream().map(s -> {
            StoreService.OpenInfo info = storeService.computeOpenInfo(s, storeService.hours(s.getId()), now);
            CatalogDTO.StoreVO vo = new CatalogDTO.StoreVO();
            vo.setId(s.getId());
            vo.setName(s.getName());
            vo.setAddress(s.getAddress());
            vo.setLng(s.getLng());
            vo.setLat(s.getLat());
            vo.setCity(s.getCity());
            vo.setPhone(s.getPhone());
            vo.setBusinessStatus(s.getBusinessStatus());
            vo.setSupportPickup(s.getSupportPickup());
            vo.setSupportDelivery(s.getSupportDelivery());
            vo.setDeliveryFee(s.getDeliveryFee());
            vo.setMinDeliveryAmount(s.getMinDeliveryAmount());
            vo.setFreeDeliveryThreshold(s.getFreeDeliveryThreshold());
            vo.setPickupLeadMinutes(s.getPickupLeadMinutes());
            vo.setDistanceKm(s.getDistanceKm());
            vo.setOpenNow(info.open());
            vo.setTodayHours(info.todayHours());
            vo.setClosedReason(info.open() ? "" : info.reason());
            return vo;
        }).toList();
        return R.ok(vos);
    }

    @GetMapping("/store/{id}")
    public R<CatalogDTO.StoreVO> storeDetail(@PathVariable Long id) {
        Store s = storeService.getById(id);
        LocalDateTime now = LocalDateTime.now();
        StoreService.OpenInfo info = storeService.computeOpenInfo(s, storeService.hours(id), now);
        CatalogDTO.StoreVO vo = new CatalogDTO.StoreVO();
        vo.setId(s.getId());
        vo.setName(s.getName());
        vo.setAddress(s.getAddress());
        vo.setLng(s.getLng());
        vo.setLat(s.getLat());
        vo.setCity(s.getCity());
        vo.setPhone(s.getPhone());
        vo.setBusinessStatus(s.getBusinessStatus());
        vo.setSupportPickup(s.getSupportPickup());
        vo.setSupportDelivery(s.getSupportDelivery());
        vo.setDeliveryFee(s.getDeliveryFee());
        vo.setMinDeliveryAmount(s.getMinDeliveryAmount());
        vo.setFreeDeliveryThreshold(s.getFreeDeliveryThreshold());
        vo.setPickupLeadMinutes(s.getPickupLeadMinutes());
        vo.setOpenNow(info.open());
        vo.setTodayHours(info.todayHours());
        vo.setClosedReason(info.open() ? "" : info.reason());
        vo.setBusinessHours(storeService.hours(id).stream().map(h -> {
            CatalogDTO.BusinessHoursVO b = new CatalogDTO.BusinessHoursVO();
            b.setDayOfWeek(h.getDayOfWeek());
            b.setOpenTime(h.getOpenTime() == null ? null : h.getOpenTime().toString());
            b.setCloseTime(h.getCloseTime() == null ? null : h.getCloseTime().toString());
            b.setCrossDay(h.getCrossDay());
            return b;
        }).toList());
        return R.ok(vo);
    }

    @GetMapping("/store/{id}/menu")
    public R<CatalogDTO.MenuVO> menu(@PathVariable Long id) {
        return R.ok(productService.getMenu(id));
    }

    @GetMapping("/product/{id}")
    public R<CatalogDTO.ProductDetailVO> product(@PathVariable Long id,
                                                 @RequestParam Long storeId) {
        return R.ok(productService.getDetail(id, storeId));
    }

    @GetMapping("/banner/list")
    public R<List<Map<String, Object>>> banners() {
        List<Banner> list = bannerMapper.selectActive();
        List<Map<String, Object>> result = list.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("title", b.getTitle());
            m.put("image", b.getImage());
            m.put("linkType", b.getLinkType());
            m.put("linkValue", b.getLinkValue());
            m.put("sort", b.getSort());
            return m;
        }).toList();
        return R.ok(result);
    }
}
