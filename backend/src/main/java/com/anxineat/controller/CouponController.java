package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.MemberDTO;
import com.anxineat.module.coupon.service.CouponService;
import com.anxineat.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/center")
    public R<List<MemberDTO.CouponCenterVO>> center() {
        return R.ok(couponService.center(AuthContext.requireUserId()));
    }

    @PostMapping("/receive/{templateId}")
    public R<Map<String, Object>> receive(@PathVariable Long templateId) {
        var uc = couponService.receive(AuthContext.requireUserId(), templateId);
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("userCouponId", uc.getId());
        vo.put("endTime", uc.getEndTime());
        return R.ok(vo);
    }

    @GetMapping("/mine")
    public R<List<MemberDTO.CouponVO>> mine(@RequestParam(required = false) String status) {
        return R.ok(couponService.mine(AuthContext.requireUserId(), status));
    }

    @GetMapping("/exchange/list")
    public R<List<MemberDTO.ExchangeVO>> exchangeList() {
        return R.ok(couponService.exchangeList(AuthContext.requireUserId()));
    }
}
