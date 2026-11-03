package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.MemberDTO;
import com.anxineat.dto.OrderDTO;
import com.anxineat.dto.RequestDTO;
import com.anxineat.module.admin.service.AdminService;
import com.anxineat.module.coupon.entity.CouponTemplate;
import com.anxineat.module.order.entity.Order;
import com.anxineat.module.product.entity.Category;
import com.anxineat.module.store.entity.Store;
import com.anxineat.security.AuthContext;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    private String operator() {
        return "ADMIN:" + AuthContext.requireAdmin().getAdminId();
    }

    @PostMapping("/login")
    public R<MemberDTO.AdminLoginVO> login(@RequestBody RequestDTO.AdminLoginRequest req) {
        return R.ok(adminService.login(req));
    }

    @GetMapping("/overview")
    public R<MemberDTO.OverviewVO> overview(@RequestParam(required = false) Long storeId) {
        return R.ok(adminService.overview(storeId));
    }

    @GetMapping("/orders")
    public R<IPage<OrderDTO.OrderListVO>> orders(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(adminService.orders(storeId, status, keyword, page, size));
    }

    @PostMapping("/order/{id}/accept")
    public R<Void> accept(@RequestParam(required = false) Long storeId, @PathVariable Long id) {
        adminService.accept(storeId, id, operator());
        return R.ok();
    }

    @PostMapping("/order/{id}/ready")
    public R<Void> ready(@RequestParam(required = false) Long storeId, @PathVariable Long id) {
        adminService.ready(storeId, id, operator());
        return R.ok();
    }

    @PostMapping("/order/{id}/delivering")
    public R<Void> delivering(@RequestParam(required = false) Long storeId, @PathVariable Long id) {
        adminService.delivering(storeId, id, operator());
        return R.ok();
    }

    @PostMapping("/order/{id}/complete")
    public R<Void> complete(@RequestParam(required = false) Long storeId, @PathVariable Long id) {
        adminService.complete(storeId, id, operator());
        return R.ok();
    }

    @PostMapping("/order/{id}/refund")
    public R<Void> refund(@RequestParam(required = false) Long storeId, @PathVariable Long id,
                           @RequestBody RefundBody body) {
        adminService.refund(storeId, id, body.reason(), operator());
        return R.ok();
    }

    @PostMapping("/order/redeem")
    public R<Void> redeem(@RequestBody RedeemBody body) {
        adminService.redeem(body.storeId(), body.pickupCode(), operator());
        return R.ok();
    }

    @GetMapping("/stores")
    public R<List<Store>> stores() {
        return R.ok(adminService.stores());
    }

    @PutMapping("/store/{id}")
    public R<Void> updateStore(@PathVariable Long id, @RequestBody RequestDTO.StoreAdminRequest req) {
        adminService.updateStore(id, req);
        return R.ok();
    }

    @GetMapping("/products")
    public R<List<MemberDTO.AdminProductVO>> products(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return R.ok(adminService.products(storeId, categoryId, keyword));
    }

    @PostMapping("/product")
    public R<Void> productUpsert(@RequestBody RequestDTO.ProductAdminRequest req) {
        adminService.productUpsert(req);
        return R.ok();
    }

    @PostMapping("/product/{id}/on-sale")
    public R<Void> onSale(@PathVariable Long id, @RequestBody RequestDTO.StoreProductFlagRequest req) {
        int v = req.getOnSale() != null ? req.getOnSale() : (req.getFlag() != null ? req.getFlag() : 1);
        adminService.setOnSale(req.getStoreId(), id, v);
        return R.ok();
    }

    @PostMapping("/product/{id}/sold-out")
    public R<Void> soldOut(@PathVariable Long id, @RequestBody RequestDTO.StoreProductFlagRequest req) {
        int v = req.getSoldOut() != null ? req.getSoldOut() : (req.getFlag() != null ? req.getFlag() : 1);
        adminService.setSoldOut(req.getStoreId(), id, v);
        return R.ok();
    }

    @PostMapping("/product/{id}/manual-sold-out")
    public R<Void> manualSoldOut(@PathVariable Long id, @RequestBody RequestDTO.CategoryStatusRequest req) {
        adminService.setProductSoldOut(id, req.getStatus() == null ? 1 : req.getStatus());
        return R.ok();
    }

    @GetMapping("/product/{id}/detail")
    public R<MemberDTO.AdminProductDetailVO> productDetail(@PathVariable Long id) {
        return R.ok(adminService.getProductDetail(id));
    }

    @PostMapping("/product/detail")
    public R<Void> productDetailSave(@RequestBody RequestDTO.ProductDetailAdminRequest req) {
        adminService.saveProductDetail(req);
        return R.ok();
    }

    // ==================== 商品分类 ====================

    @GetMapping("/categories")
    public R<List<Category>> categories() {
        return R.ok(adminService.categories());
    }

    @PostMapping("/category")
    public R<Void> categoryUpsert(@RequestBody RequestDTO.CategoryAdminRequest req) {
        adminService.categoryUpsert(req);
        return R.ok();
    }

    @PostMapping("/category/{id}/status")
    public R<Void> categoryStatus(@PathVariable Long id, @RequestBody RequestDTO.CategoryStatusRequest req) {
        adminService.categorySetStatus(id, req.getStatus() == null ? 1 : req.getStatus());
        return R.ok();
    }

    @DeleteMapping("/category/{id}")
    public R<Void> categoryDelete(@PathVariable Long id) {
        adminService.categoryDelete(id);
        return R.ok();
    }

    @PostMapping("/sku/{id}/stock")
    public R<Void> skuStock(@PathVariable Long id, @RequestBody RequestDTO.SkuStockRequest req) {
        adminService.setSkuStock(id, req.getStock() == null ? -1 : req.getStock());
        return R.ok();
    }

    @GetMapping("/coupons")
    public R<List<CouponTemplate>> coupons() {
        return R.ok(adminService.couponTemplates());
    }

    @PostMapping("/coupon")
    public R<Void> couponUpsert(@RequestBody RequestDTO.CouponAdminRequest req) {
        adminService.couponUpsert(req);
        return R.ok();
    }

    @PostMapping("/coupon/{id}/status")
    public R<Void> couponStatus(@PathVariable Long id, @RequestBody RequestDTO.CouponStatusRequest req) {
        adminService.setCouponStatus(id, req.getStatus() == null ? 1 : req.getStatus());
        return R.ok();
    }

    @GetMapping("/configs")
    public R<Map<String, String>> configs() {
        return R.ok(adminService.configs());
    }

    @PutMapping("/config/{key}")
    public R<Void> updateConfig(@PathVariable String key, @RequestBody RequestDTO.ConfigRequest req) {
        adminService.updateConfig(key, req.getValue());
        return R.ok();
    }

    private record RefundBody(String reason) {
    }

    private record RedeemBody(Long storeId, String pickupCode) {
    }
}
