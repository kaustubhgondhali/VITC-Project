package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.OrderStatusRequest;
import com.vitc.dto.response.AssignmentDeliveryResponse;
import com.vitc.dto.response.AssignmentOrderResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.AssignmentFulfilmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Main Admin delivery of purchased assignments, keyed by the order code shown in Admin -> Orders.
 * Lives under /api/v1/admin/**, so the admin session interceptor authenticates every call.
 */
@Tag(name = "Admin - Assignment delivery", description = "Deliver purchased assignment projects to buyers")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/assignment-orders")
public class AdminAssignmentOrderController {

    private final AssignmentFulfilmentService service;

    @Operation(summary = "Upload the project package, mark the order delivered and email the buyer a download link")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping(path = "/{orderCode}/deliver", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssignmentDeliveryResponse>> deliver(
            @PathVariable String orderCode,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(ApiResponse.ok("Project delivered", service.deliver(orderCode, file, note)));
    }

    @Operation(summary = "Email the buyer a new download link (the previous link stops working)")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping("/{orderCode}/resend-link")
    public ResponseEntity<ApiResponse<AssignmentDeliveryResponse>> resendLink(@PathVariable String orderCode) {
        return ResponseEntity.ok(ApiResponse.ok("Download link sent", service.resendDownloadLink(orderCode)));
    }

    @Operation(summary = "Change the fulfilment status of an assignment order")
    @RequireRole(Role.MAIN_ADMIN)
    @PatchMapping("/{orderCode}/status")
    public ResponseEntity<ApiResponse<AssignmentOrderResponse>> updateStatus(
            @PathVariable String orderCode, @Valid @RequestBody OrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateStatus(orderCode, request.status())));
    }
}
