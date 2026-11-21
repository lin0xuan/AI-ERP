package com.aierp.erp.datacenter;

import java.util.List;

/** Only selected business fields are copied. No credentials, names, phones or addresses. */
public final class DataSources {
    private DataSources() {}
    public record Source(String kind, String table, String columns, String description) {}
    public static final List<Source> ALL = List.of(
        new Source("order", "oms_shop_order", "id,merchant_id,shop_id,shop_type,order_id,pay_time,order_status,payment_amount", "多平台订单；金额单位分；按支付时间统计"),
        new Source("refund", "oms_shop_refund", "id,merchant_id,shop_id,shop_type,after_id,order_id,refund_status,refund_amount,sku_id,product_id", "退款；仅 refund_status=3 计入成功退款，按原订单支付期归属"),
        new Source("item", "oms_shop_order_item", "id,merchant_id,shop_id,shop_type,shop_order_id,order_id,sku_id,product_id,sku_code,outer_sku_id,outer_product_id,erp_goods_id,erp_goods_sku_id,quantity,sale_price", "订单商品明细；按平台订单号关联订单，按规格编码或 ERP SKU 对齐库存；不用明细金额代替订单实付"),
        new Source("inventory", "o_goods_inventory", "id,merchant_id,shop_id,goods_id,goods_num,sku_id,sku_code,warehouse_id,quantity,available_quantity,locked_quantity,is_delete", "ERP 自有库存快照；规格编码和商品编码作为默认识别码；不等于所有外部仓库库存"),
        new Source("purchase", "erp_purchase_order", "id,merchant_id,shop_id,order_num,supplier_id,warehouse_id,order_time,order_amount,status", "采购订单；元转换为分；排除已取消状态99")
    );
    public static Source of(String kind) {
        return ALL.stream().filter(s -> s.kind().equals(kind)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知主题"));
    }
}
