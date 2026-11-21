package com.aierp.erp.memory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.aierp.erp.memory.MemoryStore.*;

/** Dedicated disposable MySQL schema only; never connects to the ERP business schema. */
@EnabledIfEnvironmentVariable(named="AI_MEMORY_TEST_JDBC_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/memory_test.*")
class MemoryStoreIntegrationTest {
    JdbcTemplate jdbc;
    MemoryStore store;
    long user;
    @BeforeEach void setup() {
        var data=new DriverManagerDataSource(System.getenv("AI_MEMORY_TEST_JDBC_URL"),"root","");
        jdbc=new JdbcTemplate(data);store=new MemoryStore(jdbc,new MemoryProperties());store.initialize();store.initialize();
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_conversation_history(id BIGINT PRIMARY KEY,user_id BIGINT,session_id VARCHAR(64))");
        user=Math.abs(UUID.randomUUID().getMostSignificantBits()/2);
    }
    @AfterEach void clean() {
        for(String table:new String[]{"ai_memory_revision","ai_memory_item","ai_memory_work","ai_memory_session","ai_memory_lock","ai_conversation_history"}) jdbc.update("DELETE FROM "+table+" WHERE user_id=?",user);
    }
    @Test void sameFactUpdatesAndLateJobsCannotOverwriteNewKnowledge() {
        store.put(user,"fact","budget","预算100元","s",1,5,false,null,"");
        store.put(user,"fact","budget","预算200元","s",3,9,true,null,"");
        store.put(user,"fact","budget","预算50元","s",2,1,false,null,"");
        var rows=store.active(user);assertEquals(1,rows.size());assertEquals("预算200元",rows.get(0).get("content"));
        assertEquals(2,number(rows.get(0),"version"));
        assertEquals("预算100元",store.revisions(user,string(rows.get(0),"id")).get(0).get("content"));
        assertTrue(store.active(user+1).isEmpty());assertTrue(store.revisions(user+1,string(rows.get(0),"id")).isEmpty());
    }
    @Test void allWritePathsRespectDenialAndForgetWatermark() {
        String lease=store.acquire(user);
        jdbc.update("UPDATE ai_memory_lock SET forget_cursor=100 WHERE user_id=?",user);
        store.put(user,"fact","old.fact","预算1000元","s",99,8,true,null,"");
        store.put(user,"episode","old.episode","旧会话摘要","s",100,8,false,null,"");
        store.put(user,"fact","denied","不要保存：预算2000元","s",101,8,true,null,"");
        store.put(user,"constraint","anchor.test","预算2000元","s",101,8,false,null,"");
        store.put(user,"fact","secret","api_key=fixture-secret","s",101,8,true,null,"");
        assertTrue(store.list(user).isEmpty());
        store.put(user,"fact","new.fact","预算3000元","s",102,8,true,null,"");
        assertEquals(1,store.recall(user).size());
        store.release(user,lease);
    }
    @Test void upgradeRetiresConflictingLegacyAnchorsWithoutChangingConfirmedFacts() {
        store.put(user,"fact","legacy.old","预算1000元","s",1,8,false,null,"");
        store.put(user,"fact","legacy.new","预算2000元","s",2,8,false,null,"");
        jdbc.update("UPDATE ai_memory_item SET kind='constraint' WHERE user_id=?",user);
        store.put(user,"fact","confirmed.budget","预算2000元","s",3,9,true,null,"");
        assertEquals(1,store.recall(user).size()); // safe even before migration
        var noDdl=new MemoryProperties();noDdl.setInitializeSchema(false);
        var upgraded=new MemoryStore(jdbc,noDdl);upgraded.initialize();upgraded.initialize();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ai_memory_item WHERE user_id=? AND kind='constraint' AND status='retired'",Integer.class,user));
        assertEquals(1,upgraded.recall(user).size());
        assertEquals("confirmed.budget",upgraded.recall(user).get(0).get("fact_key"));
        assertEquals(3,upgraded.list(user).size());
    }
    @Test void leasesExcludeConcurrentTurnsAndIgnoreWrongRelease() {
        String lease=store.acquire(user);
        assertThrows(BusyException.class,()->store.acquire(user));
        store.release(user,"wrong-token");assertThrows(BusyException.class,()->store.acquire(user));
        store.renew();store.release(user,lease);
        String next=store.acquire(user);assertNotEquals(lease,next);store.release(user,next);
    }
    @Test void forgottenMemoryCannotBeResurrectedByOldEvidence() {
        String lease=store.acquire(user);
        jdbc.update("INSERT INTO ai_conversation_history(id,user_id,session_id) VALUES (?,?,?)",user,user,"s");
        store.put(user,"preference","language","中文","s",1,9,true,null,"");
        String id=string(store.active(user).get(0),"id");store.forget(user,id);
        store.put(user,"preference","language","英文","s",2,9,true,null,"");
        assertTrue(store.active(user).isEmpty());assertEquals(user,store.forgetCursor(user));
        assertTrue(store.revisions(user,id).isEmpty());store.release(user,lease);
    }
    @Test void workStateSurvivesServiceRecreationAndRemainsUserScoped() {
        store.touch(user,"s",true);
        String id=store.startWork(user,"s","run",1,"searchGoods","{\"keyword\":\"手机\"}");
        store.finishWork(user,id,"库存123件","completed");
        var restarted=new MemoryStore(jdbc,new MemoryProperties());
        assertEquals("库存123件",restarted.work(user,"s").get(0).get("result"));
        assertTrue(restarted.work(user+1,"s").isEmpty());
        restarted.deleteSession(user+1,"s");assertFalse(restarted.work(user,"s").isEmpty());
        restarted.deleteSession(user,"s");assertTrue(restarted.work(user,"s").isEmpty());
    }
    @Test void staleLowValueFactsRetireButExplicitMemoriesRemain() {
        store.put(user,"fact","low","旧事实","s",1,2,false,null,"");
        store.put(user,"fact","high","明确记住","s",2,9,true,null,"");
        jdbc.update("UPDATE ai_memory_item SET updated_at=?,last_access=? WHERE user_id=?",now()-200L*86400_000,now()-200L*86400_000,user);
        store.maintain(user);
        assertEquals(1,store.active(user).size());assertEquals("明确记住",store.active(user).get(0).get("content"));
    }
    @Test void oldImportantMemoryStaysInTheRecallSet() {
        var limited=new MemoryProperties();limited.setRecallRecent(2);limited.setRecallImportant(5);
        var recallStore=new MemoryStore(jdbc,limited);
        recallStore.put(user,"fact","old.important","三年前的预算上限是8888元","s",1,9,false,null,"");
        for(int i=0;i<3;i++) recallStore.put(user,"fact","recent."+i,"普通记录"+i,"s",10+i,2,false,null,"");
        jdbc.update("UPDATE ai_memory_item SET updated_at=? WHERE user_id=? AND fact_key=?",now()-3L*365*86400_000,user,"old.important");
        assertTrue(recallStore.recall(user).stream().anyMatch(row->"old.important".equals(row.get("fact_key"))));
        assertEquals(2,recallStore.recall(user).stream().filter(row->string(row,"fact_key").startsWith("recent.")).count());
    }
    @Test void endAndIdleSignalsArePersisted() {
        store.touch(user,"s",true);store.requestEnd(user,"s");
        assertTrue(store.idle().stream().anyMatch(row->number(row,"user_id")==user));
        store.extracted(user,"s",10);assertEquals(0,number(store.session(user,"s"),"pending"));
    }
}
