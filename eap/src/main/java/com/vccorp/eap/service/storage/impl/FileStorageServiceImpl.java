package com.vccorp.eap.service.storage.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.service.storage.FileStorageService;
import com.vccorp.eap.service.storage.SinglePassStorageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Triển khai FileStorageService với cơ chế 1-pass streaming SHA-256 và atomic rename (§1.1, §4.1).
 * Gói: com.vccorp.eap.service.storage.impl
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);
    private static final int BUFFER_SIZE = 8192; // 8KB theo §4.1

    @Value("${eap.upload.dir:./eap-storage}")
    private String uploadDir;

    @Value("${eap.upload.temp-dir:./eap-storage/tmp}")
    private String tempUploadDir;

    /**
     * Đọc stream 1-pass, vừa ghi file tạm vừa tính SHA-256 (§4.1).
     * Tên file tạm: temp_<uuid> tại /eap-storage/tmp/.
     * Nếu xảy ra lỗi giữa chừng, file tạm được xóa để tránh rác đĩa.
     */
    @Override
    public SinglePassStorageResult storeTempFile(InputStream inputStream) throws IOException {
        File tmpDir = new File(tempUploadDir).getAbsoluteFile();
        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            throw new IOException("Không thể tạo thư mục tạm.");
        }

        Path tempFilePath = tmpDir.toPath().resolve("temp_" + UUID.randomUUID());
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }

        long fileSize = 0;
        try (OutputStream fos = Files.newOutputStream(tempFilePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                digest.update(buffer, 0, bytesRead);
                fileSize += bytesRead;
            }
        } catch (IOException e) {
            // Xóa file tạm chưa hoàn chỉnh để tránh rác đĩa
            deleteTempFileQuietly(tempFilePath);
            throw e;
        }

        String hash = bytesToHex(digest.digest());
        log.debug("Stored temp file: path={}, hash={}, size={}", tempFilePath, hash, fileSize);
        return new SinglePassStorageResult(hash, fileSize, tempFilePath);
    }

    /**
     * Di chuyển file tạm sang /eap-storage/{hash} bằng atomic rename (§4.2, ADR-013).
     * Nếu tệp đích đã tồn tại (do phòng ban khác vừa ghi), bắt FileAlreadyExistsException
     * và tái sử dụng đường dẫn tệp đích có sẵn.
     */
    @Override
    public String moveTempToPermanent(Path tempFilePath, String hash) {
        try {
            File storageDir = new File(uploadDir).getAbsoluteFile();
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                throw new BusinessException(
                        ErrorCode.ERR_SYSTEM_ERROR,
                        "Không thể tạo thư mục lưu trữ."
                );
            }
            Path targetPath = storageDir.toPath().resolve(hash);
            Files.move(tempFilePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            log.debug("Atomic rename success: {} -> {}", tempFilePath, targetPath);
            return targetPath.toAbsolutePath().toString();
        } catch (FileAlreadyExistsException ex) {
            log.debug("Physical file already exists, reuse existing file={}", hash);

            return new File(uploadDir)
                    .toPath()
                    .resolve(hash)
                    .toAbsolutePath()
                    .toString();
        } catch (IOException e) {
            // SIS cross-department race: phòng ban khác vừa atomic rename thành công trước.
            // Tệp đích đã có, tệp tạm sẽ được xóa bởi finally block của DocumentServiceImpl (§4.2).
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể tải tệp lên hệ thống!");
        }
    }

    /**
     * Xóa tệp tạm yên lặng trong finally block (§1.1, §4.2).
     */
    @Override
    public void deleteTempFileQuietly(Path tempFilePath)  {
        if (tempFilePath == null) return;
        try {
            Files.deleteIfExists(tempFilePath);
            log.debug("Deleted temp file: {}", tempFilePath);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", tempFilePath, e);
        }
    }

    /**
     * Đọc toàn bộ nội dung tệp vật lý (tương thích ngược cho resolveAlias §8.1).
     */
    @Override
    public byte[] loadFile(String fileReference) throws IOException {
        File file = new File(fileReference);
        if (!file.exists()) {
            throw new IOException("Tệp tin vật lý không tồn tại: " + fileReference);
        }
        return Files.readAllBytes(file.toPath());
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }
}
