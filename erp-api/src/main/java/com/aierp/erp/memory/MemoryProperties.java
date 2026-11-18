package com.aierp.erp.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.memory")
public class MemoryProperties {
    private boolean initializeSchema = true;
    private int contextChars = 48000;
    private int recentTurns = 3;
    private int retrievalChars = 6000;
    /** Verbatim constraint lines kept inside the prompt. Older lines move to long-term memory unchanged. */
    private int anchorChars = 12000;
    private int recallRecent = 400;
    private int recallImportant = 400;
    private int maxActive = 500;
    private int idleMinutes = 15;
    private int retentionDays = 180;
    private String embeddingModel = "";
    private String embeddingEndpoint = "";
}
