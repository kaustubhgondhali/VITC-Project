package com.vitc.config;

import com.vitc.security.AdminAuthInterceptor;
import com.vitc.security.AdminOnlyApiInterceptor;
import com.vitc.security.RoleAuthorizationInterceptor;
import com.vitc.security.StudentAuthInterceptor;
import com.vitc.security.TeacherAuthInterceptor;
import com.vitc.security.VideoDirectAccessInterceptor;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final AdminOnlyApiInterceptor adminOnlyApiInterceptor;
    private final StudentAuthInterceptor studentAuthInterceptor;
    private final TeacherAuthInterceptor teacherAuthInterceptor;
    private final RoleAuthorizationInterceptor roleAuthorizationInterceptor;
    private final VideoDirectAccessInterceptor videoDirectAccessInterceptor;

    /** Payment configuration is admin-only; everything else keeps its current access rules. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Every admin-only API lives under /api/v1/admin/** (payment settings,
        // student management, course content). Backend authorisation, not menu hiding.
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/v1/admin/**", "/api/v1/admin");

        // PART 10B - the remaining Main Admin surfaces do not share that prefix, so they are
        // guarded explicitly on the backend: admin management, user/teacher accounts, payments,
        // orders, enrollments and invoice listings. A Teacher session token is rejected with
        // 403 Forbidden here; the storefront/login allow-list inside the interceptor keeps
        // public flows (admin login, visitor enrollment, invoice-by-order) working.
        registry.addInterceptor(adminOnlyApiInterceptor)
                .addPathPatterns(
                        "/api/v1/admins", "/api/v1/admins/**",
                        "/api/v1/users", "/api/v1/users/**",
                        "/api/v1/payments", "/api/v1/payments/**",
                        "/api/v1/assignment-orders", "/api/v1/assignment-orders/**",
                        "/api/v1/enrollments", "/api/v1/enrollments/**",
                        "/api/v1/invoices", "/api/v1/invoices/**",
                        "/api/v1/courses", "/api/v1/courses/**",
                        "/api/v1/gallery", "/api/v1/gallery/**",
                        // FIX - REVIEWER PHOTO UPLOAD: FileController.getAll/getById/delete/deleteMany
                        // all carry @RequireRole(MAIN_ADMIN), but (like reviews/internships before the
                        // PART 18 fix below) nothing was ever authenticating the caller for the base
                        // "/api/v1/files" path — only "/api/v1/files/gallery" was registered here. So
                        // CurrentUserContext was always null and every admin file-list/delete call was
                        // rejected with 403 even for a logged-in Main Admin. The public upload routes
                        // (POST /api/v1/files, POST /api/v1/files/gallery — used by the Reviewer Photo
                        // "Upload" button and by visitor-facing forms) are kept open via the allow-list
                        // in AdminOnlyApiInterceptor below, exactly as before.
                        "/api/v1/files", "/api/v1/files/**",
                        "/api/v1/admin/orders", "/api/v1/admin/orders/**",
                        "/api/v1/admin/jobs", "/api/v1/admin/jobs/**",
                        "/api/v1/admin/employer-enquiries", "/api/v1/admin/employer-enquiries/**",
                        // FIX - these three carry @RequireRole(MAIN_ADMIN) on their admin-facing
                        // handlers, but nothing was ever authenticating the caller for them, so
                        // RoleAuthorizationInterceptor always saw a null CurrentUserContext and
                        // rejected every request with 403 - hiding submitted reviews, internship
                        // applications and job applications from the Main Admin panel even for a
                        // logged-in admin. Registering them here (with the student/visitor-facing
                        // endpoints kept public via the allow-list below) authenticates the admin
                        // session before the role check runs, exactly like every other admin list.
                        "/api/v1/reviews", "/api/v1/reviews/**",
                        "/api/v1/internships", "/api/v1/internships/**",
                        "/api/v1/careers", "/api/v1/careers/**",
                        "/api/v1/contact-messages", "/api/v1/contact-messages/**",
                        "/api/v1/blog-posts", "/api/v1/blog-posts/**",
                        "/api/v1/testimonials", "/api/v1/testimonials/**",
                        "/api/v1/faqs", "/api/v1/faqs/**",
                        "/api/v1/pricing", "/api/v1/pricing/**",
                        // PART 2 - assignments had the same gap: their admin-facing handlers carry
                        // @RequireRole(MAIN_ADMIN) but no interceptor authenticated the caller, so
                        // CurrentUserContext was always null and every Add/Edit/Delete/Hide/Publish
                        // was rejected with 403. Registering the path here reuses the existing Main
                        // Admin session check; the public GET catalogue stays open via the
                        // method+path allow-list in AdminOnlyApiInterceptor.
                        "/api/v1/assignments", "/api/v1/assignments/**");

        // Student area: separate from the admin area, login endpoint excluded.
        // PART 6C-2A/8 - the protected video stream endpoint is also excluded: a native
        // <video> element cannot attach the X-Student-Id / X-Student-Token headers this
        // interceptor requires, so that one path authenticates itself via its own signed,
        // lesson-scoped token instead (see StudentVideoStreamController / StudentVideoStreamService).
        // PART 4 - forgot/reset-password/OTP endpoints are excluded too: by definition a student who has
        // lost their password cannot present a valid session, exactly like /auth/login.
        registry.addInterceptor(studentAuthInterceptor)
                .addPathPatterns("/api/v1/student/**")
                .excludePathPatterns("/api/v1/student/auth/login", "/api/v1/student/lessons/*/video",
                        "/api/v1/student/lessons/*/audio",
                        "/api/v1/student/auth/forgot-password", "/api/v1/student/auth/verify-otp",
                        "/api/v1/student/auth/resend-otp", "/api/v1/student/auth/reset-password");

        // Teacher Admin area: completely separate from Main Admin and Student,
        // login + session-verify endpoints excluded (they issue/check the token itself).
        registry.addInterceptor(teacherAuthInterceptor)
                .addPathPatterns("/api/v1/teacher/**")
                .excludePathPatterns("/api/v1/teacher/auth/login", "/api/v1/teacher/auth/session",
                        "/api/v1/teacher/auth/forgot-password", "/api/v1/teacher/auth/verify-otp",
                        "/api/v1/teacher/auth/resend-otp", "/api/v1/teacher/auth/reset-password");

        // PART 11B-1 - centralized role check. Runs last (registration order), after whichever
        // interceptor above authenticated the caller and published them via CurrentUserContext.
        // Applies only to handlers explicitly annotated with @RequireRole; everything else keeps
        // relying on the path-based interceptors above exactly as before.
        registry.addInterceptor(roleAuthorizationInterceptor)
                .addPathPatterns("/**");

        // PART 6C-2A/8 - course videos are served from the SAME uploads directory as ordinary
        // public files (gallery images, documents, etc.), so they cannot be locked down by
        // moving them elsewhere without a larger migration. Instead, only the videos
        // sub-path is blocked from direct static access; everything else under /uploads/**
        // (see addResourceHandlers below) is completely unaffected and stays publicly
        // linkable exactly as it is today.
        registry.addInterceptor(videoDirectAccessInterceptor)
                .addPathPatterns(publicPath + "/" + VIDEO_UPLOAD_FOLDER + "/**",
                        publicPath + "/" + AUDIO_UPLOAD_FOLDER + "/**");
    }

    /** Must match FileStorageServiceImpl's VIDEO_FOLDER - where lesson videos are stored. */
    private static final String VIDEO_UPLOAD_FOLDER = "videos";
        private static final String AUDIO_UPLOAD_FOLDER = "audio";

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.public-path:/uploads}")
    private String publicPath;

    /** Serves uploaded files straight from disk, e.g. /uploads/gallery/2026-01-01-ab12cd34.png */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(publicPath + "/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
