package com.vccorp.eap.service.helper;

import java.util.UUID;

/**
 * DTO chứa kết quả của truy vấn gộp Aggregate (Query 2) dùng cho
 * Fast-Check (ngoài giao dịch) và Double-Check (trong giao dịch).
 * Xem chi tiết tại DetailedDesign §1.3 và §5 Query 2.
 */
public record DeduplicationQueryResult(
    boolean hasActiveInDept,
    UUID activeDocId,
    String oldestFileRef
) {
    // Java Bean style getters for compatibility
    public boolean isHasActiveInDept() {
        return hasActiveInDept;
    }

    public UUID getActiveDocId() {
        return activeDocId;
    }

    public String getOldestFileRef() {
        return oldestFileRef;
    }

    public static Builder builder(boolean hasActiveInDept) {
        return new Builder(hasActiveInDept);
    }

    public static class Builder {
        private final boolean hasActiveInDept;
        private UUID activeDocId;
        private String oldestFileRef;

        public Builder(boolean hasActiveInDept) {
            this.hasActiveInDept = hasActiveInDept;
        }

        public Builder activeDocId(UUID activeDocId) {
            this.activeDocId = activeDocId;
            return this;
        }

        public Builder oldestFileRef(String oldestFileRef) {
            this.oldestFileRef = oldestFileRef;
            return this;
        }

        public DeduplicationQueryResult build() {
            return new DeduplicationQueryResult(hasActiveInDept, activeDocId, oldestFileRef);
        }
    }
}
