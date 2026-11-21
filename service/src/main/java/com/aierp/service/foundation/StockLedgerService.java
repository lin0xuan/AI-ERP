package com.aierp.service.foundation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Quantity and moving-average valuation are committed with their append-only journal. */
@Service
public class StockLedgerService implements InitializingBean {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    @Value("${erp.foundation.initialize-schema:true}") private boolean initializeSchema=true;
    public StockLedgerService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager);
    }
    @Override public void afterPropertiesSet() {
        if(initializeSchema) new ResourceDatabasePopulator(new ClassPathResource("db/erp-foundation.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
    }
    public Map<String,Object> inventory(long id) {
        var rows=jdbc.queryForList("SELECT * FROM o_goods_inventory WHERE id=? AND is_delete=0",id);
        if(rows.isEmpty()) throw new IllegalArgumentException("库存记录不存在");
        return rows.get(0);
    }
    public Map<String,Object> move(long id,long delta,long lockedDelta,BigDecimal incomingValue,
            String key,String source,String actor,String reason) {
        if(key==null || key.isBlank() || key.length()>100 || source==null || source.length()>100)
            throw new IllegalArgumentException("库存业务键无效");
        return tx.execute(status -> {
            var inv=jdbc.queryForMap("SELECT * FROM o_goods_inventory WHERE id=? AND is_delete=0 FOR UPDATE",id);
            var existing=jdbc.queryForList("SELECT * FROM ef_stock_ledger WHERE id=? FOR UPDATE",key);
            if(!existing.isEmpty()) {
                var e=existing.get(0);
                if(n(e,"inventory_id")!=id || n(e,"quantity_delta")!=delta || n(e,"locked_delta")!=lockedDelta
                        || !source.equals(e.get("source")) || delta>0 && !sameMoney(incomingValue,decimal(e,"value_delta"))
                        || delta==0 && lockedDelta==0 && incomingValue!=null && !sameMoney(incomingValue,decimal(e,"value_after"))) throw new IllegalArgumentException("业务键已用于其他库存操作");
                return e;
            }
            long qty=n(inv,"quantity"),locked=n(inv,"locked_quantity");
            if(qty<0 || locked<0 || locked>qty || n(inv,"available_quantity")!=qty-locked)
                throw new IllegalArgumentException("原库存数量关系不平，请先盘点核对");
            var balances=jdbc.queryForList("SELECT * FROM ef_stock_balance WHERE inventory_id=? FOR UPDATE",id);
            if(balances.isEmpty()) {
                BigDecimal initial=qty==0?BigDecimal.ZERO:null;
                jdbc.update("INSERT INTO ef_stock_balance VALUES(?,?,?,?,0)",id,qty,locked,initial);
                journal("opening:"+id,id,"OPENING",qty,locked,initial,qty,locked,initial,0,"system","接管时数量快照；非零期初库存成本待核定",null);
            }
            var balance=jdbc.queryForMap("SELECT * FROM ef_stock_balance WHERE inventory_id=? FOR UPDATE",id);
            if(n(balance,"quantity")!=qty || n(balance,"locked_quantity")!=locked)
                throw new IllegalArgumentException("库存与账本不一致，存在未记账变动，请核对来源");
            long after=Math.addExact(qty,delta),afterLocked=Math.addExact(locked,lockedDelta);
            if(after<0 || afterLocked<0 || afterLocked>after || after>Integer.MAX_VALUE)
                throw new IllegalArgumentException("可用库存不足、解锁超量或数量超限");
            BigDecimal value=decimal(balance,"stock_value"),change=null;
            if(qty==0 && value==null) value=BigDecimal.ZERO;
            if(incomingValue!=null && (incomingValue.signum()<0 || incomingValue.precision()>24 || incomingValue.scale()>6))
                throw new IllegalArgumentException("成本金额无效");
            if(delta>0) {
                if(value==null && incomingValue!=null)
                    throw new IllegalArgumentException("库存成本未知，请先核定总成本后再按成本入库");
                change=incomingValue;
            }
            if(delta<0) change=value==null?null:after==0?value.negate():value.multiply(BigDecimal.valueOf(delta)).divide(BigDecimal.valueOf(qty),6,RoundingMode.HALF_UP);
            // A valuation voucher establishes the total. Unknown opening cost is recorded as the whole amount, not as zero.
            if(delta==0 && lockedDelta==0 && incomingValue!=null) {
                if(qty==0 && incomingValue.signum()!=0) throw new IllegalArgumentException("零库存不能设置非零成本");
                change=value==null?incomingValue:incomingValue.subtract(value);value=incomingValue;
            } else if(delta!=0) value=value==null || change==null?null:value.add(change);
            if(after==0) value=BigDecimal.ZERO;
            long version=n(balance,"version")+1;
            jdbc.update("UPDATE o_goods_inventory SET quantity=?,locked_quantity=?,available_quantity=? WHERE id=?",after,afterLocked,after-afterLocked,id);
            jdbc.update("UPDATE ef_stock_balance SET quantity=?,locked_quantity=?,stock_value=?,version=? WHERE inventory_id=?",after,afterLocked,value,version,id);
            journal(key,id,source,delta,lockedDelta,change,after,afterLocked,value,version,actor,reason,null);
            return jdbc.queryForMap("SELECT * FROM ef_stock_ledger WHERE id=?",key);
        });
    }
    public void reverse(String entryId,String actor,String reason) {
        tx.executeWithoutResult(status -> {
            var original=jdbc.queryForMap("SELECT * FROM ef_stock_ledger WHERE id=?",entryId);
            long id=n(original,"inventory_id");
            var inventory=jdbc.queryForMap("SELECT * FROM o_goods_inventory WHERE id=? FOR UPDATE",id);
            if(!jdbc.queryForList("SELECT id FROM ef_stock_ledger WHERE reversal_of=?",entryId).isEmpty()) return;
            var b=jdbc.queryForMap("SELECT * FROM ef_stock_balance WHERE inventory_id=? FOR UPDATE",id);
            if(n(b,"version")!=n(original,"version")) throw new IllegalArgumentException("已有后续库存变动，不能直接撤销；请使用新的调整单");
            var previous=jdbc.queryForMap("SELECT * FROM ef_stock_ledger WHERE inventory_id=? AND version=?",id,n(b,"version")-1);
            long qty=n(previous,"quantity_after"),locked=n(previous,"locked_after");
            if(n(inventory,"quantity")!=n(b,"quantity") || n(inventory,"locked_quantity")!=n(b,"locked_quantity") || n(inventory,"available_quantity")!=n(b,"quantity")-n(b,"locked_quantity"))
                throw new IllegalArgumentException("库存与账本不一致");
            BigDecimal value=decimal(previous,"value_after"),old=decimal(b,"stock_value");
            long version=n(b,"version")+1;
            jdbc.update("UPDATE o_goods_inventory SET quantity=?,locked_quantity=?,available_quantity=? WHERE id=?",qty,locked,qty-locked,id);
            jdbc.update("UPDATE ef_stock_balance SET quantity=?,locked_quantity=?,stock_value=?,version=? WHERE inventory_id=?",qty,locked,value,version,id);
            journal(UUID.randomUUID().toString(),id,"REVERSAL",-n(original,"quantity_delta"),-n(original,"locked_delta"),
                    value==null || old==null?null:value.subtract(old),qty,locked,value,version,actor,reason,entryId);
        });
    }
    private void journal(String key,long id,String source,long delta,long lock,BigDecimal change,long qty,long locked,
            BigDecimal value,long version,String actor,String reason,String reversal) {
        jdbc.update("INSERT INTO ef_stock_ledger VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",key,id,source,delta,lock,change,qty,locked,value,version,
                actor==null?"system":actor,reason==null?"":reason,System.currentTimeMillis(),reversal);
    }
    public static long n(Map<String,Object> row,String key) {Object v=row.get(key);return v==null?0:((Number)v).longValue();}
    public static BigDecimal decimal(Map<String,Object> row,String key) {return row.get(key)==null?null:new BigDecimal(row.get(key).toString());}
    private static boolean sameMoney(BigDecimal a,BigDecimal b) {return a==null?b==null:b!=null && a.compareTo(b)==0;}
    public void requireLegacyPurchase(long id) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM ef_document WHERE purchase_id=?",Long.class,id)>0)
            throw new IllegalArgumentException("此采购单由业务工作台管理，请在工作台收货或取消，避免重复记账");
    }
}
