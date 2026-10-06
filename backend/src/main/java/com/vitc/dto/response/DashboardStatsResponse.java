package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(name = "DashboardStatsResponse", description = "Aggregated counters for the admin dashboard")
public record DashboardStatsResponse(
    long totalCourses,
    long totalAssignments,
    long totalUsers,
    long totalOrders,
    long totalPayments,
    long totalReviews,
    long pendingReviews,
    long totalGalleryItems,
    long totalFaqs,
    BigDecimal totalRevenue,
    long totalStudents,
    long activeStudents,
    long totalTeachers,
    long totalEnrollments,
    long totalJobs,
    long totalJobApplications,
    long totalInternships,
    long totalEmployerEnquiries,
    long totalExams
) {
}
