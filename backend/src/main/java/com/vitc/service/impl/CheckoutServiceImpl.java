package com.vitc.service.impl;

import com.vitc.dto.request.CheckoutOrderRequest;
import com.vitc.dto.request.PaymentConfirmRequest;
import com.vitc.dto.request.PaymentInitiateRequest;
import com.vitc.dto.response.InvoiceResponse;
import com.vitc.dto.response.OrderConfirmationResponse;
import com.vitc.dto.response.PaymentInitiationResponse;
import com.vitc.dto.response.PaymentOrderResponse;
import com.vitc.dto.response.StudentCredentialsResponse;
import com.vitc.entity.Invoice;
import com.vitc.entity.Payment;
import com.vitc.entity.PaymentOrder;
import com.vitc.entity.enums.InvoiceStatus;
import com.vitc.entity.enums.PaymentOrderStatus;
import com.vitc.entity.enums.PaymentStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.InvoiceMapper;
import com.vitc.mapper.PaymentMapper;
import com.vitc.mapper.PaymentOrderMapper;
import com.vitc.payment.gateway.GatewayOrder;
import com.vitc.payment.gateway.GatewayOrderRequest;
import com.vitc.payment.gateway.GatewayVerification;
import com.vitc.payment.gateway.GatewayVerificationRequest;
import com.vitc.payment.gateway.PaymentGateway;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.InvoiceRepository;
import com.vitc.repository.PaymentOrderRepository;
import com.vitc.repository.PaymentRepository;
import com.vitc.service.AssignmentFulfilmentService;
import com.vitc.service.CheckoutService;
import com.vitc.service.StudentAccountService;
import com.vitc.service.StudentCredentialEmailService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order -> payment -> invoice -> confirmation flow. Talks only to the
 * {@link PaymentGateway} port, never to a concrete provider SDK.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckoutServiceImpl implements CheckoutService {

    private static final Map<String, Integer> COUPONS = Map.of(
            "VITC10", 10,
            "STUDENT15", 15,
            "NEW20", 20);

    private final PaymentOrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentFulfilmentService assignmentFulfilmentService;
    private final PaymentGateway gateway;
    private final StudentAccountService studentAccountService;
    private final StudentCredentialEmailService studentCredentialEmailService;

    @Value("${payment.tax-rate:18}")
    private BigDecimal taxRate;

    @Value("${payment.currency:INR}")
    private String currency;

    @Override
    public List<PaymentOrderResponse> getAllOrders() {
        return orderRepository.findAll().stream().map(PaymentOrderMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public PaymentOrderResponse createOrder(CheckoutOrderRequest request) {
        String itemType = request.itemType().toUpperCase(Locale.ROOT);
        BigDecimal subtotal = request.subtotal().setScale(2, RoundingMode.HALF_UP);

        String coupon = request.couponCode() == null ? null : request.couponCode().trim().toUpperCase(Locale.ROOT);
        int pct = coupon == null ? 0 : COUPONS.getOrDefault(coupon, 0);
        if (coupon != null && !coupon.isEmpty() && pct == 0) {
            throw new BadRequestException("Invalid coupon code: " + coupon);
        }

        BigDecimal discount = subtotal.multiply(BigDecimal.valueOf(pct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal tax = taxable.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(tax).setScale(2, RoundingMode.HALF_UP);

        PaymentOrder order = PaymentOrder.builder()
                .orderCode(generateOrderCode())
                .itemType(itemType)
                .itemRefId(request.itemRefId())
                .itemTitle(request.itemTitle())
                .itemMeta(request.itemMeta())
                .customerName(request.customerName())
                .email(request.email())
                .phone(request.phone())
                .city(request.city())
                .couponCode(pct > 0 ? coupon : null)
                .subtotal(subtotal)
                .discountAmount(discount)
                .taxRate(taxRate.setScale(2, RoundingMode.HALF_UP))
                .taxAmount(tax)
                .totalAmount(total)
                .currency(currency)
                .status(PaymentOrderStatus.CREATED)
                .build();

        return PaymentOrderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    public PaymentOrderResponse getOrder(String orderCode) {
        return PaymentOrderMapper.toResponse(findOrder(orderCode));
    }

    @Override
    public List<PaymentOrderResponse> getOrdersByEmail(String email) {
        return orderRepository.findByEmailIgnoreCaseOrderByIdDesc(email).stream()
                .map(PaymentOrderMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public PaymentInitiationResponse initiatePayment(String orderCode, PaymentInitiateRequest request) {
        PaymentOrder order = findOrder(orderCode);
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            throw new BadRequestException("Order " + orderCode + " is already paid");
        }
        if (order.getStatus() == PaymentOrderStatus.CANCELLED) {
            throw new BadRequestException("Order " + orderCode + " has been cancelled");
        }

        GatewayOrder gatewayOrder = gateway.createOrder(new GatewayOrderRequest(
                order.getOrderCode(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCustomerName(),
                order.getEmail(),
                order.getPhone(),
                order.getItemTitle()));

        Payment payment = Payment.builder()
                .order(order)
                .referenceType(order.getItemType())
                .referenceId(order.getItemRefId())
                .transactionId(generateTransactionId())
                .payerName(order.getCustomerName())
                .email(order.getEmail())
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .method(request.method())
                .methodDetail(request.methodDetail())
                .status(PaymentStatus.PENDING)
                .provider(gateway.name())
                .providerOrderId(gatewayOrder.providerOrderId())
                .build();
        payment = paymentRepository.save(payment);

        order.setStatus(PaymentOrderStatus.AWAITING_PAYMENT);
        orderRepository.save(order);

        return new PaymentInitiationResponse(
                gateway.name(),
                order.getOrderCode(),
                payment.getTransactionId(),
                gatewayOrder.providerOrderId(),
                gatewayOrder.providerKey(),
                gatewayOrder.checkoutUrl(),
                order.getTotalAmount(),
                order.getCurrency(),
                gatewayOrder.autoConfirm(),
                PaymentMapper.toResponse(payment));
    }

    @Override
    @Transactional
    public OrderConfirmationResponse confirmPayment(String orderCode, PaymentConfirmRequest request) {
        PaymentOrder order = findOrder(orderCode);
        Payment payment = paymentRepository.findByProviderOrderId(request.providerOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment attempt found for provider order: " + request.providerOrderId()));

        if (!payment.getOrder().getId().equals(order.getId())) {
            throw new BadRequestException("Payment does not belong to order " + orderCode);
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return buildConfirmation(order, payment);
        }

        GatewayVerification verification = gateway.verify(new GatewayVerificationRequest(
                order.getOrderCode(),
                request.providerOrderId(),
                request.providerPaymentId(),
                request.providerSignature(),
                Boolean.TRUE.equals(request.simulateFailure())));

        payment.setGatewayResponse(verification.rawResponse());

        if (!verification.success()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(verification.failureReason());
            paymentRepository.save(payment);
            order.setStatus(PaymentOrderStatus.FAILED);
            orderRepository.save(order);
            throw new BadRequestException(verification.failureReason());
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderPaymentId(verification.providerPaymentId());
        payment.setProviderSignature(verification.providerSignature());
        payment.setPaidAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        order.setStatus(PaymentOrderStatus.PAID);
        orderRepository.save(order);

        confirmFulfilment(order);
        Invoice invoice = issueInvoice(order, payment);
        // Payment is verified by the backend at this point - only now may a
        // student account and its enrolment be created/activated.
        StudentCredentialsResponse studentAccount = studentAccountService.provisionForPaidOrder(order);
        log.info("Order {} verified and PAID - reached credential email step (studentId={})",
                order.getOrderCode(), studentAccount == null ? "n/a" : studentAccount.studentLoginId());
        // Signature verified + account provisioned -> mail the login details.
        // This never throws, so email problems cannot affect the payment.
        studentCredentialEmailService.sendForPaidOrder(order, studentAccount);

        log.info("Order {} paid via {} ({})", order.getOrderCode(), payment.getProvider(), payment.getMethod());
        return new OrderConfirmationResponse(
                PaymentOrderMapper.toResponse(order),
                PaymentMapper.toResponse(payment),
                InvoiceMapper.toResponse(invoice),
                // The temporary password is never returned by any API - it has
                // already been BCrypt-hashed into the database and mailed to the
                // student above. Only the non-secret fields (login id / whether
                // this was a new account / a status message) go back to the
                // browser, so the payment-success screen can say "check your
                // email" without ever holding the password itself.
                redactPassword(studentAccount));
    }

    /** Strips the one-time temporary password before a response can reach the client. */
    private StudentCredentialsResponse redactPassword(StudentCredentialsResponse account) {
        if (account == null) {
            return null;
        }
        return new StudentCredentialsResponse(
                account.studentLoginId(), null, account.newAccount(), account.message());
    }

    @Override
    public OrderConfirmationResponse getConfirmation(String orderCode) {
        PaymentOrder order = findOrder(orderCode);
        Payment payment = paymentRepository.findFirstByOrderIdAndStatusOrderByIdDesc(order.getId(), PaymentStatus.SUCCESS)
                .orElseGet(() -> paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).orElse(null));
        return buildConfirmation(order, payment);
    }

    @Override
    @Transactional
    public void settleVerifiedWebhookPayment(String providerOrderId, String providerPaymentId) {
        Payment payment = paymentRepository.findByProviderOrderId(providerOrderId).orElse(null);
        if (payment == null) {
            log.warn("Razorpay webhook for unknown provider order {}", providerOrderId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return; // already settled - replay safe
        }
        PaymentOrder order = payment.getOrder();

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderPaymentId(providerPaymentId);
        payment.setPaidAt(LocalDateTime.now());
        payment.setGatewayResponse("{\"status\":\"captured\",\"provider\":\"RAZORPAY\",\"source\":\"webhook\"}");
        payment = paymentRepository.save(payment);

        order.setStatus(PaymentOrderStatus.PAID);
        orderRepository.save(order);

        confirmFulfilment(order);
        issueInvoice(order, payment);
        StudentCredentialsResponse webhookAccount = studentAccountService.provisionForPaidOrder(order);
        log.info("Order {} verified via webhook and PAID - reached credential email step (studentId={})",
                order.getOrderCode(), webhookAccount == null ? "n/a" : webhookAccount.studentLoginId());
        studentCredentialEmailService.sendForPaidOrder(order, webhookAccount);
        log.info("Order {} settled from Razorpay webhook", order.getOrderCode());
    }

    @Override
    @Transactional
    public PaymentOrderResponse cancelOrder(String orderCode) {
        PaymentOrder order = findOrder(orderCode);
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            throw new BadRequestException("A paid order cannot be cancelled; refund it instead");
        }
        order.setStatus(PaymentOrderStatus.CANCELLED);
        return PaymentOrderMapper.toResponse(orderRepository.save(order));
    }

    /* ------------------------------------------------------------------ */

    private OrderConfirmationResponse buildConfirmation(PaymentOrder order, Payment payment) {
        InvoiceResponse invoice = invoiceRepository.findByOrderId(order.getId())
                .map(InvoiceMapper::toResponse).orElse(null);
        return new OrderConfirmationResponse(
                PaymentOrderMapper.toResponse(order),
                payment == null ? null : PaymentMapper.toResponse(payment),
                invoice);
    }

    private Invoice issueInvoice(PaymentOrder order, Payment payment) {
        return invoiceRepository.findByOrderId(order.getId()).orElseGet(() -> {
            Invoice invoice = Invoice.builder()
                    .invoiceNumber(generateInvoiceNumber())
                    .order(order)
                    .payment(payment)
                    .billingName(order.getCustomerName())
                    .billingEmail(order.getEmail())
                    .billingPhone(order.getPhone())
                    .billingAddress(order.getCity())
                    .subtotal(order.getSubtotal())
                    .discountAmount(order.getDiscountAmount())
                    .taxAmount(order.getTaxAmount())
                    .totalAmount(order.getTotalAmount())
                    .status(InvoiceStatus.PAID)
                    .issuedAt(LocalDateTime.now())
                    .build();
            return invoiceRepository.save(invoice);
        });
    }

    /**
     * Order confirmation flow: move the purchased entity to its next state.
     *
     * <p>For a COURSE order, {@code order.getItemRefId()} is the purchased
     * Course's id (see {@code StudentAccountServiceImpl.resolveCourse}), not
     * an Enrollment id - the enrolment itself is created/activated right
     * after this call, in {@code studentAccountService.provisionForPaidOrder
     * -> linkEnrollment}, which resolves it correctly by (user, course).
     * There is nothing to do here for COURSE orders.</p>
     *
     * <p>For an ASSIGNMENT order, {@code order.getItemRefId()} is likewise the
     * purchased Assignment's catalogue id: the fulfilment service opens the
     * assignment order (same order code) and emails the buyer a confirmation.</p>
     */
    private void confirmFulfilment(PaymentOrder order) {
        if ("ASSIGNMENT".equals(order.getItemType())) {
            assignmentFulfilmentService.onAssignmentPaid(order);
        }
    }

    private PaymentOrder findOrder(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderCode));
    }

    private String generateOrderCode() {
        return "VITC-" + shortId(8);
    }

    private String generateTransactionId() {
        return "TXN" + shortId(12);
    }

    private String generateInvoiceNumber() {
        return "INV-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + shortId(6);
    }

    private String shortId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase(Locale.ROOT);
    }
}
