package com.vccorp.eap.service.department;

import com.vccorp.eap.dto.department.CreateDepartmentRequest;
import com.vccorp.eap.dto.department.DepartmentResponse;
import com.vccorp.eap.dto.department.UpdateDepartmentRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentService {
    DepartmentResponse createDepartment(CreateDepartmentRequest request);
    List<DepartmentResponse> listDepartments();
    DepartmentResponse getDepartmentDetail(UUID id);
    DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request);
    void deleteDepartment(UUID id);
    Optional<DepartmentResponse> getDepartmentByName(String name);
}
