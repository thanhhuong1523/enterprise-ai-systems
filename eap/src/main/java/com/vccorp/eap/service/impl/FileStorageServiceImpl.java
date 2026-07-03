package com.vccorp.eap.service.impl;

import com.vccorp.eap.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageServiceImpl implements StorageService {

    @Value("${eap.upload.dir:./eap-storage}")
    private String uploadDir;

    @Override
    public String storeFile(MultipartFile file, String filename) throws IOException {
        File dir = new File(uploadDir).getAbsoluteFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File targetFile = new File(dir, filename);
        Files.copy(file.getInputStream(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return targetFile.getAbsolutePath();
    }

    @Override
    public byte[] loadFile(String fileReference) throws IOException {
        File file = new File(fileReference);
        if (!file.exists()) {
            throw new IOException("Tệp tin vật lý không tồn tại.");
        }
        return Files.readAllBytes(file.toPath());
    }
}
