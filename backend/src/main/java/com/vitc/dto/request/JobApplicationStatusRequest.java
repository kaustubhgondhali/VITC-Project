package com.vitc.dto.request;

import com.vitc.entity.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin -> Job Applications: move an application through the hiring pipeline. */
public record JobApplicationStatusRequest(
        @NotNull(message = "Status is required") ApplicationStatus status,
        @Size(max = 1000, message = "Notes can be at most 1000 characters") String adminNotes) {
}
