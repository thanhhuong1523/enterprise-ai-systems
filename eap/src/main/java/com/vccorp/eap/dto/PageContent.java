package com.vccorp.eap.dto;

/**
 * Đại diện cho nội dung văn bản của một trang PDF đã được trích xuất.
 *
 * @param pageNumber số trang trong PDF gốc (bắt đầu từ 1)
 * @param text       nội dung văn bản thô của trang đó
 */
public record PageContent(int pageNumber, String text) {}
