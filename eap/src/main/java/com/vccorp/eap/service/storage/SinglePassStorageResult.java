package com.vccorp.eap.service.storage;

import java.nio.file.Path;

/**
 * DTO chứa kết quả của thao tác ghi tệp tạm 1-pass (§4.1).
 * Được trả về bởi {@link FileStorageService#storeTempFile(java.io.InputStream)}.
 */
public record SinglePassStorageResult(
    String hash,
    long fileSize,
    Path tempFilePath
) {
    // Java Bean style getters for compatibility
    public String getHash() {
        return hash;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Path getTempFilePath() {
        return tempFilePath;
    }

    public static Builder builder(String hash, long fileSize, Path tempFilePath) {
        return new Builder(hash, fileSize, tempFilePath);
    }

    public static class Builder {
        private final String hash;
        private final long fileSize;
        private final Path tempFilePath;

        public Builder(String hash, long fileSize, Path tempFilePath) {
            this.hash = hash;
            this.fileSize = fileSize;
            this.tempFilePath = tempFilePath;
        }

        public SinglePassStorageResult build() {
            return new SinglePassStorageResult(hash, fileSize, tempFilePath);
        }
    }
}
