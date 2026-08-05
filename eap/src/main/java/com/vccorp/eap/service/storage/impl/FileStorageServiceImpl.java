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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Triển khai FileStorageService với cơ chế 1-pass streaming SHA-256 và atomic rename (§1.1, §4.1).
 * Hỗ trợ fallback copy-delete và hash verification (§9.3).
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);
    private static final int BUFFER_SIZE = 8192; // 8KB theo §4.1

    @Value("${eap.upload.dir:./eap-storage}")
    private String uploadDir;

    @Value("${eap.upload.temp-dir:./eap-storage/tmp}")
    private String tempUploadDir;

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
            deleteTempFileQuietly(tempFilePath);
            throw e;
        }

        String hash = bytesToHex(digest.digest());
        log.debug("Stored temp file: path={}, hash={}, size={}", tempFilePath, hash, fileSize);
        return new SinglePassStorageResult(hash, fileSize, tempFilePath);
    }

    @Override
    public String moveTempToPermanent(Path tempFilePath, String hash) {
        File storageDir = new File(uploadDir).getAbsoluteFile();
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            throw new BusinessException(
                    ErrorCode.ERR_SYSTEM_ERROR,
                    "Không thể tạo thư mục lưu trữ."
            );
        }
        Path targetPath = storageDir.toPath().resolve(hash);

        if (Files.exists(targetPath)) {
            log.debug("Physical file already exists (pre-check), reuse existing file={}", hash);
            deleteTempFileQuietly(tempFilePath);
            return targetPath.toAbsolutePath().toString();
        }

        try {
            Files.move(tempFilePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            log.debug("Atomic rename success: {} -> {}", tempFilePath, targetPath);
            return targetPath.toAbsolutePath().toString();
        } catch (FileAlreadyExistsException ex) {
            log.debug("Physical file already exists (race), reuse existing file={}", hash);
            deleteTempFileQuietly(tempFilePath);
            return targetPath.toAbsolutePath().toString();
        } catch (IOException e) {
            log.warn("Atomic move failed, attempting copy-delete fallback", e);
            return performFallbackCopyDelete(tempFilePath, targetPath, hash);
        }
    }

    private String performFallbackCopyDelete(Path tempFilePath, Path targetPath, String hash) {
        if (Files.exists(targetPath)) {
            log.debug("Physical file already exists (fallback check), reuse existing file={}", hash);
            deleteTempFileQuietly(tempFilePath);
            return targetPath.toAbsolutePath().toString();
        }

        try {
            Files.copy(tempFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            String targetHash = calculateFileHash(targetPath);

            if (targetHash.equals(hash)) {
                Files.deleteIfExists(tempFilePath);
                log.debug("Fallback copy-delete success: {} -> {}", tempFilePath, targetPath);
                return targetPath.toAbsolutePath().toString();
            } else {
                Files.deleteIfExists(targetPath);
                throw new BusinessException(ErrorCode.ERR_HASH_MISMATCH, "Tải tệp thất bại: Sai mã băm hash sau khi sao chép.");
            }
        } catch (IOException fallbackEx) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            throw new BusinessException(ErrorCode.ERR_STORAGE_ERROR, "Lỗi lưu trữ tệp (fallback): " + fallbackEx.getMessage(), fallbackEx);
        }
    }

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

    @Override
    public byte[] loadFile(String fileReference) throws IOException {
        File file = new File(fileReference);
        if (!file.exists()) {
            throw new IOException("Tệp tin vật lý không tồn tại: " + fileReference);
        }
        return Files.readAllBytes(file.toPath());
    }

    @Override
    public boolean exists(String fileReference) {
        if (fileReference == null || fileReference.isBlank()) {
            return false;
        }
        try {
            return Files.exists(Path.of(fileReference));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private String calculateFileHash(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
        try (InputStream fis = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return bytesToHex(digest.digest());
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
