package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.module.marketing.service.MarketingService;
import com.anxineat.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台营销活动管理（超管统一配置，复用 /api/admin 鉴权，不做门店数据隔离）。
 */
@RestController
@RequestMapping("/api/admin/marketing")
@RequiredArgsConstructor
public class AdminMarketingController {

    private final MarketingService marketingService;

    @PostMapping("/activity")
    public R<Map<String, Object>> upsert(@RequestBody MarketingService.MarketingAdminRequest req) {
        Long id = marketingService.upsertActivity(req);
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("activityId", id);
        return R.ok(vo);
    }

    @GetMapping("/activities")
    public R<List<MarketingService.ActivityAdminVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return R.ok(marketingService.listActivities(status, type));
    }

    @PutMapping("/activity/{id}/status")
    public R<Void> setStatus(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        marketingService.setStatus(id, body == null ? null : body.get("status"));
        return R.ok();
    }

    @GetMapping("/activity/{id}")
    public R<MarketingService.ActivityDetailVO> detail(@PathVariable Long id) {
        return R.ok(marketingService.activityDetail(id));
    }
}
