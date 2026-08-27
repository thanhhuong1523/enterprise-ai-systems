package com.vccorp.eap.service;

import com.vccorp.eap.dto.ChunkDraft;
import com.vccorp.eap.service.impl.ParagraphChunkerImpl;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ParagraphChunkerImplTest {

    @Test
    public void testChunkText_SeparatesParagraphs() {
        ParagraphChunkerImpl chunker = new ParagraphChunkerImpl();

        String input = "Đoạn văn thứ nhất.\n\nĐoạn văn thứ hai.\n\nĐoạn văn thứ ba.";

        List<ChunkDraft> chunks = chunker.chunkText(input);

        assertNotNull(chunks);
        assertEquals(3, chunks.size());
        assertEquals("Đoạn văn thứ nhất.", chunks.get(0).content());
        assertEquals("Đoạn văn thứ hai.", chunks.get(1).content());
        assertEquals("Đoạn văn thứ ba.", chunks.get(2).content());
    }

    @Test
    public void testChunkText_AttachesHeadings() {
        ParagraphChunkerImpl chunker = new ParagraphChunkerImpl();

        String input = "PHẦN 1: TỔNG QUAN VỀ KIẾN TRÚC RAG VÀ VAI TRÒ CỦA CHUNKING\n" +
                "1.1 Khái niệm về Hệ thống RAG (Retrieval-Augmented Generation)\n" +
                "Hệ thống tạo sinh tăng cường truy xuất (RAG) là một kiến trúc tiên tiến.\n\n" +
                "Vai trò của việc chia nhỏ tài liệu:\n" +
                "• Tối ưu hóa dung lượng ngữ cảnh: Giúp hệ thống không vượt quá giới hạn.";

        List<ChunkDraft> chunks = chunker.chunkText(input);

        assertNotNull(chunks);
        assertEquals(2, chunks.size());

        // Tiêu đề không còn nằm trong content nữa, chỉ được giữ ở headingContext
        String expectedChunk0 = "Hệ thống tạo sinh tăng cường truy xuất (RAG) là một kiến trúc tiên tiến.";
        assertEquals(expectedChunk0, chunks.get(0).content());
        assertEquals("PHẦN 1: TỔNG QUAN VỀ KIẾN TRÚC RAG VÀ VAI TRÒ CỦA CHUNKING > 1.1 Khái niệm về Hệ thống RAG (Retrieval-Augmented Generation)", chunks.get(0).headingContext());

        String expectedChunk1 = "• Tối ưu hóa dung lượng ngữ cảnh: Giúp hệ thống không vượt quá giới hạn.";
        assertEquals(expectedChunk1, chunks.get(1).content());
        assertEquals("Vai trò của việc chia nhỏ tài liệu:", chunks.get(1).headingContext());
    }

    @Test
    public void testChunkText_FiltersPageNumbers() {
        ParagraphChunkerImpl chunker = new ParagraphChunkerImpl();

        String input = "Đoạn văn mở đầu tài liệu.\n" +
                "Trang 1 / 10\n\n" +
                "Đoạn văn tiếp theo sau trang thứ nhất.\n" +
                "Page 2\n\n" +
                "Nội dung ở giữa trang.\n" +
                " - 3 - \n\n" +
                "Một đoạn văn khác.\n" +
                "[4]\n\n" +
                "Đoạn cuối cùng.\n" +
                "5";

        List<ChunkDraft> chunks = chunker.chunkText(input);

        assertNotNull(chunks);
        assertEquals(5, chunks.size());

        // Tất cả số trang ở mọi định dạng đều bị lọc bỏ hoàn toàn khỏi content chunk
        assertEquals("Đoạn văn mở đầu tài liệu.", chunks.get(0).content());
        assertEquals("Đoạn văn tiếp theo sau trang thứ nhất.", chunks.get(1).content());
        assertEquals("Nội dung ở giữa trang.", chunks.get(2).content());
        assertEquals("Một đoạn văn khác.", chunks.get(3).content());
        assertEquals("Đoạn cuối cùng.", chunks.get(4).content());
    }

    @Test
    public void testChunkText_HeadingWithSingleDigitDot() {
        ParagraphChunkerImpl chunker = new ParagraphChunkerImpl();

        String input = "1. Giới thiệu chung\n" +
                "Đây là nội dung của phần giới thiệu chung.";

        List<ChunkDraft> chunks = chunker.chunkText(input);

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        // '1. Giới thiệu chung' should be heading context, not in content
        assertEquals("Đây là nội dung của phần giới thiệu chung.", chunks.get(0).content());
        assertEquals("1. Giới thiệu chung", chunks.get(0).headingContext());
    }

    @Test
    public void testChunkText_HeadingWithSingleDigitDotLongContent() {
        ParagraphChunkerImpl chunker = new ParagraphChunkerImpl();

        String input = "1. Giới thiệu chung\n" +
                "Đây là nội dung của phần giới thiệu chung. Đây là nội dung của phần giới thiệu chung. Đây là nội dung của phần giới thiệu chung. Đây là nội dung của phần giới thiệu chung. Đây là nội dung của phần giới thiệu chung.";

        List<ChunkDraft> chunks = chunker.chunkText(input);

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals("Đây là nội dung của phần giới thiệu chung. Đây là nội dung của phần giới thiệu chung. Đây là nội dung của phần giới thiệu chung. Đây là nội dung của phần giới thiệu chung. Đây là nội dung của phần giới thiệu chung.", chunks.get(0).content());
        assertEquals("1. Giới thiệu chung", chunks.get(0).headingContext());
    }

    @Test
    public void testChunkText_IgnoresLinesBeforeFirstHeading() {
        ParagraphChunkerImpl chunker = new ParagraphChunkerImpl();

        String input = "Tên tài liệu: Hướng dẫn sử dụng\n" +
                "Ngày tạo: 2026-08-21\n" +
                "Người tạo: Nguyễn Văn A\n\n" +
                "1. Giới thiệu chung\n" +
                "Đây là nội dung của phần giới thiệu chung.";

        List<ChunkDraft> chunks = chunker.chunkText(input);

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals("Đây là nội dung của phần giới thiệu chung.", chunks.get(0).content());
        assertEquals("1. Giới thiệu chung", chunks.get(0).headingContext());
    }

    @Test
    public void testChunkText_IgnoresIntroductoryParagraphsBeforeNumberedHeadingWithTitle() {
        ParagraphChunkerImpl chunker = new ParagraphChunkerImpl();

        String input = "BÁO CÁO KẾT QUẢ NGHIÊN CỨU\n" +
                "Tài liệu này trình bày các nội dung chính.\n\n" +
                "1. Đặt vấn đề\n" +
                "Nội dung đặt vấn đề ở đây.";

        List<ChunkDraft> chunks = chunker.chunkText(input);

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals("Nội dung đặt vấn đề ở đây.", chunks.get(0).content());
        assertEquals("1. Đặt vấn đề", chunks.get(0).headingContext());
    }

    @Test
    public void testChunkText_WithoutNumberedHeadingsPreservesBehavior() {
        ParagraphChunkerImpl chunker = new ParagraphChunkerImpl();

        String input = "HƯỚNG DẪN SỬ DỤNG\n" +
                "Nội dung hướng dẫn sử dụng.";

        List<ChunkDraft> chunks = chunker.chunkText(input);

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals("Nội dung hướng dẫn sử dụng.", chunks.get(0).content());
        assertEquals("HƯỚNG DẪN SỬ DỤNG", chunks.get(0).headingContext());
    }

    @Test
    public void testChunkByPage_PreservesPageNumbers() {
        ParagraphChunkerImpl chunker = new ParagraphChunkerImpl();

        com.vccorp.eap.dto.PageContent page1 = new com.vccorp.eap.dto.PageContent(1, "1. Tổng quan trang 1\nNội dung trang một.");
        com.vccorp.eap.dto.PageContent page2 = new com.vccorp.eap.dto.PageContent(2, "2. Chi tiết trang 2\nNội dung trang hai.");

        List<ChunkDraft> chunks = chunker.chunkByPage(List.of(page1, page2));

        assertNotNull(chunks);
        assertEquals(2, chunks.size());
        assertEquals("Nội dung trang một.", chunks.get(0).content());
        assertEquals(1, chunks.get(0).pageNumber());

        assertEquals("Nội dung trang hai.", chunks.get(1).content());
        assertEquals(2, chunks.get(1).pageNumber());
    }
}

