package com.anxineat.module.product.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.dto.CatalogDTO;
import com.anxineat.module.product.entity.Product;
import com.anxineat.module.product.entity.Category;
import com.anxineat.module.product.entity.ProductSku;
import com.anxineat.module.product.entity.SpecGroup;
import com.anxineat.module.product.entity.SpecOption;
import com.anxineat.module.product.entity.Topping;
import com.anxineat.module.product.mapper.CategoryMapper;
import com.anxineat.module.product.mapper.ProductMapper;
import com.anxineat.module.product.mapper.ProductSkuMapper;
import com.anxineat.module.product.mapper.SpecGroupMapper;
import com.anxineat.module.product.mapper.SpecOptionMapper;
import com.anxineat.module.product.mapper.ToppingMapper;
import com.anxineat.module.store.entity.StoreProduct;
import com.anxineat.module.store.mapper.StoreProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品服务：菜单聚合、商品详情、以及下单前的商品解析与校验。
 *
 * <p>「售罄归一」在这里落地——需求文档原设计有 product.sold_out、
 * product_sku.stock、product_sku.status 三个状态源却没有定义优先级，
 * 本项目定义为：手动售罄 / 门店停售 / 库存耗尽 三者任一成立即不可售。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final SpecGroupMapper specGroupMapper;
    private final SpecOptionMapper specOptionMapper;
    private final ToppingMapper toppingMapper;
    private final StoreProductMapper storeProductMapper;
    private final ObjectMapper objectMapper;

    // ==================== 菜单 ====================

    public CatalogDTO.MenuVO getMenu(Long storeId) {
        List<StoreProduct> storeProducts = storeProductMapper.selectByStore(storeId);
        if (storeProducts.isEmpty()) {
            return emptyMenu(storeId);
        }
        Map<Long, StoreProduct> spMap = storeProducts.stream()
                .collect(Collectors.toMap(StoreProduct::getProductId, sp -> sp, (a, b) -> a));

        List<Product> products = productMapper.selectBatchIds(spMap.keySet());
        List<Long> productIds = products.stream().map(Product::getId).toList();

        Map<Long, List<ProductSku>> skuMap = loadSkus(productIds);

        List<CatalogDTO.ProductVO> vos = new ArrayList<>();
        for (Product p : products) {
            StoreProduct sp = spMap.get(p.getId());
            List<ProductSku> skus = skuMap.getOrDefault(p.getId(), Collections.emptyList());
            CatalogDTO.ProductVO vo = toProductVO(p, sp, skus);
            vos.add(vo);
        }
        vos.sort(Comparator.comparing(CatalogDTO.ProductVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CatalogDTO.ProductVO::getId));

        CatalogDTO.MenuVO menu = new CatalogDTO.MenuVO();
        menu.setStoreId(storeId);
        menu.setProducts(vos);
        menu.setCategories(loadCategories());
        return menu;
    }

    private CatalogDTO.MenuVO emptyMenu(Long storeId) {
        CatalogDTO.MenuVO menu = new CatalogDTO.MenuVO();
        menu.setStoreId(storeId);
        menu.setProducts(Collections.emptyList());
        menu.setCategories(loadCategories());
        return menu;
    }

    private List<CatalogDTO.CategoryVO> loadCategories() {
        List<Category> list = categoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSort)
                        .orderByAsc(Category::getId));
        return list.stream().map(c -> {
            CatalogDTO.CategoryVO vo = new CatalogDTO.CategoryVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setIcon(c.getIcon());
            vo.setSort(c.getSort());
            return vo;
        }).toList();
    }

    private Map<Long, List<ProductSku>> loadSkus(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .in(ProductSku::getProductId, productIds)
                        .eq(ProductSku::getStatus, 1)
                        .orderByAsc(ProductSku::getPrice))
                .stream()
                .collect(Collectors.groupingBy(ProductSku::getProductId));
    }

    private CatalogDTO.ProductVO toProductVO(Product p, StoreProduct sp, List<ProductSku> skus) {
        long diff = sp == null || sp.getPriceDiff() == null ? 0L : sp.getPriceDiff();
        List<CatalogDTO.SkuVO> skuVos = skus.stream().map(s -> {
            CatalogDTO.SkuVO vo = new CatalogDTO.SkuVO();
            vo.setId(s.getId());
            vo.setCupSize(s.getCupSize());
            vo.setPrice(s.getPrice() + diff);
            vo.setStock(s.getStock());
            vo.setInStock(s.getStock() == null || s.getStock() == -1 || s.getStock() > 0);
            return vo;
        }).toList();

        Availability avail = computeAvailability(p, sp, skus);

        CatalogDTO.ProductVO vo = new CatalogDTO.ProductVO();
        vo.setId(p.getId());
        vo.setCategoryId(p.getCategoryId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setImage(p.getImage());
        vo.setBasePrice(p.getBasePrice());
        vo.setStorePrice(p.getBasePrice() + diff);
        vo.setCalories(p.getCalories());
        vo.setSales(p.getSales());
        vo.setSoldOut(!avail.available);
        vo.setAvailable(avail.available);
        vo.setSoldOutReason(avail.reason);
        vo.setSort(p.getSort());
        vo.setSkus(skuVos);
        return vo;
    }

    /**
     * 可售性判定（三个状态源的归一）。
     */
    private Availability computeAvailability(Product p, StoreProduct sp, List<ProductSku> skus) {
        if (sp == null || !Integer.valueOf(1).equals(sp.getStatus())) {
            return Availability.no("门店暂不售卖");
        }
        if (!Integer.valueOf(1).equals(p.getStatus())) {
            return Availability.no("已下架");
        }
        if (Integer.valueOf(1).equals(p.getSoldOut()) || Integer.valueOf(1).equals(sp.getSoldOut())) {
            return Availability.no("已售罄");
        }
        if (skus.isEmpty()) {
            return Availability.no("未配置规格");
        }
        boolean anyInStock = skus.stream().anyMatch(s -> s.getStock() == null || s.getStock() == -1 || s.getStock() > 0);
        if (!anyInStock) {
            return Availability.no("已售罄");
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

    // ==================== 商品详情 ====================

    public CatalogDTO.ProductDetailVO getDetail(Long productId, Long storeId) {
        Product p = productMapper.selectById(productId);
        if (p == null || !Integer.valueOf(1).equals(p.getStatus())) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        }
        StoreProduct sp = storeProductMapper.selectOneInStore(storeId, productId);
        long diff = sp == null || sp.getPriceDiff() == null ? 0L : sp.getPriceDiff();

        List<ProductSku> skus = skuMapper.selectByProduct(productId);
        Availability avail = computeAvailability(p, sp, skus);

        CatalogDTO.ProductDetailVO vo = new CatalogDTO.ProductDetailVO();
        vo.setId(p.getId());
        vo.setCategoryId(p.getCategoryId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setImage(p.getImage());
        vo.setStorePrice(p.getBasePrice() + diff);
        vo.setCalories(p.getCalories());
        vo.setSales(p.getSales());
        vo.setAvailable(avail.available());
        vo.setSoldOutReason(avail.reason());

        vo.setSkus(skus.stream().map(s -> {
            CatalogDTO.SkuVO skuVo = new CatalogDTO.SkuVO();
            skuVo.setId(s.getId());
            skuVo.setCupSize(s.getCupSize());
            skuVo.setPrice(s.getPrice() + diff);
            skuVo.setStock(s.getStock());
            skuVo.setInStock(s.getStock() == null || s.getStock() == -1 || s.getStock() > 0);
            return skuVo;
        }).toList());

        // 规格组 + 规格项
        List<SpecGroup> groups = specGroupMapper.selectByProduct(productId);
        List<CatalogDTO.SpecGroupVO> groupVos = new ArrayList<>();
        for (SpecGroup g : groups) {
            CatalogDTO.SpecGroupVO gvo = new CatalogDTO.SpecGroupVO();
            gvo.setId(g.getId());
            gvo.setCode(g.getCode());
            gvo.setName(g.getName());
            gvo.setIsRequired(g.getIsRequired());
            gvo.setSort(g.getSort());
            gvo.setOptions(specOptionMapper.selectByGroup(g.getId()).stream().map(o -> {
                CatalogDTO.SpecOptionVO ovo = new CatalogDTO.SpecOptionVO();
                ovo.setId(o.getId());
                ovo.setLabel(o.getLabel());
                ovo.setExtraPrice(o.getExtraPrice());
                ovo.setIsDefault(o.getIsDefault());
                ovo.setSort(o.getSort());
                return ovo;
            }).toList());
            groupVos.add(gvo);
        }
        vo.setSpecGroups(groupVos);

        vo.setToppings(toppingMapper.selectByProduct(productId).stream().map(t -> {
            CatalogDTO.ToppingVO tvo = new CatalogDTO.ToppingVO();
            tvo.setId(t.getId());
            tvo.setName(t.getName());
            tvo.setPrice(t.getPrice());
            tvo.setMaxCount(t.getMaxCount());
            tvo.setStock(t.getStock());
            return tvo;
        }).toList());

        return vo;
    }

    // ==================== 下单前的商品解析 ====================

    /** 前端提交的单个条目 */
    @Data
    public static class ItemRequest {
        private Long productId;
        private Long skuId;
        private Integer quantity;
        private List<Long> specOptionIds;
        private List<ToppingRequest> toppings;
        /** 秒杀下单时携带的秒杀明细 id（可选）；非空表示本条目走秒杀价与独立库存 */
        private Long flashSaleId;
    }

    @Data
    public static class ToppingRequest {
        private Long toppingId;
        private Integer count;
    }

    /** 解析并校验后的条目，含服务端重算的价格 */
    @Data
    public static class ResolvedItem {
        private Long productId;
        private String productName;
        private String productImage;
        /** 商品所属分类，优惠券按分类限定时用于范围匹配 */
        private Long categoryId;
        private Long skuId;
        private String skuName;
        private Integer quantity;
        /** SKU 价 + 门店加价 */
        private Long basePrice;
        private Long specExtra;
        private Long toppingAmount;
        /** basePrice + specExtra + toppingAmount */
        private Long unitPrice;
        private Long totalPrice;
        private String specSnapshot;
        private String toppingSnapshot;
        private String specText;
        private String toppingText;
        private List<ResolvedTopping> resolvedToppings;
        /** 是否命中秒杀（FLASH_SALE） */
        private boolean flash;
        /** 命中的秒杀明细 id */
        private Long flashSaleId;
        /** 来源活动 id */
        private Long activityId;
    }

    @Data
    public static class ResolvedTopping {
        private Long toppingId;
        private String name;
        private Long price;
        private Integer count;
    }

    /**
     * 解析并校验购物车/订单条目。
     *
     * <p>这是「服务端重算金额，不信任前端」的落地点：前端传来的只有
     * productId / skuId / 规格选项 / 小料，所有价格一律由服务端查库计算。
     */
    public List<ResolvedItem> resolveItems(Long storeId, List<ItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException(ResultCode.CART_EMPTY);
        }
        List<ResolvedItem> result = new ArrayList<>();

        for (ItemRequest req : requests) {
            if (req.getProductId() == null || req.getSkuId() == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "商品与规格不能为空");
            }
            int qty = req.getQuantity() == null || req.getQuantity() <= 0 ? 1 : req.getQuantity();
            if (qty > 99) {
                throw new BizException(ResultCode.PARAM_ERROR, "单个商品数量不能超过 99");
            }

            Product p = productMapper.selectById(req.getProductId());
            if (p == null || !Integer.valueOf(1).equals(p.getStatus())) {
                throw new BizException(ResultCode.PRODUCT_OFF_SHELF, "「" + nameOf(p) + "」已下架");
            }
            StoreProduct sp = storeProductMapper.selectOneInStore(storeId, p.getId());
            if (sp == null || !Integer.valueOf(1).equals(sp.getStatus())) {
                throw new BizException(ResultCode.PRODUCT_NOT_IN_STORE, "「" + p.getName() + "」在该门店暂不售卖");
            }
            if (Integer.valueOf(1).equals(p.getSoldOut()) || Integer.valueOf(1).equals(sp.getSoldOut())) {
                throw new BizException(ResultCode.PRODUCT_SOLD_OUT, "「" + p.getName() + "」已售罄");
            }

            ProductSku sku = skuMapper.selectById(req.getSkuId());
            if (sku == null || !Objects.equals(sku.getProductId(), p.getId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                throw new BizException(ResultCode.SKU_NOT_FOUND, "「" + p.getName() + "」规格不存在");
            }
            if (sku.getStock() != null && sku.getStock() != -1 && sku.getStock() < qty) {
                throw new BizException(ResultCode.STOCK_NOT_ENOUGH, "「" + p.getName() + "」库存不足");
            }

            long diff = sp.getPriceDiff() == null ? 0L : sp.getPriceDiff();
            long basePrice = sku.getPrice() + diff;

            // ---- 规格 ----
            List<SpecGroup> groups = specGroupMapper.selectByProduct(p.getId());
            Map<Long, SpecOption> selected = new LinkedHashMap<>();
            if (req.getSpecOptionIds() != null) {
                for (Long optId : req.getSpecOptionIds()) {
                    SpecOption opt = specOptionMapper.selectById(optId);
                    if (opt == null || !Integer.valueOf(1).equals(opt.getStatus())) {
                        throw new BizException(ResultCode.PARAM_ERROR, "规格选项不存在");
                    }
                    selected.put(opt.getGroupId(), opt);
                }
            }

            long specExtra = 0L;
            StringBuilder specText = new StringBuilder();
            List<Map<String, Object>> specSnapshotList = new ArrayList<>();

            for (SpecGroup g : groups) {
                SpecOption opt = selected.get(g.getId());
                if (opt == null) {
                    if (Integer.valueOf(1).equals(g.getIsRequired())) {
                        throw new BizException(ResultCode.SPEC_REQUIRED, "请选择「" + g.getName() + "」");
                    }
                    continue;
                }
                if (!Objects.equals(opt.getGroupId(), g.getId())) {
                    throw new BizException(ResultCode.PARAM_ERROR, "规格选项与规格组不匹配");
                }
                specExtra += opt.getExtraPrice() == null ? 0L : opt.getExtraPrice();

                // CUP 为库存维度组：选项 label 必须与所选 SKU 的杯型一致，
                // 否则会出现「选了大杯但按中杯扣库存」的不一致。
                if ("CUP".equals(g.getCode()) && !Objects.equals(opt.getLabel(), sku.getCupSize())) {
                    throw new BizException(ResultCode.PARAM_ERROR,
                            "「" + p.getName() + "」所选杯型与规格不一致");
                }

                if (specText.length() > 0) {
                    specText.append(" / ");
                }
                specText.append(opt.getLabel());

                Map<String, Object> snap = new LinkedHashMap<>();
                snap.put("groupId", g.getId());
                snap.put("groupName", g.getName());
                snap.put("optionId", opt.getId());
                snap.put("label", opt.getLabel());
                snap.put("extraPrice", opt.getExtraPrice());
                specSnapshotList.add(snap);
            }

            // ---- 小料 ----
            long toppingAmount = 0L;
            StringBuilder toppingText = new StringBuilder();
            List<ResolvedTopping> resolvedToppings = new ArrayList<>();
            List<Map<String, Object>> toppingSnapshotList = new ArrayList<>();

            if (req.getToppings() != null) {
                for (ToppingRequest tr : req.getToppings()) {
                    if (tr.getToppingId() == null || tr.getCount() == null || tr.getCount() <= 0) {
                        continue;
                    }
                    Topping t = toppingMapper.selectById(tr.getToppingId());
                    if (t == null || !Objects.equals(t.getProductId(), p.getId()) || !Integer.valueOf(1).equals(t.getStatus())) {
                        throw new BizException(ResultCode.PARAM_ERROR, "小料不存在");
                    }
                    int max = t.getMaxCount() == null ? 1 : t.getMaxCount();
                    if (tr.getCount() > max) {
                        throw new BizException(ResultCode.PARAM_ERROR,
                                "「" + t.getName() + "」最多加 " + max + " 份");
                    }
                    if (t.getStock() != null && t.getStock() != -1 && t.getStock() < tr.getCount()) {
                        throw new BizException(ResultCode.STOCK_NOT_ENOUGH, "「" + t.getName() + "」库存不足");
                    }
                    toppingAmount += (t.getPrice() == null ? 0L : t.getPrice()) * tr.getCount();

                    if (toppingText.length() > 0) {
                        toppingText.append(" / ");
                    }
                    toppingText.append(t.getName()).append(" x").append(tr.getCount());

                    ResolvedTopping rt = new ResolvedTopping();
                    rt.setToppingId(t.getId());
                    rt.setName(t.getName());
                    rt.setPrice(t.getPrice());
                    rt.setCount(tr.getCount());
                    resolvedToppings.add(rt);

                    Map<String, Object> snap = new LinkedHashMap<>();
                    snap.put("toppingId", t.getId());
                    snap.put("name", t.getName());
                    snap.put("price", t.getPrice());
                    snap.put("count", tr.getCount());
                    toppingSnapshotList.add(snap);
                }
            }

            ResolvedItem item = new ResolvedItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductImage(p.getImage());
            item.setCategoryId(p.getCategoryId());
            item.setSkuId(sku.getId());
            item.setSkuName(sku.getCupSize());
            item.setQuantity(qty);
            item.setBasePrice(basePrice);
            item.setSpecExtra(specExtra);
            item.setToppingAmount(toppingAmount);
            item.setUnitPrice(basePrice + specExtra + toppingAmount);
            item.setTotalPrice(item.getUnitPrice() * qty);
            item.setSpecSnapshot(toJson(specSnapshotList));
            item.setToppingSnapshot(toJson(toppingSnapshotList));
            item.setSpecText(specText.toString());
            item.setToppingText(toppingText.toString());
            item.setResolvedToppings(resolvedToppings);
            result.add(item);
        }
        return result;
    }

    /**
     * 购物车条目指纹：同规格合并、异规格分立。
     */
    public String buildItemKey(Long productId, Long skuId, List<Long> specOptionIds,
                               List<ToppingRequest> toppings) {
        StringBuilder sb = new StringBuilder();
        sb.append(productId).append(':').append(skuId).append(":s[");
        if (specOptionIds != null) {
            sb.append(specOptionIds.stream().filter(Objects::nonNull).sorted().map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
        sb.append("]:t[");
        if (toppings != null) {
            sb.append(toppings.stream()
                    .filter(t -> t != null && t.getToppingId() != null && t.getCount() != null && t.getCount() > 0)
                    .map(t -> t.getToppingId() + "*" + t.getCount())
                    .sorted()
                    .collect(Collectors.joining(",")));
        }
        sb.append(']');
        return sb.toString();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("序列化快照失败", e);
            return "[]";
        }
    }

    private static String nameOf(Product p) {
        return p == null ? "该商品" : p.getName();
    }
}
