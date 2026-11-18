package com.aierp.erp.memory;

import com.aierp.service.AiConversationHistoryService;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static com.aierp.erp.memory.MemoryPolicyTest.message;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConversationMemoryTest {
    MemoryStore store=mock(MemoryStore.class);
    MemoryModel model=mock(MemoryModel.class);
    MemoryProperties properties=new MemoryProperties();
    AiConversationHistoryService history=mock(AiConversationHistoryService.class);
    ConversationMemory memory=new ConversationMemory(store,model,properties,history);

    @Test void summariesPreserveOriginalAnchorsAndRecentRawTurns() {
        when(store.session(1,"s")).thenReturn(Map.of());
        when(model.json(anyString(),any())).thenReturn(JSON.parseObject("{\"summary\":\"目标：完成采购；待办：核对库存\"}"));
        var rows=List.of(message(1,"user","预算只能是999.50元，不要下单。"),message(2,"assistant","已了解。"),message(3,"user","查库存"),message(4,"assistant","库存4件"),message(5,"user","供应商呢"),message(6,"assistant","待查询"),message(7,"user","继续"));
        memory.compact(1,"s",rows,false);
        var anchor=ArgumentCaptor.forClass(String.class);
        verify(store).summary(eq(1L),eq("s"),anyString(),anchor.capture(),eq(2L));
        assertTrue(anchor.getValue().contains("预算只能是999.50元，不要下单。"));
    }
    @Test void failedSummaryDoesNotAdvanceCursor() {
        when(store.session(1,"s")).thenReturn(Map.of());
        when(model.json(anyString(),any())).thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class,()->memory.compact(1,"s",List.of(message(1,"user","金额100")),true));
        verify(store,never()).summary(anyLong(),anyString(),anyString(),anyString(),anyLong());
    }
    @Test void fabricatedQuotesNeverEnterLongTermMemory() {
        when(store.session(1,"s")).thenReturn(Map.of());
        when(store.recall(1)).thenReturn(List.of());
        when(model.json(anyString(),any())).thenReturn(JSON.parseObject("{\"facts\":[{\"kind\":\"fact\",\"key\":\"预算\",\"quote\":\"预算100万\",\"importance\":9}]}"));
        assertEquals(0,memory.extract(1,"s",List.of(message(1,"user","预算100元")),false));
        verify(store,never()).put(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyInt(),anyBoolean(),any(),any());
    }
    @Test void conflictUsesExistingCanonicalKeyAndExactNewQuote() {
        when(store.session(1,"s")).thenReturn(Map.of());
        when(store.recall(1)).thenReturn(List.of(Map.of("id","existing","kind","preference","fact_key","user.language","content","使用英文")));
        when(model.json(anyString(),any())).thenReturn(JSON.parseObject("{\"facts\":[{\"kind\":\"preference\",\"key\":\"另一个同义键\",\"targetId\":\"existing\",\"quote\":\"以后都使用中文\",\"importance\":9}]}"));
        when(model.embed(anyString())).thenReturn(new float[0]);when(model.embeddingIdentity()).thenReturn("");
        assertEquals(1,memory.extract(1,"s",List.of(message(2,"user","以后都使用中文")),false));
        verify(store).put(1,"preference","user.language","以后都使用中文","s",2,9,true,null,"");
    }
    @Test void forgottenHistoryIsNotReextractedOrArchived() {
        when(store.session(1,"s")).thenReturn(Map.of());when(store.forgetCursor(1)).thenReturn(100L);
        var rows=List.of(message(1,"user","请记住：我喜欢红色"));
        assertEquals(0,memory.extract(1,"s",rows,false));
        when(history.getConversation("s",1L)).thenReturn(rows);
        memory.archive(1,"s");
        verifyNoInteractions(model);verify(store).archived(1,"s");
    }
    @Test void vectorOnlyMatchCanBeRetrievedAndAccessIsRecorded() {
        when(model.embed(anyString())).thenReturn(new float[]{1,0});when(model.embeddingIdentity()).thenReturn("v1");
        when(store.recall(1)).thenReturn(List.of(Map.of("id","m","kind","fact","fact_key","lang","content","英文输出", "embedding","[1,0]","embedding_model","v1","importance",8,"updated_at",MemoryStore.now(),"last_access",MemoryStore.now())));
        assertTrue(memory.retrieve(1,"English answers").contains("英文输出"));verify(store).accessed(1,"m");
    }
    @Test void idleExtractionClosesWhenNothingNewRemains() {
        when(store.session(1,"s")).thenReturn(Map.of("extract_cursor",9L));
        when(store.forgetCursor(1)).thenReturn(0L);
        assertEquals(0,memory.extract(1,"s",List.of(message(1,"user","旧消息")),false));
        verify(store).extracted(1L,"s",9L);
        verify(store,never()).put(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyInt(),anyBoolean(),any(),any());
    }
    @Test void oversizedAnchorsMoveVerbatimIntoLongTermMemory() {
        properties.setAnchorChars(150);
        when(store.session(1,"s")).thenReturn(Map.of());
        when(model.json(anyString(),any())).thenReturn(JSON.parseObject("{\"summary\":\"已压缩旧对话\"}"));
        var rows=new ArrayList<com.aierp.model.entity.AiConversationHistory>();
        for(int i=1;i<=6;i++) rows.add(message(i,"user","必须保留金额"+i+"00.50元，截止2026-12-03。"));
        memory.compact(1,"s",rows,true);
        var stored=ArgumentCaptor.forClass(String.class);
        verify(store,atLeastOnce()).put(eq(1L),eq("constraint"),startsWith("anchor."),stored.capture(),eq("s"),eq(6L),eq(8),eq(false),isNull(),eq(""));
        assertTrue(stored.getAllValues().get(0).contains("必须保留金额100.50元，截止2026-12-03。"));
        var anchors=ArgumentCaptor.forClass(String.class);
        verify(store).summary(eq(1L),eq("s"),anyString(),anchors.capture(),eq(6L));
        assertTrue(anchors.getValue().length()<=150);
        assertTrue(anchors.getValue().contains("600.50元"));
        assertFalse(anchors.getValue().contains("100.50元"));
    }
}
