package com.vccorp.eap.repository;

import com.vccorp.eap.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsernameOrEmail(String username, String email);
    boolean existsByDepartmentId(UUID departmentId);

    List<User> findByDepartmentIdAndDeletedAtIsNull(UUID departmentId);
    Optional<User> findByFullNameIgnoreCaseAndDeletedAtIsNull(String fullName);
    Optional<User> findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);
    List<User> findByFullNameContainingIgnoreCaseOrUsernameContainingIgnoreCase(String fullName, String username);
}
