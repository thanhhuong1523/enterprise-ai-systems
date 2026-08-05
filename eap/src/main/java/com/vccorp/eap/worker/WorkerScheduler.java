package com.vccorp.eap.worker;

import org.springframework.context.SmartLifecycle;

/**
 * Daemon điều phối và quét tìm các tác vụ READY từ database để đẩy vào hàng đợi thực thi ThreadPool.
 */
public interface WorkerScheduler extends SmartLifecycle {
    
    /**
     * Thực hiện một chu kỳ quét tác vụ trong DB.
     * Xác định số lượng slot trống trong ThreadPool và claim các tác vụ READY tương ứng để thực thi.
     */
    void pollTasks();
}
