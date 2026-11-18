package com.aierp.erp.memory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MemoryExecution {
    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        var scheduler=new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);scheduler.setThreadNamePrefix("memory-scheduler-");
        return scheduler;
    }
    @Bean("chatMemoryExecutor")
    public ThreadPoolTaskExecutor chatMemoryExecutor() {
        var pool=new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);pool.setMaxPoolSize(4);pool.setQueueCapacity(32);
        pool.setThreadNamePrefix("chat-memory-");pool.setWaitForTasksToCompleteOnShutdown(false);
        return pool;
    }
}
