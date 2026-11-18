package com.aierp.erp.controller.ai;

import com.aierp.common.AjaxResult;
import com.aierp.erp.serviceImpl.ChatService;
import com.aierp.model.entity.AiConfig;
import com.aierp.model.entity.AiConversationHistory;
import com.aierp.security.common.BaseController;
import com.aierp.service.AiConfigService;
import com.aierp.service.AiConversationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/ai")
public class ChatController extends BaseController {

    private final ChatService chatService;
    private final AiConfigService aiConfigService;
    private final AiConversationHistoryService historyService;
    private final com.aierp.erp.memory.MemoryStore memoryStore;

    @PostMapping(value = "/chat/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@RequestBody ChatSendRequest req) {
        if (req.getMessage() == null || req.getMessage().isBlank() || req.getMessage().length() > 16000
                || (req.getSessionId() != null && req.getSessionId().length() > 64)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "消息须为1至16000字符，会话ID不超过64字符");
        }
        String sessionId = req.getSessionId() != null && !req.getSessionId().isEmpty()
                ? req.getSessionId() : UUID.randomUUID().toString();
        Long userId = getUserId();



        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return errorEmitter("请先在 AI 智能 > 模型配置 中添加并启用默认模型");
        }

        return chatService.send(config, sessionId, userId, req.getMessage());
    }

    @GetMapping("/chat/history")
    public AjaxResult getHistory(@RequestParam String sessionId) {
        List<AiConversationHistory> list = historyService.getConversation(sessionId, getUserId());
        return AjaxResult.success(list);
    }

    @GetMapping("/chat/sessions")
    public AjaxResult getSessions() {
        List<String> sessions = historyService.getSessions(getUserId());
        return AjaxResult.success(sessions);
    }

    @DeleteMapping("/chat/session/{sessionId}")
    public AjaxResult deleteSession(@PathVariable String sessionId) {
        long user = getUserId();
        String lease = memoryStore.acquire(user);
        try {
            historyService.deleteSession(sessionId, user);
            memoryStore.deleteSession(user, sessionId);
            return AjaxResult.success();
        } finally { memoryStore.release(user, lease); }
    }

    @ExceptionHandler(com.aierp.erp.memory.MemoryStore.BusyException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public AjaxResult busy(com.aierp.erp.memory.MemoryStore.BusyException error) { return AjaxResult.error(error.getMessage()); }

    private SseEmitter errorEmitter(String msg) {
        SseEmitter emitter = new SseEmitter();
        try {
            emitter.send(SseEmitter.event().data(
                    "{\"type\":\"error\",\"content\":\"" + msg + "\"}"));
            emitter.complete();
        } catch (Exception ignored) {}
        return emitter;
    }

    public static class ChatSendRequest {
        private String sessionId;
        private String message;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
