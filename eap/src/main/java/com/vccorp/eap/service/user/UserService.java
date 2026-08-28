package com.vccorp.eap.service.user;

import com.vccorp.eap.dto.user.CreateUserRequest;
import com.vccorp.eap.dto.user.UpdateUserRequest;
import com.vccorp.eap.dto.user.UserResponse;
import java.util.List;
import java.util.UUID;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
    List<UserResponse> listUsers();
    UserResponse getUserDetail(UUID id);
    UserResponse updateUser(UUID id, UpdateUserRequest request);
    void deleteUser(UUID id);
}
