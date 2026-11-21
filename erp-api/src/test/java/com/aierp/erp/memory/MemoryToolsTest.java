package com.aierp.erp.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MemoryToolsTest {
    @Test void largeWorkResultsArePagedWithoutLosingDigits() {
        MemoryStore store=mock(MemoryStore.class);
        String text="x".repeat(7990)+"预算不得超过12345.67元";
        when(store.workEntry(42,"session","id")).thenReturn(java.util.Map.of("result",text));
        var tools=new MemoryTools(store,42,"session");
        var first=com.alibaba.fastjson2.JSON.parseObject(tools.readWorkMemoryDetail("id",0));
        assertTrue(first.getBooleanValue("hasMore"));
        var second=com.alibaba.fastjson2.JSON.parseObject(tools.readWorkMemoryDetail("id",first.getIntValue("nextOffset")));
        assertEquals("参数：\n结果："+text,first.getString("content")+second.getString("content"));
        assertFalse(second.getBooleanValue("hasMore"));
    }
    public static class Fixture {
        @Tool(description="fixture") public String lookup() {return "库存12件，单价99.90元";}
        @Tool(description="failure") public String fail() {throw new IllegalStateException("failure");}
    }
    @Test void recordsToolArgumentsResultsAndFailureWithServerIdentity() {
        MemoryStore store=mock(MemoryStore.class);when(store.startWork(anyLong(),anyString(),anyString(),anyInt(),anyString(),anyString())).thenReturn("work");
        var callbacks=MemoryTools.tracked(store,42,"session","run",new Fixture());
        for(var callback:callbacks) {
            if(callback.getToolDefinition().name().equals("lookup")) {
                String raw=callback.call("{}");
                assertEquals("库存12件，单价99.90元",com.alibaba.fastjson2.JSON.parseObject(raw,String.class));
                verify(store).finishWork(42,"work",raw,"completed");
            } else assertThrows(RuntimeException.class,()->callback.call("{}"));
        }
        verify(store,times(2)).startWork(eq(42L),eq("session"),eq("run"),anyInt(),anyString(),eq("{}"));
        verify(store).finishWork(eq(42L),eq("work"),contains("工具执行失败"),eq("failed"));
    }
    @Test void observeReportsStartAndFinishWithoutChangingTheToolResult() {
        MemoryStore store=mock(MemoryStore.class);when(store.startWork(anyLong(),anyString(),anyString(),anyInt(),anyString(),anyString())).thenReturn("work");
        var seen=new java.util.ArrayList<String>();
        var callbacks=MemoryTools.observe(store,42,"session","run",(step,name,status,input,output)->seen.add(status+":"+name),new Fixture());
        for(var callback:callbacks) if(callback.getToolDefinition().name().equals("lookup")) assertEquals("库存12件，单价99.90元",com.alibaba.fastjson2.JSON.parseObject(callback.call("{}"),String.class));
        assertEquals(java.util.List.of("running:lookup","completed:lookup"),seen);
    }
}
