package com.aierp.erp.serviceImpl.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Local/environment overrides stay in memory and are never written to ai_config. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.provider")
public class AiProviderProperties {
    private String apiKey = "";
    private String endpoint = "https://api.siliconflow.cn/v1";
    private String model = "deepseek-ai/DeepSeek-V4.1-Flash";
}
