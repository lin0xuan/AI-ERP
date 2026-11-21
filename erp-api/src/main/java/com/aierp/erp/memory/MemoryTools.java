package com.aierp.erp.memory;

import com.alibaba.fastjson2.JSON;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** User and session are captured server-side, never supplied by the model. */
public class MemoryTools {
    private final MemoryStore store;
    private final long user;
    private final String session;
    public MemoryTools(MemoryStore store,long user,String session) { this.store=store;this.user=user;this.session=session; }

    @Tool(description="查看当前会话最近的工具调用原始记录，包括查过什么资料、参数、返回结果、步骤和成功/失败状态。历史结果不保证仍然有效，实时业务数据应重新查询。")
    public String readWorkMemory() {
        return JSON.toJSONString(store.work(user,session).stream().map(row->{
            var preview=new java.util.LinkedHashMap<String,Object>();
            for(String key:new String[]{"id","run_id","step_no","tool_name","status","created_at"}) preview.put(key,row.get(key));
            String result=MemoryStore.string(row,"result");
            preview.put("resultChars",result.length());
            preview.put("result",result.length()<=500?result:"请用readWorkMemoryDetail分页读取原文，不可推断缺失内容");
            return preview;
        }).toList());
    }

    @Tool(description="按readWorkMemory返回的id分页读取当前会话的一条工具记录。offset从0开始，每页最多8000字符，hasMore=true时继续读取nextOffset。分页可能在数字中间分割，必须拼接完整原文后再使用数字。")
    public String readWorkMemoryDetail(String id,int offset) {
        var row=store.workEntry(user,session,id);
        if(row.isEmpty()) return "未找到当前用户当前会话的记录";
        String text="参数："+MemoryStore.string(row,"arguments")+"\n结果："+MemoryStore.string(row,"result");
        if(offset<0 || offset>text.length()) return "offset超出范围";
        int end=Math.min(text.length(),offset+8000);
        return JSON.toJSONString(java.util.Map.of("content",text.substring(offset,end),"offset",offset,"nextOffset",end,"hasMore",end<text.length(),"totalChars",text.length()));
    }

    @FunctionalInterface
    public interface TraceHook {
        void onTool(int step, String name, String status, String input, String output);
    }

    public static ToolCallback[] tracked(MemoryStore store,long user,String session,String run,Object... toolObjects) {
        return observe(store, user, session, run, null, toolObjects);
    }

    public static ToolCallback[] observe(MemoryStore store,long user,String session,String run,TraceHook hook,Object... toolObjects) {
        AtomicInteger step=new AtomicInteger();
        return Arrays.stream(ToolCallbacks.from(toolObjects)).map(delegate->new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
            @Override public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
            @Override public String call(String input) { return call(input,new ToolContext(java.util.Map.of())); }
            @Override public String call(String input,ToolContext context) {
                int stepNo=step.incrementAndGet();
                String name=getToolDefinition().name();
                String redacted=MemoryPolicy.redact(input);
                String id=store.startWork(user,session,run,stepNo,name,redacted);
                report(hook, stepNo, name, "running", redacted, null);
                try {
                    String output=delegate.call(input,context);
                    // readWorkMemory is itself recorded without recursively duplicating the entire work log.
                    String stored=name.startsWith("readWorkMemory")?"已读取工作记录":MemoryPolicy.redact(output);
                    store.finishWork(user,id,stored,"completed");
                    report(hook, stepNo, name, "completed", redacted, stored);
                    return output;
                } catch(RuntimeException e) {
                    String failure="工具执行失败："+e.getClass().getSimpleName();
                    store.finishWork(user,id,failure,"failed");
                    report(hook, stepNo, name, "failed", redacted, failure);
                    throw e;
                }
            }
        }).toArray(ToolCallback[]::new);
    }

    private static void report(TraceHook hook,int step,String name,String status,String input,String output) {
        if (hook == null) return;
        try { hook.onTool(step, name, status, input, output); }
        catch (RuntimeException ignored) { }
    }
}
