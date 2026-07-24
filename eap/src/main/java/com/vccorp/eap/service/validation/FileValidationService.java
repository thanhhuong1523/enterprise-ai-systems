package com.vccorp.eap.service.validation;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Xác thực tính hợp lệ của tệp tin tải lên (§8.2).
 * Tách biệt khỏi DocumentServiceImpl để tuân thủ SRP.
 * <p>
 * Hai bước kiểm tra độc lập:
 * 1. {@link #validateExtensionAndSize}: kiểm tra phần mở rộng và dung lượng từ metadata MultipartFile
 *    (trước khi ghi tệp tạm — không cần đọc stream).
 * 2. {@link #validateMagicBytes}: kiểm tra định dạng nhị phân thực tế bằng Apache Tika
 *    từ tệp tạm đã ghi (§8.2 — "từ luồng file tạm").
 */
@Service
public class FileValidationService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".docx", ".xlsx", ".pptx");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB (§3.1)

    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private final Tika tika = new Tika();

    /**
     * Bước 1: Kiểm tra phần mở rộng tệp và dung lượng từ metadata MultipartFile (§3.1).
     * Thực hiện trước khi ghi stream để trả lỗi sớm, tiết kiệm I/O đĩa.
     *
     * @param file tệp tin từ HTTP multipart request
     * @throws BusinessException nếu tệp không hợp lệ
     */
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
     * Bước 2: Kiểm tra định dạng nhị phân thực tế (magic bytes) bằng Apache Tika
     * từ tệp tạm đã ghi trên đĩa (§3.1, §8.2 — chống giả mạo phần mở rộng).
     *
     * @param tempFilePath đường dẫn tệp tạm đã được ghi bởi FileStorageService
     * @param filename     tên tệp gốc từ request (dùng để Tika hint định dạng)
     * @throws BusinessException nếu định dạng thực tế không hợp lệ
     */
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
