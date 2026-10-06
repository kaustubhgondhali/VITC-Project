package com.vitc.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record ExamScheduleRequest(
        @NotNull @FutureOrPresent LocalDate examDate,
        @NotNull LocalTime examTime,
        String mode,
        String locationOrLink,
        String notes) {}
