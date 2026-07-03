package com.vccorp.eap.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface StorageService {
    String storeFile(MultipartFile file, String filename) throws IOException;
    byte[] loadFile(String fileReference) throws IOException;
}
