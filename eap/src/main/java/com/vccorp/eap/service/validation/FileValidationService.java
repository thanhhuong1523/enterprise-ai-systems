package com.vccorp.eap.service.validation;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;

/**
 * Interface chịu trách nhiệm xác thực định dạng và tính toàn vẹn của tệp tải lên.
 */
public interface FileValidationService {
    
    /**
     * Xác thực sơ bộ thông tin tệp tải lên dựa vào phần mở rộng (extension) và dung lượng tệp.
     * @throws com.vccorp.eap.common.exception.BusinessException nếu tệp không hợp lệ.
     */
    void validateExtensionAndSize(MultipartFile file);
    
    /**
     * Xác thực nâng cao thông qua Magic Bytes của tệp vật lý (sử dụng Apache Tika).
     * Ngăn chặn hành vi giả mạo phần mở rộng tệp tin (Extension Spoofing).
     * @throws com.vccorp.eap.common.exception.BusinessException nếu định dạng thực tế không khớp với danh sách cho phép.
     */
    void validateMagicBytes(Path tempFilePath, String filename);
}
