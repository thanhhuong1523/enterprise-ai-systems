package com.vccorp.eap.worker.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DocumentPageCounterTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("counter_test_");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted((p1, p2) -> p2.compareTo(p1))
                      .forEach(p -> {
                          try {
                              Files.delete(p);
                          } catch (IOException ignored) {}
                      });
            }
        }
    }

    @Test
    public void testCountPages_PdfRealFile() {
        Path realPdf = Path.of("vcc_intern.pdf");
        if (Files.exists(realPdf)) {
            int count = DocumentPageCounter.countPages(realPdf.toAbsolutePath().toString());
            assertEquals(7, count);
        }
    }

    @Test
    public void testCountPages_NonExistentFile() {
        int count = DocumentPageCounter.countPages("non_existent_file.pdf");
        assertEquals(5, count);
    }

    @Test
    public void testCountPages_UnsupportedFormat() throws IOException {
        Path txtFile = Files.createTempFile(tempDir, "test", ".txt");
        Files.writeString(txtFile, "Some mock txt content");
        int count = DocumentPageCounter.countPages(txtFile.toAbsolutePath().toString());
        assertEquals(5, count);
    }

    @Test
    public void testCountPages_MockDocx() throws IOException {
        Path docxFile = tempDir.resolve("test.docx");
        try (XWPFDocument docx = new XWPFDocument()) {
            // Set page count property
            docx.getProperties().getExtendedProperties().getUnderlyingProperties().setPages(12);
            try (FileOutputStream fos = new FileOutputStream(docxFile.toFile())) {
                docx.write(fos);
            }
        }
        
        int count = DocumentPageCounter.countPages(docxFile.toAbsolutePath().toString());
        assertEquals(12, count);
    }

    @Test
    public void testCountPages_MockXlsx() throws IOException {
        Path xlsxFile = tempDir.resolve("test.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sheet1");
            workbook.createSheet("Sheet2");
            workbook.createSheet("Sheet3");
            workbook.createSheet("Sheet4");
            try (FileOutputStream fos = new FileOutputStream(xlsxFile.toFile())) {
                workbook.write(fos);
            }
        }
        
        int count = DocumentPageCounter.countPages(xlsxFile.toAbsolutePath().toString());
        assertEquals(4, count);
    }

    @Test
    public void testCountPages_MockPptx() throws IOException {
        Path pptxFile = tempDir.resolve("test.pptx");
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            pptx.createSlide();
            pptx.createSlide();
            pptx.createSlide();
            try (FileOutputStream fos = new FileOutputStream(pptxFile.toFile())) {
                pptx.write(fos);
            }
        }
        
        int count = DocumentPageCounter.countPages(pptxFile.toAbsolutePath().toString());
        assertEquals(3, count);
    }
}
