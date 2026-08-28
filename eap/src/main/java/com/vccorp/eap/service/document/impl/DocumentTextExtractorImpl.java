package com.vccorp.eap.service.document.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.document.PageContent;
import com.vccorp.eap.service.document.DocumentTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.extractor.XSLFExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


@Service
public class DocumentTextExtractorImpl implements DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractorImpl.class);
    private static final Tika TIKA = new Tika();

    @Override
    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST, "Tệp tin không được rỗng.");
        }
        try {
            byte[] bytes = file.getBytes();
            String mimeType = TIKA.detect(bytes);
            return doExtract(new ByteArrayInputStream(bytes), mimeType, file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Lỗi đọc dữ liệu tệp tin: {}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.ERR_STORAGE_ERROR, "Không thể đọc nội dung tệp tin.", e);
        }
    }

    @Override
    public String extractText(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND, "Không tìm thấy tệp vật lý: " + filePath);
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String mimeType = TIKA.detect(bytes);
            return doExtract(new ByteArrayInputStream(bytes), mimeType, filePath.getFileName().toString());
        } catch (IOException e) {
            log.error("Lỗi đọc tệp vật lý: {}", filePath, e);
            throw new BusinessException(ErrorCode.ERR_STORAGE_ERROR, "Không thể đọc nội dung tệp tin.", e);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    @Override
    public List<PageContent> extractTextByPage(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND, "Không tìm thấy tệp vật lý: " + filePath);
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String mimeType = TIKA.detect(bytes);
            if ("application/pdf".equals(mimeType)) {
                return extractPdfByPage(new ByteArrayInputStream(bytes), filePath.getFileName().toString());
            }
            // Với các định dạng khác, trả về toàn bộ text trong 1 PageContent với pageNumber = 0
            String fullText = doExtract(new ByteArrayInputStream(bytes), mimeType, filePath.getFileName().toString());
            return List.of(new PageContent(0, fullText != null ? fullText : ""));
        } catch (IOException e) {
            log.error("Lỗi đọc tệp vật lý: {}", filePath, e);
            throw new BusinessException(ErrorCode.ERR_STORAGE_ERROR, "Không thể đọc nội dung tệp tin.", e);
        }
    }

    private String doExtract(InputStream inputStream, String mimeType, String fileName) throws IOException {
        log.debug("Trích xuất văn bản từ '{}' (MIME: {})", fileName, mimeType);
        if (mimeType == null) {
            return readAsUtf8(inputStream);
        }
        return switch (mimeType) {
            case "application/pdf"
                    -> extractPdf(inputStream, fileName);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    -> extractDocx(inputStream, fileName);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    -> extractXlsx(inputStream, fileName);
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    -> extractPptx(inputStream, fileName);
            default -> readAsUtf8(inputStream);
        };
    }

    private String extractPdf(InputStream inputStream, String fileName) throws IOException {
        try (PDDocument doc = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setParagraphEnd("\n\n");
            String text = stripper.getText(doc);
            log.debug("PDF '{}': trích xuất {} ký tự", fileName, text.length());
            return text;
        } catch (IOException e) {
            log.error("Lỗi trích xuất PDF '{}': {}", fileName, e.getMessage());
            throw e;
        }
    }

    private String extractDocx(InputStream inputStream, String fileName) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            if (text != null) {
                text = text.replace("\r\n", "\n").replace("\n", "\n\n");
            }
            log.debug("DOCX '{}': trích xuất {} ký tự", fileName, text != null ? text.length() : 0);
            return text;
        } catch (IOException e) {
            log.error("Lỗi trích xuất DOCX '{}': {}", fileName, e.getMessage());
            throw e;
        }
    }

    private String extractXlsx(InputStream inputStream, String fileName) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
             XSSFExcelExtractor extractor = new XSSFExcelExtractor(workbook)) {
            extractor.setIncludeSheetNames(true);
            String text = extractor.getText();
            if (text != null) {
                text = text.replace("\r\n", "\n").replace("\n", "\n\n");
            }
            log.debug("XLSX '{}': trích xuất {} ký tự", fileName, text != null ? text.length() : 0);
            return text;
        } catch (IOException e) {
            log.error("Lỗi trích xuất XLSX '{}': {}", fileName, e.getMessage());
            throw e;
        }
    }

    private String extractPptx(InputStream inputStream, String fileName) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(inputStream);
             XSLFExtractor extractor = new XSLFExtractor(ppt)) {
            String text = extractor.getText();
            if (text != null) {
                text = text.replace("\r\n", "\n").replace("\n", "\n\n");
            }
            log.debug("PPTX '{}': trích xuất {} ký tự", fileName, text != null ? text.length() : 0);
            return text;
        } catch (IOException e) {
            log.error("Lỗi trích xuất PPTX '{}': {}", fileName, e.getMessage());
            throw e;
        }
    }


    private String readAsUtf8(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Trích xuất văn bản từng trang của PDF, trả về danh sách {@link PageContent}.
     * Các trang không có nội dung văn bản sẽ bị bỏ qua.
     */
    private List<PageContent> extractPdfByPage(InputStream inputStream, String fileName) throws IOException {
        List<PageContent> pages = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(inputStream)) {
            int totalPages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(doc);
                if (pageText != null && !pageText.isBlank()) {
                    pages.add(new PageContent(i, pageText));
                }
            }
            log.debug("PDF '{}': trích xuất {} trang có nội dung / {} trang tổng", fileName, pages.size(), totalPages);
        } catch (IOException e) {
            log.error("Lỗi trích xuất PDF theo trang '{}': {}", fileName, e.getMessage());
            throw e;
        }
        return pages;
    }
}
