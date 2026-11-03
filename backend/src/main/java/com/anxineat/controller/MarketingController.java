package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.module.marketing.service.MarketingService;
import com.anxineat.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户端营销活动接口（需用户 token，由 WebConfig 的 userAuthInterceptor 统一拦截）。
 */
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingService marketingService;

    @GetMapping("/activities")
    public R<List<MarketingService.ActivityDiscoverVO>> activities(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long storeId) {
        return R.ok(marketingService.discover(storeId, type));
    }

    @GetMapping("/flash-sale/list")
    public R<List<MarketingService.FlashSaleVO>> flashSaleList(@RequestParam(required = false) Long storeId) {
        return R.ok(marketingService.flashSaleList(storeId));
    }

    @PostMapping("/command/redeem")
    public R<MarketingService.CommandRedeemVO> commandRedeem(@RequestBody(required = false) Map<String, String> body) {
        String command = body == null ? null : body.get("command");
        return R.ok(marketingService.redeemCommand(AuthContext.requireUserId(), command));
    }

    @PostMapping("/zero-exchange/{id}/redeem")
    public R<MarketingService.ZeroRedeemVO> zeroRedeem(@PathVariable Long id) {
        return R.ok(marketingService.redeemZeroExchange(AuthContext.requireUserId(), id));
    }
}
