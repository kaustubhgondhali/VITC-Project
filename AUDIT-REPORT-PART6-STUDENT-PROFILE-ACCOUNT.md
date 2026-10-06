# AUDIT REPORT — PART 6: Student Profile & Account

## Scope
Student Profile page: view profile, edit profile (name/phone/city), profile
picture, password change (reusing existing endpoint), backend authorization,
navigation, responsive layout. No public-site redesign, no auth architecture
changes, no Teacher or Certificate features.

## What was added

### Backend (Spring Boot)
- `entity/User.java` — new nullable `profile_image_url` column. No existing
  column touched.
- `database/student_profile_upgrade_part6.sql` — additive migration only
  (`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`), safe to run after
  `student_portal_upgrade.sql`.
- `dto/request/StudentProfileUpdateRequest.java` — fullName (required),
  phone, city. Deliberately excludes studentLoginId, role, email, status.
- `dto/request/StudentProfileImageUpdateRequest.java` — imageUrl only; no
  binary upload logic duplicated here.
- `dto/response/StudentProfileResponse.java` — added `profileImageUrl`.
- `service/StudentAccountService.java` / `service/impl/StudentAccountServiceImpl.java`
  — added `updateProfile()` and `updateProfileImage()`. Both resolve the
  account strictly from the `studentId` passed in by
  `StudentAuthInterceptor` (server-side session), never from the request
  body. Neither method writes to `studentLoginId`, `role`, `status`, or any
  enrollment.
- `controller/StudentController.java` — new endpoints:
  - `PUT /api/v1/student/profile` (edit name/phone/city)
  - `PUT /api/v1/student/profile/image` (attach an already-uploaded image
    URL)
  Both sit under the existing `/api/v1/student/**` path already guarded by
  `StudentAuthInterceptor` in `WebConfig` — no interceptor changes needed.
- Reused the existing generic `POST /api/v1/files` upload endpoint
  (`FileStorageService`) for the actual image bytes — no second upload
  pipeline, no third-party storage service.
- `StudentProfileSecurityTest.java` (new) — 7 tests: owner view/edit,
  validation rejects invalid input, protected fields can never be set via
  the edit payload, a second student's session cannot read or modify the
  first student's account, unauthenticated access is rejected on every
  profile route, an invalid/expired token is rejected, profile-image update
  is scoped to the caller only.

### Frontend
- `student-profile.html` (new) — profile photo + upload, read-only account
  info (Student ID, email, last login), edit-profile form, change-password
  form. Same header/footer/hero structure as the other student pages; no
  public pages touched.
- `assets/js/student.js` — added `updateProfile()`, `updateProfileImage()`,
  `uploadProfileImage()` (calls the existing `/api/v1/files` endpoint) to
  `VITCStudent`, plus the page controller for `student-profile.html` and a
  shared `#subNavLogout` handler.
- `assets/css/style.css` — added `.student-subnav` (shared nav bar) and
  `.profile-*` classes for the new page; reused existing card/message/form
  classes (`.student-auth-card`, `.student-msg`, `.student-cred-grid`,
  `.field`) instead of introducing a parallel style system.
- `student-dashboard.html`, `student-course.html` — added the same
  `.student-subnav` bar (Dashboard / My Courses / Continue Learning /
  Profile / Logout) so all three logged-in student pages share one
  navigation component, as required.

## Security
- Every new/edited endpoint sits under `/api/v1/student/**`, already
  intercepted by `StudentAuthInterceptor`; a request without a valid,
  unexpired session token for an ACTIVE STUDENT is rejected with 401/403
  before it reaches the controller.
- The student id used for every read/write always comes from
  `@RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE)`, i.e. the
  server-resolved session — it is never accepted from the request body or a
  path variable, so one student can never edit another student's profile.
- `StudentProfileUpdateRequest` has no `id`, `role`, or `studentLoginId`
  field, so even a maliciously crafted JSON body with those keys present is
  simply ignored by Jackson binding — verified by
  `protectedFieldsAreNeverAccepted()`.
- Profile picture upload reuses the existing `FileStorageService` validation
  (file-type allow-list, folder sanitisation, path traversal guard) — no new
  file-handling logic was written.

## Manual test checklist (see also StudentProfileSecurityTest.java)
- [x] View profile (own data only)
- [x] Edit profile (name/phone/city) with success message
- [x] Validation: blank name, malformed phone rejected with clear message
- [x] Password change: current password required, new/confirm must match,
      min length enforced (reuses `POST /api/v1/student/auth/change-password`
      unchanged)
- [x] Unauthorized access (no/expired/invalid token) rejected on every route
- [x] Cross-student access rejected (own session can only ever see/edit own
      account)
- [x] Mobile layout: profile grid collapses to a single column under 900px
      (see `.profile-grid` media query); sub-nav wraps on narrow screens
- [x] Logout (both the dashboard's existing button and the new shared
      sub-nav button)

## Explicitly out of scope for this part
- Teacher-facing features
- Certificates
- Any change to the public marketing pages or the authentication
  architecture (login flow, session model, interceptors) beyond adding two
  new endpoints under the existing student route prefix
