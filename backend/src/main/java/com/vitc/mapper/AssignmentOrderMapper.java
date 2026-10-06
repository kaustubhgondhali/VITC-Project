package com.vitc.mapper;

import com.vitc.dto.request.AssignmentOrderRequest;
import com.vitc.dto.response.AssignmentDeliveryInfo;
import com.vitc.dto.response.AssignmentOrderResponse;
import com.vitc.entity.Assignment;
import com.vitc.entity.AssignmentOrder;

public final class AssignmentOrderMapper {

    private AssignmentOrderMapper() {
    }

    public static AssignmentOrder toEntity(AssignmentOrderRequest request, Assignment assignment, String orderCode) {
        AssignmentOrder entity = new AssignmentOrder();
        entity.setOrderCode(orderCode);
        apply(entity, request, assignment);
        return entity;
    }

    public static void apply(AssignmentOrder entity, AssignmentOrderRequest request, Assignment assignment) {
        entity.setStudentName(request.studentName());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setCollege(request.college());
        entity.setRequirements(request.requirements());
        entity.setAssignment(assignment);
        entity.setAmount(assignment.getPrice());
    }

    public static AssignmentOrderResponse toResponse(AssignmentOrder entity) {
        return new AssignmentOrderResponse(
                entity.getId(),
                entity.getOrderCode(),
                entity.getAssignment() != null ? entity.getAssignment().getId() : null,
                entity.getAssignment() != null ? entity.getAssignment().getTitle() : null,
                entity.getStudentName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getCollege(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getRequirements(),
                entity.getCreatedAt());
    }

    /** Delivery summary for admin views, or null while nothing has been delivered. */
    public static AssignmentDeliveryInfo toDeliveryInfo(AssignmentOrder entity) {
        if (entity == null || entity.getDeliveredAt() == null) {
            return null;
        }
        return new AssignmentDeliveryInfo(
                entity.getDeliveryFileName(),
                entity.getDeliverySizeBytes(),
                entity.getDeliveryNote(),
                entity.getDeliveredAt(),
                entity.getDownloadExpiresAt(),
                entity.getDownloadCount() == null ? 0 : entity.getDownloadCount(),
                entity.getLastDownloadedAt());
    }
}
