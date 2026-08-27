package com.vccorp.eap.dto;

/**
 * Đại diện cho một đoạn văn bản thô (draft) sẵn sàng để nhúng vector.
 *
 * @param content        nội dung văn bản của chunk
 * @param headingContext chuỗi tiêu đề phân cấp (ví dụ: "Chương I > Điều 1")
 * @param pageNumber     số trang PDF gốc chứa chunk này (0 nếu không xác định)
 */
public record ChunkDraft(String content, String headingContext, int pageNumber) {}

