package com.anxineat.module.payment.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.dto.OrderDTO;
import com.anxineat.module.order.entity.Order;
import com.anxineat.module.order.enums.OrderStatus;
import com.anxineat.module.order.mapper.OrderMapper;
import com.anxineat.module.order.service.OrderService;
import com.anxineat.module.payment.entity.Payment;
import com.anxineat.module.payment.mapper.PaymentMapper;
import com.anxineat.module.system.service.SysConfigService;
import com.anxineat.module.user.entity.User;
import com.anxineat.module.user.mapper.UserMapper;
import com.anxineat.util.NoGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 支付服务。
 *
 * <p>本地联调以「模拟支付」为主：{@code /api/pay/mock/callback} 直接驱动订单状态流转，
 * 并通过 {@code duplicate=true} 连续两次回调验证幂等（订单只变更一次）。
 *
 * <p>真实微信支付采用 JSAPI 适配层（V3 接口）：
 * <ul>
 *   <li>{@link #createPay} 在非 MOCK 渠道下调用微信 {@code /v3/pay/transactions/jsapi} 拉起预支付，
 *       用商户私钥对请求做 RSA 签名，并将 {@code wx.requestPayment} 所需的
 *       {@code timeStamp/nonceStr/package/signType/paySign} 一并返回前端。</li>
 *   <li>微信支付结果通过 {@code /api/pay/wechat/notify} 回调（解密 AES-256-GCM 报文后标记支付成功），
 *       与 MOCK 回调复用同一幂等闸门 {@code PaymentMapper.tryMarkSuccess}。</li>
 * </ul>
 *
 * <p>商户参数通过 {@code anxineat.pay.wechat.*} 注入（环境变量 {@code WECHAT_*}），本环境无企业
 * 商户号与备案域名，默认 {@code MOCK} 渠道，配置齐全后把 {@code sys_config.pay.channel} 设为
 * {@code WECHAT_JSAPI} 即可切到真实支付。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final SysConfigService config;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Value("${anxineat.pay.wechat.enabled:false}")
    private boolean wechatEnabled;
    @Value("${anxineat.pay.wechat.app-id:}")
    private String wxAppId;
    @Value("${anxineat.pay.wechat.mch-id:}")
    private String mchId;
    @Value("${anxineat.pay.wechat.api-v3-key:}")
    private String apiV3Key;
    @Value("${anxineat.pay.wechat.mch-serial-no:}")
    private String mchSerialNo;
    @Value("${anxineat.pay.wechat.private-key-path:}")
    private String privateKeyPath;
    @Value("${anxineat.pay.wechat.notify-url:}")
    private String notifyUrl;

    /** 商户私钥缓存（PKCS#8，加载一次） */
    private PrivateKey merchantPrivateKey;

    private String channel() {
        return config.get("pay.channel", "MOCK");
    }

    private boolean isMock() {
        return "MOCK".equalsIgnoreCase(channel());
    }

    /** 创建/复用支付单 */
    @Transactional
    public OrderDTO.PayCreateVO createPay(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (OrderStatus.PENDING_PAYMENT != order.statusEnum()) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "订单不在待支付状态");
        }
        Payment payment = paymentMapper.selectLatestByOrder(orderId);
        if (payment == null || !"PAYING".equals(payment.getStatus())) {
            payment = new Payment();
            payment.setPayNo(NoGenerator.payNo());
            payment.setOrderId(orderId);
            payment.setOrderNo(order.getOrderNo());
            payment.setChannel(channel());
            payment.setAmount(order.getPayAmount());
            payment.setStatus("PAYING");
            if (!isMock()) {
                payment.setPrepayJson(buildWechatPrepay(order, payment.getPayNo()));
            }
            paymentMapper.insert(payment);
        }

        OrderDTO.PayCreateVO vo = new OrderDTO.PayCreateVO();
        vo.setPayNo(payment.getPayNo());
        vo.setOrderId(orderId);
        vo.setAmount(payment.getAmount());
        vo.setChannel(payment.getChannel());
        vo.setExpireTime(order.getExpireTime());
        vo.setMock(isMock());
        if (isMock()) {
            vo.setPrepay(Map.of("note", "模拟支付，无需真实拉起微信支付"));
        } else {
            // prepayJson 已是合法 JSON 字符串，解析为对象返回，避免前端再 JSON.parse
            try {
                vo.setPrepay(objectMapper.readValue(payment.getPrepayJson(), Map.class));
            } catch (Exception e) {
                vo.setPrepay(payment.getPrepayJson());
            }
        }
        return vo;
    }

    /**
     * 模拟支付回调。
     *
     * @param duplicate true 时连续处理两次回调，用于验证幂等
     */
    @Transactional
    public OrderDTO.PayResultVO mockCallback(String payNo, String result, boolean duplicate) {
        Payment payment = paymentMapper.selectByPayNo(payNo);
        if (payment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "支付单不存在");
        }
        if (!"SUCCESS".equalsIgnoreCase(result)) {
            int rows = paymentMapper.tryMarkFailed(payNo, "用户取消或支付失败");
            OrderDTO.PayResultVO vo = buildResult(payment.getOrderId());
            vo.setHandled(rows == 1);
            vo.setMessage("支付失败");
            return vo;
        }

        boolean handled = markPaid(payNo);
        boolean duplicateHandled = false;
        if (duplicate) {
            boolean second = markPaid(payNo);
            duplicateHandled = !second; // 第二次返回 false => 被幂等拦截
        }

        OrderDTO.PayResultVO vo = buildResult(payment.getOrderId());
        vo.setHandled(handled);
        vo.setDuplicateHandled(duplicateHandled);
        vo.setMessage(handled ? "支付成功" : "订单已支付，重复回调已忽略");
        return vo;
    }

    /** 主动查询支付结果（兜底：用户已扣款但回调丢失） */
    @Transactional
    public OrderDTO.PayResultVO query(Long orderId) {
        Payment payment = paymentMapper.selectLatestByOrder(orderId);
        if (payment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "支付单不存在");
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            // 确保订单状态已推进（幂等）
            orderService.markPaid(orderId, payment.getTransactionId());
        }
        OrderDTO.PayResultVO vo = buildResult(orderId);
        vo.setHandled("SUCCESS".equals(payment.getStatus()));
        vo.setMessage(vo.getOrderStatus() != null && OrderStatus.PAID.name().equals(vo.getOrderStatus())
                ? "支付成功" : "尚未支付");
        return vo;
    }

    /**
     * 处理微信支付结果通知（V3）。
     * 对调微信回调的 AES-256-GCM 报文解密，提取 out_trade_no / transaction_id 后标记支付成功。
     *
     * <p>说明：生产环境应在解密前校验 {@code Wechatpay-Signature} 头（用微信平台证书验签），
     * 本实现聚焦「解密 + 标记」核心链路，平台证书链校验需结合证书下载接口，联调时由调用方补充。
     *
     * @param resource 回调报文中的 resource 节点：{ciphertext, nonce, associated_data}
     */
    @Transactional
    public void handleWechatNotify(JsonNode resource) {
        if (resource == null || resource.get("ciphertext") == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "微信回调报文缺少 resource.ciphertext");
        }
        String plaintext = decryptNotify(
                resource.get("ciphertext").asText(),
                resource.get("nonce").asText(),
                resource.has("associated_data") && !resource.get("associated_data").isNull()
                        ? resource.get("associated_data").asText() : "");
        try {
            JsonNode data = objectMapper.readTree(plaintext);
            String outTradeNo = data.get("out_trade_no").asText();
            String transactionId = data.has("transaction_id") ? data.get("transaction_id").asText() : "";
            String tradeState = data.has("trade_state") ? data.get("trade_state").asText() : "SUCCESS";
            if (!"SUCCESS".equals(tradeState)) {
                log.warn("微信回调交易状态非 SUCCESS：{} -> {}", outTradeNo, tradeState);
                return;
            }
            markPaidByPayNo(outTradeNo, transactionId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.BIZ_ERROR, "解析微信回调明文失败：" + e.getMessage());
        }
    }

    /** 处理一次支付成功（幂等闸门在 PaymentMapper.tryMarkSuccess） */
    private boolean markPaid(String payNo) {
        Payment payment = paymentMapper.selectByPayNo(payNo);
        if (payment == null) {
            return false;
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            return false;
        }
        int rows = paymentMapper.tryMarkSuccess(payNo, generateTransactionId());
        if (rows != 1) {
            return false;
        }
        orderService.markPaid(payment.getOrderId(), payment.getTransactionId());
        return true;
    }

    /** 微信回调：已知 out_trade_no（即 pay_no），用微信返回的真实 transaction_id 标记 */
    private boolean markPaidByPayNo(String payNo, String transactionId) {
        Payment payment = paymentMapper.selectByPayNo(payNo);
        if (payment == null) {
            log.warn("微信回调：支付单不存在 {}", payNo);
            return false;
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            return false;
        }
        String txn = (transactionId == null || transactionId.isEmpty()) ? generateTransactionId() : transactionId;
        int rows = paymentMapper.tryMarkSuccess(payNo, txn);
        if (rows != 1) {
            return false;
        }
        orderService.markPaid(payment.getOrderId(), payment.getTransactionId());
        return true;
    }

    private OrderDTO.PayResultVO buildResult(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        OrderDTO.PayResultVO vo = new OrderDTO.PayResultVO();
        vo.setOrderId(orderId);
        vo.setOrderNo(order == null ? null : order.getOrderNo());
        vo.setOrderStatus(order == null ? null : order.getStatus());
        vo.setOrderStatusLabel(order == null ? null : order.statusEnum().getLabel());
        vo.setPickupCode(order == null ? null : order.getPickupCode());
        return vo;
    }

    /**
     * 真实微信 JSAPI 预支付：调用 V3 unifiedorder 拿到 prepay_id，
     * 再用商户私钥对 {@code wx.requestPayment} 参数做 RSA 签名，返回完整支付参数。
     */
    private String buildWechatPrepay(Order order, String payNo) {
        if (!wechatEnabled) {
            throw new BizException(ResultCode.BIZ_ERROR,
                    "微信支付未启用（anxineat.pay.wechat.enabled=false）。请配置 WECHAT_* 环境变量并将 enabled 置为 true，"
                            + "或将 sys_config.pay.channel 改回 MOCK。");
        }
        requireMerchantConfig();
        String openid = resolveOpenId(order);
        String body = buildJsapiBody(order, payNo, openid);
        String prepayId = callUnifiedOrder(body);

        // 构造前端 wx.requestPayment 所需的参数并做二次签名（paySign）
        long ts = System.currentTimeMillis() / 1000;
        String nonce = randomNonce();
        String pkg = "prepay_id=" + prepayId;
        String paySign = signRSA(wxAppId + "\n" + ts + "\n" + nonce + "\n" + pkg + "\n");

        Map<String, Object> prepay = new LinkedHashMap<>();
        prepay.put("appId", wxAppId);
        prepay.put("timeStamp", String.valueOf(ts));
        prepay.put("nonceStr", nonce);
        prepay.put("package", pkg);
        prepay.put("signType", "RSA");
        prepay.put("paySign", paySign);
        prepay.put("prepayId", prepayId);
        try {
            return objectMapper.writeValueAsString(prepay);
        } catch (Exception e) {
            throw new BizException(ResultCode.BIZ_ERROR, "构造微信预支付参数失败：" + e.getMessage());
        }
    }

    private void requireMerchantConfig() {
        StringBuilder miss = new StringBuilder();
        if (isBlank(wxAppId)) miss.append(" app-id");
        if (isBlank(mchId)) miss.append(" mch-id");
        if (isBlank(mchSerialNo)) miss.append(" mch-serial-no");
        if (isBlank(apiV3Key)) miss.append(" api-v3-key");
        if (isBlank(privateKeyPath)) miss.append(" private-key-path");
        if (isBlank(notifyUrl)) miss.append(" notify-url");
        if (miss.length() > 0) {
            throw new BizException(ResultCode.BIZ_ERROR,
                    "微信支付商户参数缺失：" + miss + "。请通过 WECHAT_* 环境变量注入后重试。");
        }
    }

    private String resolveOpenId(Order order) {
        if (order.getUserId() == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "订单缺少用户，无法发起微信支付");
        }
        User u = userMapper.selectById(order.getUserId());
        if (u == null || isBlank(u.getOpenId())) {
            throw new BizException(ResultCode.BIZ_ERROR,
                    "用户未绑定微信 openid，无法发起 JSAPI 支付（请先用真实 wx.login 登录）");
        }
        return u.getOpenId();
    }

    /** 组装 JSAPI 下单请求体（金额单位为分，币种 CNY） */
    private String buildJsapiBody(Order order, String payNo, String openid) {
        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("total", order.getPayAmount());
        amount.put("currency", "CNY");
        Map<String, Object> payer = new LinkedHashMap<>();
        payer.put("openid", openid);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("appid", wxAppId);
        req.put("mchid", mchId);
        req.put("description", "安心饮点餐-" + order.getOrderNo());
        req.put("out_trade_no", payNo);
        req.put("notify_url", notifyUrl);
        req.put("amount", amount);
        req.put("payer", payer);
        try {
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            throw new BizException(ResultCode.BIZ_ERROR, "构造微信支付请求失败：" + e.getMessage());
        }
    }

    /** 调用微信 V3 JSAPI 下单，返回 prepay_id */
    private String callUnifiedOrder(String body) {
        long ts = System.currentTimeMillis() / 1000;
        String nonce = randomNonce();
        String method = "POST";
        String path = "/v3/pay/transactions/jsapi";
        String message = method + "\n" + path + "\n" + ts + "\n" + nonce + "\n" + body + "\n";
        String signature = signRSA(message);
        String auth = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + mchId
                + "\",nonce_str=\"" + nonce
                + "\",signature=\"" + signature
                + "\",timestamp=\"" + ts
                + "\",serial_no=\"" + mchSerialNo + "\"";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi"))
                .header("Authorization", auth)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "anxineat/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp;
        try {
            resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new BizException(ResultCode.BIZ_ERROR, "调用微信支付失败：" + e.getMessage());
        }
        int code = resp.statusCode();
        String respBody = resp.body();
        if (code != 200) {
            throw new BizException(ResultCode.BIZ_ERROR, "微信支付下单失败(" + code + ")：" + respBody);
        }
        try {
            JsonNode node = objectMapper.readTree(respBody);
            String prepayId = node.get("prepay_id").asText();
            if (isBlank(prepayId)) {
                throw new BizException(ResultCode.BIZ_ERROR, "微信支付未返回 prepay_id：" + respBody);
            }
            return prepayId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.BIZ_ERROR, "解析微信支付响应失败：" + respBody);
        }
    }

    /** 用商户私钥对消息做 SHA256withRSA 签名，返回 Base64 */
    private String signRSA(String message) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(loadPrivateKey());
            sig.update(message.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new BizException(ResultCode.BIZ_ERROR, "微信支付签名失败：" + e.getMessage());
        }
    }

    /** 加载商户私钥（PKCS#8 PEM），缓存复用 */
    private PrivateKey loadPrivateKey() {
        if (merchantPrivateKey == null) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(privateKeyPath)), StandardCharsets.UTF_8);
                content = content.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s+", "");
                byte[] decoded = java.util.Base64.getDecoder().decode(content);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                merchantPrivateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(decoded));
            } catch (Exception e) {
                throw new BizException(ResultCode.BIZ_ERROR, "加载商户私钥失败：" + e.getMessage());
            }
        }
        return merchantPrivateKey;
    }

    /** 解密微信回调 AES-256-GCM 报文（api-v3-key 作密钥） */
    private String decryptNotify(String ciphertext, String nonce, String associatedData) {
        if (isBlank(apiV3Key)) {
            throw new BizException(ResultCode.BIZ_ERROR, "未配置微信 api-v3-key，无法解密回调");
        }
        try {
            byte[] aesKey = apiV3Key.getBytes(StandardCharsets.UTF_8);
            byte[] nonceBytes = nonce.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = java.util.Base64.getDecoder().decode(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, nonceBytes);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            if (associatedData != null && !associatedData.isEmpty()) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ResultCode.BIZ_ERROR, "解密微信回调失败：" + e.getMessage());
        }
    }

    private String randomNonce() {
        return new BigInteger(128, new java.security.SecureRandom()).toString(16);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String generateTransactionId() {
        return "MOCK" + System.currentTimeMillis() + NoGenerator.formatPickupCode(
                (int) (System.nanoTime() % 1000));
    }
}
