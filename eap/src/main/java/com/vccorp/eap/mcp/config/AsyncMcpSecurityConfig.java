package com.vccorp.eap.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * Cấu hình Virtual Thread Executor an toàn đa luồng, kế thừa SecurityContext từ Servlet Thread sang Worker Thread.
 */
@Configuration
@EnableAsync
public class AsyncMcpSecurityConfig {

    @Bean(name = "mcpTaskExecutor")
    public AsyncTaskExecutor mcpTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("McpWorker-");
        executor.setVirtualThreads(true);
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
