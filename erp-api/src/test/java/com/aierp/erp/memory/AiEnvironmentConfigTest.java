package com.aierp.erp.memory;

import com.aierp.erp.serviceImpl.AiConfigServiceImpl;
import com.aierp.erp.serviceImpl.ai.AiProviderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AiEnvironmentConfigTest {
    @TempDir Path directory;
    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties({AiProviderProperties.class,MemoryProperties.class})
    static class Settings {}

    @Test void dotenvBindsSharedKeyChatModelAndBgeWithoutDatabaseWrites() throws Exception {
        Path env=directory.resolve(".env");
        Files.writeString(env,"SILICONFLOW_API_KEY=fixture-local-key\nAI_CHAT_ENDPOINT=https://api.siliconflow.cn/v1\nAI_CHAT_MODEL=deepseek-ai/DeepSeek-V4.1-Flash\nAI_MEMORY_EMBEDDING_MODEL=BAAI/bge-m3\nAI_MEMORY_EMBEDDING_ENDPOINT=https://api.siliconflow.cn/v1\nAI_MEMORY_EMBEDDING_API_KEY=fixture-vector-key\n");
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Settings.class)
                .withPropertyValues("spring.config.location=classpath:/application.yml",
                        "spring.config.import="+env.toUri()+"[.properties]")
                .run(context->{
                    assertNull(context.getStartupFailure());
                    var provider=context.getBean(AiProviderProperties.class);
                    var memory=context.getBean(MemoryProperties.class);
                    // No mapper/DB is injected: an explicit local key must bypass stored credentials.
                    var selected=new AiConfigServiceImpl(provider).getDefaultConfig();
                    assertEquals("fixture-local-key",selected.getApiKey());
                    assertEquals("deepseek-ai/DeepSeek-V4.1-Flash",selected.getModelName());
                    assertEquals("https://api.siliconflow.cn/v1",selected.getApiEndpoint());
                    assertEquals("BAAI/bge-m3",memory.getEmbeddingModel());
                    assertEquals("fixture-vector-key",memory.getEmbeddingApiKey());
                    assertFalse(memory.toString().contains("fixture-vector-key"));
                });
    }
}
