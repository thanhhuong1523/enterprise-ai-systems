package com.vccorp.eap.worker.impl;

import com.vccorp.eap.dto.TaskClaimedResult;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.worker.WorkerExecutor;
import com.vccorp.eap.worker.WorkerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/**
 * Lớp thực thi của WorkerScheduler.
 * Tạo ra một ScheduledExecutorService chạy nền để thực hiện chu kỳ quét hàng đợi và submit công việc vào Spring ThreadPoolTaskExecutor.
 */
@Service
public class WorkerSchedulerImpl implements WorkerScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkerSchedulerImpl.class);

    private final WorkerExecutor workerExecutor;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final DocumentRepository documentRepository;

    private final long pollingIntervalMs;
    private final int maxRetries;

    private ScheduledExecutorService schedulerExecutor;
    private boolean running = false;

    /**
     * Khởi tạo WorkerSchedulerImpl.
     */
    public WorkerSchedulerImpl(WorkerExecutor workerExecutor,
                               ThreadPoolTaskExecutor taskExecutor,
                               DocumentRepository documentRepository,
                               @Value("${eap.worker.polling-interval-ms:1000}") long pollingIntervalMs,
                               @Value("${eap.worker.max-retries:5}") int maxRetries) {
        this.workerExecutor = workerExecutor;
        this.taskExecutor = taskExecutor;
        this.documentRepository = documentRepository;
        this.pollingIntervalMs = pollingIntervalMs;
        this.maxRetries = maxRetries;
    }

    @Override
    public boolean isAutoStartup() {
        return false;
    }

    @Override
    public synchronized void start() {
        if (this.running) {
            return;
        }
        log.info("Starting WorkerScheduler polling daemon thread (pollingIntervalMs={})...", pollingIntervalMs);
        this.schedulerExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WorkerScheduler-Thread");
            thread.setDaemon(true);
            return thread;
        });

        this.schedulerExecutor.scheduleWithFixedDelay(
                () -> {
                     try {
                         pollTasks();
                     } catch (Throwable t) {
                         log.error("Unhandled error in task polling cycle", t);
                     }
                },
                0L,
                pollingIntervalMs,
                TimeUnit.MILLISECONDS
        );
        this.running = true;
    }

    @Override
    public synchronized void stop() {
        if (!this.running) {
            return;
        }
        log.info("Stopping WorkerScheduler polling daemon...");
        if (this.schedulerExecutor != null) {
            this.schedulerExecutor.shutdown();
            try {
                if (!this.schedulerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    this.schedulerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                this.schedulerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        this.running = false;
    }

    @Override
    public synchronized void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public synchronized boolean isRunning() {
        return this.running;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public void pollTasks() {
        int maxPoolSize = taskExecutor.getMaxPoolSize();
        int activeCount = taskExecutor.getActiveCount();
        int idleWorkers = maxPoolSize - activeCount;

        if (idleWorkers <= 0) {
            log.debug("No idle workers available (active={}, max={})", activeCount, maxPoolSize);
            return;
        }

        log.debug("Polling tasks. Free slots: {}", idleWorkers);
        for (int i = 0; i < idleWorkers; i++) {
            String workerId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();

            Optional<TaskClaimedResult> claimed = documentRepository.claimTask(workerId, now, maxRetries);
            if (!claimed.isPresent()) {
                log.debug("No more READY tasks in queue. Polling cycle finished.");
                break;
            }

            TaskClaimedResult task = claimed.get();
            log.info("Successfully claimed task {} using workerId {}", task.id(), workerId);

            try {
                taskExecutor.submit(() -> workerExecutor.executeTask(task.id(), workerId));
            } catch (RejectedExecutionException ex) {
                log.error("Task {} submission was rejected by Thread Pool. Releasing task in DB.", task.id(), ex);
                try {
                    documentRepository.releaseTask(task.id(), workerId, LocalDateTime.now());
                } catch (Throwable t) {
                    log.error("Failed to release task {} after executor rejection", task.id(), t);
                }
            }
        }
    }
}
