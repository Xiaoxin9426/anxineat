package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.OrderDTO;
import com.anxineat.module.order.service.OrderService;
import com.anxineat.module.payment.service.PaymentService;
import com.anxineat.security.AuthContext;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @PostMapping("/preview")
    public R<OrderDTO.PreviewVO> preview(@RequestBody OrderDTO.PreviewRequest req) {
        return R.ok(orderService.preview(AuthContext.requireUserId(), req));
    }

    @PostMapping
    public R<OrderDTO.SubmitVO> submit(@RequestBody OrderDTO.SubmitRequest req) {
        return R.ok(orderService.submit(AuthContext.requireUserId(), req));
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        orderService.cancel(AuthContext.requireUserId(), id);
        return R.ok();
    }

    @GetMapping("/list")
    public R<IPage<OrderDTO.OrderListVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(orderService.list(AuthContext.requireUserId(), status, page, size));
    }

    @GetMapping("/{id}")
    public R<OrderDTO.OrderDetailVO> detail(@PathVariable Long id) {
        return R.ok(orderService.detail(AuthContext.requireUserId(), id));
    }

    @PostMapping("/{id}/reorder")
    public R<OrderDTO.ReorderVO> reorder(@PathVariable Long id) {
        return R.ok(orderService.reorder(AuthContext.requireUserId(), id));
    }

    /** 主动查支付结果（兜底：用户已扣款但回调丢失） */
    @PostMapping("/{id}/pay/query")
    public R<OrderDTO.PayResultVO> payQuery(@PathVariable Long id) {
        return R.ok(paymentService.query(id));
    }
}
