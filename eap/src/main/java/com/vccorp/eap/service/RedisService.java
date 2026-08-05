package com.vccorp.eap.service;

/**
 * Interface cung cấp các thao tác cơ bản với Redis Cache.
 * Định nghĩa các phương thức lưu trữ, lấy dữ liệu và thu hồi khóa.
 */
public interface RedisService {
    
    /**
     * Lưu giá trị kiểu chuỗi với key tương ứng (không giới hạn thời gian).
     */
    void set(String key, String value);
    
    /**
     * Lưu giá trị kiểu chuỗi kèm thời gian sống (TTL) tính bằng mili-giây.
     */
    void set(String key, String value, long timeoutMs);
    
    /**
     * Lấy giá trị chuỗi lưu trữ từ Redis dựa vào key.
     */
    String get(String key);
    
    /**
     * Xóa key và dữ liệu liên quan khỏi Redis.
     * @return true nếu xóa thành công, false nếu không tồn tại hoặc lỗi.
     */
    Boolean delete(String key);
    
    /**
     * Kiểm tra sự tồn tại của key trong Redis cache.
     */
    Boolean hasKey(String key);
}
