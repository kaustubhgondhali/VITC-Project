package com.vitc.dto.response;

/** Result of uploading an assignment's project file, including any waiting buyers it was sent to. */
public record AssignmentFileUploadResponse(
        AdminAssignmentFileResponse assignment,
        int sentToWaitingBuyers,
        int emailsFailed) {
}
