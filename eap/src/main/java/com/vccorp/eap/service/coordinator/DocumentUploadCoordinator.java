package com.vccorp.eap.service.coordinator;

import com.vccorp.eap.service.storage.FileStorageService;
import com.vccorp.eap.service.storage.SinglePassStorageResult;
import com.vccorp.eap.service.validation.FileValidationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Điều phối luồng tải lên vật lý (§8.2).
 * <p>
 * Nhận MultipartFile từ DocumentServiceImpl, điều phối tuần tự:
 * <ol>
 *   <li>Kiểm tra phần mở rộng và dung lượng (trước khi đọc stream)</li>
 *   <li>Ghi file tạm và tính SHA-256 1-pass (FileStorageService)</li>
 *   <li>Kiểm tra magic bytes từ tệp tạm (Apache Tika)</li>
 * </ol>
 * <p>
 * Nếu bước 3 thất bại, coordinator tự xóa tệp tạm trước khi ném ngoại lệ
 * để tránh rò rỉ đĩa — vì DocumentServiceImpl chưa thiết lập finally block
 * tại thời điểm coordinate() được gọi.
 */
@Service
public class DocumentUploadCoordinator {

    private final FileStorageService fileStorageService;
    private final FileValidationService fileValidationService;

    public DocumentUploadCoordinator(FileStorageService fileStorageService,
                                     FileValidationService fileValidationService) {
        this.fileStorageService = fileStorageService;
        this.fileValidationService = fileValidationService;
    }

    /**
     * Điều phối luồng tải lên vật lý và trả về kết quả 1-pass.
     *
     * @param file tệp tin từ HTTP multipart request
     * @return SinglePassStorageResult chứa hash, fileSize, tempFilePath
     * @throws IOException       nếu lỗi I/O khi ghi file tạm
     */
    public SinglePassStorageResult coordinate(MultipartFile file) throws IOException {
        // Bước 1: Kiểm tra phần mở rộng và dung lượng (trước khi đọc stream)
        fileValidationService.validateExtensionAndSize(file);

        // Bước 2: Ghi file tạm và tính SHA-256 1-pass
        SinglePassStorageResult result = fileStorageService.storeTempFile(file.getInputStream());

        // Bước 3: Kiểm tra magic bytes từ tệp tạm (§8.2 — "từ luồng file tạm")
        try {
            fileValidationService.validateMagicBytes(result.getTempFilePath(), file.getOriginalFilename());
            return result;
        } catch (RuntimeException | Error e) {
            fileStorageService.deleteTempFileQuietly(result.getTempFilePath());
            throw e;
        }
    }
}