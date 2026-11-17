package com.aierp.open.amazon;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal Amazon Selling Partner API client.
 *
 * Amazon's current SP-API connection flow uses an LWA access token in the
 * x-amz-access-token header. Restricted order data still depends on the roles
 * granted to the SP-API application.
 */
public class AmazonSpApiClient {
    public static final String ENDPOINT_NA = "https://sellingpartnerapi-na.amazon.com";
    public static final String ENDPOINT_EU = "https://sellingpartnerapi-eu.amazon.com";
    public static final String ENDPOINT_FE = "https://sellingpartnerapi-fe.amazon.com";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final Map<String, CachedToken> TOKENS = new ConcurrentHashMap<>();
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final String endpoint;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;

    public AmazonSpApiClient(String endpoint, String clientId, String clientSecret, String refreshToken) {
        this.endpoint = normalizeEndpoint(endpoint);
        this.clientId = require(clientId, "Amazon LWA clientId");
        this.clientSecret = require(clientSecret, "Amazon LWA clientSecret");
        this.refreshToken = require(refreshToken, "Amazon refreshToken");
    }

    public JSONObject marketplaceParticipations() throws IOException {
        return execute("GET", "/sellers/v1/marketplaceParticipations", null, null);
    }

    /** Uses the current Orders API v2026-01-01. */
    public JSONObject searchOrders(String marketplaceId, String updatedAfter,
                                   String paginationToken, Integer maxResults) throws IOException {
        JSONObject query = new JSONObject();
        query.put("marketplaceIds", require(marketplaceId, "marketplaceId"));
        if (updatedAfter != null && !updatedAfter.isBlank()) query.put("lastUpdatedAfter", updatedAfter);
        if (paginationToken != null && !paginationToken.isBlank()) query.put("paginationToken", paginationToken);
        query.put("maxResultsPerPage", maxResults == null ? 50 : Math.min(Math.max(maxResults, 1), 100));
        query.put("includedData", "FULFILLMENT,PROCEEDS,PROMOTION,CANCELLATION,PACKAGES,PAYMENT");
        return execute("GET", "/orders/2026-01-01/orders", query, null);
    }

    public JSONObject getOrder(String orderId) throws IOException {
        JSONObject query = new JSONObject();
        query.put("includedData", "FULFILLMENT,PROCEEDS,PROMOTION,CANCELLATION,PACKAGES,PAYMENT");
        return execute("GET", "/orders/2026-01-01/orders/" + encodePath(orderId), query, null);
    }

    public JSONObject searchListings(String sellerId, String marketplaceId,
                                     String pageToken, Integer pageSize) throws IOException {
        JSONObject query = new JSONObject();
        query.put("marketplaceIds", require(marketplaceId, "marketplaceId"));
        query.put("includedData", "summaries,attributes,issues,offers,fulfillmentAvailability");
        query.put("pageSize", pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 20));
        if (pageToken != null && !pageToken.isBlank()) query.put("pageToken", pageToken);
        return execute("GET", "/listings/2021-08-01/items/" + encodePath(sellerId), query, null);
    }

    public JSONObject updateInventory(String sellerId, String marketplaceId,
                                      String sellerSku, int quantity) throws IOException {
        JSONObject availability = new JSONObject();
        availability.put("fulfillment_channel_code", "DEFAULT");
        availability.put("quantity", Math.max(quantity, 0));
        JSONObject patch = new JSONObject();
        patch.put("op", "replace");
        patch.put("path", "/attributes/fulfillment_availability");
        patch.put("value", JSONArray.of(availability));
        JSONObject body = new JSONObject();
        body.put("productType", "PRODUCT");
        body.put("patches", JSONArray.of(patch));
        JSONObject query = new JSONObject();
        query.put("marketplaceIds", require(marketplaceId, "marketplaceId"));
        return execute("PATCH", "/listings/2021-08-01/items/" + encodePath(sellerId)
                + "/" + encodePath(sellerSku), query, body);
    }

    /** Updates the shipment state for an Amazon order (MFN workflow). */
    public JSONObject updateShipmentStatus(String orderId, String marketplaceId, String status) throws IOException {
        JSONObject body = new JSONObject();
        body.put("marketplaceId", require(marketplaceId, "marketplaceId"));
        body.put("shipmentStatus", status == null || status.isBlank() ? "ReadyForPickup" : status);
        return execute("POST", "/orders/v0/orders/" + encodePath(orderId) + "/shipment", null, body);
    }

    private JSONObject execute(String method, String path, JSONObject query, JSONObject body) throws IOException {
        HttpUrl.Builder url = Objects.requireNonNull(HttpUrl.parse(endpoint + path)).newBuilder();
        if (query != null) query.forEach((key, value) -> url.addQueryParameter(key, String.valueOf(value)));
        Request.Builder builder = new Request.Builder()
                .url(url.build())
                .header("Accept", "application/json")
                .header("x-amz-access-token", accessToken())
                .header("x-amz-date", AMZ_DATE.format(Instant.now()))
                .header("User-Agent", "AI-ERP/4.1.0 (Language=Java/17)");
        RequestBody requestBody = body == null ? RequestBody.create(new byte[0], JSON)
                : RequestBody.create(body.toJSONString(), JSON);
        switch (method) {
            case "POST" -> builder.post(requestBody);
            case "PATCH" -> builder.patch(requestBody);
            case "DELETE" -> builder.delete();
            default -> builder.get();
        }
        try (Response response = HTTP.newCall(builder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Amazon SP-API HTTP " + response.code() + ": " + abbreviate(responseBody));
            }
            return responseBody.isBlank() ? new JSONObject() : JSONObject.parseObject(responseBody);
        }
    }

    private String accessToken() throws IOException {
        String key = clientId + ":" + Integer.toHexString(refreshToken.hashCode());
        CachedToken cached = TOKENS.get(key);
        long now = Instant.now().getEpochSecond();
        if (cached != null && cached.expiresAt > now + 60) return cached.value;
        FormBody form = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();
        Request request = new Request.Builder().url("https://api.amazon.com/auth/o2/token").post(form).build();
        try (Response response = HTTP.newCall(request).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("Amazon LWA HTTP " + response.code() + ": " + abbreviate(text));
            JSONObject json = JSONObject.parseObject(text);
            String token = json.getString("access_token");
            if (token == null || token.isBlank()) throw new IOException("Amazon LWA response has no access_token");
            long ttl = json.getLongValue("expires_in", 3600L);
            TOKENS.put(key, new CachedToken(token, now + ttl));
            return token;
        }
    }

    private static String normalizeEndpoint(String value) {
        String endpoint = value == null || value.isBlank() ? ENDPOINT_NA : value.trim();
        while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        if (!endpoint.equals(ENDPOINT_NA) && !endpoint.equals(ENDPOINT_EU) && !endpoint.equals(ENDPOINT_FE)) {
            throw new IllegalArgumentException("Unsupported Amazon SP-API endpoint");
        }
        return endpoint;
    }

    private static String encodePath(String value) {
        return HttpUrl.get("https://placeholder/" + require(value, "path value"))
                .encodedPathSegments().get(0);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "不能为空");
        return value.trim();
    }

    private static String abbreviate(String value) {
        if (value == null) return "";
        return value.length() <= 800 ? value : value.substring(0, 800) + "...";
    }

    private record CachedToken(String value, long expiresAt) {}
}
