package com.vccorp.eap.recovery;

import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.worker.WorkerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;

@Service
public class RecoveryService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final DocumentRepository documentRepository;
    private final WorkerScheduler workerScheduler;
    private final TransactionTemplate transactionTemplate;
    private final boolean schedulerEnabled;

    public RecoveryService(DocumentRepository documentRepository,
                           WorkerScheduler workerScheduler,
                           PlatformTransactionManager transactionManager,
                           @Value("${eap.worker.scheduler.enabled:true}") boolean schedulerEnabled) {
        this.documentRepository = documentRepository;
        this.workerScheduler = workerScheduler;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.schedulerEnabled = schedulerEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Executing Startup Recovery for interrupted processing tasks...");
        
        LocalDateTime now = LocalDateTime.now();
        Integer affectedRows = transactionTemplate.execute(status -> {
            return documentRepository.resetProcessingTasksToReady(now);
        });
        
        log.info("Startup Recovery completed. Reset {} tasks from PROCESSING to READY.", affectedRows);
        
        // Start the scheduler polling thread pool after recovery finishes if enabled
        if (schedulerEnabled && !workerScheduler.isRunning()) {
            workerScheduler.start();
            log.info("WorkerScheduler started programmatically after startup recovery.");
        }
    }
}
