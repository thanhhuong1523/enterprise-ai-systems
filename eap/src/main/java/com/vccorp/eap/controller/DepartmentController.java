package com.vccorp.eap.controller;

import com.vccorp.eap.common.response.ApiResponse;
import com.vccorp.eap.dto.CreateDepartmentRequest;
import com.vccorp.eap.dto.DepartmentResponse;
import com.vccorp.eap.dto.UpdateDepartmentRequest;
import com.vccorp.eap.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DepartmentResponse> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse department = departmentService.createDepartment(request);
        return ApiResponse.success(department);
    }

    @GetMapping
    public ApiResponse<List<DepartmentResponse>> listDepartments() {
        List<DepartmentResponse> departments = departmentService.listDepartments();
        return ApiResponse.success(departments);
    }

    @GetMapping("/{id}")
    public ApiResponse<DepartmentResponse> getDepartmentDetail(@PathVariable("id") UUID id) {
        DepartmentResponse department = departmentService.getDepartmentDetail(id);
        return ApiResponse.success(department);
    }

    @PutMapping("/{id}")
    public ApiResponse<DepartmentResponse> updateDepartment(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        DepartmentResponse department = departmentService.updateDepartment(id, request);
        return ApiResponse.success(department);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDepartment(@PathVariable("id") UUID id) {
        departmentService.deleteDepartment(id);
        return ApiResponse.success(null);
    }
}
