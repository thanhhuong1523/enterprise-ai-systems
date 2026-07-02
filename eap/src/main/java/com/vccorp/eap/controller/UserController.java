package com.vccorp.eap.controller;

import com.vccorp.eap.common.response.ApiResponse;
import com.vccorp.eap.dto.CreateUserRequest;
import com.vccorp.eap.dto.UpdateUserRequest;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<User> createUser(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return ApiResponse.success(user);
    }

    @GetMapping
    public ApiResponse<List<User>> listUsers() {
        List<User> users = userService.listUsers();
        return ApiResponse.success(users);
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getUserDetail(@PathVariable("id") UUID id) {
        User user = userService.getUserDetail(id);
        return ApiResponse.success(user);
    }

    @PutMapping("/{id}")
    public ApiResponse<User> updateUser(
            @PathVariable("id") UUID id,
            @RequestBody UpdateUserRequest request) {
        User user = userService.updateUser(id, request);
        return ApiResponse.success(user);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable("id") UUID id) {
        userService.deleteUser(id);
        return ApiResponse.success(null);
    }
}
