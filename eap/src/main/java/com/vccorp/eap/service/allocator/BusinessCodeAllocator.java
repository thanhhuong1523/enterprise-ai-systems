package com.vccorp.eap.service.allocator;

/**
 * Interface chịu trách nhiệm cấp phát mã số nghiệp vụ (business code) duy nhất cho tài liệu.
 */
public interface BusinessCodeAllocator {
    
    /**
     * Sinh mã số nghiệp vụ duy nhất dựa trên sequence của database.
     * Mã số thường có tiền tố ORIG_ kèm số tự tăng được pad chữ số 0.
     */
    String allocate();
}
