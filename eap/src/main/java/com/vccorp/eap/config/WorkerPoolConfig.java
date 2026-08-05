package com.vccorp.eap.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WorkerPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkerPoolConfig.class);

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;

    public WorkerPoolConfig(
            @Value("${eap.worker.core-pool-size:0}") int corePoolSize,
            @Value("${eap.worker.max-pool-size:0}") int maxPoolSize,
            @Value("${eap.worker.queue-capacity:0}") int queueCapacity) {
        
        int cores = Runtime.getRuntime().availableProcessors();
        
        // Fallback calculations based on host cores if not explicitly configured
        this.corePoolSize = corePoolSize > 0 ? corePoolSize : (cores + 1); // CPU-bound defaults
        this.maxPoolSize = maxPoolSize > 0 ? maxPoolSize : (int) (cores * 2.5); // IO-bound defaults
        this.queueCapacity = queueCapacity;
        
        log.info("Worker thread pool sizing configured: cores={}, corePoolSize={}, maxPoolSize={}, queueCapacity={}", 
                cores, this.corePoolSize, this.maxPoolSize, this.queueCapacity);
    }

    @Bean(name = "workerTaskExecutor")
    public ThreadPoolTaskExecutor workerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("WorkerExecutor-");
        
        // Graceful shutdown configuration (Section 10.6)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        return executor;
    }
}
