package com.vitc.service;

import com.vitc.dto.response.TeacherDashboardStatsResponse;

/**
 * Teacher Admin dashboard statistics. Kept separate from
 * {@link TeacherAccountService} (authentication only) since this reads
 * course/content data instead. Every method here MUST scope its result to
 * the given teacher's own courses only - never to the whole catalogue.
 */
public interface TeacherDashboardService {

    TeacherDashboardStatsResponse dashboardStats(Long teacherId);
}
