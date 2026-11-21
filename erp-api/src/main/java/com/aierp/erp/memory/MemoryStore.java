package com.aierp.erp.memory;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** All reads and writes carry the authenticated user, including background jobs. */
@Repository
@RequiredArgsConstructor
public class MemoryStore {
    private final JdbcTemplate jdbc;
    private final MemoryProperties properties;
    private final java.util.concurrent.ConcurrentMap<Long,String> leases = new java.util.concurrent.ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        if (properties.isInitializeSchema())
            new ResourceDatabasePopulator(new ClassPathResource("db/ai-memory.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
        // Idempotent data repair also runs when schema creation is disabled.
        new ResourceDatabasePopulator(new ClassPathResource("db/ai-memory-retire-anchors.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
    }

    public String acquire(long user) {
        jdbc.update("INSERT IGNORE INTO ai_memory_lock(user_id,lease_until) VALUES (?,0)", user);
        String token = UUID.randomUUID().toString();
        int n = jdbc.update("UPDATE ai_memory_lock SET lease_token=?,lease_until=? WHERE user_id=? AND lease_until<?",
                token, now() + 900_000, user, now());
        if (n == 0) throw new BusyException();
        leases.put(user,token);
        return token;
    }
    public void release(long user, String token) {
        jdbc.update("UPDATE ai_memory_lock SET lease_until=0,lease_token=NULL WHERE user_id=? AND lease_token=?", user, token);
        leases.remove(user,token);
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=30000)
    public void renew() {
        leases.forEach((user,token)->jdbc.update("UPDATE ai_memory_lock SET lease_until=? WHERE user_id=? AND lease_token=?",now()+900_000,user,token));
    }
    public void touch(long user, String session, boolean pending) {
        jdbc.update("INSERT INTO ai_memory_session(user_id,session_id,updated_at,pending) VALUES (?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE updated_at=VALUES(updated_at),pending=VALUES(pending),archived=0", user, session, now(), pending ? 1 : 0);
    }
    public Map<String,Object> session(long user, String session) {
        var rows = jdbc.queryForList("SELECT * FROM ai_memory_session WHERE user_id=? AND session_id=?", user, session);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }
    public void summary(long user, String session, String summary, String anchors, long cursor) {
        jdbc.update("UPDATE ai_memory_session SET summary=?,anchors=?,summary_cursor=? WHERE user_id=? AND session_id=? AND summary_cursor<=?",
                summary, anchors, cursor, user, session, cursor);
    }
    public void extracted(long user, String session, long cursor) {
        jdbc.update("UPDATE ai_memory_session SET extract_cursor=?,pending=0 WHERE user_id=? AND session_id=? AND extract_cursor<=?", cursor, user, session, cursor);
    }
    public List<Map<String,Object>> active(long user) {
        return jdbc.queryForList("SELECT * FROM ai_memory_item WHERE user_id=? AND status='active' AND kind<>'constraint' ORDER BY updated_at DESC LIMIT ?", user, properties.getMaxActive() + 100);
    }
    /** Recent rows alone would hide an important fact that has not changed for years. */
    public List<Map<String,Object>> recall(long user) {
        var merged = new LinkedHashMap<String, Map<String,Object>>();
        for (var row : jdbc.queryForList("SELECT * FROM ai_memory_item WHERE user_id=? AND status='active' AND kind<>'constraint' AND (importance>=7 OR explicit_memory=1) ORDER BY importance DESC, updated_at DESC LIMIT ?", user, Math.max(1, properties.getRecallImportant())))
            merged.put(string(row, "id"), row);
        for (var row : jdbc.queryForList("SELECT * FROM ai_memory_item WHERE user_id=? AND status='active' AND kind<>'constraint' ORDER BY updated_at DESC LIMIT ?", user, Math.max(1, properties.getRecallRecent())))
            merged.putIfAbsent(string(row, "id"), row);
        return new ArrayList<>(merged.values());
    }
    public List<Map<String,Object>> list(long user) {
        return jdbc.queryForList("SELECT id,kind,fact_key,content,importance,explicit_memory,updated_at,last_access,access_count,version,status,source_session,source_id FROM ai_memory_item WHERE user_id=? ORDER BY updated_at DESC LIMIT 600", user);
    }

    @Transactional
    public void put(long user, String kind, String key, String content, String session, long sourceId,
                    int importance, boolean explicit, String embedding, String embeddingModel) {
        // All long-term paths, including episodic archives, must respect the deletion watermark.
        if("constraint".equals(kind) || !MemoryPolicy.longTermAllowed(content) || sourceId<=forgetCursor(user)) return;
        String hash = MemoryPolicy.hash(key);
        var rows = jdbc.queryForList("SELECT * FROM ai_memory_item WHERE user_id=? AND kind=? AND key_hash=? FOR UPDATE", user, kind, hash);
        long time = now();
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO ai_memory_item(id,user_id,kind,fact_key,key_hash,content,source_session,source_id,importance,explicit_memory,created_at,updated_at,last_access,embedding,embedding_model) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), user, kind, key, hash, content, session, sourceId, importance, explicit ? 1 : 0, time, time, time, embedding, embeddingModel);
            return;
        }
        var old = rows.get(0);
        // A delayed idle job must never overwrite newer user evidence or resurrect a forgotten fact.
        if (sourceId <= number(old,"source_id") || ("forgotten".equals(old.get("status")) && !explicit)) return;
        if (content.equals(old.get("content"))) {
            jdbc.update("UPDATE ai_memory_item SET source_id=?,source_session=?,updated_at=?,importance=GREATEST(importance,?),explicit_memory=GREATEST(explicit_memory,?) WHERE user_id=? AND id=?",
                    sourceId, session, time, importance, explicit ? 1 : 0, user, old.get("id"));
            return;
        }
        revision(user, old, "superseded");
        jdbc.update("UPDATE ai_memory_item SET content=?,source_session=?,source_id=?,importance=?,explicit_memory=?,updated_at=?,version=version+1,status='active',embedding=?,embedding_model=? WHERE user_id=? AND id=?",
                content, session, sourceId, importance, explicit ? 1 : 0, time, embedding, embeddingModel, user, old.get("id"));
    }
    private void revision(long user, Map<String,Object> old, String reason) {
        jdbc.update("INSERT INTO ai_memory_revision(id,memory_id,user_id,version,content,source_session,source_id,replaced_at,reason) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), old.get("id"), user, old.get("version"), old.get("content"), old.get("source_session"), old.get("source_id"), now(), reason);
    }
    public List<Map<String,Object>> revisions(long user, String id) {
        return jdbc.queryForList("SELECT version,content,source_session,source_id,replaced_at,reason FROM ai_memory_revision WHERE user_id=? AND memory_id=? ORDER BY version DESC LIMIT 50", user, id);
    }
    public void accessed(long user, String id) {
        jdbc.update("UPDATE ai_memory_item SET last_access=?,access_count=access_count+1 WHERE user_id=? AND id=? AND status='active'", now(), user, id);
    }
    @Transactional
    public void forget(long user, String id) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM ai_memory_item WHERE user_id=? AND id=?",Integer.class,user,id)==0) return;
        // Tombstone blocks replay by old extraction jobs; erase contents and old revisions.
        jdbc.update("UPDATE ai_memory_item SET status='forgotten',content='',embedding=NULL,updated_at=? WHERE user_id=? AND id=?", now(), user, id);
        // Advance past all existing evidence so a later idle job cannot replay an old quote.
        jdbc.update("UPDATE ai_memory_item SET source_id=GREATEST(source_id,COALESCE((SELECT MAX(h.id) FROM ai_conversation_history h WHERE h.user_id=?),0)) WHERE user_id=? AND id=?",user,user,id);
        jdbc.update("DELETE FROM ai_memory_revision WHERE user_id=? AND memory_id=?", user, id);
        jdbc.update("UPDATE ai_memory_lock SET forget_cursor=GREATEST(forget_cursor,COALESCE((SELECT MAX(h.id) FROM ai_conversation_history h WHERE h.user_id=?),0)) WHERE user_id=?",user,user);
    }
    public long forgetCursor(long user) {
        var rows=jdbc.queryForList("SELECT forget_cursor FROM ai_memory_lock WHERE user_id=?",Long.class,user);
        return rows.isEmpty()?0:rows.get(0);
    }
    public String startWork(long user, String session, String run, int step, String tool, String arguments) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ai_memory_work(id,user_id,session_id,run_id,step_no,tool_name,arguments,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'running',?,?)",
                id, user, session, run, step, tool, arguments, now(), now());
        return id;
    }
    public void finishWork(long user, String id, String result, String status) {
        jdbc.update("UPDATE ai_memory_work SET result=?,status=?,updated_at=? WHERE user_id=? AND id=?", result, status, now(), user, id);
    }
    public List<Map<String,Object>> work(long user, String session) {
        return jdbc.queryForList("SELECT * FROM ai_memory_work WHERE user_id=? AND session_id=? ORDER BY created_at DESC,step_no DESC LIMIT 30", user, session);
    }
    public Map<String,Object> workEntry(long user,String session,String id) {
        var rows=jdbc.queryForList("SELECT * FROM ai_memory_work WHERE user_id=? AND session_id=? AND id=?",user,session,id);
        return rows.isEmpty()?Map.of():rows.get(0);
    }
    public List<Map<String,Object>> idle() {
        return jdbc.queryForList("SELECT * FROM ai_memory_session WHERE pending=2 OR (pending=1 AND updated_at<?) ORDER BY updated_at LIMIT 20", now() - properties.getIdleMinutes()*60_000L);
    }
    public void requestEnd(long user,String session) {
        jdbc.update("UPDATE ai_memory_session SET pending=2 WHERE user_id=? AND session_id=?",user,session);
    }
    public List<Map<String,Object>> oldSessions() {
        return jdbc.queryForList("SELECT * FROM ai_memory_session WHERE archived=0 AND updated_at<? ORDER BY updated_at LIMIT 20", now()-30L*86400_000);
    }
    public void archived(long user, String session) {
        jdbc.update("UPDATE ai_memory_session SET archived=1 WHERE user_id=? AND session_id=?", user, session);
    }
    @Transactional
    public void deleteSession(long user, String session) {
        jdbc.update("DELETE FROM ai_memory_session WHERE user_id=? AND session_id=?", user, session);
        jdbc.update("DELETE FROM ai_memory_work WHERE user_id=? AND session_id=?", user, session);
    }
    public void maintain(long user) {
        long cutoff = now()-properties.getRetentionDays()*86400_000L;
        jdbc.update("UPDATE ai_memory_item SET status='retired',embedding=NULL WHERE user_id=? AND status='active' AND explicit_memory=0 AND importance<7 AND last_access<? AND updated_at<?", user, cutoff, cutoff);
        var excess = jdbc.queryForList("SELECT id FROM ai_memory_item WHERE user_id=? AND status='active' AND explicit_memory=0 ORDER BY importance DESC,last_access DESC LIMIT 10000 OFFSET ?", user, properties.getMaxActive());
        for(var row: excess) jdbc.update("UPDATE ai_memory_item SET status='retired',embedding=NULL WHERE user_id=? AND id=?", user,row.get("id"));
        jdbc.update("DELETE FROM ai_memory_revision WHERE user_id=? AND replaced_at<?", user, now()-365L*86400_000);
        jdbc.update("DELETE FROM ai_memory_item WHERE user_id=? AND status='retired' AND updated_at<?", user, now()-365L*86400_000);
        jdbc.update("DELETE FROM ai_memory_item WHERE user_id=? AND status='forgotten' AND updated_at<?", user, now()-365L*86400_000);
        jdbc.update("DELETE FROM ai_memory_work WHERE user_id=? AND updated_at<?", user, now()-30L*86400_000);
        jdbc.update("UPDATE ai_memory_work SET status='interrupted',updated_at=? WHERE user_id=? AND status='running' AND updated_at<?", now(),user,now()-900_000);
    }
    public List<Long> users() { return jdbc.queryForList("SELECT user_id FROM ai_memory_lock",Long.class); }
    @Transactional
    public void merge(long user,String keep,String drop) {
        var rows=jdbc.queryForList("SELECT * FROM ai_memory_item WHERE user_id=? AND id=? AND status='active' FOR UPDATE",user,drop);
        if(rows.isEmpty()) return;
        revision(user,rows.get(0),"merged");
        jdbc.update("UPDATE ai_memory_item SET importance=GREATEST(importance,?),explicit_memory=GREATEST(explicit_memory,?) WHERE user_id=? AND id=? AND status='active'",number(rows.get(0),"importance"),number(rows.get(0),"explicit_memory"),user,keep);
        jdbc.update("UPDATE ai_memory_item SET status='retired',embedding=NULL WHERE user_id=? AND id=?",user,drop);
    }
    public void embedding(long user,String id,long version,String vector,String model) {
        jdbc.update("UPDATE ai_memory_item SET embedding=?,embedding_model=? WHERE user_id=? AND id=? AND version=? AND status='active'",vector,model,user,id,version);
    }
    public static long number(Map<String,Object> map, String key) { Object value=map.get(key); return value instanceof Number n ? n.longValue() : 0; }
    public static String string(Map<String,Object> map, String key) { return Objects.toString(map.get(key),""); }
    public static long now() { return System.currentTimeMillis(); }
    public static class BusyException extends RuntimeException {
        public BusyException() { super("当前用户已有对话或记忆整理正在处理，请稍后重试"); }
    }
}
