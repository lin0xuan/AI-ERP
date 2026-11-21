package com.aierp.erp.datacenter;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DataCenterServiceTest {
    JdbcTemplate jdbc;
    DataCenterService service;
    static final LocalDate DAY=LocalDate.of(2026,11,21);
    static final long TIME=DAY.atStartOfDay(DataCenterService.ZONE).toEpochSecond();
    @BeforeEach void setup() {
        String url=System.getenv("DATA_CENTER_TEST_JDBC_URL");
        if(url!=null && !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/datacenter_test[a-zA-Z0-9_]*.*"))
            throw new IllegalStateException("测试仅允许独立 datacenter_test 数据库");
        var ds=new DriverManagerDataSource(url==null?"jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1":url,"root","");
        jdbc=new JdbcTemplate(ds);
        if(url!=null) {
            for(var source:DataSources.ALL) jdbc.execute("DROP TABLE IF EXISTS "+source.table());
            for(String table:List.of("dc_issue","dc_fact","dc_job","dc_mapping_history","dc_mapping","dc_state")) jdbc.execute("DROP TABLE IF EXISTS "+table);
        }
        service=new DataCenterService(jdbc,new DataSourceTransactionManager(ds));service.initialize();service.initialize();
        for(var source:DataSources.ALL) {
            var cols=Arrays.stream(source.columns().split(",")).map(c->c.equals("id")?"id BIGINT PRIMARY KEY":c+" VARCHAR(200)").toList();
            jdbc.execute("CREATE TABLE "+source.table()+"("+String.join(",",cols)+")");
        }
    }
    void order(long id,long shop,String key,long cents,long time) {
        jdbc.update("INSERT INTO oms_shop_order(id,merchant_id,shop_id,shop_type,order_id,pay_time,order_status,payment_amount) VALUES(?,1,?,1,?,?,3,?)",id,shop,key,time,cents);
    }
    void refund(long id,long shop,String key,String order,long cents,int status) {
        jdbc.update("INSERT INTO oms_shop_refund(id,merchant_id,shop_id,shop_type,after_id,order_id,refund_status,refund_amount) VALUES(?,1,?,1,?,?,?,?)",id,shop,key,order,status,cents);
    }
    String sync() {
        var job=service.synchronize(1);assertEquals("SUCCESS",job.get("status"),job.toString());return job.get("id").toString();
    }
    Map<String,Object> metrics(String batch) {return service.metrics(DAY,DAY,null,batch);}
    void amount(String expected,Object actual) {assertEquals(0,new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));}
    @Test void partialRefundsAndShopNamespacesHaveExactMoneyAndShanghaiBoundaries() {
        order(1,1,"same",10001,TIME);order(2,2,"same",20000,TIME+86399);
        order(3,1,"outside",99999,TIME-1);order(4,1,"tomorrow",99999,TIME+86400);
        refund(1,1,"r1","same",2000,3);refund(2,1,"pending","same",5000,2);
        refund(3,2,"r1","same",3000,3);
        String batch=sync();var result=metrics(batch);
        amount("300.01",result.get("paidAmount"));amount("50",result.get("refundAmount"));amount("250.01",result.get("netSales"));
        amount("80.01",service.metrics(DAY,DAY,"1:1",batch).get("netSales"));
    }
    @Test void repeatedSyncDoesNotDoubleCountAndOldSnapshotsRemainStable() {
        order(1,1,"a",10000,TIME);String old=sync();sync();amount("100",metrics(null).get("netSales"));
        refund(1,1,"r","a",4000,3);sync();amount("60",metrics(null).get("netSales"));amount("100",metrics(old).get("netSales"));
        jdbc.update("DELETE FROM oms_shop_refund");sync();amount("100",metrics(null).get("netSales"));
    }
    @Test void duplicatesAndOrphansAreQuarantinedInsteadOfChoosingArbitraryRecord() {
        order(1,1,"a",10000,TIME);order(2,1,"a",10000,TIME);
        refund(1,1,"r","a",4000,3);refund(2,1,"r2","missing",1000,3);
        String batch=sync();amount("0",metrics(batch).get("netSales"));
        assertEquals(4L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE severity='ERROR'",Long.class));
    }
    @Test void failedRefreshPreservesPublishedSnapshotAndRecordsFailure() {
        order(1,1,"a",10000,TIME);String old=sync();jdbc.execute("DROP TABLE erp_purchase_order");
        var failed=service.synchronize(1);assertEquals("FAILED",failed.get("status"));
        assertEquals(old,service.publishedBatch(null));amount("100",metrics(null).get("netSales"));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_fact WHERE batch_id=?",Long.class,failed.get("id")));
    }
    @Test void mappingChangesOnlyAffectNewSnapshotsAndAreAudited() {
        order(1,1,"a",10000,TIME);String old=sync();
        service.saveMapping(new DataCenterService.Mapping("shop","1:1","SHOP-A","统一店铺"),8);
        String current=sync();amount("100",service.metrics(DAY,DAY,"SHOP-A",current).get("netSales"));
        amount("0",service.metrics(DAY,DAY,"SHOP-A",old).get("netSales"));
        assertEquals(8L,jdbc.queryForObject("SELECT actor_id FROM dc_mapping_history",Long.class));
        assertThrows(IllegalArgumentException.class,()->service.saveMapping(new DataCenterService.Mapping("sql","a","b","c"),8));
    }
    @Test void inventorySnapshotAndPurchaseAmountsUseDifferentTimeAndUnits() {
        jdbc.update("INSERT INTO o_goods_inventory(id,merchant_id,shop_id,sku_id,warehouse_id,quantity,locked_quantity,available_quantity,is_delete) VALUES(1,1,1,10,1,15,3,12,0)");
        jdbc.update("INSERT INTO erp_purchase_order(id,merchant_id,shop_id,order_num,order_time,order_amount,status) VALUES(1,1,1,'p',?,'123.45',1)",TIME);
        var result=metrics(sync());amount("12",result.get("availableStock"));amount("123.45",result.get("purchaseAmount"));
        amount("12",service.metrics(DAY.minusDays(1),DAY.minusDays(1),null,null).get("availableStock"));
    }
    @Test void malformedDatesAmountsAndUnpaidOrdersCannotPolluteMetrics() {
        order(1,1,"negative",-1,TIME);order(2,1,"millis",12345,TIME*1000);order(3,1,"unpaid",10000,0);
        jdbc.update("UPDATE oms_shop_order SET order_status=21 WHERE id=3");
        amount("123.45",metrics(sync()).get("netSales"));
        assertThrows(IllegalArgumentException.class,()->service.metrics(DAY,DAY.minusDays(1),null,null));
        assertThrows(IllegalArgumentException.class,()->service.rows("order; DROP TABLE dc_fact",null,1,20,false));
    }
    @Test void aiToolReturnsTheSameSnapshotAndCannotBypassPermission() {
        order(1,1,"a",10001,TIME);String batch=sync();
        var tool=new DataCenterTools(service,true);
        var answer=JSON.parseObject(tool.getDataCenterMetrics(DAY.toString(),DAY.toString(),null,batch));
        amount(metrics(batch).get("netSales").toString(),answer.get("netSales"));assertEquals(batch,answer.get("batchId"));
        assertTrue(new DataCenterTools(service,false).getDataCenterMetrics(null,null,null,null).contains("没有"));
        assertFalse(DataCenterAccess.allowed("read"));
        jdbc.update("UPDATE dc_job SET metric_version='unknown-version' WHERE id=?",batch);
        assertThrows(IllegalArgumentException.class,()->metrics(batch));
    }
    @Test void lineageContainsOnlySelectedBusinessFieldsAndExactSourceIdentity() {
        order(1,1,"a",10000,TIME);String batch=sync();
        var trace=service.lineage("order",1,batch);assertEquals("oms_shop_order",trace.get("sourceTable"));
        assertFalse(JSON.toJSONString(trace).contains("receiver_phone"));
        assertThrows(IllegalArgumentException.class,()->service.lineage("order",999,batch));
    }
    @Test void crossBorderAndMissingIdentityCannotBeAggregatedAsCny() {
        order(1,1,"usd",10000,TIME);order(2,1,"missing",10000,TIME);
        jdbc.update("UPDATE oms_shop_order SET shop_type=800 WHERE id=1");
        jdbc.update("UPDATE oms_shop_order SET merchant_id=NULL WHERE id=2");
        amount("0",metrics(sync()).get("netSales"));
        assertEquals(2L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE severity='ERROR'",Long.class));
    }
    @Test void readerCannotManageOrRunSyncAndAnonymousCannotRead() {
        var controller=new DataCenterController(service);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,controller::catalog);
        var user=new com.aierp.security.LoginUser(42L,1L,new com.aierp.model.entity.SysUser(),Set.of("data:center:read"));
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user,null,List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            assertDoesNotThrow(controller::catalog);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,controller::sync);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.mapping(new DataCenterService.Mapping("shop","a","b","c")));
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void batchingDoesNotTruncateAtOldAiLimit() {
        List<Object[]> rows=new ArrayList<>();
        for(int i=1;i<=2001;i++) rows.add(new Object[]{i,"order-"+i,TIME});
        jdbc.batchUpdate("INSERT INTO oms_shop_order(id,merchant_id,shop_id,shop_type,order_id,pay_time,order_status,payment_amount) VALUES(?,1,1,1,?,?,3,101)",rows);
        var result=metrics(sync());amount("2021.01",result.get("paidAmount"));
        assertEquals(2001L,((Number)result.get("paidOrders")).longValue());
    }
    @Test void duplicateRefundsDoNotDoubleDeductAndOverRefundIsVisible() {
        order(1,1,"a",10000,TIME);
        refund(1,1,"duplicate","a",2000,3);refund(2,1,"duplicate","a",2000,3);
        refund(3,1,"valid","a",12000,3);
        var result=metrics(sync());amount("-20",result.get("netSales"));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE rule_code='REFUND_EXCEEDS_PAYMENT'",Long.class));
    }
    @Test void unpaidVoidsStayOutOfMetricsWithoutQualityErrors() {
        order(1,1,"paid",10000,TIME);
        order(2,1,"paid-cancel",5000,TIME);
        jdbc.update("UPDATE oms_shop_order SET order_status=11 WHERE id=2");
        for(int[] sample:new int[][]{{3,0},{4,11},{5,13},{6,21},{7,22},{8,29}})
            jdbc.update("INSERT INTO oms_shop_order(id,merchant_id,shop_id,shop_type,order_id,pay_time,order_status,payment_amount) VALUES(?,1,1,1,?,NULL,?,100)",sample[0],"void-"+sample[0],sample[1]);
        jdbc.update("INSERT INTO oms_shop_order(id,merchant_id,shop_id,shop_type,order_id,pay_time,order_status,payment_amount) VALUES(10,1,1,1,'zero-pay',0,11,100)");
        jdbc.update("INSERT INTO oms_shop_order(id,merchant_id,shop_id,shop_type,order_id,pay_time,order_status,payment_amount) VALUES(9,1,1,1,'broken',NULL,1,100)");
        String batch=sync();
        amount("150",metrics(batch).get("paidAmount"));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE severity='ERROR'",Long.class));
        assertEquals("INVALID_VALUE",jdbc.queryForObject("SELECT rule_code FROM dc_issue WHERE source_id=9",String.class));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE source_id IN (3,4,5,6,7,8,10)",Long.class));
    }
    @Test void itemsFollowPlatformOrderAndErpSkuCode() {
        order(1,1,"A",10000,TIME);
        jdbc.update("INSERT INTO o_goods_inventory(id,merchant_id,shop_id,goods_id,goods_num,sku_id,sku_code,warehouse_id,quantity,locked_quantity,available_quantity,is_delete) VALUES(1,1,1,9,'G-9',10,'SKU-1',1,5,0,5,0)");
        jdbc.update("INSERT INTO o_goods_inventory(id,merchant_id,shop_id,goods_id,goods_num,sku_id,sku_code,warehouse_id,quantity,locked_quantity,available_quantity,is_delete) VALUES(2,1,1,9,'G-9',77,'AAA',1,1,0,1,0)");
        jdbc.update("INSERT INTO o_goods_inventory(id,merchant_id,shop_id,goods_id,goods_num,sku_id,sku_code,warehouse_id,quantity,locked_quantity,available_quantity,is_delete) VALUES(3,1,1,9,'G-9',77,'BBB',1,1,0,1,0)");
        service.saveMapping(new DataCenterService.Mapping("sku","1:1:1:mapped","MANUAL","手工规格"),8);
        insertItem(1,"A","psku","SKU-1","10","9");
        insertItem(2,"MISSING","x",null,"0","0");
        insertItem(3,"","y",null,"0","0");
        insertItem(4,"A","mapped","RAW","0","0");
        insertItem(5,"A","only-erp",null,"10","9");
        insertItem(6,"A","conflict",null,"77","9");
        String batch=sync();
        assertEquals("1:1:1:A",fact(batch,"item",1,"parent_key"));
        assertEquals("SKU-1",fact(batch,"item",1,"sku_code"));
        assertEquals("G-9",fact(batch,"item",1,"product_code"));
        assertEquals("SKU-1",fact(batch,"inventory",1,"sku_code"));
        assertEquals("G-9",fact(batch,"inventory",1,"product_code"));
        assertEquals("MANUAL",fact(batch,"item",4,"sku_code"));
        assertEquals("SKU-1",fact(batch,"item",5,"sku_code"));
        assertEquals("1:1:1:conflict",fact(batch,"item",6,"sku_code"));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE kind='item' AND source_id=1",Long.class));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE kind='item' AND source_id=2 AND rule_code='ORPHAN_ITEM'",Long.class));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE kind='item' AND source_id=2 AND rule_code='UNMAPPED_SKU'",Long.class));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE kind='item' AND source_id=3 AND rule_code='MISSING_ORDER_NO'",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE kind='item' AND source_id=3 AND rule_code='ORPHAN_ITEM'",Long.class));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM dc_issue WHERE kind='item' AND source_id=6 AND rule_code='UNMAPPED_SKU'",Long.class));
        assertTrue(JSON.toJSONString(service.lineage("item",1,batch).get("parent")).contains("1:1:1:A"));
        assertTrue(JSON.toJSONString(service.lineage("order",1,batch).get("related")).contains("SKU-1"));
    }
    void insertItem(long id,String orderId,String skuId,String skuCode,String erpSku,String erpGoods) {
        jdbc.update("INSERT INTO oms_shop_order_item(id,merchant_id,shop_id,shop_type,order_id,sku_id,product_id,sku_code,erp_goods_id,erp_goods_sku_id,quantity) VALUES(?,1,1,1,?,?,?,?,?,?,1)",
                id,orderId,skuId,"p"+id,skuCode,erpGoods,erpSku);
    }
    String fact(String batch,String kind,long id,String column) {
        return jdbc.queryForObject("SELECT "+column+" FROM dc_fact WHERE batch_id=? AND kind=? AND source_id=?",String.class,batch,kind,id);
    }
}
