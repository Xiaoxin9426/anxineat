package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.MemberDTO;
import com.anxineat.module.coupon.service.CouponService;
import com.anxineat.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/point")
@RequiredArgsConstructor
public class PointController {

    private final CouponService couponService;

    @PostMapping("/exchange")
    public R<MemberDTO.ExchangeResultVO> exchange(@RequestBody Map<String, Long> body) {
        Long templateId = body.get("templateId");
        return R.ok(couponService.exchange(AuthContext.requireUserId(), templateId));
    }
}
