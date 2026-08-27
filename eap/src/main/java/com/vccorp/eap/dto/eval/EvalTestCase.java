package com.vccorp.eap.dto.eval;

import com.vccorp.eap.enums.Role;

/**
 * Record đại diện cho một câu hỏi test case trong Evaluation Harness.
 *
 * @param id                    mã định danh test case (vd: "TC01")
 * @param question              nội dung câu hỏi
 * @param userRole              vai trò người dùng gửi câu hỏi
 * @param userDepartmentCode    mã phòng ban người dùng
 * @param expectedDepartmentCode mã phòng ban kỳ vọng được truy cập
 * @param expectedDocumentTitle  tên tài liệu kỳ vọng được trích dẫn
 * @param expectedPageNumber     số trang kỳ vọng được trích dẫn (null nếu không bắt buộc)
 * @param groundTruthAnswer      câu trả lời chuẩn để làm mốc đối sánh
 */
public record EvalTestCase(
    String id,
    String question,
    Role userRole,
    String userDepartmentCode,
    String expectedDepartmentCode,
    String expectedDocumentTitle,
    Integer expectedPageNumber,
    String groundTruthAnswer
) {}
