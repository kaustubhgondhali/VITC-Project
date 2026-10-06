package com.vitc.service.impl;

import com.vitc.dto.request.AdminLoginRequest;
import com.vitc.dto.request.AdminRequest;
import com.vitc.dto.request.AdminUpdateRequest;
import com.vitc.dto.request.ForgotPasswordRequest;
import com.vitc.dto.request.PasswordChangeRequest;
import com.vitc.dto.request.ResetPasswordRequest;
import com.vitc.dto.request.SessionVerifyRequest;
import com.vitc.dto.response.AdminResponse;
import com.vitc.dto.response.PasswordResetTokenResponse;
import com.vitc.dto.response.DashboardStatsResponse;
import com.vitc.entity.Admin;
import com.vitc.entity.enums.AdminRole;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.DuplicateResourceException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.AdminMapper;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.AssignmentOrderRepository;
import com.vitc.repository.AssignmentRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EmployerEnquiryRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.ExamRepository;
import com.vitc.repository.FaqRepository;
import com.vitc.repository.GalleryItemRepository;
import com.vitc.repository.InternshipApplicationRepository;
import com.vitc.repository.JobApplicationRepository;
import com.vitc.repository.JobRequirementRepository;
import com.vitc.repository.PaymentOrderRepository;
import com.vitc.repository.PaymentRepository;
import com.vitc.repository.ReviewRepository;
import com.vitc.repository.UserRepository;
import com.vitc.security.TokenSecurityUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vitc.service.AdminService;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final AdminRepository repository;
    private final PasswordEncoder passwordEncoder;

    private static final long SESSION_HOURS = 8;
    private static final long RESET_MINUTES = 20;
    private final CourseRepository courseRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final AssignmentOrderRepository assignmentOrderRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final GalleryItemRepository galleryItemRepository;
    private final FaqRepository faqRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExamRepository examRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final InternshipApplicationRepository internshipApplicationRepository;
    private final EmployerEnquiryRepository employerEnquiryRepository;

    @Override
    public List<AdminResponse> getAll() {
        return repository.findAll().stream().map(AdminMapper::toResponse).toList();
    }

    @Override
    public AdminResponse getById(Long id) {
        return AdminMapper.toResponse(find(id));
    }

    @Override
    public AdminResponse getByUsername(String username) {
        return repository.findByUsernameIgnoreCase(username).map(AdminMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with username: " + username));
    }

    @Override
    @Transactional
    public AdminResponse create(AdminRequest request) {
        if (repository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException("Admin already exists with username: " + request.username());
        }
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Admin already exists with email: " + request.email());
        }
        Admin entity = AdminMapper.toEntity(request, passwordEncoder.encode(request.password()));
        return AdminMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public AdminResponse update(Long id, AdminUpdateRequest request) {
        Admin entity = find(id);
        if (!entity.getEmail().equalsIgnoreCase(request.email())
                && repository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Admin already exists with email: " + request.email());
        }
        AdminMapper.apply(entity, request);
        return AdminMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Admin entity = find(id);
        if (entity.getRole() == AdminRole.SUPER_ADMIN
                && repository.findByRole(AdminRole.SUPER_ADMIN).size() <= 1) {
            throw new BadRequestException("The last super admin cannot be deleted");
        }
        repository.delete(entity);
    }

    @Override
    public List<AdminResponse> getActive() {
        return repository.findByActiveTrue().stream().map(AdminMapper::toResponse).toList();
    }

    @Override
    public List<AdminResponse> getByRole(AdminRole role) {
        return repository.findByRole(role).stream().map(AdminMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public AdminResponse updateActive(Long id, boolean active) {
        Admin entity = find(id);
        entity.setActive(active);
        return AdminMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        Admin entity = find(id);
        if (!passwordEncoder.matches(request.currentPassword(), entity.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        entity.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        repository.save(entity);
    }

    @Override
    @Transactional
    public AdminResponse login(AdminLoginRequest request) {
        String identifier = request.username() == null ? "" : request.username().trim();
        Admin entity = repository.findByUsernameIgnoreCase(identifier)
                .or(() -> repository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new BadRequestException("Invalid username or password"));
        log.info("Admin login attempt: identifier={}, foundAdminId={}, active={}", identifier, entity.getId(), entity.getActive());
        if (!Boolean.TRUE.equals(entity.getActive())) {
            throw new BadRequestException("Admin account is disabled");
        }
        if (!passwordEncoder.matches(request.password(), entity.getPasswordHash())) {
            throw new BadRequestException("Invalid username or password");
        }
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        entity.setLastLoginAt(LocalDateTime.now());
        entity.setSessionToken(token);
        entity.setSessionExpiresAt(LocalDateTime.now().plusHours(SESSION_HOURS));
        // A fresh login always invalidates any pending recovery token.
        entity.setResetToken(null);
        entity.setResetTokenExpiresAt(null);
        return AdminMapper.toResponse(repository.save(entity), token);
    }

    /** Server-side session check - the admin panel calls this on every page load. */
    @Override
    @Transactional(readOnly = true)
    public AdminResponse verifySession(SessionVerifyRequest request) {
        String identifier = request.username() == null ? "" : request.username().trim();
        Admin entity = repository.findByUsernameIgnoreCase(identifier)
                .or(() -> repository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new BadRequestException("Session expired, please sign in again"));
        if (!Boolean.TRUE.equals(entity.getActive())
                || entity.getSessionToken() == null
                || !TokenSecurityUtil.matches(entity.getSessionToken(), request.token())
                || entity.getSessionExpiresAt() == null
                || entity.getSessionExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Session expired, please sign in again");
        }
        return AdminMapper.toResponse(entity);
    }

    @Override
    @Transactional
    public void logout(SessionVerifyRequest request) {
        repository.findByUsernameIgnoreCase(request.username()).ifPresent(entity -> {
            if (entity.getSessionToken() != null && TokenSecurityUtil.matches(entity.getSessionToken(), request.token())) {
                entity.setSessionToken(null);
                entity.setSessionExpiresAt(null);
                repository.save(entity);
            }
        });
    }

    @Override
    @Transactional
    public PasswordResetTokenResponse forgotPassword(ForgotPasswordRequest request) {
        String key = request.usernameOrEmail().trim();
        Admin entity = repository.findByUsernameIgnoreCase(key)
                .or(() -> repository.findByEmailIgnoreCase(key))
                .orElseThrow(() -> new BadRequestException("No admin account matches that username or email"));

        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(RESET_MINUTES);
        entity.setResetToken(token);
        entity.setResetTokenExpiresAt(expiry);
        repository.save(entity);

        return new PasswordResetTokenResponse(maskEmail(entity.getEmail()), token, expiry);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.newPassword() == null || request.newPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters.");
        }
        Admin entity = repository.findAll().stream()
                .filter(a -> TokenSecurityUtil.matches(a.getResetToken(), request.token()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("This password reset link is no longer valid. Please request a new one."));
        if (entity.getResetTokenExpiresAt() == null
                || entity.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            entity.setResetToken(null);
            entity.setResetTokenExpiresAt(null);
            repository.save(entity);
            throw new BadRequestException("This password reset link has expired. Please request a new one.");
        }
        entity.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        entity.setResetToken(null);
        entity.setResetTokenExpiresAt(null);
        // Force a re-login everywhere after a password reset.
        entity.setSessionToken(null);
        entity.setSessionExpiresAt(null);
        repository.save(entity);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "your registered email";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String visible = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return visible + "*".repeat(Math.max(1, name.length() - visible.length())) + "@" + parts[1];
    }

    @Override
    public DashboardStatsResponse dashboardStats() {
        BigDecimal revenue = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == com.vitc.entity.enums.PaymentStatus.SUCCESS)
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingReviews = reviewRepository.findAll().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getApproved()))
                .count();

        long totalStudents = userRepository.findAll().stream()
                .filter(u -> u.getRole() == com.vitc.entity.enums.UserRole.STUDENT)
                .count();

        long activeStudents = userRepository.findAll().stream()
                .filter(u -> u.getRole() == com.vitc.entity.enums.UserRole.STUDENT && u.getStatus() == com.vitc.entity.enums.UserStatus.ACTIVE)
                .count();

        long totalTeachers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == com.vitc.entity.enums.UserRole.TEACHER)
                .count();

        // A paid assignment purchase is a checkout order plus its assignment order (same code) -
        // count it once, exactly as Admin -> Orders lists it.
        long totalOrders = paymentOrderRepository.count() + assignmentOrderRepository.countWithoutCheckoutOrder();

        return new DashboardStatsResponse(
                courseRepository.count(),
                assignmentRepository.count(),
                userRepository.count(),
                totalOrders,
                paymentRepository.count(),
                reviewRepository.count(),
                pendingReviews,
                galleryItemRepository.count(),
                faqRepository.count(),
                revenue,
                totalStudents,
                activeStudents,
                totalTeachers,
                enrollmentRepository.count(),
                jobRequirementRepository.count(),
                jobApplicationRepository.count(),
                internshipApplicationRepository.count(),
                employerEnquiryRepository.count(),
                examRepository.count());
    }

    private Admin find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Admin", id));
    }
}
