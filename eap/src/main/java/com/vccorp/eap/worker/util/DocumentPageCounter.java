package com.vccorp.eap.worker.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiện ích đếm số trang, slide, hoặc sheet động của tài liệu dựa trên định dạng tệp (.pdf, .docx, .xlsx, .pptx).
 */
public class DocumentPageCounter {

    private static final Logger log = LoggerFactory.getLogger(DocumentPageCounter.class);

    private static final int DEFAULT_CHUNKS = 5;
    private static final Tika tika = new Tika();

    /**
     * Đếm số lượng trang/sheet/slide của tệp tài liệu.
     *
     * @param filePath Đường dẫn tệp vật lý
     * @return Số lượng trang/sheet/slide tương ứng, hoặc mặc định là 5 nếu xảy ra lỗi/không hỗ trợ.
     */
    public static int countPages(String filePath) {
        if (filePath == null) {
            log.warn("Đường dẫn tệp rỗng, sử dụng giá trị mặc định {}", DEFAULT_CHUNKS);
            return DEFAULT_CHUNKS;
        }

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            log.warn("Tệp vật lý không tồn tại tại {}. Sử dụng giá trị mặc định {}", filePath, DEFAULT_CHUNKS);
            return DEFAULT_CHUNKS;
        }

        try {
            // Xác định MIME type bằng Apache Tika (do tệp bị đổi tên thành mã hash và mất extension trong storage)
            String mimeType = tika.detect(path.toFile());
            log.info("Detected MIME type: {} for file: {}", mimeType, path.getFileName());

            if ("application/pdf".equals(mimeType)) {
                return countPdfPages(path);
            } else if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mimeType)) {
                return countDocxPages(path);
            } else if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(mimeType)) {
                return countXlsxSheets(path);
            } else if ("application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(mimeType)) {
                return countPptxSlides(path);
            } else {
                // Fallback check theo extension trong trường hợp tệp tin vẫn giữ đuôi mở rộng
                String fileName = path.getFileName().toString().toLowerCase();
                if (fileName.endsWith(".pdf")) {
                    return countPdfPages(path);
                } else if (fileName.endsWith(".docx")) {
                    return countDocxPages(path);
                } else if (fileName.endsWith(".xlsx")) {
                    return countXlsxSheets(path);
                } else if (fileName.endsWith(".pptx")) {
                    return countPptxSlides(path);
                } else {
                    log.info("Định dạng tệp {} không được hỗ trợ đếm trang động. Sử dụng mặc định {}", fileName, DEFAULT_CHUNKS);
                }
            }
        } catch (Throwable t) {
            log.warn("Lỗi khi đếm số trang của tệp {}. Sử dụng mặc định {}. Chi tiết lỗi: {}", 
                    path.getFileName(), DEFAULT_CHUNKS, t.getMessage(), t);
        }

        return DEFAULT_CHUNKS;
    }

    private static int countPdfPages(Path path) throws Exception {
        try (PDDocument document = PDDocument.load(path.toFile())) {
            int pages = document.getNumberOfPages();
            log.info("PDF file {} has {} pages", path.getFileName(), pages);
            return pages > 0 ? pages : DEFAULT_CHUNKS;
        }
    }

    private static int countDocxPages(Path path) throws Exception {
        try (InputStream is = Files.newInputStream(path);
             XWPFDocument docx = new XWPFDocument(is)) {
            int pages = docx.getProperties().getExtendedProperties().getUnderlyingProperties().getPages();
            log.info("DOCX file {} has {} pages", path.getFileName(), pages);
            return pages > 0 ? pages : 1;
        }
    }

    private static int countXlsxSheets(Path path) throws Exception {
        try (InputStream is = Files.newInputStream(path);
             Workbook workbook = new XSSFWorkbook(is)) {
            int sheets = workbook.getNumberOfSheets();
            log.info("XLSX file {} has {} sheets", path.getFileName(), sheets);
            return sheets > 0 ? sheets : 1;
        }
    }

    private static int countPptxSlides(Path path) throws Exception {
        try (InputStream is = Files.newInputStream(path);
             XMLSlideShow pptx = new XMLSlideShow(is)) {
            int slides = pptx.getSlides().size();
            log.info("PPTX file {} has {} slides", path.getFileName(), slides);
            return slides > 0 ? slides : 1;
        }
    }
}
