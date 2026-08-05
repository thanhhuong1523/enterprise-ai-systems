package com.vccorp.eap.worker.impl;

import com.vccorp.eap.worker.MockProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Lớp thực thi của MockProcessingService.
 * Thực hiện mô phỏng tải xử lý phân đoạn tài liệu của Worker bằng Thread.sleep.
 */
@Service
public class MockProcessingServiceImpl implements MockProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MockProcessingServiceImpl.class);

    /** Thời gian giả lập xử lý mỗi phân đoạn (mili-giây) */
    private final long chunkProcessingTimeMs;

    /**
     * Khởi tạo MockProcessingServiceImpl.
     */
    public MockProcessingServiceImpl(
            @Value("${eap.worker.chunk-processing-time-ms:1000}") long chunkProcessingTimeMs) {
        this.chunkProcessingTimeMs = chunkProcessingTimeMs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processChunk(UUID documentId, int chunkIndex) throws InterruptedException {
        log.info("Starting processing chunk {} for document {}", chunkIndex, documentId);
        Thread.sleep(chunkProcessingTimeMs);
        log.info("Completed processing chunk {} for document {}", chunkIndex, documentId);
    }
}
