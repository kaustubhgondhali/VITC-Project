package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Course;
import com.vitc.entity.enums.EmailDeliveryStatus;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.PaymentOrderStatus;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EmailDeliveryLogRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.PaymentOrderRepository;
import com.vitc.repository.UserRepository;
import com.vitc.service.CheckoutService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PART 6 — end-to-end coverage of Buy Course -> Checkout -> (mock) gateway ->
 * backend verification -> student account -> enrolment -> credential email.
 *
 * <p>No SMTP is configured in the test profile, so every credential email in
 * this test fails to send by design - that is exactly the scenario PART 6
 * hardens: payment/account/enrolment must all succeed regardless, and the
 * failure must be recorded, not retried automatically or silently dropped.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentToEmailFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CourseRepository courseRepository;
    @Autowired UserRepository userRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired EmailDeliveryLogRepository emailLogRepository;
    @Autowired PaymentOrderRepository orderRepository;
    @Autowired CheckoutService checkoutService;

    private static final String EMAIL = "payment-flow-student@example.com";
    private Long courseId;

    @BeforeEach
    void seedCourse() {
        // Clear dependent rows first: the previous test in this (non-transactional) class may have
        // left an enrollment pointing at this user, and deleting the user first violates the FK.
        enrollmentRepository.deleteAll(enrollmentRepository.findByEmailIgnoreCase(EMAIL));
        userRepository.findByEmailIgnoreCase(EMAIL).ifPresent(userRepository::delete);
        Course course = courseRepository.findByCode("PART6-TEST-COURSE").orElseGet(() ->
                courseRepository.save(Course.builder()
                        .code("PART6-TEST-COURSE")
                        .title("Part 6 Test Course")
                        .description("Seeded for the payment-to-email flow test")
                        .price(new BigDecimal("999.00"))
                        .active(true)
                        .build()));
        courseId = course.getId();
    }

    @Test
    void paidOrder_activatesStudentAndEnrollment_andRecordsEmailOutcome_evenWhenSmtpFails() throws Exception {
        String orderCode = createOrder();
        String providerOrderId = initiatePayment(orderCode);

        // First confirmation: this is the real, verified success path.
        confirmPayment(orderCode, providerOrderId, "pay_" + orderCode, status().isOk());

        assertThat(orderRepository.findByOrderCode(orderCode).orElseThrow().getStatus())
                .isEqualTo(PaymentOrderStatus.PAID);

        var user = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getStudentLoginId()).isNotBlank();
        // The temporary password must never be persisted or exposed in plain form.
        assertThat(user.getPasswordHash()).isNotBlank();

        var enrollment = enrollmentRepository.findFirstByUserIdAndCourseId(user.getId(), courseId).orElseThrow();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);

        // SMTP is not configured in the test profile, so the credential email
        // must be recorded as FAILED - never left PENDING, never silently lost,
        // and never allowed to roll back the payment/account/enrolment above.
        var logs = emailLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(logs.get(0).getStudentLoginId()).isEqualTo(user.getStudentLoginId());

        // --- Duplicate callback / page refresh: re-confirm the SAME payment ---
        confirmPayment(orderCode, providerOrderId, "pay_" + orderCode, status().isOk());

        assertThat(userRepository.findByEmailIgnoreCase(EMAIL)).isPresent(); // still exactly one account
        assertThat(enrollmentRepository.findByEmailIgnoreCase(EMAIL)).hasSize(1); // no duplicate enrolment
        assertThat(emailLogRepository.findAll()).hasSize(1); // no duplicate email attempt logged
    }

    @Test
    void duplicateWebhookAfterBrowserConfirm_doesNotDuplicateAnything() throws Exception {
        String orderCode = createOrder();
        String providerOrderId = initiatePayment(orderCode);
        String providerPaymentId = "pay_" + orderCode;

        confirmPayment(orderCode, providerOrderId, providerPaymentId, status().isOk());
        boolean userExistsAfterConfirm = userRepository.findByEmailIgnoreCase(EMAIL).isPresent();
        int enrollmentsAfterConfirm = enrollmentRepository.findByEmailIgnoreCase(EMAIL).size();
        int emailLogsAfterConfirm = emailLogRepository.findAll().size();

        // Razorpay's webhook can arrive after the browser already confirmed the
        // same payment (or be retried by Razorpay itself). This calls the same
        // settlement path the signature-verified webhook controller calls once
        // the HMAC check passes - signature verification itself is exercised by
        // hitting PaymentGatewayController directly and is out of scope here;
        // this test focuses on the idempotency of the settlement it guards.
        checkoutService.settleVerifiedWebhookPayment(providerOrderId, providerPaymentId);

        assertThat(userRepository.findByEmailIgnoreCase(EMAIL).isPresent()).isEqualTo(userExistsAfterConfirm);
        assertThat(enrollmentRepository.findByEmailIgnoreCase(EMAIL)).hasSize(enrollmentsAfterConfirm);
        assertThat(emailLogRepository.findAll()).hasSize(emailLogsAfterConfirm);
    }

    /* ------------------------------------------------------------------ */

    private String createOrder() throws Exception {
        String body = mapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("itemType", "COURSE");
            put("itemRefId", courseId);
            put("itemTitle", "Part 6 Test Course");
            put("customerName", "Part Six Tester");
            put("email", EMAIL);
            put("phone", "9999999999");
            put("city", "Pune");
            put("subtotal", "999.00");
        }});
        String response = mvc.perform(post("/api/v1/checkout/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("data").path("orderCode").asText();
    }

    private String initiatePayment(String orderCode) throws Exception {
        String body = "{\"method\":\"UPI\",\"methodDetail\":\"test@upi\"}";
        String response = mvc.perform(post("/api/v1/checkout/orders/" + orderCode + "/pay")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = mapper.readTree(response).path("data");
        return data.path("providerOrderId").asText();
    }

    private void confirmPayment(String orderCode, String providerOrderId, String providerPaymentId,
                                 org.springframework.test.web.servlet.ResultMatcher expect) throws Exception {
        String body = mapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("providerOrderId", providerOrderId);
            put("providerPaymentId", providerPaymentId);
            put("providerSignature", "mock-signature");
        }});
        mvc.perform(post("/api/v1/checkout/orders/" + orderCode + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(expect);
    }
}
