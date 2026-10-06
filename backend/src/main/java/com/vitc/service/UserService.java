package com.vitc.service;

import com.vitc.common.PageResponse;
import com.vitc.dto.request.PasswordChangeRequest;
import com.vitc.dto.request.UserRequest;
import com.vitc.dto.request.UserUpdateRequest;
import com.vitc.dto.response.UserResponse;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import java.util.List;

public interface UserService {

    List<UserResponse> getAll();

    PageResponse<UserResponse> search(String keyword, int page, int size);

    UserResponse getById(Long id);

    UserResponse getByEmail(String email);

    UserResponse create(UserRequest request);

    UserResponse update(Long id, UserUpdateRequest request);

    void delete(Long id);

    List<UserResponse> getByRole(UserRole role);

    List<UserResponse> getByStatus(UserStatus status);

    UserResponse updateStatus(Long id, UserStatus status);

    void changePassword(Long id, PasswordChangeRequest request);
}
