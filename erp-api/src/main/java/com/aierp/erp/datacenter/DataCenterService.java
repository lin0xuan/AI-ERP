package com.aierp.erp.datacenter;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class DataCenterService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final String VERSION = "payment-cohort-v1";
    /** 0新订单 11已取消 13已关闭 21待付款 22锁定 29删除。无支付时间时不是已支付事实。21始终不计入。 */
    private static final Set<Long> UNPAID_STATUSES = Set.of(0L, 11L, 13L, 21L, 22L, 29L);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    @Value("${data-center.initialize-schema:true}") private boolean initializeSchema = true;

    public DataCenterService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(300);
    }
    @PostConstruct public void initialize() {
        if (initializeSchema) new ResourceDatabasePopulator(new ClassPathResource("db/data-center.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
    }
    public List<Map<String,Object>> jobs() {
        return jdbc.queryForList("SELECT * FROM dc_job ORDER BY started_at DESC LIMIT 50");
    }
    public List<Map<String,Object>> mappings() {
        return jdbc.queryForList("SELECT * FROM dc_mapping ORDER BY entity_type,source_key LIMIT 1000");
    }
    public record Mapping(String entityType, String sourceKey, String canonicalCode, String displayName) {}
    public void saveMapping(Mapping m, long actor) {
        if (m == null || !Set.of("product","sku","shop","warehouse","supplier").contains(m.entityType()))
            throw new IllegalArgumentException("主数据类型无效");
        checkText(m.sourceKey(),180); checkText(m.canonicalCode(),100); checkText(m.displayName(),150);
        tx.executeWithoutResult(status -> {
            long version = number(jdbc.queryForMap("SELECT mapping_version FROM dc_state WHERE id=1 FOR UPDATE"),"mapping_version") + 1;
            int changed = jdbc.update("UPDATE dc_mapping SET canonical_code=?,display_name=?,version=?,updated_at=?,actor_id=? WHERE entity_type=? AND source_key=?",
                    m.canonicalCode(),m.displayName(),version,now(),actor,m.entityType(),m.sourceKey());
            if (changed == 0) jdbc.update("INSERT INTO dc_mapping VALUES(?,?,?,?,?,?,?)",
                    m.entityType(),m.sourceKey(),m.canonicalCode(),m.displayName(),version,now(),actor);
            jdbc.update("INSERT INTO dc_mapping_history VALUES(?,?,?,?,?,?,?)",version,m.entityType(),m.sourceKey(),m.canonicalCode(),m.displayName(),now(),actor);
            jdbc.update("UPDATE dc_state SET mapping_version=? WHERE id=1",version);
        });
    }
    private static void checkText(String s,int max) {
        if(s == null || s.isBlank() || s.length()>max || !s.equals(s.trim())) throw new IllegalArgumentException("字段不能为空、超长或含首尾空格");
    }
    /** Full reconciliation also captures old updates and hard deletions. Publish only after all sources succeed. */
    public Map<String,Object> synchronize(long actor) {
        String batch = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO dc_job(id,status,started_at,actor_id,metric_version) VALUES(?,'RUNNING',?,?,?)",batch,now(),actor,VERSION);
        try {
            tx.executeWithoutResult(status -> {
                var state = jdbc.queryForMap("SELECT * FROM dc_state WHERE id=1 FOR UPDATE");
                Map<String,String> mapping = new HashMap<>();
                jdbc.queryForList("SELECT * FROM dc_mapping").forEach(m -> mapping.put(m.get("entity_type")+":"+m.get("source_key"),text(m,"canonical_code")));
                Map<String,String> erpSkuCodes = new HashMap<>();
                Map<String,String> erpProductCodes = new HashMap<>();
                loadErpCodes(erpSkuCodes, erpProductCodes);
                for (var source : DataSources.ALL) {
                    long cursor = 0;
                    while (true) {
                        var rows = jdbc.queryForList("SELECT "+source.columns()+" FROM "+source.table()+" WHERE id>? ORDER BY id LIMIT 500",cursor);
                        if(rows.isEmpty()) break;
                        for(var row : rows) ingest(batch,source.kind(),row,mapping,erpSkuCodes,erpProductCodes);
                        cursor = number(rows.get(rows.size()-1),"id");
                    }
                }
                validateRelations(batch);
                long count = jdbc.queryForObject("SELECT COUNT(*) FROM dc_fact WHERE batch_id=?",Long.class,batch);
                long issues = jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE batch_id=?",Long.class,batch);
                jdbc.update("UPDATE dc_job SET status='SUCCESS',finished_at=?,row_count=?,issue_count=?,mapping_version=? WHERE id=?",
                        now(),count,issues,number(state,"mapping_version"),batch);
                jdbc.update("UPDATE dc_state SET batch_id=? WHERE id=1",batch);
            });
        } catch (RuntimeException ex) {
            // Do not return database URLs, SQL or credentials from driver exception messages.
            jdbc.update("UPDATE dc_job SET status='FAILED',finished_at=?,error_message=? WHERE id=?",now(),
                    "同步失败（"+ex.getClass().getSimpleName()+"）；检查源表结构、数据库连接及超时，修复后可重试。旧快照仍可用。",batch);
        }
        return jdbc.queryForMap("SELECT * FROM dc_job WHERE id=?",batch);
    }
    private void loadErpCodes(Map<String,String> skuCodes, Map<String,String> productCodes) {
        for (var row : jdbc.queryForList("SELECT merchant_id,sku_id,sku_code,goods_id,goods_num,is_delete FROM o_goods_inventory")) {
            if (number(row, "is_delete") != 0) continue;
            String merchant = text(row, "merchant_id");
            putCode(skuCodes, erpKey(merchant, text(row, "sku_id")), text(row, "sku_code"));
            putCode(productCodes, erpKey(merchant, text(row, "goods_id")), text(row, "goods_num"));
        }
    }
    private void ingest(String batch,String kind,Map<String,Object> r,Map<String,String> mapping,Map<String,String> erpSkuCodes,Map<String,String> erpProductCodes) {
        long id=number(r,"id");
        String scope=text(r,"merchant_id")+":"+text(r,"shop_id");
        String platform=scope+":"+text(r,"shop_type");
        String key=kind+":"+id, parent=null, sku=null, product=null, warehouse=null, supplier=null;
        String shop=resolve(mapping,"shop",scope);
        Long event=null; long amount=0, quantity=0; int eligible=1;
        try {
            if(r.get("merchant_id")==null || r.get("shop_id")==null)
                throw new IllegalArgumentException("商户或店铺身份缺失");
            if(Set.of("order","refund","item").contains(kind) && r.get("shop_type")==null)
                throw new IllegalArgumentException("平台类型缺失");
            if(Set.of("order","refund","item").contains(kind) && Set.of(800L,850L).contains(number(r,"shop_type")))
                throw new IllegalArgumentException("跨境来源尚无币种与汇率转换，暂不计入人民币指标");
            switch(kind) {
                case "order" -> {
                    key=platform+":"+text(r,"order_id");
                    if(text(r,"order_id").isBlank()) throw new IllegalArgumentException("平台订单号缺失");
                    if(r.get("payment_amount")==null) throw new IllegalArgumentException("支付金额缺失");
                    if(r.get("order_status")==null) throw new IllegalArgumentException("订单状态缺失");
                    amount=number(r,"payment_amount");
                    // 待付款一律排除。取消、关闭、新订单、锁定、删除只有在没有支付时间时才排除，已支付的仍留给退款冲减。
                    if(unpaidOrder(r)) eligible=0;
                    else event=epoch(r.get("pay_time"));
                }
                case "refund" -> {
                    key=platform+":"+text(r,"after_id"); parent=platform+":"+text(r,"order_id");
                    if(text(r,"after_id").isBlank() || text(r,"order_id").isBlank()) throw new IllegalArgumentException("退款单号或原订单号缺失");
                    eligible=number(r,"refund_status")==3 ? 1 : 0;
                    if(r.get("refund_amount")==null) throw new IllegalArgumentException("退款金额缺失");
                    amount=number(r,"refund_amount");
                }
                case "item" -> {
                    quantity=number(r,"quantity");
                    String platformOrderId=blankToNull(text(r,"order_id"));
                    if(platformOrderId==null)
                        issue(batch,kind,id,"MISSING_ORDER_NO","WARN","平台订单号缺失，无法关联订单");
                    else parent=platform+":"+platformOrderId;
                    String merchant=text(r,"merchant_id");
                    String platformSku=blankToNull(text(r,"sku_id"))==null?null:platform+":"+text(r,"sku_id");
                    String erpSkuKey=erpKey(merchant,text(r,"erp_goods_sku_id"));
                    sku=mappedCode(mapping,"sku",platformSku,erpSkuKey);
                    if(sku==null) sku=blankToNull(text(r,"sku_code"));
                    if(sku==null && erpSkuKey!=null) sku=blankToNull(erpSkuCodes.get(erpSkuKey));
                    if(sku==null) sku=blankToNull(text(r,"outer_sku_id"));
                    if(sku==null) {
                        if(platformSku==null) throw new IllegalArgumentException("平台 SKU 缺失");
                        sku=platformSku;
                        issue(batch,kind,id,"UNMAPPED_SKU","WARN","SKU 没有手工映射、规格编码或 ERP SKU，暂保留来源编码；补齐后重新同步");
                    }
                    String platformProduct=blankToNull(text(r,"product_id"))==null?null:platform+":"+text(r,"product_id");
                    String erpProductKey=erpKey(merchant,text(r,"erp_goods_id"));
                    product=mappedCode(mapping,"product",platformProduct,erpProductKey);
                    if(product==null && erpProductKey!=null) product=blankToNull(erpProductCodes.get(erpProductKey));
                    if(product==null) product=blankToNull(text(r,"outer_product_id"));
                    if(product==null) product=platformProduct;
                }
                case "inventory" -> {
                    if(r.get("sku_id")==null || r.get("warehouse_id")==null || r.get("available_quantity")==null || r.get("quantity")==null || r.get("locked_quantity")==null)
                        throw new IllegalArgumentException("库存 SKU、仓库或数量字段缺失");
                    String merchant=text(r,"merchant_id");
                    String erpSkuKey="erp:"+merchant+":"+text(r,"sku_id");
                    sku=mappedCode(mapping,"sku",erpSkuKey);
                    if(sku==null) sku=blankToNull(text(r,"sku_code"));
                    if(sku==null) sku=blankToNull(erpSkuCodes.get(erpSkuKey));
                    if(sku==null) sku=erpSkuKey;
                    String erpProductKey=erpKey(merchant,text(r,"goods_id"));
                    product=mappedCode(mapping,"product",erpProductKey);
                    if(product==null) product=blankToNull(text(r,"goods_num"));
                    if(product==null && erpProductKey!=null) product=blankToNull(erpProductCodes.get(erpProductKey));
                    if(product==null) product=erpProductKey;
                    warehouse=resolve(mapping,"warehouse",merchant+":"+text(r,"warehouse_id"));
                    quantity=number(r,"available_quantity");
                    eligible=number(r,"is_delete")==0 ? 1 : 0;
                    if(number(r,"quantity")-number(r,"locked_quantity")!=quantity)
                        issue(batch,kind,id,"STOCK_BALANCE","WARN","可用库存不等于总库存减锁定库存");
                }
                case "purchase" -> {
                    key=scope+":"+text(r,"order_num"); event=epoch(r.get("order_time"));
                    if(r.get("order_amount")==null) throw new IllegalArgumentException("采购金额缺失");
                    amount=new BigDecimal(text(r,"order_amount")).movePointRight(2).longValueExact();
                    supplier=resolve(mapping,"supplier",text(r,"merchant_id")+":"+text(r,"supplier_id"));
                    warehouse=resolve(mapping,"warehouse",text(r,"merchant_id")+":"+text(r,"warehouse_id"));
                    eligible=number(r,"status")==99 ? 0 : 1;
                }
                default -> throw new IllegalArgumentException("未知来源");
            }
            if(amount<0 || quantity<0) throw new IllegalArgumentException("金额或数量为负数");
        } catch (IllegalArgumentException | ArithmeticException ex) {
            eligible=0;
            issue(batch,kind,id,"INVALID_VALUE","ERROR",ex.getMessage()==null ? "字段格式无效" : ex.getMessage());
        }
        jdbc.update("INSERT INTO dc_fact(batch_id,kind,source_id,source_key,parent_key,shop_code,sku_code,product_code,warehouse_code,supplier_code,event_time,amount_minor,quantity,eligible,payload) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                batch,kind,id,key,parent,shop,sku,product,warehouse,supplier,event,amount,quantity,eligible,JSON.toJSONString(r));
    }
    static Long epoch(Object value) {
        if(value==null) throw new IllegalArgumentException("业务时间缺失");
        long v=new BigDecimal(value.toString()).longValueExact();
        if(v>=1_000_000_000_000L) v/=1000; // Accept platform seconds and milliseconds explicitly.
        if(v<946684800L || v>4102444800L) throw new IllegalArgumentException("业务时间超出2000至2100年范围");
        return v;
    }
    private static String resolve(Map<String,String> mapping,String type,String key) {
        return mapping.getOrDefault(type+":"+key,key);
    }
    private void validateRelations(String batch) {
        for(String kind:List.of("order","refund","purchase")) {
            var duplicates=jdbc.queryForList("SELECT source_key FROM dc_fact WHERE batch_id=? AND kind=? GROUP BY source_key HAVING COUNT(*)>1",batch,kind);
            for(var duplicate:duplicates) {
                for(var row:jdbc.queryForList("SELECT source_id FROM dc_fact WHERE batch_id=? AND kind=? AND source_key=?",batch,kind,duplicate.get("source_key")))
                    issue(batch,kind,number(row,"source_id"),"DUPLICATE_KEY","ERROR","业务键重复，本组全部隔离；修正源数据后重试");
                jdbc.update("UPDATE dc_fact SET eligible=0 WHERE batch_id=? AND kind=? AND source_key=?",batch,kind,duplicate.get("source_key"));
            }
        }
        var orphans=jdbc.queryForList("SELECT r.source_id FROM dc_fact r WHERE r.batch_id=? AND r.kind='refund' AND r.eligible=1 AND NOT EXISTS (SELECT 1 FROM dc_fact o WHERE o.batch_id=r.batch_id AND o.kind='order' AND o.eligible=1 AND o.source_key=r.parent_key)",batch);
        for(var row:orphans) {
            long id=number(row,"source_id");
            issue(batch,"refund",id,"ORPHAN_REFUND","ERROR","成功退款未匹配有效支付订单，不计入净销售额");
            jdbc.update("UPDATE dc_fact SET eligible=0 WHERE batch_id=? AND kind='refund' AND source_id=?",batch,id);
        }
        var over=jdbc.queryForList("SELECT o.source_id FROM dc_fact o JOIN dc_fact r ON r.batch_id=o.batch_id AND r.parent_key=o.source_key AND r.kind='refund' AND r.eligible=1 WHERE o.batch_id=? AND o.kind='order' AND o.eligible=1 GROUP BY o.source_id,o.amount_minor HAVING SUM(r.amount_minor)>o.amount_minor",batch);
        for(var row:over) issue(batch,"order",number(row,"source_id"),"REFUND_EXCEEDS_PAYMENT","WARN","成功退款超过实付，保留实际金额并提示核查；不会强行归零");
        var looseItems=jdbc.queryForList("SELECT i.source_id FROM dc_fact i WHERE i.batch_id=? AND i.kind='item' AND i.parent_key IS NOT NULL AND i.parent_key<>'' AND NOT EXISTS (SELECT 1 FROM dc_fact o WHERE o.batch_id=i.batch_id AND o.kind='order' AND o.source_key=i.parent_key)",batch);
        for(var row:looseItems) issue(batch,"item",number(row,"source_id"),"ORPHAN_ITEM","WARN","商品明细的平台订单号在本快照中没有对应订单");
    }
    private void issue(String batch,String kind,long id,String rule,String severity,String message) {
        jdbc.update("INSERT INTO dc_issue VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),batch,kind,id,rule,severity,message.substring(0,Math.min(400,message.length())));
    }
    public String publishedBatch(String requested) {
        String batch=requested;
        if(batch==null || batch.isBlank()) batch=jdbc.queryForObject("SELECT batch_id FROM dc_state WHERE id=1",String.class);
        if(batch==null) throw new IllegalArgumentException("尚无成功快照，请先在数据接入中同步");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM dc_job WHERE id=? AND status='SUCCESS'",Long.class,batch)!=1)
            throw new IllegalArgumentException("快照不存在或未发布");
        return batch;
    }
    public Map<String,Object> metrics(LocalDate from,LocalDate to,String shop,String requested) {
        validateRange(from,to);
        String batch=publishedBatch(requested);
        var snapshot=jdbc.queryForMap("SELECT started_at,finished_at,mapping_version,issue_count,metric_version FROM dc_job WHERE id=?",batch);
        if(!VERSION.equals(snapshot.get("metric_version"))) throw new IllegalArgumentException("该历史快照指标版本未被当前服务支持，请使用对应版本服务复核");
        long start=from.atStartOfDay(ZONE).toEpochSecond(),end=to.plusDays(1).atStartOfDay(ZONE).toEpochSecond();
        String filter=" AND o.event_time>=? AND o.event_time<?";
        List<Object> args=new ArrayList<>(List.of(batch,start,end));
        if(shop!=null && !shop.isBlank()) {filter+=" AND o.shop_code=?"; args.add(shop);}
        var paid=jdbc.queryForMap("SELECT COUNT(*) AS orders,COALESCE(SUM(o.amount_minor),0) AS paid FROM dc_fact o WHERE o.batch_id=? AND o.kind='order' AND o.eligible=1"+filter,args.toArray());
        BigDecimal refund=jdbc.queryForObject("SELECT COALESCE(SUM(r.amount_minor),0) FROM dc_fact o JOIN dc_fact r ON r.batch_id=o.batch_id AND r.kind='refund' AND r.eligible=1 AND r.parent_key=o.source_key WHERE o.batch_id=? AND o.kind='order' AND o.eligible=1"+filter,BigDecimal.class,args.toArray());
        BigDecimal payment=decimal(paid.get("paid"));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("batchId",batch); result.put("metricVersion",VERSION); result.put("timezone",ZONE.toString());
        result.put("from",from.toString()); result.put("to",to.toString()); result.put("shop",shop==null?"":shop);
        result.put("currency","CNY"); result.put("paidOrders",paid.get("orders"));
        result.put("paidAmount",payment.movePointLeft(2)); result.put("refundAmount",refund.movePointLeft(2));
        result.put("netSales",payment.subtract(refund).movePointLeft(2));
        List<Object> stockArgs=new ArrayList<>(List.of(batch));
        String shopFilter="";
        if(shop!=null && !shop.isBlank()) {shopFilter=" AND shop_code=?";stockArgs.add(shop);}
        result.put("availableStock",jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM dc_fact WHERE batch_id=? AND kind='inventory' AND eligible=1"+shopFilter,BigDecimal.class,stockArgs.toArray()));
        result.put("purchaseAmount",jdbc.queryForObject("SELECT COALESCE(SUM(o.amount_minor),0) FROM dc_fact o WHERE o.batch_id=? AND o.kind='purchase' AND o.eligible=1"+filter,BigDecimal.class,args.toArray()).movePointLeft(2));
        result.put("snapshot",snapshot);
        result.put("excludedRows",jdbc.queryForObject("SELECT COUNT(DISTINCT CONCAT(kind,':',source_id)) FROM dc_issue WHERE batch_id=? AND severity='ERROR'",Long.class,batch));
        result.put("definitions",definitions());
        return result;
    }
    public static void validateRange(LocalDate from,LocalDate to) {
        if(from==null || to==null || to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from,to)>366)
            throw new IllegalArgumentException("请选择不超过367个自然日的有效日期范围");
    }
    public static List<Map<String,String>> definitions() {
        return List.of(
            Map.of("code","paidAmount","name","支付金额","unit","元/CNY","definition","按 Asia/Shanghai 支付日期筛选平台订单实付。状态21不计入。状态0、11、13、22、29且支付时间为空或0视为未支付，不计入也不记质量错误。这些状态已有支付时间则保留，由成功退款冲减。待发货、已发货、已完成等状态缺少支付时间按质量错误隔离。不混入内部订单。源金额须为人民币分。"),
            Map.of("code","refundAmount","name","该批订单成功退款","unit","元/CNY","definition","归属于筛选支付期订单、截至快照的 refund_status=3 退款。不是退款发生期现金流；不以售后成功替代退款成功。"),
            Map.of("code","netSales","name","净销售额（支付批次）","unit","元/CNY","definition","支付金额减该批订单截至快照的成功退款；保留源支付字段含运费/税费口径，不等同财务确认收入。"),
            Map.of("code","availableStock","name","快照可用库存","unit","件","definition","同步时 ERP 自有库存 available_quantity 合计；排除删除或隔离行；不随支付日期变化。"),
            Map.of("code","purchaseAmount","name","采购订单金额","unit","元/CNY","definition","按采购 order_time 日期统计 order_amount；排除状态99；不代表采购付款或成本。"));
    }
    public Map<String,Object> rows(String kind,String batch,int page,int size,boolean issues) {
        if(page<1 || page>100000 || size<1 || size>100) throw new IllegalArgumentException("分页参数无效");
        DataSources.of(kind); batch=publishedBatch(batch);
        String table=issues?"dc_issue":"dc_fact";
        return Map.of("batchId",batch,"total",jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE batch_id=? AND kind=?",Long.class,batch,kind),
                "rows",jdbc.queryForList("SELECT * FROM "+table+" WHERE batch_id=? AND kind=? ORDER BY source_id LIMIT ? OFFSET ?",batch,kind,size,(page-1)*size));
    }
    public Map<String,Object> lineage(String kind,long id,String requested) {
        var source=DataSources.of(kind); String batch=publishedBatch(requested);
        var rows=jdbc.queryForList("SELECT * FROM dc_fact WHERE batch_id=? AND kind=? AND source_id=?",batch,kind,id);
        if(rows.isEmpty()) throw new IllegalArgumentException("快照中无此记录");
        var fact=rows.get(0);
        String parentKey=text(fact,"parent_key");
        String sourceKey=text(fact,"source_key");
        var parent=parentKey.isBlank()?List.<Map<String,Object>>of():jdbc.queryForList("SELECT * FROM dc_fact WHERE batch_id=? AND kind='order' AND source_key=?",batch,parentKey);
        var related=sourceKey.isBlank()?List.<Map<String,Object>>of():jdbc.queryForList("SELECT * FROM dc_fact WHERE batch_id=? AND parent_key=? AND kind IN ('refund','item') ORDER BY kind,source_id LIMIT 200",batch,sourceKey);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("sourceTable",source.table()); result.put("sourceId",id); result.put("batchId",batch);
        result.put("metricVersion",VERSION); result.put("fact",fact);
        result.put("issues",jdbc.queryForList("SELECT * FROM dc_issue WHERE batch_id=? AND kind=? AND source_id=?",batch,kind,id));
        result.put("parent",parent); result.put("related",related);
        return result;
    }
    private static boolean unpaidOrder(Map<String,Object> row) {
        long status=number(row,"order_status");
        return status==21 || (UNPAID_STATUSES.contains(status) && missingTime(row.get("pay_time")));
    }
    private static boolean missingTime(Object value) {
        if(value==null) return true;
        try {return new BigDecimal(value.toString()).longValueExact()==0;}
        catch(RuntimeException ex) {return false;}
    }
    private static String erpKey(String merchantId,String entityId) {
        if(entityId==null || entityId.isBlank() || "0".equals(entityId)) return null;
        return "erp:"+merchantId+":"+entityId;
    }
    private static String mappedCode(Map<String,String> mapping,String type,String... keys) {
        if(keys==null) return null;
        for(String key:keys) {
            if(key==null || key.isBlank()) continue;
            String code=blankToNull(mapping.get(type+":"+key));
            if(code!=null) return code;
        }
        return null;
    }
    private static void putCode(Map<String,String> codes,String key,String code) {
        String normalized=blankToNull(code);
        if(key==null || normalized==null) return;
        String previous=codes.get(key);
        if(previous==null) codes.put(key,normalized);
        else if(!previous.equals(normalized)) codes.put(key,"");
    }
    private static String blankToNull(String value) {
        if(value==null) return null;
        String trimmed=value.trim();
        return trimmed.isEmpty()?null:trimmed;
    }
    static long number(Map<String,Object> row,String key) {return row.get(key)==null?0:new BigDecimal(row.get(key).toString()).longValueExact();}
    static String text(Map<String,Object> row,String key) {return row.get(key)==null?"":row.get(key).toString();}
    static BigDecimal decimal(Object n) {return n==null?BigDecimal.ZERO:new BigDecimal(n.toString());}
    private static long now() {return System.currentTimeMillis();}
}
