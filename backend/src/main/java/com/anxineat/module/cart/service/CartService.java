package com.anxineat.module.cart.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.dto.OrderDTO;
import com.anxineat.module.cart.entity.CartItem;
import com.anxineat.module.cart.mapper.CartItemMapper;
import com.anxineat.module.product.entity.Product;
import com.anxineat.module.product.entity.ProductSku;
import com.anxineat.module.product.mapper.ProductMapper;
import com.anxineat.module.product.mapper.ProductSkuMapper;
import com.anxineat.module.product.service.ProductService;
import com.anxineat.module.store.entity.StoreProduct;
import com.anxineat.module.store.mapper.StoreProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车服务。
 *
 * <p>相对原需求文档的两处偏离：
 * <ol>
 *   <li>购物车落库而非存 Redis —— Redis 重启即丢，且单机演示环境不依赖 Redis 也能跑通。
 *       按 user_id + store_id 隔离，item_key 唯一键保证同规格合并。</li>
 *   <li>每次读取时重新校验可售性 —— 商品可能在加购后被下架或售罄，
 *       若只在加购时校验，用户会带着已售罄的商品走到支付环节才报错。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CartService {

    /** 单个条目数量上限 */
    private static final int MAX_QUANTITY = 99;

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final StoreProductMapper storeProductMapper;
    private final ProductService productService;

    /** 加购请求 */
    public record AddRequest(Long productId, Long skuId, Integer quantity,
                             List<Long> specOptionIds,
                             List<ProductService.ToppingRequest> toppings) {
    }

    @Transactional
    public OrderDTO.CartVO add(Long userId, Long storeId, AddRequest req) {
        int qty = req.quantity() == null || req.quantity() <= 0 ? 1 : req.quantity();

        // 服务端重算价格：加购时即落库单价，避免取值时商品已改价造成金额漂移
        List<ProductService.ItemRequest> one = List.of(buildItemRequest(req, qty));
        List<ProductService.ResolvedItem> resolved = productService.resolveItems(storeId, one);
        ProductService.ResolvedItem item = resolved.get(0);

        String itemKey = productService.buildItemKey(
                req.productId(), req.skuId(), req.specOptionIds(), req.toppings());

        CartItem entity = new CartItem();
        entity.setUserId(userId);
        entity.setStoreId(storeId);
        entity.setProductId(req.productId());
        entity.setSkuId(req.skuId());
        entity.setItemKey(itemKey);
        entity.setQuantity(qty);
        entity.setSpecSnapshot(item.getSpecSnapshot());
        entity.setToppingSnapshot(item.getToppingSnapshot());
        entity.setUnitPrice(item.getUnitPrice());
        cartItemMapper.upsert(entity);

        // 累加后若超过上限则收敛
        CartItem saved = cartItemMapper.selectByKey(userId, storeId, itemKey);
        if (saved != null && saved.getQuantity() > MAX_QUANTITY) {
            saved.setQuantity(MAX_QUANTITY);
            cartItemMapper.updateById(saved);
        }
        return getCart(userId, storeId);
    }

    private ProductService.ItemRequest buildItemRequest(AddRequest req, int qty) {
        ProductService.ItemRequest ir = new ProductService.ItemRequest();
        ir.setProductId(req.productId());
        ir.setSkuId(req.skuId());
        ir.setQuantity(qty);
        ir.setSpecOptionIds(req.specOptionIds());
        ir.setToppings(req.toppings() == null ? List.of() : req.toppings());
        return ir;
    }

    @Transactional
    public OrderDTO.CartVO updateQuantity(Long userId, Long itemId, int quantity) {
        CartItem item = getOwnedItem(userId, itemId);
        if (quantity <= 0) {
            cartItemMapper.deleteById(itemId);
        } else {
            item.setQuantity(Math.min(quantity, MAX_QUANTITY));
            cartItemMapper.updateById(item);
        }
        return getCart(userId, item.getStoreId());
    }

    @Transactional
    public OrderDTO.CartVO remove(Long userId, Long itemId) {
        CartItem item = getOwnedItem(userId, itemId);
        cartItemMapper.deleteById(itemId);
        return getCart(userId, item.getStoreId());
    }

    @Transactional
    public void clear(Long userId, Long storeId) {
        cartItemMapper.clearByStore(userId, storeId);
    }

    /**
     * 切换门店时清空原门店购物车。
     * 需求文档验收标准：切换门店时给出明确提示，确认后清空原门店购物车。
     */
    @Transactional
    public void clearOnStoreSwitch(Long userId, Long newStoreId) {
        cartItemMapper.clearByStore(userId, newStoreId);
    }

    /**
     * 查询购物车并重新校验每个条目的可售性。
     */
    public OrderDTO.CartVO getCart(Long userId, Long storeId) {
        List<CartItem> items = cartItemMapper.selectByUserAndStore(userId, storeId);

        OrderDTO.CartVO vo = new OrderDTO.CartVO();
        vo.setStoreId(storeId);
        if (items.isEmpty()) {
            vo.setItems(List.of());
            vo.setTotalQuantity(0);
            vo.setTotalAmount(0L);
            return vo;
        }

        List<Long> productIds = items.stream().map(CartItem::getProductId).distinct().toList();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> skuIds = items.stream().map(CartItem::getSkuId).distinct().toList();
        Map<Long, ProductSku> skuMap = skuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, Function.identity()));

        Map<Long, StoreProduct> spMap = storeProductMapper.selectByStore(storeId).stream()
                .collect(Collectors.toMap(StoreProduct::getProductId, Function.identity(), (a, b) -> a));

        List<OrderDTO.CartItemVO> vos = new ArrayList<>();
        long totalAmount = 0L;
        int totalQuantity = 0;

        for (CartItem ci : items) {
            OrderDTO.CartItemVO ivo = new OrderDTO.CartItemVO();
            ivo.setId(ci.getId());
            ivo.setProductId(ci.getProductId());
            ivo.setSkuId(ci.getSkuId());
            ivo.setQuantity(ci.getQuantity());
            ivo.setUnitPrice(ci.getUnitPrice());
            ivo.setTotalPrice(ci.getUnitPrice() * ci.getQuantity());
            ivo.setSpecSnapshot(ci.getSpecSnapshot());
            ivo.setToppingSnapshot(ci.getToppingSnapshot());
            ivo.setMaxQuantity(MAX_QUANTITY);

            Product p = productMap.get(ci.getProductId());
            ProductSku sku = skuMap.get(ci.getSkuId());
            StoreProduct sp = spMap.get(ci.getProductId());

            ivo.setProductName(p == null ? "商品已删除" : p.getName());
            ivo.setProductImage(p == null ? null : p.getImage());
            ivo.setSkuName(sku == null ? "" : sku.getCupSize());
            ivo.setBasePrice(sku == null ? ci.getUnitPrice() : sku.getPrice());

            Availability a = check(ci, p, sku, sp);
            ivo.setAvailable(a.available);
            ivo.setUnavailableReason(a.reason);

            vos.add(ivo);
            // 不可用条目不计入合计，避免用户为售罄商品付费
            if (a.available) {
                totalAmount += ivo.getTotalPrice();
                totalQuantity += ci.getQuantity();
            }
        }

        vo.setItems(vos);
        vo.setTotalQuantity(totalQuantity);
        vo.setTotalAmount(totalAmount);
        return vo;
    }

    /**
     * 返回当前门店购物车中「可售」的条目，供下单使用。
     */
    public List<CartItem> availableItems(Long userId, Long storeId) {
        List<CartItem> items = cartItemMapper.selectByUserAndStore(userId, storeId);
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productMap = productMapper.selectBatchIds(
                        items.stream().map(CartItem::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductSku> skuMap = skuMapper.selectBatchIds(
                        items.stream().map(CartItem::getSkuId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductSku::getId, Function.identity()));
        Map<Long, StoreProduct> spMap = storeProductMapper.selectByStore(storeId).stream()
                .collect(Collectors.toMap(StoreProduct::getProductId, Function.identity(), (a, b) -> a));

        return items.stream()
                .filter(ci -> check(ci, productMap.get(ci.getProductId()),
                        skuMap.get(ci.getSkuId()), spMap.get(ci.getProductId())).available)
                .toList();
    }

    private Availability check(CartItem ci, Product p, ProductSku sku, StoreProduct sp) {
        if (p == null || !Integer.valueOf(1).equals(p.getStatus())) {
            return Availability.no("商品已下架");
        }
        if (sp == null || !Integer.valueOf(1).equals(sp.getStatus())) {
            return Availability.no("该门店暂不售卖");
        }
        if (Integer.valueOf(1).equals(p.getSoldOut()) || Integer.valueOf(1).equals(sp.getSoldOut())) {
            return Availability.no("商品已售罄");
        }
        if (sku == null || !Objects.equals(sku.getProductId(), p.getId())) {
            return Availability.no("规格已失效");
        }
        if (sku.getStock() != null && sku.getStock() != -1 && sku.getStock() < ci.getQuantity()) {
            return Availability.no("库存不足，仅剩 " + sku.getStock() + " 份");
        }
        if (ci.getQuantity() > MAX_QUANTITY) {
            return Availability.no("单次购买数量不能超过 " + MAX_QUANTITY);
        }
        return Availability.yes();
    }

    private record Availability(boolean available, String reason) {

        static Availability yes() {
            return new Availability(true, "");
        }

        static Availability no(String reason) {
            return new Availability(false, reason);
        }
    }

    private CartItem getOwnedItem(Long userId, Long itemId) {
        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getUserId(), userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "购物车条目不存在");
        }
        return item;
    }
}
