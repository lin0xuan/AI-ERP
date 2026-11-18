package com.aierp.erp.controller.ai;

import com.aierp.common.AjaxResult;
import com.aierp.erp.memory.*;
import com.aierp.security.common.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class MemoryController extends BaseController {
    private final MemoryStore store;
    private final MemoryProperties properties;
    @GetMapping("/memory")
    public AjaxResult list() {
        return AjaxResult.success(Map.of("items",store.list(getUserId()),"semanticSearch",!properties.getEmbeddingModel().isBlank()));
    }
    @GetMapping("/memory/{id}/revisions")
    public AjaxResult revisions(@PathVariable String id) { return AjaxResult.success(store.revisions(getUserId(),id)); }
    @DeleteMapping("/memory/{id}")
    public AjaxResult forget(@PathVariable String id) {
        long user=getUserId();String lease=store.acquire(user);
        try { store.forget(user,id);return AjaxResult.success(); } finally {store.release(user,lease);}
    }
    @GetMapping("/chat/session/{session}/work")
    public AjaxResult work(@PathVariable String session) {
        return AjaxResult.success(store.work(getUserId(),session).stream().map(MemoryController::preview).toList());
    }
    private static Map<String,Object> preview(Map<String,Object> row) {
        var copy=new java.util.LinkedHashMap<String,Object>(row);
        for(String key:new String[]{"arguments","result"}) {
            String text=MemoryStore.string(row,key);
            if(text.length()>2000) copy.put(key,text.substring(0,2000)+"\n…共"+text.length()+"字符，完整原文在对话中分页读取");
        }
        return copy;
    }
    @PostMapping("/chat/session/{session}/end")
    public AjaxResult end(@PathVariable String session) {store.requestEnd(getUserId(),session);return AjaxResult.success();}
    @ExceptionHandler(MemoryStore.BusyException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public AjaxResult busy(MemoryStore.BusyException error) {return AjaxResult.error(error.getMessage());}
}
