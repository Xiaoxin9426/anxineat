package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.OrderDTO;
import com.anxineat.module.cart.service.CartService;
import com.anxineat.module.product.service.ProductService;
import com.anxineat.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public R<OrderDTO.CartVO> get(@RequestParam Long storeId) {
        return R.ok(cartService.getCart(AuthContext.requireUserId(), storeId));
    }

    @PostMapping("/add")
    public R<OrderDTO.CartVO> add(@RequestBody CartAddBody body) {
        CartService.AddRequest req = new CartService.AddRequest(
                body.productId(), body.skuId(), body.quantity(),
                body.specOptionIds(), body.toppings());
        return R.ok(cartService.add(AuthContext.requireUserId(), body.storeId(), req));
    }

    @PutMapping("/{itemId}")
    public R<OrderDTO.CartVO> update(@PathVariable Long itemId, @RequestBody QuantityBody body) {
        return R.ok(cartService.updateQuantity(AuthContext.requireUserId(), itemId, body.quantity()));
    }

    @DeleteMapping("/{itemId}")
    public R<OrderDTO.CartVO> remove(@PathVariable Long itemId) {
        return R.ok(cartService.remove(AuthContext.requireUserId(), itemId));
    }

    @DeleteMapping("/clear")
    public R<Void> clear(@RequestParam Long storeId) {
        cartService.clear(AuthContext.requireUserId(), storeId);
        return R.ok();
    }

    private record CartAddBody(Long storeId, Long productId, Long skuId, Integer quantity,
                               List<Long> specOptionIds,
                               List<ProductService.ToppingRequest> toppings) {
    }

    private record QuantityBody(Integer quantity) {
    }
}
