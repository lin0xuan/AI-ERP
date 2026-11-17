-- Amazon SP-API and Shopify Admin API platform definitions.
-- Credentials intentionally remain empty and must be configured per environment/shop.
INSERT INTO `o_shop_platform`
(`id`, `name`, `code`, `app_key`, `app_secret`, `redirect_uri`, `server_url`, `sort`, `region_id`, `status`)
VALUES
(800, 'Amazon', 'AMAZON', '', '', '', 'https://sellingpartnerapi-na.amazon.com', 80, 0, 0),
(850, 'Shopify', 'SHOPIFY', '', '', '', '2026-07', 85, 0, 0)
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`), `code` = VALUES(`code`), `server_url` = VALUES(`server_url`),
`sort` = VALUES(`sort`), `status` = VALUES(`status`);
