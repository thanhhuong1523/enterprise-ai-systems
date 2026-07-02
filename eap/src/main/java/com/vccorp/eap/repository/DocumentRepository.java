package com.vccorp.eap.repository;

import com.vccorp.eap.model.Document;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.deletedAt IS NULL")
    Optional<Document> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(UUID parentId, UUID ownerDepartmentId);

    boolean existsByOwnerDepartmentIdAndDeletedAtIsNull(UUID ownerDepartmentId);

    @Modifying
    @Query("UPDATE Document d SET d.deletedAt = :deletedAt WHERE d.parentId = :parentId AND d.deletedAt IS NULL")
    void softDeleteAliasesByOriginalId(@Param("parentId") UUID parentId, @Param("deletedAt") LocalDateTime deletedAt);

    List<Document> findAllByParentIdAndDeletedAtIsNull(UUID parentId);

    Page<Document> findByParentIdIsNull(Pageable pageable);

    Page<Document> findByParentIdIsNullAndOwnerDepartmentId(UUID ownerDepartmentId, Pageable pageable);

    Page<Document> findByParentIdIsNotNullAndOwnerDepartmentId(UUID ownerDepartmentId, Pageable pageable);
}
