package com.vccorp.eap.service;

import com.vccorp.eap.dto.CreateDepartmentRequest;
import com.vccorp.eap.dto.DepartmentResponse;
import com.vccorp.eap.dto.UpdateDepartmentRequest;
import java.util.List;
import java.util.UUID;

public interface DepartmentService {
    DepartmentResponse createDepartment(CreateDepartmentRequest request);
    List<DepartmentResponse> listDepartments();
    DepartmentResponse getDepartmentDetail(UUID id);
    DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request);
    void deleteDepartment(UUID id);
}
