package com.aierp.erp.memory;

import com.aierp.erp.serviceImpl.ai.AiOrchestrationService;
import com.aierp.model.entity.AiConfig;
import com.aierp.service.AiConfigService;
import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryModelTest {
    @Test void providerProtocolSupportsRealChatClientAndEmbeddingRequests() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> embeddingInput=new AtomicReference<>();
        AtomicReference<String> embeddingAuth=new AtomicReference<>();
        server.createContext("/v1/embeddings",exchange->{
            embeddingAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            embeddingInput.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response="{\"data\":[{\"index\":0,\"embedding\":[0.25,0.75]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.createContext("/v1/chat/completions",exchange->{
            exchange.getRequestBody().readAllBytes();
            String response=JSON.toJSONString(Map.of("id","fixture","object","chat.completion","created",1,"model","fixture","choices",new Object[]{Map.of("index",0,"message",Map.of("role","assistant","content","{\"summary\":\"预算为99.90元\"}"),"finish_reason","stop")},"usage",Map.of("prompt_tokens",1,"completion_tokens",1,"total_tokens",2)));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var config=new AiConfig();config.setApiEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/v1");config.setApiKey("test-fixture-only");config.setModelName("fixture");
            var configs=mock(AiConfigService.class);when(configs.getDefaultConfig()).thenReturn(config);
            var properties=new MemoryProperties();properties.setEmbeddingModel("fixture-embedding");
            var model=new MemoryModel(new AiOrchestrationService(configs),properties);
            assertArrayEquals(new float[]{.25f,.75f},model.embed("预算"));
            assertEquals("预算",JSON.parseObject(embeddingInput.get()).getString("input"));
            assertEquals("Bearer test-fixture-only",embeddingAuth.get());
            assertEquals("预算为99.90元",model.json("总结",Map.of("text","预算为99.90元")).getString("summary"));
            properties.setEmbeddingApiKey("fixture-separate-vector-key");
            properties.setEmbeddingEndpoint(config.getApiEndpoint());
            when(configs.getDefaultConfig()).thenReturn(null);
            assertArrayEquals(new float[]{.25f,.75f},model.embed("独立向量凭据"));
            assertEquals("Bearer fixture-separate-vector-key",embeddingAuth.get());
        } finally {server.stop(0);}
    }
}
