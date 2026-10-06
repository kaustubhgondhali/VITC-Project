package com.vitc.dto.request;

import com.vitc.entity.enums.EnquiryStatus;
import jakarta.validation.constraints.NotNull;

public record EnquiryStatusRequest(@NotNull EnquiryStatus status) {}
