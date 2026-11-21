package com.aierp.erp.trace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AiTraceServiceTest {
    JdbcTemplate jdbc;
    AiTraceService traces;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        traces = new AiTraceService(jdbc);
        traces.schema();
        jdbc.execute("CREATE TABLE ai_memory_work(id VARCHAR(64) PRIMARY KEY,user_id BIGINT,session_id VARCHAR(64),run_id VARCHAR(64),step_no INT,tool_name VARCHAR(128),arguments VARCHAR(1000),result VARCHAR(1000),status VARCHAR(24),created_at BIGINT,updated_at BIGINT)");
    }

    @Test void runStoresModelStepToolResultAndHidesOtherUsers() {
        String id = traces.start(7, "session", "帮我采购，电话13800138000", "deepseek-chat");
        String model = traces.event(id, 0, "MODEL", "deepseek-chat", "");
        traces.noteTool(id, 1, "createDocument", "running", "{\"qty\":2}", null);
        traces.noteTool(id, 1, "createDocument", "completed", "{\"qty\":2}",
                "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"kind\":\"PURCHASE\",\"purchase_id\":88,\"inventory_id\":5}");
        traces.finishEvent(model, "流式输出结束", "COMPLETED");
        traces.finish(id, "已生成采购草稿", "COMPLETED", null);
        traces.finish(id, "后来的失败", "FAILED", "重复收尾");
        jdbc.update("INSERT INTO ai_memory_work VALUES('w',7,'session',?,1,'createDocument','{}','ok','completed',1,2)", id);

        var detail = traces.run(7, id);
        assertEquals("COMPLETED", detail.get("status"));
        assertEquals("已生成采购草稿", detail.get("output_text"));
        assertFalse(detail.get("input_text").toString().contains("13800138000"));
        assertEquals(2, ((java.util.List<?>) detail.get("events")).size());
        assertEquals(1, ((java.util.List<?>) detail.get("toolCalls")).size());
        var links = (java.util.List<?>) detail.get("links");
        assertEquals(3, links.size());
        assertThrows(IllegalArgumentException.class, () -> traces.run(8, id));
        assertTrue(traces.list(8, "session").isEmpty());
        assertEquals(id, traces.list(7, "session").get(0).get("id"));
    }

    @Test void inventoryListsDoNotBecomeBusinessLinks() {
        String json = "[{\"inventory_id\":1},{\"inventory_id\":2},{\"inventory_id\":3},{\"inventory_id\":4}]";
        assertTrue(AiTraceService.businessLinks(json).isEmpty());
        var one = AiTraceService.businessLinks("{\"inventory_id\":9,\"sku\":\"A\"}");
        assertEquals("INVENTORY", one.get(0).get("kind"));
        assertEquals("9", one.get(0).get("businessId"));
    }
}
