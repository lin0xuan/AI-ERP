package com.aierp.erp.trace;

import com.aierp.erp.memory.MemoryPolicy;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class AiTraceService {
    private static final Set<String> DOCUMENT_KINDS = Set.of("PURCHASE", "RECEIPT", "ISSUE", "ADJUSTMENT", "VALUATION", "TRANSFER");
    private final JdbcTemplate jdbc;
    @Value("${ai.trace.initialize-schema:true}")
    private boolean init = true;

    public AiTraceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void schema() {
        if (init) {
            new ResourceDatabasePopulator(new ClassPathResource("db/ai-trace.sql"))
                    .execute(Objects.requireNonNull(jdbc.getDataSource()));
        }
    }

    public String start(long user, String session, String input, String model) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ai_trace_run(id,user_id,session_id,input_text,status,model,started_at,finished_at,error_message,output_text) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id, user, session, clip(MemoryPolicy.redact(input), 20000), "RUNNING", model, System.currentTimeMillis(), null, null, null);
        return id;
    }

    public String event(String run, int step, String type, String name, String input) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ai_trace_event(id,run_id,step_no,type,name,status,input_text,output_text,started_at,finished_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id, run, step, type, clip(name, 128), "RUNNING", clip(MemoryPolicy.redact(input), 20000), null, System.currentTimeMillis(), null);
        return id;
    }

    public void finishEvent(String id, String output, String status) {
        if (id == null) return;
        jdbc.update("UPDATE ai_trace_event SET output_text=?,status=?,finished_at=? WHERE id=? AND status='RUNNING'",
                clip(MemoryPolicy.redact(output), 20000), status, System.currentTimeMillis(), id);
    }

    /** Records a tool step and, on completion, the business documents found in its result. */
    public void noteTool(String run, int step, String name, String status, String input, String output) {
        if ("running".equals(status)) {
            event(run, step, "TOOL", name, input);
            return;
        }
        jdbc.update("UPDATE ai_trace_event SET output_text=?,status=?,finished_at=? WHERE run_id=? AND step_no=? AND type='TOOL' AND status='RUNNING'",
                clip(MemoryPolicy.redact(output), 20000), "failed".equals(status) ? "FAILED" : "COMPLETED", System.currentTimeMillis(), run, step);
        if ("completed".equals(status)) rememberLinks(run, output);
    }

    public void finish(String run, String output, String status, String error) {
        String message = error == null ? null : clip(error, 500);
        jdbc.update("UPDATE ai_trace_run SET output_text=?,status=?,finished_at=?,error_message=? WHERE id=? AND status='RUNNING'",
                clip(MemoryPolicy.redact(output), 20000), status, System.currentTimeMillis(), message, run);
    }

    public Map<String, Object> run(long user, String id) {
        var rows = jdbc.queryForList("SELECT * FROM ai_trace_run WHERE id=? AND user_id=?", id, user);
        if (rows.isEmpty()) throw new IllegalArgumentException("Trace不存在");
        var result = new LinkedHashMap<>(rows.get(0));
        result.put("events", jdbc.queryForList("SELECT * FROM ai_trace_event WHERE run_id=? ORDER BY step_no,started_at", id));
        result.put("toolCalls", jdbc.queryForList("SELECT id,step_no,tool_name,status,arguments,result,created_at,updated_at FROM ai_memory_work WHERE user_id=? AND run_id=? ORDER BY step_no,created_at", user, id));
        result.put("links", jdbc.queryForList("SELECT kind,business_id,label,created_at FROM ai_trace_link WHERE run_id=? ORDER BY created_at,kind,business_id", id));
        return result;
    }

    public List<Map<String, Object>> list(long user, String session) {
        if (session != null && session.isBlank()) session = null;
        return jdbc.queryForList("SELECT id,session_id,status,model,started_at,finished_at,error_message,LEFT(input_text,80) AS input_preview FROM ai_trace_run WHERE user_id=? AND (? IS NULL OR session_id=?) ORDER BY started_at DESC LIMIT 100",
                user, session, session);
    }

    public static List<Map<String, String>> businessLinks(String json) {
        if (json == null || json.isBlank()) return List.of();
        Object parsed;
        try {
            parsed = JSON.parse(json);
        } catch (RuntimeException ex) {
            return List.of();
        }
        var found = new ArrayList<Map<String, String>>();
        boolean broad = parsed instanceof List<?> list && list.size() > 3;
        walk(parsed, found, broad);
        var distinct = new ArrayList<Map<String, String>>();
        var seen = new java.util.HashSet<String>();
        for (var link : found) {
            if (seen.add(link.get("kind") + ":" + link.get("businessId")) && distinct.size() < 8) distinct.add(link);
        }
        return distinct;
    }

    private void rememberLinks(String run, String output) {
        for (var link : businessLinks(output)) {
            try {
                jdbc.update("INSERT INTO ai_trace_link(id,run_id,kind,business_id,label,created_at) VALUES(?,?,?,?,?,?)",
                        UUID.randomUUID().toString(), run, link.get("kind"), link.get("businessId"), link.get("label"), System.currentTimeMillis());
            } catch (DataIntegrityViolationException ignored) {
                // The same document can appear in more than one tool result.
            }
        }
    }

    private static void walk(Object node, List<Map<String, String>> found, boolean broad) {
        if (node instanceof Map<?, ?> map) {
            boolean document = isDocument(map);
            if (!broad || map.get("purchase_id") != null || map.get("purchaseId") != null || document) {
                addId(found, "PURCHASE", first(map, "purchase_id", "purchaseId"), "采购单");
                if (document) addId(found, "DOCUMENT", map.get("id"), String.valueOf(map.get("kind")));
            }
            if (!broad) addId(found, "INVENTORY", first(map, "inventory_id", "inventoryId"), "库存");
            for (Object value : map.values()) walk(value, found, broad);
        } else if (node instanceof List<?> list) {
            for (Object value : list) walk(value, found, broad);
        }
    }

    private static boolean isDocument(Map<?, ?> map) {
        Object kind = map.get("kind");
        Object id = map.get("id");
        return kind != null && DOCUMENT_KINDS.contains(kind.toString()) && id != null && id.toString().length() >= 32;
    }

    private static Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) if (map.get(key) != null) return map.get(key);
        return null;
    }

    private static void addId(List<Map<String, String>> found, String kind, Object id, String label) {
        if (id == null) return;
        String text = id.toString();
        if (text.isBlank() || "0".equals(text) || text.length() > 64) return;
        found.add(Map.of("kind", kind, "businessId", text, "label", label + " " + text));
    }

    private static String clip(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
