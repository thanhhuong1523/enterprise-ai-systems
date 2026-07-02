package com.vccorp.eap.repository;

import com.vccorp.eap.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByCode(String code);
    boolean existsByName(String name);
}
