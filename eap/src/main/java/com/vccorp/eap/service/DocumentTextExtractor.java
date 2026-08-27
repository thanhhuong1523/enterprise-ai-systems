package com.vccorp.eap.service;

import com.vccorp.eap.dto.PageContent;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.util.List;

public interface DocumentTextExtractor {
    String extractText(MultipartFile file);
    String extractText(Path filePath);

    /**
     * Trích xuất văn bản theo từng trang từ file PDF.
     * Với các định dạng không phải PDF, trả về danh sách 1 phần tử với pageNumber = 0.
     */
    List<PageContent> extractTextByPage(Path filePath);
}

