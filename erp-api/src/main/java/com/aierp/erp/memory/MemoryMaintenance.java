package com.aierp.erp.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import static com.aierp.erp.memory.MemoryStore.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryMaintenance {
    private final MemoryStore store;
    private final ConversationMemory memory;
    private final MemoryModel model;

    @Scheduled(fixedDelayString="${ai.memory.maintenance-delay-ms:60000}",initialDelay=60000)
    public void tick() {
        for(var session:store.idle()) process(session,false);
        for(var session:store.oldSessions()) process(session,true);
        for(long user:store.users()) {
            String lease;
            try { lease=store.acquire(user); } catch(BusyException ignored) { continue; }
            try { consolidate(user);reindex(user);store.maintain(user); }
            catch(Exception e) { log.warn("Memory maintenance deferred ({})",e.getClass().getSimpleName()); }
            finally { store.release(user,lease); }
        }
    }
    private void process(Map<String,Object> session,boolean archive) {
        long user=number(session,"user_id");String id=string(session,"session_id");String lease;
        try { lease=store.acquire(user); } catch(BusyException ignored) { return; }
        try {
            // Re-check under the lease: a new turn may have arrived since the scheduler's scan.
            var current=store.session(user,id);
            if(number(current,"updated_at")!=number(session,"updated_at")) return;
            if(archive) memory.archive(user,id); else memory.finish(user,id);
        } catch(Exception e) { log.warn("Memory extraction deferred; transcript retained ({})",e.getClass().getSimpleName()); }
        finally { store.release(user,lease); }
    }
    private void consolidate(long user) {
        var rows=store.active(user);
        // Deterministic duplicate removal: never merge merely similar facts with different numbers.
        java.util.Map<String,Map<String,Object>> seen=new java.util.HashMap<>();
        for(var row:rows) {
            String identity=string(row,"kind")+"|"+MemoryPolicy.key(string(row,"content").length()>255 ? MemoryPolicy.hash(string(row,"content")) : string(row,"content"));
            var prior=seen.putIfAbsent(identity,row);
            if(prior!=null) {
                var keep=number(prior,"source_id")>=number(row,"source_id")?prior:row;
                var drop=keep==prior?row:prior;
                store.merge(user,string(keep,"id"),string(drop,"id"));seen.put(identity,keep);
            }
        }
    }
    private void reindex(long user) {
        if(model.embeddingIdentity().isBlank()) return;
        int count=0;
        for(var row:store.active(user)) {
            try {
                if(row.get("embedding")!=null && model.embeddingIdentity().equals(row.get("embedding_model"))) continue;
                String content=string(row,"content");
                if(content.length()>8000) continue;
                float[] vector=model.embed(content);
                if(vector.length>0) store.embedding(user,string(row,"id"),number(row,"version"),com.alibaba.fastjson2.JSON.toJSONString(vector),model.embeddingIdentity());
            } catch(RuntimeException e) {
                log.warn("Memory embedding skipped; keyword retrieval and forgetting continue ({})", e.getClass().getSimpleName());
            }
            if(++count==10) break;
        }
    }
}
