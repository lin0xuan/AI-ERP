package com.aierp.erp.controller.oms;

import com.aierp.common.AjaxResult;
import com.aierp.enums.EnumShopType;
import com.aierp.model.entity.OShop;
import com.aierp.open.amazon.AmazonSpApiClient;
import com.aierp.open.shopify.ShopifyAdminClient;
import com.aierp.security.common.BaseController;
import com.aierp.service.OShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Amazon SP-API and Shopify Admin API entry points.
 *
 * Credentials are read from the existing OShop record and are never returned
 * by these endpoints.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/oms-api/cross-border")
public class CrossBorderPlatformController extends BaseController {
    private final OShopService shopService;

    @GetMapping("/{shopId}/connection")
    public AjaxResult connection(@PathVariable Long shopId) {
        try {
            OShop shop = requiredShop(shopId);
            if (shop.getType() == EnumShopType.AMAZON.getIndex()) {
                return success(amazon(shop).marketplaceParticipations());
            }
            if (shop.getType() == EnumShopType.SHOPIFY.getIndex()) {
                return success(shopify(shop).shop());
            }
            return error("该店铺不是 Amazon 或 Shopify 店铺");
        } catch (Exception e) {
            log.warn("跨境店铺连通性检查失败，shopId={}: {}", shopId, e.getMessage());
            return error(e.getMessage());
        }
    }

    @GetMapping("/amazon/{shopId}/orders")
    public AjaxResult amazonOrders(@PathVariable Long shopId,
                                   @RequestParam(required = false) String updatedAfter,
                                   @RequestParam(required = false) String paginationToken,
                                   @RequestParam(required = false) Integer pageSize) {
        try {
            OShop shop = requiredShop(shopId, EnumShopType.AMAZON);
            String since = StringUtils.hasText(updatedAfter) ? updatedAfter : Instant.now().minusSeconds(86400).toString();
            return success(amazon(shop).searchOrders(marketplaceId(shop), since, paginationToken, pageSize));
        } catch (Exception e) {
            log.warn("Amazon订单拉取失败，shopId={}: {}", shopId, e.getMessage());
            return error(e.getMessage());
        }
    }

    @GetMapping("/amazon/{shopId}/orders/{orderId}")
    public AjaxResult amazonOrder(@PathVariable Long shopId, @PathVariable String orderId) {
        try {
            return success(amazon(requiredShop(shopId, EnumShopType.AMAZON)).getOrder(orderId));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @GetMapping("/amazon/{shopId}/products")
    public AjaxResult amazonProducts(@PathVariable Long shopId,
                                     @RequestParam(required = false) String pageToken,
                                     @RequestParam(required = false) Integer pageSize) {
        try {
            OShop shop = requiredShop(shopId, EnumShopType.AMAZON);
            return success(amazon(shop).searchListings(require(shop.getSellerId(), "Amazon sellerId"),
                    marketplaceId(shop), pageToken, pageSize));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/amazon/{shopId}/inventory")
    public AjaxResult amazonInventory(@PathVariable Long shopId, @RequestBody AmazonInventoryRequest request) {
        try {
            OShop shop = requiredShop(shopId, EnumShopType.AMAZON);
            return success(amazon(shop).updateInventory(require(shop.getSellerId(), "Amazon sellerId"),
                    marketplaceId(shop), request.sellerSku(), request.quantity()));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/amazon/{shopId}/shipment-status")
    public AjaxResult amazonShipment(@PathVariable Long shopId, @RequestBody AmazonShipmentRequest request) {
        try {
            OShop shop = requiredShop(shopId, EnumShopType.AMAZON);
            return success(amazon(shop).updateShipmentStatus(request.orderId(), marketplaceId(shop), request.status()));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @GetMapping("/shopify/{shopId}/orders")
    public AjaxResult shopifyOrders(@PathVariable Long shopId,
                                    @RequestParam(required = false) String updatedAt,
                                    @RequestParam(required = false) String cursor,
                                    @RequestParam(required = false) Integer pageSize) {
        try {
            return success(shopify(requiredShop(shopId, EnumShopType.SHOPIFY)).orders(updatedAt, cursor, pageSize));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @GetMapping("/shopify/{shopId}/products")
    public AjaxResult shopifyProducts(@PathVariable Long shopId,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer pageSize) {
        try {
            return success(shopify(requiredShop(shopId, EnumShopType.SHOPIFY)).products(cursor, pageSize));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/shopify/{shopId}/inventory")
    public AjaxResult shopifyInventory(@PathVariable Long shopId, @RequestBody ShopifyInventoryRequest request) {
        try {
            return success(shopify(requiredShop(shopId, EnumShopType.SHOPIFY)).setInventory(
                    request.inventoryItemId(), request.locationId(), request.quantity(),
                    request.compareQuantity(), request.referenceUri()));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @GetMapping("/shopify/{shopId}/orders/{orderId}/fulfillment-orders")
    public AjaxResult shopifyFulfillmentOrders(@PathVariable Long shopId, @PathVariable String orderId) {
        try {
            return success(shopify(requiredShop(shopId, EnumShopType.SHOPIFY)).fulfillmentOrders(orderId));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/shopify/{shopId}/fulfillments")
    public AjaxResult shopifyFulfillment(@PathVariable Long shopId, @RequestBody ShopifyFulfillmentRequest request) {
        try {
            return success(shopify(requiredShop(shopId, EnumShopType.SHOPIFY)).createFulfillment(
                    request.fulfillmentOrderId(), request.company(), request.trackingNumber(),
                    request.trackingUrl(), request.notifyCustomer()));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private AmazonSpApiClient amazon(OShop shop) {
        return new AmazonSpApiClient(shop.getApiRequestUrl(), shop.getAppKey(), shop.getAppSecret(), shop.getRefreshToken());
    }

    private ShopifyAdminClient shopify(OShop shop) {
        String version = StringUtils.hasText(shop.getApiRequestUrl()) && shop.getApiRequestUrl().matches("20\\d{2}-(01|04|07|10)")
                ? shop.getApiRequestUrl() : ShopifyAdminClient.DEFAULT_API_VERSION;
        return new ShopifyAdminClient(shop.getUrl(), shop.getAccessToken(), version);
    }

    private OShop requiredShop(Long shopId) {
        OShop shop = shopService.getById(shopId);
        if (shop == null) throw new IllegalArgumentException("店铺不存在");
        return shop;
    }

    private OShop requiredShop(Long shopId, EnumShopType type) {
        OShop shop = requiredShop(shopId);
        if (shop.getType() == null || shop.getType() != type.getIndex()) {
            throw new IllegalArgumentException("店铺类型不是" + type.getName());
        }
        return shop;
    }

    private String marketplaceId(OShop shop) {
        return require(shop.getSellerNum(), "Amazon marketplaceId（保存到sellerNum字段）");
    }

    private static String require(String value, String name) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(name + "不能为空");
        return value.trim();
    }

    public record AmazonInventoryRequest(String sellerSku, int quantity) {}
    public record AmazonShipmentRequest(String orderId, String status) {}
    public record ShopifyInventoryRequest(String inventoryItemId, String locationId, int quantity,
                                           Integer compareQuantity, String referenceUri) {}
    public record ShopifyFulfillmentRequest(String fulfillmentOrderId, String company, String trackingNumber,
                                            String trackingUrl, boolean notifyCustomer) {}
}
