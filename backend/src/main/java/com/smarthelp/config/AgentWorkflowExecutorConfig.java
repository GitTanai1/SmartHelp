package com.smarthelp.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Bounded execution capacity for long-lived SSE agent workflows. */
@Configuration
public class AgentWorkflowExecutorConfig {

    @Bean("agentWorkflowExecutor")
    TaskExecutor agentWorkflowExecutor(
            @Value("${smarthelp.agent.executor.max-threads:8}") int maxThreads,
            @Value("${smarthelp.agent.executor.queue-capacity:32}") int queueCapacity) {
        int boundedMaxThreads = Math.max(1, maxThreads);
        int boundedQueueCapacity = Math.max(0, queueCapacity);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.min(2, boundedMaxThreads));
        executor.setMaxPoolSize(boundedMaxThreads);
        executor.setQueueCapacity(boundedQueueCapacity);
        executor.setThreadNamePrefix("agent-workflow-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
