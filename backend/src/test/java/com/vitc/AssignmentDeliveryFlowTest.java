package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Admin;
import com.vitc.entity.Assignment;
import com.vitc.entity.AssignmentOrder;
import com.vitc.entity.enums.EmailDeliveryStatus;
import com.vitc.entity.enums.EmailType;
import com.vitc.entity.enums.OrderStatus;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.AssignmentOrderRepository;
import com.vitc.repository.AssignmentRepository;
import com.vitc.repository.EmailDeliveryLogRepository;
import com.vitc.repository.UserRepository;
import com.vitc.service.CheckoutService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Buy Assignment -> Checkout -> (mock) gateway -> assignment order + confirmation email ->
 * Main Admin delivers the project -> buyer downloads it through the private link.
 *
 * <p>Not @Transactional on purpose: the confirmation email is sent after the payment commits.
 * No SMTP is configured in the test profile, so emails are recorded as FAILED - the flow itself
 * must succeed regardless.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssignmentDeliveryFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired AssignmentOrderRepository assignmentOrderRepository;
    @Autowired EmailDeliveryLogRepository emailLogRepository;
    @Autowired UserRepository userRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired CheckoutService checkoutService;

    @Value("${app.assignment.delivery-dir:private-uploads/assignment-deliveries}")
    String deliveryDir;

    private static final String EMAIL = "assignment-buyer@example.com";
    private static final String ADMIN_USERNAME = "deliveryflowadmin";
    private static final String ADMIN_TOKEN = "delivery-flow-admin-token";
    private static final byte[] ZIP = "PK\u0003\u0004 pretend project archive".getBytes(StandardCharsets.ISO_8859_1);

    private Long assignmentId;
    private final List<String> orderCodes = new ArrayList<>();

    @BeforeEach
    void seed() {
        Assignment assignment = assignmentRepository.findByCode("DELIVERY-TEST-ASSIGNMENT").orElseGet(() ->
                assignmentRepository.save(Assignment.builder()
                        .code("DELIVERY-TEST-ASSIGNMENT")
                        .title("Delivery Test Library System")
                        .price(new BigDecimal("1499.00"))
                        .deliveryDays("3-5 days")
                        .active(true)
                        .build()));
        // Every test starts without a ready-made project file on the shared test assignment.
        assignment.setProjectFilePath(null);
        assignment.setProjectFileName(null);
        assignment.setProjectContentType(null);
        assignment.setProjectSizeBytes(null);
        assignment.setProjectUploadedAt(null);
        assignmentRepository.save(assignment);
        assignmentId = assignment.getId();

        Admin admin = adminRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseGet(() -> adminRepository.save(
                Admin.builder().username(ADMIN_USERNAME).email("deliveryflowadmin@test.io")
                        .fullName("Delivery Flow Admin").passwordHash("x").build()));
        admin.setActive(true);
        admin.setSessionToken(ADMIN_TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        adminRepository.save(admin);
    }

    /**
     * Leave no email-log rows or files behind: other tests count email logs globally. Covers every
     * order of the test assignment, since a project-file upload also delivers earlier tests' orders.
     */
    @AfterEach
    void cleanUp() throws Exception {
        java.util.Set<String> codes = new java.util.HashSet<>(orderCodes);
        List<String> files = new ArrayList<>();
        for (AssignmentOrder order : assignmentOrderRepository.findAll()) {
            if (assignmentId.equals(order.getAssignment().getId())) {
                codes.add(order.getOrderCode());
                files.add(order.getDeliveryFilePath());
            }
        }
        files.add(assignmentRepository.findById(assignmentId).orElseThrow().getProjectFilePath());
        emailLogRepository.findAll().stream()
                .filter(l -> codes.contains(l.getOrderCode()))
                .forEach(emailLogRepository::delete);
        for (String file : files) {
            if (file != null) {
                Files.deleteIfExists(Paths.get(deliveryDir).toAbsolutePath().resolve(file));
            }
        }
    }

    @Test
    void paidAssignment_opensAssignmentOrder_andRecordsOneConfirmationEmail() throws Exception {
        String orderCode = buyAssignment();

        AssignmentOrder order = assignmentOrderRepository.findByOrderCode(orderCode).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(order.getAssignment().getId()).isEqualTo(assignmentId);
        assertThat(order.getEmail()).isEqualTo(EMAIL);
        assertThat(order.getAmount()).isNotNull();

        var confirmation = emailLogRepository.findByDedupeKey(EmailType.ASSIGNMENT_ORDER_CONFIRMED + ":" + orderCode);
        assertThat(confirmation).isPresent();
        assertThat(confirmation.get().getStatus()).isNotEqualTo(EmailDeliveryStatus.PENDING);

        // An assignment purchase is not a course: no student account is created for it.
        assertThat(userRepository.findByEmailIgnoreCase(EMAIL)).isEmpty();

        // Repeated browser confirm + Razorpay webhook replay: still one order, one email.
        String providerOrderId = lastProviderOrderId;
        confirmPayment(orderCode, providerOrderId);
        checkoutService.settleVerifiedWebhookPayment(providerOrderId, "pay_" + orderCode);
        assertThat(assignmentOrderRepository.findAll().stream()
                .filter(o -> orderCode.equals(o.getOrderCode()))).hasSize(1);
        assertThat(emailLogRepository.findAll().stream()
                .filter(l -> orderCode.equals(l.getOrderCode()))).hasSize(1);

        // Admin -> Orders shows the purchase once, as deliverable.
        JsonNode rows = data(mvc.perform(asAdmin(get("/api/v1/admin/orders"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<JsonNode> mine = new ArrayList<>();
        rows.forEach(r -> { if (orderCode.equals(r.path("orderCode").asText())) mine.add(r); });
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).path("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(mine.get(0).path("orderStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(mine.get(0).path("canDeliver").asBoolean()).isTrue();

        // Admin -> Assignment Upload lists only paid assignment orders, this one included.
        JsonNode uploads = data(mvc.perform(asAdmin(get("/api/v1/admin/orders/assignments")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> codes = new ArrayList<>();
        uploads.forEach(r -> {
            assertThat(r.path("orderType").asText()).isEqualTo("ASSIGNMENT");
            assertThat(r.path("paymentStatus").asText()).isIn("PAID", "NOT_TRACKED");
            codes.add(r.path("orderCode").asText());
        });
        assertThat(codes).containsOnlyOnce(orderCode);
        mvc.perform(get("/api/v1/admin/orders/assignments")).andExpect(status().is4xxClientError());
    }

    @Test
    void adminDelivers_buyerDownloadsWithPrivateLink_andOldLinksStopWorking() throws Exception {
        String orderCode = buyAssignment();

        JsonNode delivered = data(mvc.perform(asAdmin(multipart("/api/v1/admin/assignment-orders/" + orderCode + "/deliver")
                        .file(new MockMultipartFile("file", "library-system.zip", "application/zip", ZIP))
                        .param("note", "Run with: mvn spring-boot:run")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(delivered.path("status").asText()).isEqualTo("DELIVERED");
        String token = tokenOf(delivered.path("downloadUrl").asText());

        AssignmentOrder order = assignmentOrderRepository.findByOrderCode(orderCode).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDownloadTokenHash()).isNotBlank().isNotEqualTo(token); // only the hash is stored
        assertThat(Files.exists(Paths.get(deliveryDir).toAbsolutePath().resolve(order.getDeliveryFilePath()))).isTrue();
        assertThat(emailLogRepository.findAll().stream()
                .filter(l -> orderCode.equals(l.getOrderCode()) && l.getEmailType() == EmailType.ASSIGNMENT_DELIVERED))
                .hasSize(1);

        // The buyer's page and the file itself.
        JsonNode info = data(mvc.perform(get("/api/v1/public/assignment-downloads/" + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(info.path("fileName").asText()).isEqualTo("library-system.zip");
        assertThat(info.path("assignmentTitle").asText()).isEqualTo("Delivery Test Library System");
        assertThat(info.path("note").asText()).isEqualTo("Run with: mvn spring-boot:run");

        var file = mvc.perform(get("/api/v1/public/assignment-downloads/" + token + "/file"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(file.getContentAsByteArray()).isEqualTo(ZIP);
        assertThat(file.getHeader("Content-Disposition")).contains("attachment").contains("library-system.zip");
        assertThat(assignmentOrderRepository.findByOrderCode(orderCode).orElseThrow().getDownloadCount()).isEqualTo(1);

        // Resend: a new link works, the old one no longer does.
        JsonNode resent = data(mvc.perform(asAdmin(post("/api/v1/admin/assignment-orders/" + orderCode + "/resend-link")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String newToken = tokenOf(resent.path("downloadUrl").asText());
        assertThat(newToken).isNotEqualTo(token);
        mvc.perform(get("/api/v1/public/assignment-downloads/" + token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/assignment-downloads/" + newToken)).andExpect(status().isOk());

        // Cancelling the order revokes the download.
        mvc.perform(asAdmin(patch("/api/v1/admin/assignment-orders/" + orderCode + "/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/assignment-downloads/" + newToken)).andExpect(status().isNotFound());
    }

    @Test
    void assignmentProjectFile_isSentToWaitingBuyers_andAutomaticallyToNewBuyers() throws Exception {
        String waitingOrder = buyAssignment();
        assertThat(assignmentOrderRepository.findByOrderCode(waitingOrder).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.IN_PROGRESS);

        JsonNode before = assignmentRow();
        assertThat(before.path("fileName").isMissingNode()).isTrue();
        assertThat(before.path("waitingOrders").asLong()).isGreaterThanOrEqualTo(1);

        byte[] project = "PK\u0003\u0004 ready-made library system".getBytes(StandardCharsets.ISO_8859_1);
        JsonNode uploaded = data(mvc.perform(asAdmin(multipart("/api/v1/admin/assignment-files/" + assignmentId)
                        .file(new MockMultipartFile("file", "library-ready.zip", "application/zip", project))
                        .param("sendToWaitingBuyers", "true")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(uploaded.path("sentToWaitingBuyers").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(uploaded.path("assignment").path("fileName").asText()).isEqualTo("library-ready.zip");

        // The waiting buyer was delivered the shared file.
        String storedFile = assignmentRepository.findById(assignmentId).orElseThrow().getProjectFilePath();
        AssignmentOrder waiting = assignmentOrderRepository.findByOrderCode(waitingOrder).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(waiting.getDeliveryFilePath()).isEqualTo(storedFile);

        // A new buyer is delivered automatically on payment - no "we are preparing it" email.
        String newOrder = buyAssignment();
        AssignmentOrder auto = assignmentOrderRepository.findByOrderCode(newOrder).orElseThrow();
        assertThat(auto.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(auto.getDeliveryFilePath()).isEqualTo(storedFile);
        assertThat(emailLogRepository.findByDedupeKey(EmailType.ASSIGNMENT_DELIVERED + ":" + newOrder + ":paid")).isPresent();
        assertThat(emailLogRepository.findByDedupeKey(EmailType.ASSIGNMENT_ORDER_CONFIRMED + ":" + newOrder)).isEmpty();

        String token = tokenOf(data(mvc.perform(asAdmin(post("/api/v1/admin/assignment-orders/" + newOrder + "/resend-link")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("downloadUrl").asText());
        assertThat(mvc.perform(get("/api/v1/public/assignment-downloads/" + token + "/file"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isEqualTo(project);

        // Removing the file stops automatic delivery, but buyers who have it keep a working download.
        mvc.perform(asAdmin(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/assignment-files/" + assignmentId)))
                .andExpect(status().isOk());
        assertThat(assignmentRow().path("fileName").isMissingNode()).isTrue();
        assertThat(Files.exists(Paths.get(deliveryDir).toAbsolutePath().resolve(storedFile))).isTrue();
        mvc.perform(get("/api/v1/public/assignment-downloads/" + token + "/file")).andExpect(status().isOk());
        String afterRemoval = buyAssignment();
        assertThat(assignmentOrderRepository.findByOrderCode(afterRemoval).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.IN_PROGRESS);

        mvc.perform(get("/api/v1/admin/assignment-files")).andExpect(status().is4xxClientError());
    }

    @Test
    void deliveryIsAdminOnly_andRejectsUnsafeFilesAndBadTokens() throws Exception {
        String orderCode = buyAssignment();
        MockMultipartFile zip = new MockMultipartFile("file", "project.zip", "application/zip", ZIP);

        mvc.perform(multipart("/api/v1/admin/assignment-orders/" + orderCode + "/deliver").file(zip))
                .andExpect(status().is4xxClientError());
        mvc.perform(asAdmin(multipart("/api/v1/admin/assignment-orders/" + orderCode + "/deliver")
                        .file(new MockMultipartFile("file", "setup.exe", "application/octet-stream", ZIP))))
                .andExpect(status().isBadRequest());
        assertThat(assignmentOrderRepository.findByOrderCode(orderCode).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.IN_PROGRESS);

        mvc.perform(get("/api/v1/public/assignment-downloads/not-a-real-token")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/assignment-downloads/" + "A".repeat(43) + "/file"))
                .andExpect(status().isNotFound());
    }

    /* ------------------------------------------------------------------ */

    private String lastProviderOrderId;

    private String buyAssignment() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("itemType", "ASSIGNMENT");
        body.put("itemRefId", assignmentId);
        body.put("itemTitle", "Delivery Test Library System");
        body.put("customerName", "Assignment Buyer");
        body.put("email", EMAIL);
        body.put("phone", "9999999999");
        body.put("subtotal", "1499.00");
        String orderCode = data(mvc.perform(post("/api/v1/checkout/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("orderCode").asText();
        orderCodes.add(orderCode);

        lastProviderOrderId = data(mvc.perform(post("/api/v1/checkout/orders/" + orderCode + "/pay")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"UPI\",\"methodDetail\":\"test@upi\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("providerOrderId").asText();
        confirmPayment(orderCode, lastProviderOrderId);
        return orderCode;
    }

    private void confirmPayment(String orderCode, String providerOrderId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("providerOrderId", providerOrderId);
        body.put("providerPaymentId", "pay_" + orderCode);
        body.put("providerSignature", "mock-signature");
        mvc.perform(post("/api/v1/checkout/orders/" + orderCode + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private JsonNode assignmentRow() throws Exception {
        JsonNode rows = data(mvc.perform(asAdmin(get("/api/v1/admin/assignment-files")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode row : rows) {
            if (assignmentId.equals(row.path("id").asLong())) {
                return row;
            }
        }
        throw new AssertionError("Test assignment missing from Admin -> Assignment Upload");
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", ADMIN_TOKEN);
    }

    private JsonNode data(String json) throws Exception {
        return mapper.readTree(json).path("data");
    }

    private static String tokenOf(String downloadUrl) {
        assertThat(downloadUrl).contains("/assignment-download.html?token=");
        return downloadUrl.substring(downloadUrl.indexOf("token=") + "token=".length());
    }
}
