# Amazon 与 Shopify 接入

## 已实现范围

- Amazon SP-API：LWA token 自动换取和缓存、站点检查、Orders API v2026-01-01、Listings Items、库存数量更新、订单发货状态更新。
- Shopify：GraphQL Admin API、店铺检查、订单和商品游标分页、库存绝对值同步、Fulfillment Order 查询与履约创建。
- 所有凭证均从现有 `o_shop` 店铺记录读取，接口响应不会返回凭证。

先执行迁移脚本：`docs/migrations/20260910_add_amazon_shopify.sql`。

## Amazon 店铺字段

平台类型为 `800`。

| `o_shop` 字段 | 内容 |
| --- | --- |
| `app_key` | LWA Client ID |
| `app_secret` | LWA Client Secret |
| `refresh_token` | 卖家授权后的 LWA Refresh Token |
| `seller_id` | Amazon Selling Partner ID |
| `seller_num` | Marketplace ID，例如美国站 `ATVPDKIKX0DER` |
| `api_request_url` | `https://sellingpartnerapi-na.amazon.com`、`-eu` 或 `-fe` |

订单收件人等 PII 数据取决于 Amazon 应用获批的角色。客户端不会绕过 Amazon 的权限控制。

## Shopify 店铺字段

平台类型为 `850`。

| `o_shop` 字段 | 内容 |
| --- | --- |
| `url` | `your-store.myshopify.com`，不可填写任意域名 |
| `access_token` | Admin API access token |
| `api_request_url` | API 版本，默认 `2026-07` |

建议至少授予 `read_orders`、`read_products`、`read_inventory`、`write_inventory`，以及实际履约模式对应的 fulfillment scopes。公开应用应实现 Shopify OAuth 并安全保存轮换后的 offline token；当前接入层也支持开发店/自有店铺直接配置 token。

## 接口

基础路径：`/api/oms-api/cross-border`

- `GET /{shopId}/connection`
- `GET /amazon/{shopId}/orders`
- `GET /amazon/{shopId}/products`
- `POST /amazon/{shopId}/inventory`
- `POST /amazon/{shopId}/shipment-status`
- `GET /shopify/{shopId}/orders`
- `GET /shopify/{shopId}/products`
- `POST /shopify/{shopId}/inventory`
- `GET /shopify/{shopId}/orders/{orderId}/fulfillment-orders`
- `POST /shopify/{shopId}/fulfillments`

订单和商品接口返回平台原始结构，便于先完成 Sandbox/开发店字段核对。进入生产前，再根据实际站点、商品类目和履约方式，将字段转换接入 `ShopPullApiService` 的 `oms_shop_*` 统一落库流程。
