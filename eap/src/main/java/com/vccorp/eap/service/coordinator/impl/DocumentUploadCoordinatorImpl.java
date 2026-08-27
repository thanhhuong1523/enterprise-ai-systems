package com.vccorp.eap.service.coordinator.impl;

import com.vccorp.eap.service.coordinator.DocumentUploadCoordinator;
import com.vccorp.eap.service.storage.FileStorageService;
import com.vccorp.eap.service.storage.SinglePassStorageResult;
import com.vccorp.eap.service.validation.FileValidationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class DocumentUploadCoordinatorImpl implements DocumentUploadCoordinator {

    private final FileStorageService fileStorageService;
    private final FileValidationService fileValidationService;

    public DocumentUploadCoordinatorImpl(FileStorageService fileStorageService,
                                         FileValidationService fileValidationService) {
        this.fileStorageService = fileStorageService;
        this.fileValidationService = fileValidationService;
    }

    @Override
    public SinglePassStorageResult coordinate(MultipartFile file) throws IOException {
        // Bước 1: Kiểm tra phần mở rộng và dung lượng (trước khi đọc stream)
        fileValidationService.validateExtensionAndSize(file);

        // Bước 2: Ghi file tạm và tính SHA-256 1-pass
        SinglePassStorageResult result = fileStorageService.storeTempFile(file.getInputStream());

        // Bước 3: Kiểm tra magic bytes từ tệp tạm (§8.2 — "từ luồng file tạm")
        try {
            fileValidationService.validateMagicBytes(result.getTempFilePath(), file.getOriginalFilename());
            return result;
        } catch (RuntimeException e) {
            fileStorageService.deleteTempFileQuietly(result.getTempFilePath());
            throw e;
        }
    }
}
