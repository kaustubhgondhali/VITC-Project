package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.response.AdminOrderResponse;
import com.vitc.entity.AssignmentOrder;
import com.vitc.entity.PaymentOrder;
import com.vitc.entity.enums.OrderStatus;
import com.vitc.entity.enums.PaymentOrderStatus;
import com.vitc.mapper.AssignmentOrderMapper;
import com.vitc.repository.AssignmentOrderRepository;
import com.vitc.repository.PaymentOrderRepository;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final PaymentOrderRepository paymentOrders;
    private final AssignmentOrderRepository assignmentOrders;

    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<AdminOrderResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(allOrders()));
    }

    /**
     * Admin -> Assignment Upload: only the assignment orders that can be (or have been)
     * delivered - paid purchases and directly entered orders. Unpaid checkouts are left out.
     */
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/assignments")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<AdminOrderResponse>>> getAssignments() {
        List<AdminOrderResponse> orders = allOrders().stream()
                .filter(o -> "ASSIGNMENT".equals(o.orderType()))
                .filter(o -> "PAID".equals(o.paymentStatus()) || "NOT_TRACKED".equals(o.paymentStatus()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(orders));
    }

    private List<AdminOrderResponse> allOrders() {
        // A paid assignment purchase is a checkout order plus an assignment order with the same
        // code (its fulfilment) - shown as one row carrying both statuses, not as two rows.
        Map<String, AssignmentOrder> fulfilments = new HashMap<>();
        assignmentOrders.findAll().forEach(o -> fulfilments.put(o.getOrderCode(), o));

        List<AdminOrderResponse> orders = new ArrayList<>();
        for (PaymentOrder order : paymentOrders.findAll()) {
            AssignmentOrder fulfilment = "ASSIGNMENT".equals(order.getItemType())
                    ? fulfilments.remove(order.getOrderCode()) : null;
            orders.add(checkoutOrder(order, fulfilment));
        }
        fulfilments.values().stream().map(this::assignmentOrder).forEach(orders::add);
        orders.sort(Comparator.comparing(AdminOrderResponse::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return orders;
    }

    private AdminOrderResponse checkoutOrder(PaymentOrder order, AssignmentOrder fulfilment) {
        boolean paid = order.getStatus() == PaymentOrderStatus.PAID;
        String orderStatus = fulfilment != null
                ? fulfilment.getStatus().name()
                : paid ? "FULFILLMENT_PENDING" : "NOT_FULFILLED";
        boolean canDeliver = paid && "ASSIGNMENT".equals(order.getItemType())
                && (fulfilment == null || fulfilment.getStatus() != OrderStatus.CANCELLED);
        return new AdminOrderResponse(order.getId(), order.getOrderCode(), order.getItemType(),
                order.getCustomerName(), order.getEmail(), order.getPhone(), order.getItemTitle(),
                order.getTotalAmount(), order.getStatus().name(), orderStatus, order.getCreatedAt(),
                canDeliver, AssignmentOrderMapper.toDeliveryInfo(fulfilment));
    }

    /** An assignment order with no checkout behind it (entered directly, before online payments). */
    private AdminOrderResponse assignmentOrder(AssignmentOrder order) {
        var response = AssignmentOrderMapper.toResponse(order);
        return new AdminOrderResponse(response.id(), response.orderCode(), "ASSIGNMENT",
                response.studentName(), response.email(), response.phone(), response.assignmentTitle(),
                response.amount(), "NOT_TRACKED", response.status().name(), response.createdAt(),
                order.getStatus() != OrderStatus.CANCELLED, AssignmentOrderMapper.toDeliveryInfo(order));
    }
}
