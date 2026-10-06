package com.vitc.service.impl;

import com.vitc.common.PageResponse;
import com.vitc.dto.request.PasswordChangeRequest;
import com.vitc.dto.request.UserRequest;
import com.vitc.dto.request.UserUpdateRequest;
import com.vitc.dto.response.UserResponse;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.DuplicateResourceException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.UserMapper;
import com.vitc.repository.UserRepository;
import com.vitc.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserResponse> getAll() {
        return repository.findAll().stream().map(UserMapper::toResponse).toList();
    }

    @Override
    public PageResponse<UserResponse> search(String keyword, int page, int size) {
        String term = keyword == null ? "" : keyword;
        return PageResponse.from(repository
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(UserMapper::toResponse));
    }

    @Override
    public UserResponse getById(Long id) {
        return UserMapper.toResponse(find(id));
    }

    @Override
    public UserResponse getByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).map(UserMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    @Transactional
    public UserResponse create(UserRequest request) {
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("User already exists with email: " + request.email());
        }
        User entity = UserMapper.toEntity(request, passwordEncoder.encode(request.password()));
        return UserMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User entity = find(id);
        if (!entity.getEmail().equalsIgnoreCase(request.email())
                && repository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("User already exists with email: " + request.email());
        }
        UserMapper.apply(entity, request);
        return UserMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<UserResponse> getByRole(UserRole role) {
        return repository.findByRole(role).stream().map(UserMapper::toResponse).toList();
    }

    @Override
    public List<UserResponse> getByStatus(UserStatus status) {
        return repository.findByStatus(status).stream().map(UserMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long id, UserStatus status) {
        User entity = find(id);
        entity.setStatus(status);
        return UserMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        User entity = find(id);
        if (!passwordEncoder.matches(request.currentPassword(), entity.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        entity.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        repository.save(entity);
    }

    private User find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
