package com.vccorp.eap.service;

import com.vccorp.eap.dto.CreateUserRequest;
import com.vccorp.eap.dto.UpdateUserRequest;
import com.vccorp.eap.dto.UserResponse;
import java.util.List;
import java.util.UUID;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
    List<UserResponse> listUsers();
    UserResponse getUserDetail(UUID id);
    UserResponse updateUser(UUID id, UpdateUserRequest request);
    void deleteUser(UUID id);
}
