package com.vccorp.eap.dto.document;

import java.util.Map;

/**
 * Kết quả chunk trả về từ API tìm kiếm ngữ nghĩa.
 *
 * @param content       nội dung văn bản của chunk
 * @param score         điểm tương đồng cosine
 * @param documentTitle tên hiển thị của tài liệu
 * @param businessCode  mã nghiệp vụ của tài liệu
 * @param metadata      toàn bộ metadata JSONB (doc_type, topics, entities, page_number, ...)
 * @param pageNumber    số trang PDF gốc chứa chunk này (null nếu không xác định)
 * @param citation      chuỗi trích dẫn định dạng sẵn, ví dụ "[Quy định lương.pdf, Trang 5]"
 */
public record ChunkResultDto(
    String content,
    double score,
    String documentTitle,
    String businessCode,
    Map<String, Object> metadata,
    Integer pageNumber,
    String citation
) {}

