package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.OrderDTO;
import com.anxineat.module.payment.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    public R<OrderDTO.PayCreateVO> create(@RequestBody Map<String, Long> body) {
        return R.ok(paymentService.createPay(body.get("orderId")));
    }

    /** 模拟支付回调（仅 MOCK 渠道）。duplicate=true 验证幂等。 */
    @PostMapping("/mock/callback")
    public R<OrderDTO.PayResultVO> mockCallback(@RequestBody MockCallbackBody body) {
        return R.ok(paymentService.mockCallback(body.payNo(), body.result(), Boolean.TRUE.equals(body.duplicate())));
    }

    /**
     * 微信支付结果通知（V3）。微信服务器主动回调，无需登录态。
     * 按微信规范返回 {code:"SUCCESS"} 表示已处理，否则会重试。
     */
    @PostMapping("/wechat/notify")
    public R<Map<String, String>> wechatNotify(@RequestBody JsonNode body) {
        paymentService.handleWechatNotify(body.get("resource"));
        Map<String, String> ok = new LinkedHashMap<>();
        ok.put("code", "SUCCESS");
        ok.put("message", "成功");
        return R.ok(ok);
    }

    private record MockCallbackBody(String payNo, String result, Boolean duplicate) {
    }
}
