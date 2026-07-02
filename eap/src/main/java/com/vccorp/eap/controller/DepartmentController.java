package com.vccorp.eap.controller;

import com.vccorp.eap.common.response.ApiResponse;
import com.vccorp.eap.dto.CreateDepartmentRequest;
import com.vccorp.eap.dto.UpdateDepartmentRequest;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Department> createDepartment(@RequestBody CreateDepartmentRequest request) {
        Department department = departmentService.createDepartment(request);
        return ApiResponse.success(department);
    }

    @GetMapping
    public ApiResponse<List<Department>> listDepartments() {
        List<Department> departments = departmentService.listDepartments();
        return ApiResponse.success(departments);
    }

    @GetMapping("/{id}")
    public ApiResponse<Department> getDepartmentDetail(@PathVariable("id") UUID id) {
        Department department = departmentService.getDepartmentDetail(id);
        return ApiResponse.success(department);
    }

    @PutMapping("/{id}")
    public ApiResponse<Department> updateDepartment(
            @PathVariable("id") UUID id,
            @RequestBody UpdateDepartmentRequest request) {
        Department department = departmentService.updateDepartment(id, request);
        return ApiResponse.success(department);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDepartment(@PathVariable("id") UUID id) {
        departmentService.deleteDepartment(id);
        return ApiResponse.success(null);
    }
}
