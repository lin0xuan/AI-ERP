package com.aierp.erp.operations;

import com.alibaba.fastjson2.JSON;
import com.aierp.service.foundation.StockLedgerService;
import com.aierp.service.ErpPurchaseOrderService;
import com.aierp.model.request.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.aierp.service.foundation.StockLedgerService.*;

/** Explicit identity is shared by HTTP and asynchronous AI callers; scope never comes from model input. */
@Service
public class OperationsService {
    public static final Set<String> KINDS=Set.of("PURCHASE","RECEIPT","ISSUE","ADJUSTMENT","VALUATION","TRANSFER");
    public record Actor(long id,boolean admin,Set<String> permissions) {
        public Actor {permissions=Set.copyOf(permissions);}
        public boolean can(String action) {return admin || permissions.contains("erp:operations:"+action);}
        public void require(String action) {if(!can(action)) throw new AccessDeniedException("缺少业务工作台权限："+action);}
    }
    public record Draft(String kind,long inventoryId,long quantity,BigDecimal unitCost,BigDecimal extraCost,
                        Long supplierId,Long targetInventoryId,String reason,Map<String,Object> attributes,String requestKey) {}
    public record Action(long version,String action,String reason,Long quantity) {}
    public record Policy(String kind,BigDecimal threshold,String firstPermission,String secondPermission,long version) {}
    public record Reorder(long inventoryId,long reorderPoint,long targetQuantity,long minOrder,long packSize,int leadDays,
                          BigDecimal dailyDemand,long supplierId,BigDecimal unitCost,boolean enabled,long version) {}
    public record Field(String kind,String key,String label,String type,boolean required,List<String> options,long version) {}
    public record Grant(long userId,long merchantId,long shopId,long warehouseId,boolean remove) {}
    public record Master(String kind,long recordId,Map<String,Object> attributes,long version) {}
    private final JdbcTemplate jdbc;
    private final StockLedgerService stock;
    private final ErpPurchaseOrderService purchases;
    private final TransactionTemplate tx;
    public OperationsService(JdbcTemplate jdbc,StockLedgerService stock,ErpPurchaseOrderService purchases,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.stock=stock;this.purchases=purchases;this.tx=new TransactionTemplate(manager);
    }
    private String scope(Actor actor,String alias,List<Object> args) {
        if(actor.admin()) return "1=1";
        args.add(actor.id());
        return "EXISTS(SELECT 1 FROM ef_scope s WHERE s.user_id=? AND s.merchant_id="+alias+".merchant_id AND (s.shop_id=-1 OR s.shop_id="+alias+".shop_id) AND (s.warehouse_id=-1 OR s.warehouse_id="+alias+".warehouse_id))";
    }
    public boolean scoped(Actor actor) {
        if(actor.admin()) return false;
        Long rows=jdbc.queryForObject("SELECT COUNT(*) FROM ef_scope WHERE user_id=?",Long.class,actor.id());
        return rows!=null && rows>0;
    }
    public void checkScope(Actor actor,Map<String,Object> row) {
        if(actor.admin()) return;
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM ef_scope WHERE user_id=? AND merchant_id=? AND (shop_id=-1 OR shop_id=?) AND (warehouse_id=-1 OR warehouse_id=?)",
                Long.class,actor.id(),n(row,"merchant_id"),n(row,"shop_id"),n(row,"warehouse_id"));
        if(count==0) throw new AccessDeniedException("没有该商户、店铺或仓库的数据权限");
    }
    public List<Map<String,Object>> inventories(Actor actor,int page) {
        actor.require("read");var args=new ArrayList<Object>();String filter=scope(actor,"i",args);args.add(offset(page));
        return jdbc.queryForList("SELECT i.*,b.stock_value,b.version AS ledger_version FROM o_goods_inventory i LEFT JOIN ef_stock_balance b ON b.inventory_id=i.id WHERE i.is_delete=0 AND "+filter+" ORDER BY i.id LIMIT 50 OFFSET ?",args.toArray());
    }
    public List<Map<String,Object>> documents(Actor actor,int page) {
        actor.require("read");var args=new ArrayList<Object>();String filter=scope(actor,"d",args);args.add(offset(page));
        return jdbc.queryForList("SELECT d.* FROM ef_document d WHERE "+filter+" ORDER BY d.created_at DESC,d.id LIMIT 50 OFFSET ?",args.toArray());
    }
    public Map<String,Object> detail(Actor actor,String id) {
        actor.require("read");var d=document(id,false);checkScope(actor,d);
        var result=new LinkedHashMap<String,Object>(d);
        result.put("history",jdbc.queryForList("SELECT * FROM ef_audit WHERE document_id=? ORDER BY created_at,id",id));
        return result;
    }
    public List<Map<String,Object>> ledger(Actor actor,long id,int page) {
        actor.require("read");checkScope(actor,stock.inventory(id));
        return jdbc.queryForList("SELECT * FROM ef_stock_ledger WHERE inventory_id=? ORDER BY version DESC LIMIT 50 OFFSET ?",id,offset(page));
    }
    private static long offset(int page) {if(page<1 || page>100000) throw new IllegalArgumentException("页码无效");return (page-1L)*50;}
    private Map<String,Object> document(String id,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM ef_document WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw new IllegalArgumentException("单据不存在");return rows.get(0);
    }
    public Map<String,Object> create(Actor actor,Draft d) {
        actor.require("write");validate(d);checkScope(actor,stock.inventory(d.inventoryId()));
        return tx.execute(status -> {
            // Inventory row serializes draft generation and replenishment retries for the same SKU/warehouse.
            var inv=jdbc.queryForMap("SELECT * FROM o_goods_inventory WHERE id=? AND is_delete=0 FOR UPDATE",d.inventoryId());checkScope(actor,inv);
            var old=jdbc.queryForList("SELECT * FROM ef_document WHERE created_by=? AND request_key=?",actor.id(),d.requestKey());
            if(!old.isEmpty()) {
                var previous=old.get(0);checkScope(actor,previous);
                if(!Objects.equals(previous.get("kind"),d.kind()) || n(previous,"inventory_id")!=d.inventoryId()
                        || n(previous,"quantity")!=d.quantity() || !sameAmount(decimal(previous,"unit_cost"),d.unitCost())
                        || !sameAmount(decimal(previous,"extra_cost"),d.extraCost()==null?BigDecimal.ZERO:d.extraCost())
                        || !Objects.equals(previous.get("reason"),d.reason()) || n(previous,"supplier_id")!=(d.supplierId()==null?0:d.supplierId())
                        || n(previous,"target_inventory_id")!=(d.targetInventoryId()==null?0:d.targetInventoryId())
                        || !JSON.parseObject(previous.get("attributes_json").toString()).equals(d.attributes()==null?Map.of():d.attributes())) throw new IllegalArgumentException("请求键已用于另一张单据");
                return previous;
            }
            validateAttributes(d.kind(),d.attributes());
            if(d.kind().equals("TRANSFER")) {
                if(d.targetInventoryId()==null || d.targetInventoryId()==d.inventoryId()) throw new IllegalArgumentException("选择不同的目标库存");
                var target=stock.inventory(d.targetInventoryId());checkScope(actor,target);
                if(n(inv,"merchant_id")!=n(target,"merchant_id") || n(inv,"sku_id")!=n(target,"sku_id") || n(inv,"stock_status")!=n(target,"stock_status"))
                    throw new IllegalArgumentException("调拨必须为同商户、同SKU、同品质库存");
            }
            if(d.kind().equals("PURCHASE")) supplier(d.supplierId(),n(inv,"merchant_id"));
            String id=UUID.randomUUID().toString();long time=System.currentTimeMillis();
            jdbc.update("INSERT INTO ef_document(id,kind,inventory_id,merchant_id,shop_id,warehouse_id,state,quantity,unit_cost,extra_cost,supplier_id,target_inventory_id,reason,attributes_json,created_by,created_at,updated_at,version,request_key) VALUES(?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?,?,0,?)",
                    id,d.kind(),d.inventoryId(),n(inv,"merchant_id"),n(inv,"shop_id"),n(inv,"warehouse_id"),d.quantity(),d.unitCost(),d.extraCost()==null?BigDecimal.ZERO:d.extraCost(),d.supplierId(),d.targetInventoryId(),d.reason(),JSON.toJSONString(d.attributes()==null?Map.of():d.attributes()),actor.id(),time,time,d.requestKey());
            audit(id,"CREATE",actor,d.reason());return document(id,false);
        });
    }
    private void validate(Draft d) {
        if(d==null || !KINDS.contains(d.kind())) throw new IllegalArgumentException("单据类型无效");
        text(d.reason(),500);text(d.requestKey(),100);
        if(Math.abs(d.quantity())>100000000 || d.quantity()==Long.MIN_VALUE) throw new IllegalArgumentException("数量超限");
        if(d.kind().equals("VALUATION") ? d.quantity()!=0 : d.quantity()==0 || (!d.kind().equals("ADJUSTMENT") && d.quantity()<0)) throw new IllegalArgumentException("单据数量无效");
        if(Set.of("PURCHASE","RECEIPT","VALUATION").contains(d.kind()) || d.kind().equals("ADJUSTMENT") && d.quantity()>0) money(d.unitCost(),6);
        if(d.extraCost()!=null) money(d.extraCost(),2);
        if(d.kind().equals("PURCHASE") && (d.supplierId()==null || d.supplierId()<=0)) throw new IllegalArgumentException("请选择供应商");
        if((Set.of("ISSUE","TRANSFER").contains(d.kind()) || d.kind().equals("ADJUSTMENT") && d.quantity()<0) && d.unitCost()!=null)
            throw new IllegalArgumentException("出库成本由库存账本计算，不能手工指定");
        if(d.kind().equals("PURCHASE") && (d.unitCost().stripTrailingZeros().scale()>2 || d.unitCost().compareTo(new BigDecimal("999999.99"))>0 ||
                d.unitCost().multiply(BigDecimal.valueOf(d.quantity())).compareTo(new BigDecimal("99999999.99"))>0 ||
                d.extraCost()!=null && d.extraCost().compareTo(new BigDecimal("9999.99"))>0)) throw new IllegalArgumentException("采购金额超出现有采购单精度或范围（单价2位小数）");
    }
    public Map<String,Object> act(Actor actor,String id,Action a) {
        actor.require("read");text(a.reason(),500);
        return tx.execute(status -> {
            var d=document(id,true);checkScope(actor,d);
            if(n(d,"version")!=a.version()) throw new IllegalArgumentException("单据已变化，请刷新后重试");
            String state=d.get("state").toString(),kind=d.get("kind").toString(),next=state;
            switch(a.action()) {
                case "submit" -> {
                    actor.require("write");state(state,"DRAFT");
                    validateAttributes(kind,JSON.parseObject(d.get("attributes_json").toString()));
                    var p=policy(kind);BigDecimal amount=amount(d);
                    jdbc.update("UPDATE ef_document SET first_permission=?,second_permission=?,policy_version=? WHERE id=?",p.get("first_permission"),
                            amount==null || amount.compareTo(decimal(p,"threshold_amount"))>=0?p.get("second_permission"):null,n(p,"version"),id);
                    next="PENDING";
                }
                case "approve" -> {
                    if(!Set.of("PENDING","PENDING_SECOND").contains(state)) throw new IllegalArgumentException("当前单据不在审批中");
                    String permission=d.get(state.equals("PENDING")?"first_permission":"second_permission").toString();
                    if(!actor.admin() && !actor.permissions().contains(permission)) throw new AccessDeniedException("缺少审批权限："+permission);
                    if(actor.id()==n(d,"created_by") || actor.id()==n(d,"first_approver")) throw new IllegalArgumentException("制单人、一级审批人与二级审批人必须分离");
                    if(state.equals("PENDING")) jdbc.update("UPDATE ef_document SET first_approver=? WHERE id=?",actor.id(),id);
                    next=state.equals("PENDING") && d.get("second_permission")!=null?"PENDING_SECOND":"APPROVED";
                }
                case "reject" -> {
                    if(!Set.of("PENDING","PENDING_SECOND").contains(state)) throw new IllegalArgumentException("当前单据不在审批中");
                    Object permission=d.get(state.equals("PENDING")?"first_permission":"second_permission");
                    if(!actor.admin() && !actor.permissions().contains(permission)) throw new AccessDeniedException("没有驳回权限");
                    next="REJECTED";
                }
                case "post" -> {actor.require("post");state(state,"APPROVED");post(actor,d);next="POSTED";}
                case "receive" -> {
                    actor.require("post");state(state,"POSTED");if(!kind.equals("PURCHASE")) throw new IllegalArgumentException("仅采购单可收货");
                    long qty=a.quantity()==null?0:a.quantity();long received=n(d,"received_quantity");
                    if(qty<=0 || qty>n(d,"quantity")-received) throw new IllegalArgumentException("收货数量超过剩余采购量");
                    var po=jdbc.queryForMap("SELECT * FROM erp_purchase_order WHERE id=? FOR UPDATE",n(d,"purchase_id"));
                    if(n(po,"status")==99 || n(po,"status")==3) throw new IllegalArgumentException("采购单已取消或已由其他流程完成入库");
                    BigDecimal total=decimal(d,"unit_cost").multiply(BigDecimal.valueOf(qty));
                    BigDecimal freight=decimal(d,"extra_cost").multiply(BigDecimal.valueOf(qty)).divide(BigDecimal.valueOf(n(d,"quantity")),6,RoundingMode.HALF_UP);
                    if(received+qty==n(d,"quantity")) {
                        BigDecimal previousValue=jdbc.queryForObject("SELECT COALESCE(SUM(value_delta),0) FROM ef_stock_ledger WHERE source=? AND quantity_delta>0",BigDecimal.class,id);
                        freight=decimal(d,"extra_cost").subtract(previousValue.subtract(decimal(d,"unit_cost").multiply(BigDecimal.valueOf(received))));
                    }
                    stock.move(n(d,"inventory_id"),qty,0,total.add(freight),id+":receipt:"+a.version(),id,Long.toString(actor.id()),a.reason());
                    jdbc.update("UPDATE ef_document SET received_quantity=? WHERE id=?",received+qty,id);
                    jdbc.update("UPDATE erp_purchase_order SET status=?,received_time=CURRENT_TIMESTAMP,stock_in_time=CASE WHEN ?=3 THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id=?",received+qty==n(d,"quantity")?3:2,received+qty==n(d,"quantity")?3:2,n(d,"purchase_id"));
                    jdbc.update("UPDATE erp_purchase_order_item SET inQty=?,status=?,locationId=? WHERE order_id=?",received+qty,received+qty==n(d,"quantity")?3:1,n(d,"warehouse_id"),n(d,"purchase_id"));
                }
                case "cancel" -> {
                    actor.require("write");
                    if(Set.of("CANCELLED","REJECTED").contains(state)) throw new IllegalArgumentException("单据已经结束");
                    if(state.equals("POSTED")) {
                        actor.require("post");
                        if(kind.equals("PURCHASE")) {
                            if(n(d,"received_quantity")!=0 || jdbc.update("UPDATE erp_purchase_order SET status=99 WHERE id=? AND status=1",n(d,"purchase_id"))!=1)
                                throw new IllegalArgumentException("采购单已履行，不能直接取消");
                        } else {
                            if(kind.equals("TRANSFER")) {checkScope(actor,stock.inventory(n(d,"target_inventory_id")));stock.reverse(id+":target",Long.toString(actor.id()),a.reason());}
                            stock.reverse(id,Long.toString(actor.id()),a.reason());
                        }
                    }
                    next="CANCELLED";
                }
                default -> throw new IllegalArgumentException("操作无效");
            }
            jdbc.update("UPDATE ef_document SET state=?,version=version+1,updated_at=? WHERE id=?",next,System.currentTimeMillis(),id);
            audit(id,a.action(),actor,state+" → "+next+"；"+a.reason());return document(id,false);
        });
    }
    private void post(Actor actor,Map<String,Object> d) {
        String id=d.get("id").toString(),kind=d.get("kind").toString();long invId=n(d,"inventory_id"),qty=n(d,"quantity");
        var inv=stock.inventory(invId);checkScope(actor,inv);
        if(n(inv,"merchant_id")!=n(d,"merchant_id") || n(inv,"shop_id")!=n(d,"shop_id") || n(inv,"warehouse_id")!=n(d,"warehouse_id")) throw new IllegalArgumentException("库存归属已变更，请重新制单");
        BigDecimal cost=decimal(d,"unit_cost"),extra=decimal(d,"extra_cost");String by=Long.toString(actor.id()),reason=d.get("reason").toString();
        if(kind.equals("PURCHASE")) {
            supplier(n(d,"supplier_id"),n(d,"merchant_id"));
            if(jdbc.queryForObject("SELECT COUNT(*) FROM o_goods_sku WHERE id=? AND merchant_id=?",Long.class,n(inv,"sku_id"),n(inv,"merchant_id"))==0) throw new IllegalArgumentException("SKU与商户不匹配");
            var req=new PurchaseOrderAddRequest();req.setMerchantId(n(d,"merchant_id"));req.setShopId(n(d,"shop_id"));
            req.setContactId(n(d,"supplier_id"));req.setCreateBy(by);req.setStatus(1);
            req.setOrderDate(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString());req.setOrderAmount(cost.multiply(BigDecimal.valueOf(qty)).setScale(2,RoundingMode.HALF_UP));
            var item=new PurchaseOrderAddItemBo();item.setId(n(inv,"sku_id"));item.setQuantity(qty);item.setPurPrice(cost);item.setAmount(req.getOrderAmount());
            item.setNumber(Objects.toString(inv.get("goods_num"),""));item.setSkuCode(Objects.toString(inv.get("sku_code"),""));item.setGoodsName(Objects.toString(inv.get("goods_name"),""));
            req.setGoodsList(List.of(item));var result=purchases.createPurchaseOrder(req);
            if(result.getCode()!=0 || result.getData()==null) throw new IllegalArgumentException("采购单创建失败："+result.getMsg());
            jdbc.update("UPDATE erp_purchase_order SET order_num=?,warehouse_id=?,ship_amount=? WHERE id=?","EF"+id.replace("-","").substring(0,24),n(d,"warehouse_id"),extra,result.getData());
            jdbc.update("UPDATE erp_purchase_order_item SET order_num=? WHERE order_id=?","EF"+id.replace("-","").substring(0,24),result.getData());
            jdbc.update("UPDATE ef_document SET purchase_id=? WHERE id=?",result.getData(),id);return;
        }
        if(kind.equals("TRANSFER")) {
            var target=stock.inventory(n(d,"target_inventory_id"));checkScope(actor,target);
            if(n(inv,"merchant_id")!=n(target,"merchant_id") || n(inv,"sku_id")!=n(target,"sku_id") || n(inv,"stock_status")!=n(target,"stock_status")) throw new IllegalArgumentException("调拨库存的SKU或归属已变更");
            // Consistent lock ordering for opposite transfers.
            jdbc.queryForList("SELECT id FROM o_goods_inventory WHERE id IN (?,?) ORDER BY id FOR UPDATE",invId,n(d,"target_inventory_id"));
            if(knownValue(invId)==null) throw new IllegalArgumentException("调出库存成本未知，请先核定总成本");
            if(n(jdbc.queryForMap("SELECT quantity FROM o_goods_inventory WHERE id=?",n(d,"target_inventory_id")),"quantity")>0
                    && knownValue(n(d,"target_inventory_id"))==null) throw new IllegalArgumentException("调入库存成本未知，请先核定总成本");
            var out=stock.move(invId,-qty,0,null,id,id,by,reason);BigDecimal value=decimal(out,"value_delta");
            if(value==null) throw new IllegalArgumentException("调出库存成本未知，请先核定总成本");
            stock.move(n(d,"target_inventory_id"),qty,0,value.negate(),id+":target",id,by,reason);return;
        }
        long delta=kind.equals("ISSUE")?-qty:kind.equals("VALUATION")?0:qty;
        BigDecimal incoming=kind.equals("VALUATION")?cost:delta>0?cost.multiply(BigDecimal.valueOf(qty)).add(extra):null;
        stock.move(invId,delta,0,incoming,id,id,by,reason);
    }
    private BigDecimal knownValue(long inventoryId) {
        var rows=jdbc.queryForList("SELECT quantity,stock_value FROM ef_stock_balance WHERE inventory_id=?",inventoryId);
        if(rows.isEmpty()) return n(stock.inventory(inventoryId),"quantity")==0?BigDecimal.ZERO:null;
        if(rows.get(0).get("stock_value")==null) return n(rows.get(0),"quantity")==0?BigDecimal.ZERO:null;
        return decimal(rows.get(0),"stock_value");
    }
    private BigDecimal amount(Map<String,Object> d) {
        if(d.get("unit_cost")!=null) return decimal(d,"unit_cost").multiply(BigDecimal.valueOf(Math.max(1,Math.abs(n(d,"quantity"))))).add(decimal(d,"extra_cost"));
        var b=jdbc.queryForList("SELECT * FROM ef_stock_balance WHERE inventory_id=?",n(d,"inventory_id"));
        if(b.isEmpty() || b.get(0).get("stock_value")==null || n(b.get(0),"quantity")==0) return null;
        return decimal(b.get(0),"stock_value").multiply(BigDecimal.valueOf(Math.abs(n(d,"quantity")))).divide(BigDecimal.valueOf(n(b.get(0),"quantity")),2,RoundingMode.HALF_UP);
    }
    private Map<String,Object> policy(String kind) {
        var rows=jdbc.queryForList("SELECT * FROM ef_policy WHERE kind=?",kind);
        return rows.isEmpty()?Map.of("threshold_amount",new BigDecimal("10000"),"first_permission","erp:operations:approve","second_permission","erp:operations:finance","version",0L):rows.get(0);
    }
    public List<Map<String,Object>> policies(Actor actor) {actor.require("read");return KINDS.stream().sorted().map(k->{var p=new LinkedHashMap<>(policy(k));p.put("kind",k);return (Map<String,Object>)p;}).toList();}
    public void savePolicy(Actor actor,Policy p) {
        admin(actor);if(!KINDS.contains(p.kind())) throw new IllegalArgumentException("类型无效");money(p.threshold(),2);
        if(!Set.of("erp:operations:approve","erp:operations:finance").contains(p.firstPermission()) || !Set.of("erp:operations:approve","erp:operations:finance").contains(p.secondPermission())) throw new IllegalArgumentException("审批权限无效");
        tx.executeWithoutResult(s->{
            int count=jdbc.update("UPDATE ef_policy SET threshold_amount=?,first_permission=?,second_permission=?,version=version+1 WHERE kind=? AND version=?",p.threshold(),p.firstPermission(),p.secondPermission(),p.kind(),p.version());
            if(count==0) {if(p.version()!=0) throw new IllegalArgumentException("规则已变化");jdbc.update("INSERT INTO ef_policy VALUES(?,?,?,?,1)",p.kind(),p.threshold(),p.firstPermission(),p.secondPermission());}
            audit("policy:"+p.kind(),"POLICY",actor,JSON.toJSONString(p));
        });
    }
    public void grant(Actor actor,Grant g) {
        admin(actor);if(g.userId()<=0 || g.merchantId()<0 || g.shopId()< -1 || g.warehouseId()< -1) throw new IllegalArgumentException("授权范围无效");
        tx.executeWithoutResult(s->{
            jdbc.update("DELETE FROM ef_scope WHERE user_id=? AND merchant_id=? AND shop_id=? AND warehouse_id=?",g.userId(),g.merchantId(),g.shopId(),g.warehouseId());
            if(!g.remove()) jdbc.update("INSERT INTO ef_scope VALUES(?,?,?,?)",g.userId(),g.merchantId(),g.shopId(),g.warehouseId());
            audit("scope:"+g.userId(),"GRANT",actor,JSON.toJSONString(g));
        });
    }
    public List<Map<String,Object>> grants(Actor actor) {admin(actor);return jdbc.queryForList("SELECT * FROM ef_scope ORDER BY user_id,merchant_id,shop_id,warehouse_id");}
    public List<Map<String,Object>> fields(Actor actor,String kind) {actor.require("read");return jdbc.queryForList("SELECT * FROM ef_field WHERE kind=? ORDER BY field_key",kind);}
    public void saveField(Actor actor,Field f) {
        admin(actor);if(!(KINDS.contains(f.kind()) || Set.of("PRODUCT","SUPPLIER").contains(f.kind())) || f.key()==null || !f.key().matches("[a-z][a-z0-9_]{0,49}") || !Set.of("text","number","date","select","boolean").contains(f.type())) throw new IllegalArgumentException("字段定义无效");
        text(f.label(),100);if(f.options()!=null && (f.options().size()>50 || f.options().stream().anyMatch(o->o==null || o.isBlank() || o.length()>100))) throw new IllegalArgumentException("选项无效");
        if(f.type().equals("select") && (f.options()==null || f.options().isEmpty())) throw new IllegalArgumentException("选择字段需要选项");
        tx.executeWithoutResult(s->{
            String options=JSON.toJSONString(f.options()==null?List.of():f.options());
            int count=jdbc.update("UPDATE ef_field SET label=?,required_flag=?,options_json=?,version=version+1 WHERE kind=? AND field_key=? AND field_type=? AND version=?",f.label(),f.required()?1:0,options,f.kind(),f.key(),f.type(),f.version());
            if(count==0) {if(f.version()!=0) throw new IllegalArgumentException("字段已变化，字段类型不可修改");jdbc.update("INSERT INTO ef_field VALUES(?,?,?,?,?,?,1)",f.kind(),f.key(),f.label(),f.type(),f.required()?1:0,options);}
            audit("field:"+f.kind()+":"+f.key(),"FIELD",actor,JSON.toJSONString(f));
        });
    }
    private void validateAttributes(String kind,Map<String,Object> attributes) {
        Map<String,Object> values=attributes==null?Map.of():attributes;
        if(JSON.toJSONString(values).length()>20000) throw new IllegalArgumentException("扩展字段内容过长");
        var defs=jdbc.queryForList("SELECT * FROM ef_field WHERE kind=?",kind);Set<String> keys=new HashSet<>();
        for(var def:defs) {
            String key=def.get("field_key").toString();keys.add(key);Object value=values.get(key);
            if(value==null || value.toString().isBlank()) {if(n(def,"required_flag")==1) throw new IllegalArgumentException("请填写："+def.get("label"));continue;}
            String str=value.toString();if(str.length()>1000) throw new IllegalArgumentException("字段过长："+key);
            try {switch(def.get("field_type").toString()) {
                case "number" -> new BigDecimal(str);
                case "date" -> LocalDate.parse(str);
                case "boolean" -> {if(!(value instanceof Boolean)) throw new IllegalArgumentException();}
                case "select" -> {if(!JSON.parseArray(def.get("options_json").toString(),String.class).contains(str)) throw new IllegalArgumentException();}
            }} catch(RuntimeException ex) {throw new IllegalArgumentException("字段值无效："+def.get("label"));}
        }
        if(!keys.containsAll(values.keySet())) throw new IllegalArgumentException("包含未定义扩展字段");
    }
    private void masterAccess(Actor actor,String kind,long id,boolean lock) {
        String table=switch(kind) {case "PRODUCT"->"o_goods";case "SUPPLIER"->"erp_supplier";default->throw new IllegalArgumentException("主数据类型无效");};
        var found=jdbc.queryForList("SELECT * FROM "+table+" WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(found.isEmpty()) throw new IllegalArgumentException("主数据不存在");
        if(actor.admin()) return;
        if(kind.equals("SUPPLIER")) {checkScope(actor,found.get(0));return;}
        var args=new ArrayList<Object>();args.add(id);String filter=scope(actor,"i",args);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM o_goods_inventory i WHERE i.goods_id=? AND i.is_delete=0 AND "+filter,Long.class,args.toArray())==0)
            throw new AccessDeniedException("无权访问此商品的扩展字段");
    }
    public Map<String,Object> master(Actor actor,String kind,long id) {
        actor.require("read");masterAccess(actor,kind,id,false);
        var rows=jdbc.queryForList("SELECT * FROM ef_custom_value WHERE kind=? AND record_id=?",kind,id);
        return Map.of("kind",kind,"recordId",id,"fields",fields(actor,kind),"version",rows.isEmpty()?0:n(rows.get(0),"version"),"attributes",rows.isEmpty()?Map.of():JSON.parseObject(rows.get(0).get("attributes_json").toString()));
    }
    public void saveMaster(Actor actor,Master m) {
        actor.require("write");tx.executeWithoutResult(s->{
            masterAccess(actor,m.kind(),m.recordId(),true);validateAttributes(m.kind(),m.attributes());
            String values=JSON.toJSONString(m.attributes()==null?Map.of():m.attributes());
            int count=jdbc.update("UPDATE ef_custom_value SET attributes_json=?,version=version+1 WHERE kind=? AND record_id=? AND version=?",values,m.kind(),m.recordId(),m.version());
            if(count==0) {if(m.version()!=0) throw new IllegalArgumentException("数据已修改，请刷新");jdbc.update("INSERT INTO ef_custom_value VALUES(?,?,?,1)",m.kind(),m.recordId(),values);}
            audit("master:"+m.kind()+":"+m.recordId(),"MASTER",actor,values);
        });
    }
    public void saveReorder(Actor actor,Reorder r) {
        actor.require("write");checkScope(actor,stock.inventory(r.inventoryId()));
        if(r.reorderPoint()<0 || r.targetQuantity()<r.reorderPoint() || r.targetQuantity()>100000000 || r.minOrder()<1 || r.packSize()<1 || r.packSize()>100000000 || r.minOrder()>100000000 || r.leadDays()<0 || r.leadDays()>3650 || r.supplierId()<=0) throw new IllegalArgumentException("补货规则无效");
        money(r.dailyDemand(),4);money(r.unitCost(),2);
        tx.executeWithoutResult(s->{
            int count=jdbc.update("UPDATE ef_reorder SET reorder_point=?,target_quantity=?,min_order=?,pack_size=?,lead_days=?,daily_demand=?,supplier_id=?,unit_cost=?,enabled=?,version=version+1 WHERE inventory_id=? AND version=?",r.reorderPoint(),r.targetQuantity(),r.minOrder(),r.packSize(),r.leadDays(),r.dailyDemand(),r.supplierId(),r.unitCost(),r.enabled()?1:0,r.inventoryId(),r.version());
            if(count==0) {if(r.version()!=0) throw new IllegalArgumentException("补货规则已变化");jdbc.update("INSERT INTO ef_reorder VALUES(?,?,?,?,?,?,?,?,?,?,1)",r.inventoryId(),r.reorderPoint(),r.targetQuantity(),r.minOrder(),r.packSize(),r.leadDays(),r.dailyDemand(),r.supplierId(),r.unitCost(),r.enabled()?1:0);}
            audit("reorder:"+r.inventoryId(),"REORDER",actor,JSON.toJSONString(r));
        });
    }
    public List<Map<String,Object>> replenishment(Actor actor,int page) {
        actor.require("read");var args=new ArrayList<Object>();String filter=scope(actor,"i",args);args.add(offset(page));
        var rows=jdbc.queryForList("SELECT r.*,i.sku_code,i.goods_name,i.available_quantity FROM ef_reorder r JOIN o_goods_inventory i ON i.id=r.inventory_id WHERE i.is_delete=0 AND "+filter+" ORDER BY r.inventory_id LIMIT 50 OFFSET ?",args.toArray());
        rows.forEach(r->suggest(r));return rows;
    }
    private void suggest(Map<String,Object> r) {
        long id=n(r,"inventory_id");
        long inbound=jdbc.queryForObject("SELECT COALESCE(SUM(quantity-received_quantity),0) FROM ef_document WHERE inventory_id=? AND kind='PURCHASE' AND state IN ('DRAFT','PENDING','PENDING_SECOND','APPROVED','POSTED')",Long.class,id);
        long available=r.containsKey("available_quantity")?n(r,"available_quantity"):n(stock.inventory(id),"available_quantity");
        long projected=Math.addExact(available,inbound);
        long demand=decimal(r,"daily_demand").multiply(BigDecimal.valueOf(n(r,"lead_days"))).setScale(0,RoundingMode.CEILING).longValueExact();
        long point=Math.max(n(r,"reorder_point"),demand),target=Math.max(n(r,"target_quantity"),point);
        long needed=n(r,"enabled")==1 && projected<=point?Math.max(n(r,"min_order"),target-projected):0;
        long pack=n(r,"pack_size");long qty=needed==0?0:Math.multiplyExact(Math.floorDiv(Math.addExact(needed,pack-1),pack),pack);
        if(qty>100000000) throw new IllegalArgumentException("建议补货数量超限，请检查日需求和交期");
        r.put("open_purchase_quantity",inbound);r.put("projected_quantity",projected);r.put("suggested_quantity",qty);r.put("effective_reorder_point",point);
        r.put("basis","可用库存 + 工作台未完成采购；日需求为人工配置，不含其他入口采购在途，请核对后制单");
    }
    public Map<String,Object> replenish(Actor actor,long id,String key) {
        actor.require("write");text(key,100);checkScope(actor,stock.inventory(id));
        return tx.execute(s->{
            jdbc.queryForMap("SELECT id FROM o_goods_inventory WHERE id=? FOR UPDATE",id);
            var previous=jdbc.queryForList("SELECT * FROM ef_document WHERE created_by=? AND request_key=?",actor.id(),key);
            if(!previous.isEmpty()) {if(n(previous.get(0),"inventory_id")!=id) throw new IllegalArgumentException("请求键冲突");return previous.get(0);}
            var r=jdbc.queryForMap("SELECT * FROM ef_reorder WHERE inventory_id=?",id);suggest(r);
            if(n(r,"suggested_quantity")==0) throw new IllegalArgumentException("当前无需补货");
            return create(actor,new Draft("PURCHASE",id,n(r,"suggested_quantity"),decimal(r,"unit_cost"),BigDecimal.ZERO,n(r,"supplier_id"),null,"按库存与交期生成补货采购",Map.of(),key));
        });
    }
    public List<Map<String,Object>> configurationAudit(Actor actor,int page) {
        admin(actor);return jdbc.queryForList("SELECT * FROM ef_audit WHERE action IN ('GRANT','POLICY','FIELD','REORDER','MASTER') ORDER BY created_at DESC LIMIT 50 OFFSET ?",offset(page));
    }
    private void audit(String id,String action,Actor actor,String detail) {jdbc.update("INSERT INTO ef_audit VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),id,action,actor.id(),System.currentTimeMillis(),detail);}
    private static void state(String current,String expected) {if(!current.equals(expected)) throw new IllegalArgumentException("当前状态不能执行此操作");}
    private static void admin(Actor actor) {if(!actor.admin()) throw new AccessDeniedException("仅管理员可维护配置与数据授权");}
    private static void text(String v,int max) {if(v==null || v.isBlank() || v.length()>max) throw new IllegalArgumentException("必填字段为空或超长");}
    private static void money(BigDecimal v,int scale) {if(v==null || v.signum()<0 || v.stripTrailingZeros().scale()>scale || v.compareTo(new BigDecimal("99999999"))>0) throw new IllegalArgumentException("金额/数值无效或超限");}
    private static boolean sameAmount(BigDecimal a,BigDecimal b) {return a==null?b==null:b!=null && a.compareTo(b)==0;}
    private void supplier(long id,long merchant) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM erp_supplier WHERE id=? AND merchant_id=? AND COALESCE(is_delete,0)=0 AND COALESCE(disable,0)=0",Long.class,id,merchant)==0)
            throw new IllegalArgumentException("供应商不存在、已停用或不属于当前商户");
    }
}
