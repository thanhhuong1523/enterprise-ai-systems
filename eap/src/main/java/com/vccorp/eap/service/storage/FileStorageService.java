package com.vccorp.eap.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Quản lý I/O tệp tin vật lý trên đĩa (DetailedDesign §1.1).
 * Thay thế hoàn toàn {@code StorageService} cũ của Week 1 (§8.1).
 */
public interface FileStorageService {

    /**
     * Đọc stream 1-pass, vừa ghi file tạm tại {@code /eap-storage/tmp/temp_<uuid>}
     * vừa tính toán SHA-256 hex hash. Buffer khuyến nghị 8KB (§4.1).
     *
     * @param inputStream luồng dữ liệu tệp từ MultipartFile
     * @return DTO chứa hash, fileSize và đường dẫn tệp tạm
     * @throws IOException nếu xảy ra lỗi I/O
     */
    SinglePassStorageResult storeTempFile(InputStream inputStream) throws IOException;

    /**
     * Di chuyển file tạm sang vị trí lưu trữ chính thức {@code /eap-storage/{hash}}
     * bằng thao tác đổi tên nguyên tử ({@code Files.move} - atomic rename ở tầng OS).
     * Tự động xử lý {@link java.nio.file.FileAlreadyExistsException} nếu tệp đích
     * đã được phòng ban khác tạo song song (§4.2, §8.1).
     *
     * @param tempFilePath đường dẫn tệp tạm
     * @param hash         mã SHA-256 hex để đặt tên tệp đích
     * @return đường dẫn tuyệt đối của tệp đích (dùng làm file_reference)
     * @throws IOException nếu xảy ra lỗi I/O nghiêm trọng
     */
    String moveTempToPermanent(Path tempFilePath, String hash);

    /**
     * Xóa tệp tạm trong khối {@code finally} khi phát hiện trùng lặp hoặc lỗi.
     * Không ném exception (xóa thất bại chỉ ghi log warning) (§1.1, §4.2).
     *
     * @param tempFilePath đường dẫn tệp tạm cần xóa
     */
    void deleteTempFileQuietly(Path tempFilePath);

    /**
     * Đọc toàn bộ nội dung tệp vật lý vào mảng byte.
     * Giữ lại để tương thích ngược cho use case resolveAlias/download (§8.1).
     *
     * @param fileReference đường dẫn tuyệt đối của tệp
     * @return nội dung tệp
     * @throws IOException nếu tệp không tồn tại hoặc lỗi đọc
     */
    byte[] loadFile(String fileReference) throws IOException;
}
