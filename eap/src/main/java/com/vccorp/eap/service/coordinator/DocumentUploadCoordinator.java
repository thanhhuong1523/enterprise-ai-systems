package com.vccorp.eap.service.coordinator;

import com.vccorp.eap.service.storage.SinglePassStorageResult;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface DocumentUploadCoordinator {
    SinglePassStorageResult coordinate(MultipartFile file) throws IOException;
}