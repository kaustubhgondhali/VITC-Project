package com.vitc.service;

import com.vitc.dto.response.AdminAssignmentFileResponse;
import com.vitc.dto.response.AssignmentDeliveryResponse;
import com.vitc.dto.response.AssignmentFileUploadResponse;
import com.vitc.dto.response.AssignmentDownloadInfoResponse;
import com.vitc.dto.response.AssignmentOrderResponse;
import com.vitc.dto.response.EmailDeliveryLogResponse;
import com.vitc.entity.EmailDeliveryLog;
import com.vitc.entity.PaymentOrder;
import com.vitc.entity.enums.OrderStatus;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * What a buyer gets after paying for an assignment: the paid checkout order becomes an
 * assignment order (same order code), the buyer is emailed a confirmation, and once a Main Admin
 * uploads the project package the buyer is emailed a private, expiring download link.
 *
 * <p>Admin operations are keyed by order code. A paid assignment checkout that has no assignment
 * order yet (bought before this flow existed) gets one on the first admin action, so it can
 * still be delivered.</p>
 */
public interface AssignmentFulfilmentService {

    /**
     * Payment verified for an ASSIGNMENT checkout order. Idempotent (browser confirm and the
     * Razorpay webhook may both arrive) and never throws, so it can never fail a verified payment.
     */
    void onAssignmentPaid(PaymentOrder order);

    /** Admin: every catalogue assignment with its ready-made project file (if any). */
    List<AdminAssignmentFileResponse> listAssignmentFiles();

    /**
     * Admin: attaches (or replaces) an assignment's ready-made project file. Future buyers get it
     * automatically after paying; with {@code sendToWaitingBuyers}, paid orders still waiting for
     * this assignment are delivered right away too.
     */
    AssignmentFileUploadResponse uploadAssignmentFile(Long assignmentId, MultipartFile file, boolean sendToWaitingBuyers);

    /** Admin: detaches the project file; future buyers are then delivered manually again. */
    AdminAssignmentFileResponse removeAssignmentFile(Long assignmentId);

    /** Admin: stores the project package, marks the order DELIVERED and emails a fresh download link. */
    AssignmentDeliveryResponse deliver(String orderCode, MultipartFile file, String note);

    /** Admin: replaces the previous download link with a new one and emails it again. */
    AssignmentDeliveryResponse resendDownloadLink(String orderCode);

    /** Admin: manual status change, e.g. delivered outside the website, or cancelled. */
    AssignmentOrderResponse updateStatus(String orderCode, OrderStatus status);

    /** Public: what a download link points at. */
    AssignmentDownloadInfoResponse downloadInfo(String token);

    /** Public: opens the delivered package for streaming and records the download. */
    AssignmentDownload openDownload(String token);

    /** Admin email-log retry for {@code ASSIGNMENT_*} email types. */
    EmailDeliveryLogResponse retryEmail(EmailDeliveryLog record);

    record AssignmentDownload(Resource resource, String fileName, String contentType, long sizeBytes) {
    }
}
