package com.vitc.service.impl;

import com.vitc.common.PageResponse;
import com.vitc.dto.request.ReviewRequest;
import com.vitc.dto.response.ReviewResponse;
import com.vitc.entity.Review;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.ReviewMapper;
import com.vitc.repository.ReviewRepository;
import com.vitc.service.ReviewService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository repository;

    @Override
    public List<ReviewResponse> getAll() {
        return repository.findAll().stream().map(ReviewMapper::toResponse).toList();
    }

    @Override
    public PageResponse<ReviewResponse> getApprovedPaged(int page, int size) {
        return PageResponse.from(repository
                .findByApprovedTrue(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(ReviewMapper::toResponse));
    }

    @Override
    public ReviewResponse getById(Long id) {
        return ReviewMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public ReviewResponse create(ReviewRequest request) {
        return ReviewMapper.toResponse(repository.save(ReviewMapper.toEntity(request)));
    }

    @Override
    @Transactional
    public ReviewResponse update(Long id, ReviewRequest request) {
        Review entity = find(id);
        ReviewMapper.apply(entity, request);
        return ReviewMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<ReviewResponse> getApproved() {
        return repository.findByApprovedTrueOrderByCreatedAtDesc().stream().map(ReviewMapper::toResponse).toList();
    }

    @Override
    public List<ReviewResponse> getFeatured() {
        return repository.findByFeaturedTrueAndApprovedTrue().stream().map(ReviewMapper::toResponse).toList();
    }

    @Override
    public List<ReviewResponse> getByCourseCode(String courseCode) {
        return repository.findByCourseCodeIgnoreCase(courseCode).stream().map(ReviewMapper::toResponse).toList();
    }

    @Override
    public List<ReviewResponse> getByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).stream().map(ReviewMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ReviewResponse updateApproval(Long id, boolean approved) {
        Review entity = find(id);
        entity.setApproved(approved);
        return ReviewMapper.toResponse(repository.save(entity));
    }

    @Override
    public Double averageRating(String courseCode) {
        return repository.averageRatingForCourse(courseCode);
    }

    private Review find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review", id));
    }
}
