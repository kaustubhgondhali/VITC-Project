package com.vitc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Admin;
import com.vitc.entity.Course;
import com.vitc.entity.Payment;
import com.vitc.entity.Review;
import com.vitc.entity.User;
import com.vitc.entity.enums.PaymentMethod;
import com.vitc.entity.enums.PaymentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminDashboardStatsAccuracyTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AdminRepository adminRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired AssignmentOrderRepository assignmentOrderRepository;
    @Autowired PaymentOrderRepository paymentOrderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired GalleryItemRepository galleryItemRepository;
    @Autowired FaqRepository faqRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired ExamRepository examRepository;
    @Autowired JobRequirementRepository jobRequirementRepository;
    @Autowired JobApplicationRepository jobApplicationRepository;
    @Autowired InternshipApplicationRepository internshipApplicationRepository;
    @Autowired EmployerEnquiryRepository employerEnquiryRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String ADMIN_USER = "dashstatadmin";
    private static final String ADMIN_TOKEN = "dashstat-token-12345";

    @BeforeEach
    void seedAdmin() {
        Admin a = adminRepository.findByUsernameIgnoreCase(ADMIN_USER).orElseGet(() -> adminRepository.save(Admin.builder()
                .username(ADMIN_USER)
                .email("dashadmin@test.io")
                .fullName("Dashboard Stat Admin")
                .passwordHash("hashed")
                .build()));
        a.setActive(true);
        a.setSessionToken(ADMIN_TOKEN);
        a.setSessionExpiresAt(LocalDateTime.now().plusHours(2));
        adminRepository.save(a);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.header("X-Admin-Username", ADMIN_USER).header("X-Admin-Token", ADMIN_TOKEN);
    }

    @Test
    void dashboardStatsReflectsActualDatabaseCounts() throws Exception {
        String res = mvc.perform(asAdmin(get("/api/v1/admins/dashboard/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        var json = mapper.readTree(res).path("data");

        long expectedCourses = courseRepository.count();
        long expectedAssignments = assignmentRepository.count();
        long expectedStudents = userRepository.findAll().stream().filter(u -> u.getRole() == UserRole.STUDENT).count();
        long expectedActiveStudents = userRepository.findAll().stream().filter(u -> u.getRole() == UserRole.STUDENT && u.getStatus() == UserStatus.ACTIVE).count();
        long expectedTeachers = userRepository.findAll().stream().filter(u -> u.getRole() == UserRole.TEACHER).count();
        long expectedUsers = userRepository.count();
        // A paid assignment purchase shares its code between both tables and is one order.
        java.util.Set<String> checkoutCodes = paymentOrderRepository.findAll().stream()
                .map(o -> o.getOrderCode()).collect(java.util.stream.Collectors.toSet());
        long expectedOrders = paymentOrderRepository.count() + assignmentOrderRepository.findAll().stream()
                .filter(o -> !checkoutCodes.contains(o.getOrderCode())).count();
        long expectedPayments = paymentRepository.count();
        long expectedReviews = reviewRepository.count();
        long expectedPendingReviews = reviewRepository.findAll().stream().filter(r -> !Boolean.TRUE.equals(r.getApproved())).count();
        long expectedGalleryItems = galleryItemRepository.count();
        long expectedFaqs = faqRepository.count();
        long expectedEnrollments = enrollmentRepository.count();
        long expectedJobs = jobRequirementRepository.count();
        long expectedJobApplications = jobApplicationRepository.count();
        long expectedInternships = internshipApplicationRepository.count();
        long expectedEmployerEnquiries = employerEnquiryRepository.count();
        long expectedExams = examRepository.count();

        assertEquals(expectedCourses, json.path("totalCourses").asLong());
        assertEquals(expectedAssignments, json.path("totalAssignments").asLong());
        assertEquals(expectedStudents, json.path("totalStudents").asLong());
        assertEquals(expectedActiveStudents, json.path("activeStudents").asLong());
        assertEquals(expectedTeachers, json.path("totalTeachers").asLong());
        assertEquals(expectedUsers, json.path("totalUsers").asLong());
        assertEquals(expectedOrders, json.path("totalOrders").asLong());
        assertEquals(expectedPayments, json.path("totalPayments").asLong());
        assertEquals(expectedReviews, json.path("totalReviews").asLong());
        assertEquals(expectedPendingReviews, json.path("pendingReviews").asLong());
        assertEquals(expectedGalleryItems, json.path("totalGalleryItems").asLong());
        assertEquals(expectedFaqs, json.path("totalFaqs").asLong());
        assertEquals(expectedEnrollments, json.path("totalEnrollments").asLong());
        assertEquals(expectedJobs, json.path("totalJobs").asLong());
        assertEquals(expectedJobApplications, json.path("totalJobApplications").asLong());
        assertEquals(expectedInternships, json.path("totalInternships").asLong());
        assertEquals(expectedEmployerEnquiries, json.path("totalEmployerEnquiries").asLong());
        assertEquals(expectedExams, json.path("totalExams").asLong());
    }

    @Test
    void dashboardStatsUpdatesDynamicallyWhenRecordsAddedAndDeleted() throws Exception {
        // 1. Snapshot initial counts
        long initStudents = userRepository.findAll().stream().filter(u -> u.getRole() == UserRole.STUDENT).count();
        long initTeachers = userRepository.findAll().stream().filter(u -> u.getRole() == UserRole.TEACHER).count();
        long initCourses = courseRepository.count();
        long initReviews = reviewRepository.count();
        long initPayments = paymentRepository.count();

        // 2. Add records
        User student = userRepository.save(User.builder()
                .studentLoginId("VITCSTU99999")
                .username("teststu99999")
                .fullName("Test Dynamic Student")
                .email("dynstudent@vitc.in")
                .passwordHash(passwordEncoder.encode("Pass@123"))
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build());

        User teacher = userRepository.save(User.builder()
                .username("testteacher99999")
                .fullName("Test Dynamic Teacher")
                .email("dynteacher@vitc.in")
                .passwordHash(passwordEncoder.encode("Pass@123"))
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());

        Course course = courseRepository.save(Course.builder()
                .title("Dynamic Test Course")
                .code("DYN-101")
                .price(BigDecimal.valueOf(1499))
                .category("Computer Science")
                .build());

        Review review = reviewRepository.save(Review.builder()
                .courseCode("DYN-101")
                .courseTitle("Dynamic Test Course")
                .reviewerName("Test Reviewer")
                .email("reviewer@test.com")
                .rating(5)
                .comment("Outstanding course!")
                .approved(false)
                .build());

        Payment payment = paymentRepository.save(Payment.builder()
                .transactionId("TXN-DYN-001")
                .payerName("Test Payer")
                .email("payer@test.com")
                .method(PaymentMethod.UPI)
                .amount(BigDecimal.valueOf(1499))
                .currency("INR")
                .status(PaymentStatus.SUCCESS)
                .build());

        // 3. Verify counts incremented by 1
        String res = mvc.perform(asAdmin(get("/api/v1/admins/dashboard/stats")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var json = mapper.readTree(res).path("data");

        assertEquals(initStudents + 1, json.path("totalStudents").asLong());
        assertEquals(initTeachers + 1, json.path("totalTeachers").asLong());
        assertEquals(initCourses + 1, json.path("totalCourses").asLong());
        assertEquals(initReviews + 1, json.path("totalReviews").asLong());
        assertEquals(initPayments + 1, json.path("totalPayments").asLong());

        // 4. Delete temporary records
        reviewRepository.delete(review);
        paymentRepository.delete(payment);
        courseRepository.delete(course);
        userRepository.delete(student);
        userRepository.delete(teacher);

        // 5. Verify counts decreased back
        String resAfter = mvc.perform(asAdmin(get("/api/v1/admins/dashboard/stats")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var jsonAfter = mapper.readTree(resAfter).path("data");

        assertEquals(initStudents, jsonAfter.path("totalStudents").asLong());
        assertEquals(initTeachers, jsonAfter.path("totalTeachers").asLong());
        assertEquals(initCourses, jsonAfter.path("totalCourses").asLong());
        assertEquals(initReviews, jsonAfter.path("totalReviews").asLong());
        assertEquals(initPayments, jsonAfter.path("totalPayments").asLong());
    }
}

