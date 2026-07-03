package com.vccorp.eap.controller;

import com.vccorp.eap.common.response.ApiResponse;
import com.vccorp.eap.dto.CreateUserRequest;
import com.vccorp.eap.dto.UpdateUserRequest;
import com.vccorp.eap.dto.UserResponse;
import com.vccorp.eap.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ApiResponse.success(user);
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> listUsers() {
        List<UserResponse> users = userService.listUsers();
        return ApiResponse.success(users);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUserDetail(@PathVariable("id") UUID id) {
        UserResponse user = userService.getUserDetail(id);
        return ApiResponse.success(user);
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse user = userService.updateUser(id, request);
        return ApiResponse.success(user);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable("id") UUID id) {
        userService.deleteUser(id);
        return ApiResponse.success(null);
    }
}
