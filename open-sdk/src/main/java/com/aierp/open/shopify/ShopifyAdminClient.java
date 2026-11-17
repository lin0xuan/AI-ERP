package com.aierp.open.shopify;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Shopify GraphQL Admin API client. */
public class ShopifyAdminClient {
    public static final String DEFAULT_API_VERSION = "2026-07";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final Pattern SHOP_DOMAIN = Pattern.compile("^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$");
    private static final Pattern API_VERSION = Pattern.compile("^20\\d{2}-(01|04|07|10)$");

    private final String shopDomain;
    private final String accessToken;
    private final String apiVersion;

    public ShopifyAdminClient(String shopDomain, String accessToken, String apiVersion) {
        this.shopDomain = normalizeShopDomain(shopDomain);
        if (accessToken == null || accessToken.isBlank()) throw new IllegalArgumentException("Shopify accessToken不能为空");
        this.accessToken = accessToken.trim();
        this.apiVersion = apiVersion == null || apiVersion.isBlank() ? DEFAULT_API_VERSION : apiVersion.trim();
        if (!API_VERSION.matcher(this.apiVersion).matches()) throw new IllegalArgumentException("Shopify API版本格式错误");
    }

    public JSONObject shop() throws IOException {
        return graphql("query Shop { shop { id name myshopifyDomain currencyCode } }", new JSONObject());
    }

    public JSONObject orders(String updatedAt, String cursor, Integer first) throws IOException {
        String query = "query Orders($first:Int!,$after:String,$query:String){orders(first:$first,after:$after,query:$query,sortKey:UPDATED_AT){edges{cursor node{id name createdAt updatedAt displayFinancialStatus displayFulfillmentStatus email currencyCode totalPriceSet{shopMoney{amount currencyCode}} subtotalPriceSet{shopMoney{amount currencyCode}} totalShippingPriceSet{shopMoney{amount currencyCode}} shippingAddress{name phone address1 address2 city province provinceCode zip country countryCodeV2} lineItems(first:100){nodes{id name title sku quantity originalUnitPriceSet{shopMoney{amount currencyCode}} variant{id sku image{url}} product{id}}}}}pageInfo{hasNextPage endCursor}}}";
        JSONObject vars = new JSONObject();
        vars.put("first", first == null ? 50 : Math.min(Math.max(first, 1), 100));
        vars.put("after", blankToNull(cursor));
        vars.put("query", updatedAt == null || updatedAt.isBlank() ? null : "updated_at:>='" + updatedAt + "'");
        return graphql(query, vars);
    }

    public JSONObject products(String cursor, Integer first) throws IOException {
        String query = "query Products($first:Int!,$after:String){products(first:$first,after:$after,sortKey:UPDATED_AT){edges{cursor node{id title handle status createdAt updatedAt featuredMedia{preview{image{url}}} variants(first:100){nodes{id title sku barcode price inventoryQuantity inventoryItem{id}}}}}pageInfo{hasNextPage endCursor}}}";
        JSONObject vars = new JSONObject();
        vars.put("first", first == null ? 50 : Math.min(Math.max(first, 1), 100));
        vars.put("after", blankToNull(cursor));
        return graphql(query, vars);
    }

    public JSONObject setInventory(String inventoryItemId, String locationId, int quantity,
                                   Integer compareQuantity, String referenceUri) throws IOException {
        String mutation = "mutation InventorySet($input:InventorySetQuantitiesInput!,$key:String!){inventorySetQuantities(input:$input) @idempotent(key:$key){inventoryAdjustmentGroup{createdAt reason referenceDocumentUri changes{name delta}} userErrors{field message code}}}";
        JSONObject level = new JSONObject();
        level.put("inventoryItemId", gid("InventoryItem", inventoryItemId));
        level.put("locationId", gid("Location", locationId));
        level.put("quantity", Math.max(quantity, 0));
        if (compareQuantity != null) level.put("compareQuantity", compareQuantity);
        JSONObject input = new JSONObject();
        input.put("name", "available");
        input.put("reason", "correction");
        input.put("referenceDocumentUri", referenceUri == null || referenceUri.isBlank()
                ? "aierp://inventory-sync/" + UUID.randomUUID() : referenceUri);
        input.put("ignoreCompareQuantity", compareQuantity == null);
        input.put("quantities", JSONArray.of(level));
        JSONObject vars = new JSONObject();
        vars.put("input", input);
        vars.put("key", UUID.randomUUID().toString());
        return graphql(mutation, vars);
    }

    public JSONObject fulfillmentOrders(String orderId) throws IOException {
        String query = "query FulfillmentOrders($id:ID!){order(id:$id){id fulfillmentOrders(first:50){nodes{id status lineItems(first:100){nodes{id remainingQuantity}}}}}}";
        return graphql(query, JSONObject.of("id", gid("Order", orderId)));
    }

    public JSONObject createFulfillment(String fulfillmentOrderId, String company, String trackingNumber,
                                        String trackingUrl, boolean notifyCustomer) throws IOException {
        String mutation = "mutation Fulfill($fulfillment:FulfillmentInput!){fulfillmentCreate(fulfillment:$fulfillment){fulfillment{id status trackingInfo{company number url}} userErrors{field message}}}";
        JSONObject tracking = new JSONObject();
        tracking.put("company", company);
        tracking.put("number", trackingNumber);
        if (trackingUrl != null && !trackingUrl.isBlank()) tracking.put("url", trackingUrl);
        JSONObject byOrder = new JSONObject();
        byOrder.put("fulfillmentOrderId", gid("FulfillmentOrder", fulfillmentOrderId));
        JSONObject input = new JSONObject();
        input.put("lineItemsByFulfillmentOrder", JSONArray.of(byOrder));
        input.put("notifyCustomer", notifyCustomer);
        input.put("trackingInfo", tracking);
        return graphql(mutation, JSONObject.of("fulfillment", input));
    }

    public JSONObject graphql(String query, JSONObject variables) throws IOException {
        JSONObject payload = new JSONObject();
        payload.put("query", query);
        payload.put("variables", variables == null ? new JSONObject() : variables);
        HttpUrl url = HttpUrl.get("https://" + shopDomain + "/admin/api/" + apiVersion + "/graphql.json");
        Request request = new Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Shopify-Access-Token", accessToken)
                .post(RequestBody.create(payload.toJSONString(), JSON)).build();
        try (Response response = HTTP.newCall(request).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("Shopify HTTP " + response.code() + ": " + abbreviate(text));
            JSONObject json = text.isBlank() ? new JSONObject() : JSONObject.parseObject(text);
            if (json.getJSONArray("errors") != null && !json.getJSONArray("errors").isEmpty()) {
                throw new IOException("Shopify GraphQL: " + abbreviate(json.getJSONArray("errors").toJSONString()));
            }
            return json;
        }
    }

    private static String normalizeShopDomain(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Shopify店铺域名不能为空");
        String domain = value.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^https?://", "").replaceAll("/.*$", "");
        if (!SHOP_DOMAIN.matcher(domain).matches()) throw new IllegalArgumentException("Shopify域名必须为 *.myshopify.com");
        return domain;
    }

    private static String gid(String type, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(type + " ID不能为空");
        return value.startsWith("gid://shopify/") ? value : "gid://shopify/" + type + "/" + value;
    }

    private static Object blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String abbreviate(String value) {
        if (value == null) return "";
        return value.length() <= 800 ? value : value.substring(0, 800) + "...";
    }
}
