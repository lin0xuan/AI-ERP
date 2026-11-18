package com.aierp.erp.memory;

import com.aierp.model.entity.AiConversationHistory;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MemoryPolicyTest {
    static AiConversationHistory message(long id,String role,String text) {
        var row=new AiConversationHistory();row.setId(id);row.setRole(role);row.setContent(text);return row;
    }
    @Test void preservesAmountsUnitsNegationAndSourceVerbatim() {
        String text="预算不得超过12,345.67元。必须在2026-12-03前完成！不要调用采购下单工具。";
        var anchors=MemoryPolicy.anchors(List.of(message(1,"user",text)));
        assertEquals(List.of("[user#1] 预算不得超过12,345.67元。","[user#1] 必须在2026-12-03前完成！","[user#1] 不要调用采购下单工具。"),anchors);
    }
    @Test void recentTurnsKeepFullMessages() {
        var history=List.of(message(1,"user","a"),message(2,"assistant","b"),message(3,"user","c"),message(4,"assistant","d"),message(5,"user","e"),message(6,"assistant","f"),message(7,"user","g"));
        assertEquals(2,MemoryPolicy.recentStart(history,3));
    }
    @Test void importantAndRecentFactsRankHigher() {
        long now=1_800_000_000_000L;
        double recent=MemoryPolicy.score(.7,5,now-86400_000,now,now);
        assertTrue(recent>MemoryPolicy.score(.7,5,now-3L*365*86400_000,now,now));
        assertTrue(MemoryPolicy.score(.7,9,now,now,now)>MemoryPolicy.score(.7,2,now,now,now));
    }
    @Test void lexicalAndVectorSignalsWorkIndependently() {
        assertTrue(MemoryPolicy.keyword("采购预算","我的采购预算是300元")>0.5);
        assertEquals(1,MemoryPolicy.cosine(new float[]{1,2},new float[]{2,4}),1e-6);
        assertEquals(0,MemoryPolicy.cosine(new float[]{1},new float[]{1,2}));
    }
    @Test void explicitMemoryAndSecretsAreHandled() {
        assertTrue(MemoryPolicy.explicit("请记住：我喜欢中文回答"));
        assertFalse(MemoryPolicy.explicit("不要记住我的偏好"));
        assertFalse(MemoryPolicy.redact("{\"api_key\":\"secret123\"}").contains("secret123"));
    }
}
