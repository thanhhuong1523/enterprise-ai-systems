package com.vccorp.eap.service.validation.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.service.validation.FileValidationService;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Lớp thực thi của FileValidationService.
 * Thực hiện kiểm tra định dạng file mở rộng và kiểm tra nội dung thực tế (Magic Bytes) bằng thư viện Apache Tika.
 */
@Service
public class FileValidationServiceImpl implements FileValidationService {

    /** Danh sách phần mở rộng tệp được hỗ trợ */
    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".docx", ".xlsx", ".pptx");
    
    /** Dung lượng tệp tối đa (50MB) */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** Danh sách mã MIME Type được chấp nhận */
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    /** Đối tượng phân tích định dạng tệp Apache Tika */
    private final Tika tika = new Tika();

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateExtensionAndSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tệp đính kèm không được trống.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dung lượng file vượt quá giới hạn 50MB.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên tệp không hợp lệ hoặc thiếu phần mở rộng.");
        }

        String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định dạng file không được hỗ trợ.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateMagicBytes(Path tempFilePath, String filename) {
        try {
            String mimeType = tika.detect(tempFilePath.toFile());
            if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định dạng file thực tế không được hỗ trợ.");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể xác minh định dạng file thực tế.");
        }
    }
}
