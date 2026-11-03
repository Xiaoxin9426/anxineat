package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.module.system.service.SysConfigService;
import com.anxineat.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端公开配置（免鉴权）。含支付渠道、起送/支付超时、积分规则等。
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final SysConfigService config;
    private final UserService userService;

    @GetMapping("/client")
    public R<Map<String, Object>> client() {
        Map<String, Object> pointRules = new LinkedHashMap<>();
        pointRules.put("perYuan", config.pointPerYuan());
        pointRules.put("signinAmount", config.signinPoint());
        pointRules.put("centsPerPoint", config.pointDeductCentsPerPoint());
        pointRules.put("minPoint", config.pointDeductMinPoint());
        pointRules.put("maxPercent", config.pointDeductMaxPercent());

        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("payExpireMinutes", config.payExpireMinutes());
        vo.put("payChannel", config.get("pay.channel", "MOCK"));
        vo.put("devMode", config.getBool("dev.mode"));
        // 小程序据此决定走真实 wx.login 还是本地 mock 登录，避免未配置凭证时登录死循环
        vo.put("wxRealLogin", userService.isRealWxLoginEnabled());
        vo.put("pointRules", pointRules);
        return R.ok(vo);
    }
}
