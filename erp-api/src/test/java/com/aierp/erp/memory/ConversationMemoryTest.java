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
    @Test void oversizedAnchorsStayInSessionAndNeverBecomeLongTermFacts() {
        properties.setAnchorChars(150);
        when(store.session(1,"s")).thenReturn(Map.of());
        when(model.json(anyString(),any())).thenReturn(JSON.parseObject("{\"summary\":\"已压缩旧对话\"}"));
        var rows=new ArrayList<com.aierp.model.entity.AiConversationHistory>();
        for(int i=1;i<=6;i++) rows.add(message(i,"user","必须保留金额"+i+"00.50元，截止2026-12-03。"));
        memory.compact(1,"s",rows,true);
        verify(store,never()).put(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyInt(),anyBoolean(),any(),any());
        var anchors=ArgumentCaptor.forClass(String.class);
        verify(store).summary(eq(1L),eq("s"),anyString(),anchors.capture(),eq(6L));
        assertTrue(anchors.getValue().length()>150);
        assertTrue(anchors.getValue().contains("600.50元"));
        assertTrue(anchors.getValue().contains("100.50元"));
    }

    @Test void continuationRestoresPreviouslySpilledBudgetWithoutKeywordOrVectorMatch() {
        properties.setAnchorChars(80);
        when(store.session(1,"s")).thenReturn(Map.of("summary_cursor",6L,"summary","任务进行中", "anchors","[\"[user#6] 库存600件。\"]"));
        var rows=List.of(message(1,"user","不要保存：本次预算为1000元。"),message(2,"user","本次预算改为2000元。"),
                message(3,"user","库存300件。"),message(4,"user","库存400件。"),message(5,"user","库存500件。"),message(6,"user","库存600件。"),
                message(7,"user","好的"),message(8,"user","照办"),message(9,"user","继续"));
        String prompt=memory.context(1,"s",rows).stream().map(m->m.getText()).reduce("",String::concat);
        assertTrue(prompt.contains("[user#2] 本次预算改为2000元。"));
        assertTrue(prompt.contains("后续用户更正覆盖旧值"));
        assertTrue(prompt.indexOf("1000元")<prompt.indexOf("2000元"));
        verify(store,never()).put(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyInt(),anyBoolean(),any(),any());
    }

    @Test void denialBlocksExtractionAndMonthlyArchiveIncludingParaphrasedSummary() {
        var rows=List.of(message(1,"user","不要保存：本次预算为1000元。"),message(2,"assistant","预算是1000元。"));
        when(history.getConversation("s",1L)).thenReturn(rows);
        when(store.session(1,"s")).thenReturn(Map.of("summary","预算1000元"));
        assertEquals(0,memory.extract(1,"s",rows,false));
        memory.archive(1,"s");
        verifyNoInteractions(model);
        verify(store,never()).put(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyInt(),anyBoolean(),any(),any());
        verify(store).archived(1,"s");
    }

    @Test void compactionAfterForgetCannotWriteOldTextThroughAnotherPath() {
        when(store.forgetCursor(1)).thenReturn(100L);
        when(store.session(1,"s")).thenReturn(Map.of());
        when(model.json(anyString(),any())).thenReturn(JSON.parseObject("{\"summary\":\"原文仍在当前会话\"}"));
        properties.setAnchorChars(20);
        memory.compact(1,"s",List.of(message(1,"user","预算1000元。"),message(2,"user","预算2000元。")),true);
        verify(store,never()).put(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyInt(),anyBoolean(),any(),any());
    }

    @Test void legacyContradictorySpillsAreNeverRecalledEvenBeforeMigration() {
        when(store.recall(1)).thenReturn(List.of(Map.of("kind","constraint","content","预算1000元"),Map.of("kind","constraint","content","预算2000元")));
        assertEquals("",memory.retrieve(1,"预算"));
        verify(store,never()).accessed(anyLong(),anyString());
    }

    @Test void mandatoryConstraintsOverBudgetFailInsteadOfSilentlyDroppingThem() {
        when(store.session(1,"s")).thenReturn(Map.of("summary_cursor",1L,"summary","任务进行中"));
        properties.setContextChars(100);
        var rows=List.of(message(1,"user","必须遵守预算12345.67元。".repeat(50)),message(2,"user","继续"));
        assertThrows(IllegalStateException.class,()->memory.context(1,"s",rows));
        verify(store,never()).put(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyInt(),anyBoolean(),any(),any());
    }
}
