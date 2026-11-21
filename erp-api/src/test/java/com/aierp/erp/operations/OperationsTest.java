package com.aierp.erp.operations;

import com.aierp.service.foundation.StockLedgerService;
import com.aierp.service.ErpPurchaseOrderService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.aierp.service.foundation.StockLedgerService.*;

class OperationsTest {
    JdbcTemplate jdbc;StockLedgerService stock;OperationsService service;ErpPurchaseOrderService purchases;
    final OperationsService.Actor admin=new OperationsService.Actor(1,true,Set.of());
    final OperationsService.Actor maker=new OperationsService.Actor(2,false,Set.of("erp:operations:read","erp:operations:write","erp:operations:post"));
    final OperationsService.Actor approver=new OperationsService.Actor(3,false,Set.of("erp:operations:read","erp:operations:approve"));
    @BeforeEach void setup() {
        String url=System.getenv("OPERATIONS_TEST_JDBC_URL");
        if(url!=null && !url.matches("jdbc:mysql://127\\.0\\.0\\.1:23316/operations_test.*")) throw new IllegalStateException("仅允许独立 operations_test 数据库");
        var ds=new DriverManagerDataSource(url==null?"jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000":url,url==null?"sa":"root","");
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);
        if(url!=null) for(String table:List.of("ef_stock_balance","ef_stock_ledger","ef_scope","ef_policy","ef_document","ef_audit","ef_reorder","ef_field","ef_custom_value","o_goods_inventory","erp_supplier","o_goods_sku","o_goods","erp_purchase_order","erp_purchase_order_item")) jdbc.execute("DROP TABLE IF EXISTS "+table);
        stock=new StockLedgerService(jdbc,manager);stock.afterPropertiesSet();stock.afterPropertiesSet();
        purchases=mock(ErpPurchaseOrderService.class);service=new OperationsService(jdbc,stock,purchases,manager);
        jdbc.execute("CREATE TABLE o_goods_inventory(id BIGINT PRIMARY KEY,merchant_id BIGINT,shop_id BIGINT,warehouse_id BIGINT,sku_id BIGINT,sku_code VARCHAR(30),goods_id BIGINT,goods_num VARCHAR(30),goods_name VARCHAR(100),quantity BIGINT,locked_quantity BIGINT,available_quantity BIGINT,stock_status INT,is_delete INT)");
        jdbc.execute("CREATE TABLE erp_supplier(id BIGINT PRIMARY KEY,merchant_id BIGINT,is_delete INT,disable INT)");
        jdbc.execute("CREATE TABLE o_goods(id BIGINT PRIMARY KEY,merchant_id BIGINT,shop_id BIGINT)");
        jdbc.execute("CREATE TABLE o_goods_sku(id BIGINT PRIMARY KEY,merchant_id BIGINT)");
        jdbc.execute("CREATE TABLE erp_purchase_order(id BIGINT PRIMARY KEY,order_num VARCHAR(30),warehouse_id BIGINT,ship_amount DECIMAL(6,2),status INT,received_time TIMESTAMP NULL,stock_in_time TIMESTAMP NULL)");
        jdbc.execute("CREATE TABLE erp_purchase_order_item(id BIGINT PRIMARY KEY,order_id BIGINT,order_num VARCHAR(30),inQty BIGINT,status INT,locationId INT)");
        jdbc.update("INSERT INTO erp_supplier VALUES(1,10,0,0)");
        jdbc.update("INSERT INTO o_goods VALUES(1,10,1)");jdbc.update("INSERT INTO o_goods_sku VALUES(1,10)");
        inv(1,10,1,10);inv(2,20,2,10);inv(3,10,2,0);
        service.grant(admin,new OperationsService.Grant(2,10,1,-1,false));service.grant(admin,new OperationsService.Grant(3,10,-1,-1,false));
    }
    void inv(long id,long merchant,long shop,long qty) {jdbc.update("INSERT INTO o_goods_inventory VALUES(?,?,?,?,1,'SKU',1,'G','商品',?,0,?,1,0)",id,merchant,shop,id,qty,qty);}
    void value(String expected,Object actual) {assertNotNull(actual);assertEquals(0,new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));}
    Map<String,Object> move(long delta,BigDecimal cost,String key) {return stock.move(1,delta,0,cost,key,"test","2","测试");}
    OperationsService.Draft draft(String kind,long qty,BigDecimal cost) {return new OperationsService.Draft(kind,1,qty,cost,BigDecimal.ZERO,1L,null,"业务原因",Map.of(),UUID.randomUUID().toString());}
    Map<String,Object> act(OperationsService.Actor who,Map<String,Object> d,String action) {return service.act(who,d.get("id").toString(),new OperationsService.Action(n(d,"version"),action,"测试原因",null));}
    @Test void movingAverageAndReversalPreserveMoneyAndJournal() {
        move(0,new BigDecimal("100"),"v");move(10,new BigDecimal("200"),"r");
        var issue=move(-5,null,"i");value("-75",issue.get("value_delta"));value("225",issue.get("value_after"));
        stock.reverse("i","2","撤销");value("300",jdbc.queryForObject("SELECT stock_value FROM ef_stock_balance WHERE inventory_id=1",BigDecimal.class));
        assertEquals(5L,jdbc.queryForObject("SELECT COUNT(*) FROM ef_stock_ledger",Long.class));
        value("100",jdbc.queryForObject("SELECT value_delta FROM ef_stock_ledger WHERE id='v'",BigDecimal.class));
        value("300",jdbc.queryForObject("SELECT COALESCE(SUM(value_delta),0) FROM ef_stock_ledger WHERE inventory_id=1",BigDecimal.class));
        stock.reverse("i","2","重试");assertEquals(20,n(stock.inventory(1),"quantity"));
    }
    @Test void unknownCostIsNeverReportedAsZeroAndCannotCancelPastSubsequentMovement() {
        assertNull(move(2,null,"legacy").get("value_after"));move(-1,null,"issue");
        assertThrows(IllegalArgumentException.class,()->stock.reverse("legacy","2","撤销"));
    }
    @Test void idempotencyAndDifferentIntentRejected() {
        move(0,new BigDecimal("100"),"seed");
        move(2,new BigDecimal("12"),"same");move(2,new BigDecimal("12.0"),"same");assertEquals(12,n(stock.inventory(1),"quantity"));
        assertThrows(IllegalArgumentException.class,()->move(3,new BigDecimal("12"),"same"));
        assertThrows(IllegalArgumentException.class,()->move(2,new BigDecimal("15"),"same"));
    }
    @Test void insufficientStockRollsBackJournalAndOpening() {
        assertThrows(IllegalArgumentException.class,()->move(-11,null,"bad"));assertEquals(10,n(stock.inventory(1),"quantity"));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM ef_stock_ledger",Long.class));
        assertThrows(IllegalArgumentException.class,()->stock.move(1,0,11,null,"lock","test","2","锁库"));
        stock.move(1,0,8,null,"lock","test","2","锁库");
        assertThrows(IllegalArgumentException.class,()->move(-3,null,"bad2"));
    }
    @Test void concurrentIssuesCannotOversell() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> request=()->{start.await();try {move(-7,null,UUID.randomUUID().toString());return true;}catch(IllegalArgumentException ex){return false;}};
            var a=executor.submit(request);var b=executor.submit(request);start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(3,n(stock.inventory(1),"quantity"));
        } finally {executor.shutdownNow();}
    }
    @Test void scopeAppliesToListsDetailLedgerAndAi() {
        assertFalse(service.scoped(new OperationsService.Actor(9,false,Set.of("erp:operations:read"))));
        assertTrue(service.scoped(maker));
        assertEquals(1,service.inventories(maker,1).size());
        assertThrows(AccessDeniedException.class,()->service.ledger(maker,2,1));
        assertThrows(AccessDeniedException.class,()->new OperationsTools(service,maker).getStockLedger(2,1));
        var outsider=service.create(admin,new OperationsService.Draft("RECEIPT",2,1,BigDecimal.ONE,BigDecimal.ZERO,null,null,"入库",Map.of(),"outside"));
        assertThrows(AccessDeniedException.class,()->service.detail(maker,outsider.get("id").toString()));
        assertTrue(service.documents(maker,1).isEmpty());
        service.grant(admin,new OperationsService.Grant(2,10,1,-1,true));assertTrue(service.inventories(maker,1).isEmpty());
    }
    @Test void approvalLifecycleRejectsSelfApprovalStaleVersionAndDuplicatePosting() {
        move(0,new BigDecimal("100"),"seed");
        var d=service.create(maker,draft("RECEIPT",5,BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,()->act(maker,d,"post"));
        var submitted=act(maker,d,"submit");
        var makerWithApproval=new OperationsService.Actor(2,false,Set.of("erp:operations:read","erp:operations:approve"));
        assertThrows(IllegalArgumentException.class,()->act(makerWithApproval,submitted,"approve"));
        var approved=act(approver,submitted,"approve");var posted=act(maker,approved,"post");
        assertEquals(15,n(stock.inventory(1),"quantity"));
        assertThrows(IllegalArgumentException.class,()->act(maker,approved,"post"));
        act(maker,posted,"cancel");assertEquals(10,n(stock.inventory(1),"quantity"));
    }
    @Test void approvalPolicyIsSnapshottedAndRequiresDifferentSecondApprover() {
        service.savePolicy(admin,new OperationsService.Policy("RECEIPT",BigDecimal.ZERO,"erp:operations:approve","erp:operations:finance",0));
        var d=act(maker,service.create(maker,draft("RECEIPT",1,BigDecimal.ONE)),"submit");
        service.savePolicy(admin,new OperationsService.Policy("RECEIPT",new BigDecimal("99999"),"erp:operations:approve","erp:operations:approve",1));
        d=act(approver,d,"approve");assertEquals("PENDING_SECOND",d.get("state"));
        var stage=d;assertThrows(AccessDeniedException.class,()->act(approver,stage,"approve"));
        d=act(admin,d,"approve");assertEquals("APPROVED",d.get("state"));
    }
    @Test void customFieldsAreValidatedServerSide() {
        service.saveField(admin,new OperationsService.Field("RECEIPT","batch","批号","text",true,List.of(),0));
        assertThrows(IllegalArgumentException.class,()->service.create(maker,draft("RECEIPT",1,BigDecimal.ONE)));
        var d=new OperationsService.Draft("RECEIPT",1,1,BigDecimal.ONE,BigDecimal.ZERO,null,null,"入库",Map.of("batch","B001"),"valid");
        assertTrue(service.create(maker,d).get("attributes_json").toString().contains("B001"));
        assertThrows(AccessDeniedException.class,()->service.saveField(maker,new OperationsService.Field("RECEIPT","x","x","text",false,List.of(),0)));
    }
    @Test void replenishmentUsesLeadTimeAndPackSizeAndAvoidsDuplicateDrafts() {
        service.saveReorder(maker,new OperationsService.Reorder(1,8,40,5,6,7,new BigDecimal("2"),1,BigDecimal.TEN,true,0));
        var r=service.replenishment(maker,1).get(0);assertEquals(30,n(r,"suggested_quantity"));
        var d=service.replenish(maker,1,"reorder-1");assertEquals(d.get("id"),service.replenish(maker,1,"reorder-1").get("id"));
        assertEquals(0,n(service.replenishment(maker,1).get(0),"suggested_quantity"));
        assertThrows(IllegalArgumentException.class,()->service.replenish(maker,1,"reorder-2"));
    }
    @Test void transferIsAtomicAndRestoresBothSidesOnCancellation() {
        service.grant(admin,new OperationsService.Grant(2,10,2,-1,false));move(0,new BigDecimal("100"),"valuation");
        var draft=new OperationsService.Draft("TRANSFER",1,4,null,BigDecimal.ZERO,null,3L,"调拨",Map.of(),"transfer");
        var d=act(maker,service.create(maker,draft),"submit");d=act(approver,d,"approve");d=act(maker,d,"post");
        assertEquals(6,n(stock.inventory(1),"quantity"));assertEquals(4,n(stock.inventory(3),"quantity"));
        value("40",jdbc.queryForObject("SELECT stock_value FROM ef_stock_balance WHERE inventory_id=3",BigDecimal.class));
        act(maker,d,"cancel");assertEquals(10,n(stock.inventory(1),"quantity"));assertEquals(0,n(stock.inventory(3),"quantity"));
    }
    @Test void externalQuantityChangesAreDetected() {
        move(0,new BigDecimal("100"),"v");jdbc.update("UPDATE o_goods_inventory SET quantity=12,available_quantity=12 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->move(-1,null,"bad"));
    }
    @Test void costedReceiptRequiresValuationAndFinalIssueKeepsTheBooksBalanced() {
        assertThrows(IllegalArgumentException.class,()->move(1,BigDecimal.ONE,"early"));
        jdbc.update("UPDATE o_goods_inventory SET quantity=3,available_quantity=3 WHERE id=1");
        move(0,new BigDecimal("1"),"seed");
        move(-1,null,"a");move(-1,null,"b");move(-1,null,"c");
        value("0",jdbc.queryForObject("SELECT stock_value FROM ef_stock_balance WHERE inventory_id=1",BigDecimal.class));
        value("0",jdbc.queryForObject("SELECT COALESCE(SUM(value_delta),0) FROM ef_stock_ledger WHERE inventory_id=1",BigDecimal.class));
    }
    @Test void transferRejectsUnknownCostAndLeavesQuantityUnchanged() {
        service.grant(admin,new OperationsService.Grant(2,10,2,-1,false));
        var draft=new OperationsService.Draft("TRANSFER",1,4,null,BigDecimal.ZERO,null,3L,"调拨",Map.of(),"transfer-unknown");
        var d=act(maker,service.create(maker,draft),"submit");d=act(approver,d,"approve");d=act(admin,d,"approve");var pending=d;
        assertThrows(IllegalArgumentException.class,()->act(maker,pending,"post"));
        assertEquals(10,n(stock.inventory(1),"quantity"));
        assertEquals("APPROVED",service.detail(maker,pending.get("id").toString()).get("state"));
    }
    @Test void purchasePostsToExistingErpAndPartialReceiptsAllocateAllFreightExactly() {
        move(0,new BigDecimal("100"),"seed");
        when(purchases.createPurchaseOrder(any())).thenAnswer(call->{
            com.aierp.model.request.PurchaseOrderAddRequest req=call.getArgument(0);
            value("30",req.getOrderAmount());assertEquals(1,req.getStatus());
            jdbc.update("INSERT INTO erp_purchase_order(id,status) VALUES(100,1)");
            jdbc.update("INSERT INTO erp_purchase_order_item(id,order_id,inQty,status) VALUES(101,100,0,1)");
            return com.aierp.common.ResultVo.success(100L);
        });
        var input=new OperationsService.Draft("PURCHASE",1,3,BigDecimal.TEN,new BigDecimal("0.01"),1L,null,"采购",Map.of(),"purchase");
        var d=act(maker,service.create(maker,input),"submit");d=act(approver,d,"approve");d=act(maker,d,"post");
        assertEquals(100,n(d,"purchase_id"));assertThrows(IllegalArgumentException.class,()->stock.requireLegacyPurchase(100));
        for(int i=0;i<3;i++) d=service.act(maker,d.get("id").toString(),new OperationsService.Action(n(d,"version"),"receive","分批到货",1L));
        assertEquals(13,n(stock.inventory(1),"quantity"));assertEquals(3,n(d,"received_quantity"));
        value("30.01",jdbc.queryForObject("SELECT SUM(value_delta) FROM ef_stock_ledger WHERE source=?",BigDecimal.class,d.get("id")));
        value("130.01",jdbc.queryForObject("SELECT stock_value FROM ef_stock_balance WHERE inventory_id=1",BigDecimal.class));
        assertEquals(3,jdbc.queryForObject("SELECT inQty FROM erp_purchase_order_item WHERE id=101",Integer.class));
        var finalDoc=d;assertThrows(IllegalArgumentException.class,()->service.act(maker,finalDoc.get("id").toString(),new OperationsService.Action(n(finalDoc,"version"),"receive","超收",1L)));
    }
    @Test void failedPostingRollsBackPurchaseAndDocumentState() {
        when(purchases.createPurchaseOrder(any())).thenAnswer(call->{jdbc.update("INSERT INTO erp_purchase_order(id,status) VALUES(100,1)");throw new IllegalArgumentException("创建失败");});
        var d=act(maker,service.create(maker,draft("PURCHASE",2,BigDecimal.TEN)),"submit");var approved=act(approver,d,"approve");
        assertThrows(IllegalArgumentException.class,()->act(maker,approved,"post"));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM erp_purchase_order",Long.class));
        assertEquals("APPROVED",service.detail(maker,approved.get("id").toString()).get("state"));
    }
    @Test void masterExtensionsHaveScopeValidationVersionAndAudit() {
        service.saveField(admin,new OperationsService.Field("PRODUCT","origin","产地","select",true,List.of("国产","进口"),0));
        service.saveMaster(maker,new OperationsService.Master("PRODUCT",1,Map.of("origin","国产"),0));
        assertEquals(1L,service.master(maker,"PRODUCT",1).get("version"));
        assertThrows(IllegalArgumentException.class,()->service.saveMaster(maker,new OperationsService.Master("PRODUCT",1,Map.of("origin","未知"),1)));
        service.grant(admin,new OperationsService.Grant(2,10,1,-1,true));
        assertThrows(AccessDeniedException.class,()->service.master(maker,"PRODUCT",1));
    }
}
