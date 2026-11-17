package com.aierp.erp.serviceImpl;

import com.aierp.common.ResultVo;
import com.aierp.enums.EnumShopType;
import com.aierp.model.entity.OShop;
import com.aierp.model.entity.OShopPullLogs;
import com.aierp.model.entity.ShopGoods;
import com.aierp.model.entity.ShopGoodsSku;
import com.aierp.model.entity.ShopOrder;
import com.aierp.model.entity.ShopOrderItem;
import com.aierp.model.entity.ShopRefund;
import com.aierp.open.amazon.AmazonSpApiClient;
import com.aierp.open.shopify.ShopifyAdminClient;
import com.aierp.service.OShopPullLogsService;
import com.aierp.service.ShopGoodsService;
import com.aierp.service.ShopOrderService;
import com.aierp.service.ShopRefundService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 Amazon、Shopify 的平台响应写入统一的店铺订单和商品表。
 * 认不出订单结构时返回错误，不会记成拉取成功。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossBorderPullService {

    private final ShopOrderService shopOrderService;
    private final ShopGoodsService shopGoodsService;
    private final ShopRefundService shopRefundService;
    private final ShopSyncService shopSyncService;
    private final OShopPullLogsService pullLogsService;

    public ResultVo<String> pullOrders(OShop shop, String startTime, String endTime, long begin) {
        try {
            JSONObject payload = shop.getType() == EnumShopType.SHOPIFY.getIndex()
                    ? shopify(shop).orders(toIso(startTime), null, 50)
                    : amazon(shop).searchOrders(marketplace(shop), toIso(startTime), null, 50);
            List<JSONObject> orders = shop.getType() == EnumShopType.SHOPIFY.getIndex()
                    ? shopifyOrders(payload) : amazonOrders(payload);
            if (orders.isEmpty()) {
                return fail(shop, "ORDER", "平台没有返回可识别的订单，未写入。请先用跨境连通性检查核对凭证和站点。", begin);
            }
            int ins = 0;
            int upd = 0;
            int fail = 0;
            for (JSONObject node : orders) {
                try {
                    ShopOrder order = shop.getType() == EnumShopType.SHOPIFY.getIndex()
                            ? shopifyOrder(node) : amazonOrder(node);
                    if (!StringUtils.hasText(order.getOrderId())) {
                        fail++;
                        continue;
                    }
                    var saved = shopOrderService.saveOrder(shop.getId(), shop.getMerchantId(), shop.getType(), order);
                    if (saved.getData() != null && saved.getData() > 0) {
                        shopSyncService.syncOrder(saved.getData());
                    }
                    if (saved.getCode() == 0) ins++;
                    else upd++;
                    if (isRefunded(node, shop.getType())) {
                        shopRefundService.saveRefund(shop.getId(), refundOf(shop, order));
                    }
                } catch (Exception ex) {
                    log.warn("跨境订单保存失败 shopId={}: {}", shop.getId(), ex.getMessage());
                    fail++;
                }
            }
            log(shop, "ORDER", "{start:" + startTime + "}", "{ins:" + ins + ",upd:" + upd + ",fail:" + fail + "}", begin);
            return ResultVo.success("成功，新增" + ins + "，更新" + upd + "，失败" + fail);
        } catch (Exception ex) {
            return fail(shop, "ORDER", ex.getMessage(), begin);
        }
    }

    public ResultVo<String> pullOrderDetail(OShop shop, String orderId, long begin) {
        try {
            if (shop.getType() == EnumShopType.SHOPIFY.getIndex()) {
                return pullOrders(shop, LocalDateTime.now().minusDays(7).toLocalDate().toString(), null, begin);
            }
            JSONObject payload = amazon(shop).getOrder(orderId);
            JSONObject node = firstObject(payload);
            if (node == null) return fail(shop, "ORDER", "没有找到订单 " + orderId, begin);
            ShopOrder order = amazonOrder(node);
            order.setOrderId(orderId);
            var saved = shopOrderService.saveOrder(shop.getId(), shop.getMerchantId(), shop.getType(), order);
            if (saved.getData() != null && saved.getData() > 0) shopSyncService.syncOrder(saved.getData());
            log(shop, "ORDER", "{orderId:" + orderId + "}", "{}", begin);
            return ResultVo.success("订单[" + orderId + "]同步完成");
        } catch (Exception ex) {
            return fail(shop, "ORDER", ex.getMessage(), begin);
        }
    }

    public ResultVo<String> pullRefunds(OShop shop, long begin) {
        String start = LocalDateTime.now().minusDays(7).toLocalDate().toString();
        ResultVo<String> orders = pullOrders(shop, start, null, begin);
        if (orders.getCode() != 0) return orders;
        return ResultVo.success("已按近 7 天订单同步退款状态。" + orders.getMsg());
    }

    public ResultVo<String> pullGoods(OShop shop, long begin) {
        try {
            JSONObject payload = shop.getType() == EnumShopType.SHOPIFY.getIndex()
                    ? shopify(shop).products(null, 50)
                    : amazon(shop).searchListings(require(shop.getSellerId(), "Amazon sellerId"), marketplace(shop), null, 20);
            List<JSONObject> products = shop.getType() == EnumShopType.SHOPIFY.getIndex()
                    ? edges(payload, "products") : listings(payload);
            if (products.isEmpty()) {
                return fail(shop, "GOODS", "平台没有返回可识别的商品，未写入。", begin);
            }
            int ok = 0;
            int fail = 0;
            for (JSONObject node : products) {
                try {
                    ShopGoods goods = shop.getType() == EnumShopType.SHOPIFY.getIndex()
                            ? shopifyGoods(node) : amazonGoods(node);
                    if (!StringUtils.hasText(goods.getProductId())) {
                        fail++;
                        continue;
                    }
                    shopGoodsService.saveOrUpdateGoods(shop.getId(), shop.getMerchantId(), shop.getType(), goods);
                    ok++;
                } catch (Exception ex) {
                    fail++;
                }
            }
            log(shop, "GOODS", "{}", "{ok:" + ok + ",fail:" + fail + "}", begin);
            return ResultVo.success("商品同步完成，成功" + ok + "，失败" + fail);
        } catch (Exception ex) {
            return fail(shop, "GOODS", ex.getMessage(), begin);
        }
    }

    private ShopOrder shopifyOrder(JSONObject node) {
        ShopOrder order = baseOrder("SHOPIFY", gidTail(node.getString("id")), node.getString("createdAt"), node.getString("updatedAt"));
        order.setOrderStatus(shopifyStatus(node.getString("displayFulfillmentStatus"), node.getString("displayFinancialStatus")));
        JSONObject money = dig(node, "totalPriceSet", "shopMoney");
        if (money != null) order.setOrderAmount(fen(money.getString("amount")));
        JSONObject address = node.getJSONObject("shippingAddress");
        if (address != null) {
            order.setReceiverName(address.getString("name"));
            order.setReceiverPhone(address.getString("phone"));
            order.setProvince(address.getString("province"));
            order.setCity(address.getString("city"));
            order.setAddress(address.getString("address1"));
        }
        List<ShopOrderItem> items = new ArrayList<>();
        JSONObject lineItems = node.getJSONObject("lineItems");
        JSONArray nodes = lineItems == null ? null : lineItems.getJSONArray("nodes");
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject line = nodes.getJSONObject(i);
                ShopOrderItem item = new ShopOrderItem();
                item.setOrderId(order.getOrderId());
                item.setProductId(gidTail(digString(line, "product", "id")));
                item.setSkuId(firstText(line, "sku", digString(line, "variant", "sku")));
                item.setTitle(firstText(line, "title", "name"));
                item.setQuantity(line.getInteger("quantity"));
                JSONObject price = dig(line, "originalUnitPriceSet", "shopMoney");
                if (price != null) item.setSalePrice(fen(price.getString("amount")));
                item.setOrderTime(order.getOrderTime());
                items.add(item);
            }
        }
        order.setItems(items);
        return order;
    }

    private ShopOrder amazonOrder(JSONObject node) {
        String orderId = firstText(node, "AmazonOrderId", "amazonOrderId", "orderId", "id");
        ShopOrder order = baseOrder("AMAZON", orderId, firstText(node, "PurchaseDate", "purchaseDate", "createdAt"),
                firstText(node, "LastUpdateDate", "lastUpdateDate", "updatedAt"));
        order.setOrderStatus(amazonStatus(firstText(node, "OrderStatus", "orderStatus", "status")));
        String amount = firstText(node, "Amount", "amount");
        JSONObject total = node.getJSONObject("OrderTotal");
        if (total == null) total = node.getJSONObject("orderTotal");
        if (total != null && total.getString("Amount") != null) amount = total.getString("Amount");
        if (amount != null) order.setOrderAmount(fen(amount));
        return order;
    }

    private ShopGoods shopifyGoods(JSONObject node) {
        ShopGoods goods = new ShopGoods();
        goods.setProductId(gidTail(node.getString("id")));
        goods.setTitle(node.getString("title"));
        goods.setOuterProductId(node.getString("handle"));
        JSONObject media = dig(node, "featuredMedia", "preview", "image");
        if (media != null) goods.setImg(media.getString("url"));
        List<ShopGoodsSku> skus = new ArrayList<>();
        JSONObject variants = node.getJSONObject("variants");
        JSONArray nodes = variants == null ? null : variants.getJSONArray("nodes");
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject variant = nodes.getJSONObject(i);
                ShopGoodsSku sku = new ShopGoodsSku();
                sku.setSkuId(gidTail(variant.getString("id")));
                sku.setSkuName(variant.getString("title"));
                sku.setOuterSkuId(variant.getString("sku"));
                sku.setStockNum(variant.getInteger("inventoryQuantity"));
                skus.add(sku);
            }
        }
        goods.setSkuList(skus);
        return goods;
    }

    private ShopGoods amazonGoods(JSONObject node) {
        ShopGoods goods = new ShopGoods();
        goods.setProductId(firstText(node, "sku", "sellerSku", "asin", "id"));
        goods.setTitle(firstText(node, "itemName", "title", "name"));
        JSONArray summaries = node.getJSONArray("summaries");
        if (summaries != null && !summaries.isEmpty()) {
            JSONObject summary = summaries.getJSONObject(0);
            if (!StringUtils.hasText(goods.getTitle())) goods.setTitle(summary.getString("itemName"));
            if (!StringUtils.hasText(goods.getProductId())) goods.setProductId(summary.getString("asin"));
        }
        return goods;
    }

    private ShopRefund refundOf(OShop shop, ShopOrder order) {
        ShopRefund refund = new ShopRefund();
        refund.setShopId(shop.getId());
        refund.setMerchantId(shop.getMerchantId());
        refund.setShopType(shop.getType());
        refund.setAfterId(order.getOrderId() + "-refund");
        refund.setOrderId(order.getOrderId());
        refund.setStatus(4);
        refund.setPlatformType(order.getPlatformType());
        return refund;
    }

    private static ShopOrder baseOrder(String platform, String orderId, String created, String updated) {
        ShopOrder order = new ShopOrder();
        order.setPlatformType(platform);
        order.setOrderId(orderId);
        order.setOrderTime(epoch(created));
        order.setUpdateTime(epoch(updated));
        order.setOrderType(0);
        order.setOrderMode(0);
        order.setErpShipStatus(0);
        order.setConfirmStatus(0);
        order.setItems(new ArrayList<>());
        return order;
    }

    private static int shopifyStatus(String fulfillment, String financial) {
        if ("REFUNDED".equals(financial) || "VOIDED".equals(financial)) return 12;
        if ("FULFILLED".equals(fulfillment)) return 2;
        if ("PARTIALLY_FULFILLED".equals(fulfillment)) return 101;
        if ("PENDING".equals(financial)) return 21;
        return 1;
    }

    private static int amazonStatus(String status) {
        if (status == null) return 1;
        return switch (status) {
            case "Pending", "PendingAvailability" -> 21;
            case "Unshipped", "PartiallyShipped" -> "PartiallyShipped".equals(status) ? 101 : 1;
            case "Shipped" -> 2;
            case "Canceled", "Cancelled" -> 11;
            case "InvoiceUnconfirmed" -> 3;
            default -> 1;
        };
    }

    private static boolean isRefunded(JSONObject node, Integer shopType) {
        if (shopType == EnumShopType.SHOPIFY.getIndex()) {
            String financial = node.getString("displayFinancialStatus");
            return "REFUNDED".equals(financial) || "PARTIALLY_REFUNDED".equals(financial);
        }
        String status = firstText(node, "OrderStatus", "orderStatus", "status");
        return "Canceled".equals(status) || "Cancelled".equals(status);
    }

    private static List<JSONObject> shopifyOrders(JSONObject payload) {
        return edges(payload, "orders");
    }

    private static List<JSONObject> edges(JSONObject payload, String field) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject data = payload == null ? null : payload.getJSONObject("data");
        JSONObject connection = data == null ? null : data.getJSONObject(field);
        JSONArray edges = connection == null ? null : connection.getJSONArray("edges");
        if (edges == null) return list;
        for (int i = 0; i < edges.size(); i++) {
            JSONObject node = edges.getJSONObject(i).getJSONObject("node");
            if (node != null) list.add(node);
        }
        return list;
    }

    private static List<JSONObject> amazonOrders(JSONObject payload) {
        List<JSONObject> found = new ArrayList<>();
        collectOrders(payload, found);
        return found;
    }

    private static List<JSONObject> listings(JSONObject payload) {
        List<JSONObject> found = new ArrayList<>();
        JSONArray items = payload == null ? null : payload.getJSONArray("items");
        if (items == null && payload != null && payload.getJSONObject("payload") != null) {
            items = payload.getJSONObject("payload").getJSONArray("items");
        }
        if (items == null) return found;
        for (int i = 0; i < items.size(); i++) found.add(items.getJSONObject(i));
        return found;
    }

    private static void collectOrders(Object node, List<JSONObject> found) {
        if (node instanceof JSONObject object) {
            if (StringUtils.hasText(firstText(object, "AmazonOrderId", "amazonOrderId"))) {
                found.add(object);
                return;
            }
            for (String key : object.keySet()) collectOrders(object.get(key), found);
        } else if (node instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) collectOrders(array.get(i), found);
        }
    }

    private static JSONObject firstObject(JSONObject payload) {
        List<JSONObject> orders = amazonOrders(payload);
        return orders.isEmpty() ? payload : orders.get(0);
    }

    private AmazonSpApiClient amazon(OShop shop) {
        return new AmazonSpApiClient(shop.getApiRequestUrl(), shop.getAppKey(), shop.getAppSecret(), shop.getRefreshToken());
    }

    private ShopifyAdminClient shopify(OShop shop) {
        String version = StringUtils.hasText(shop.getApiRequestUrl()) && shop.getApiRequestUrl().matches("20\\d{2}-(01|04|07|10)")
                ? shop.getApiRequestUrl() : ShopifyAdminClient.DEFAULT_API_VERSION;
        return new ShopifyAdminClient(shop.getUrl(), shop.getAccessToken(), version);
    }

    private static String marketplace(OShop shop) {
        return require(shop.getSellerNum(), "Amazon marketplaceId（保存到 sellerNum）");
    }

    private static String require(String value, String name) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(name + "不能为空");
        return value.trim();
    }

    private static String toIso(String day) {
        if (!StringUtils.hasText(day)) return Instant.now().minusSeconds(86400).toString();
        return day.length() == 10 ? day + "T00:00:00Z" : day;
    }

    private static Long epoch(String text) {
        if (!StringUtils.hasText(text)) return null;
        try {
            return OffsetDateTime.parse(text).toEpochSecond();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(text.replace(" ", "T")).atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond();
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static Integer fen(String amount) {
        if (!StringUtils.hasText(amount)) return 0;
        return new BigDecimal(amount).movePointRight(2).intValue();
    }

    private static String gidTail(String gid) {
        if (!StringUtils.hasText(gid)) return null;
        int slash = gid.lastIndexOf('/');
        return slash >= 0 ? gid.substring(slash + 1) : gid;
    }

    private static JSONObject dig(JSONObject node, String... path) {
        JSONObject current = node;
        for (String key : path) {
            if (current == null) return null;
            current = current.getJSONObject(key);
        }
        return current;
    }

    private static String digString(JSONObject node, String objectKey, String field) {
        JSONObject child = node.getJSONObject(objectKey);
        return child == null ? null : child.getString(field);
    }

    private static String firstText(JSONObject node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            String value = node.getString(key);
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    private ResultVo<String> fail(OShop shop, String type, String message, long begin) {
        log(shop, type, "{}", "error:" + message, begin);
        return ResultVo.error(message);
    }

    private void log(OShop shop, String type, String params, String result, long begin) {
        try {
            OShopPullLogs row = new OShopPullLogs();
            row.setShopId(shop.getId());
            row.setShopType(shop.getType());
            row.setPullType(type);
            row.setPullParams(params);
            row.setPullResult(result);
            row.setPullWay("manual");
            row.setDuration(System.currentTimeMillis() - begin);
            pullLogsService.save(row);
        } catch (Exception ex) {
            log.warn("写入拉取日志失败: {}", ex.getMessage());
        }
    }
}
