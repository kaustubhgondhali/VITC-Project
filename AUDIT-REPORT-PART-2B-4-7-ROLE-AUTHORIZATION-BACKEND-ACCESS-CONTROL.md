# AUDIT REPORT — PART 2B-4/7: VITC SECURITY HARDENING — ROLE AUTHORIZATION & BACKEND ACCESS CONTROL

Scope implemented: server-side role authorization and backend access-control only. No
authentication architecture changes, no endpoint renaming, no redesign of the public website, no
audit logging.

## 0. Important scope note — the role model has 3 roles, not 4

The prompt for this part describes four roles (Student, Teacher, Student Admin, Main Admin). This
codebase's actual authorization model (`security/Role.java`, built in PART 11B-1) has **three**:
`MAIN_ADMIN`, `TEACHER`, `STUDENT`. There is no "Student Admin" account type anywhere in the
entity model (`Admin` uses `AdminRole { SUPER_ADMIN, ADMIN, EDITOR, SUPPORT }` — all of which are
Main Admin panel roles; `User` uses `UserRole { TEACHER, STUDENT }`). Section 4 of the prompt
("Student Admin Authorization") does not apply to this build and nothing was invented to satisfy
it — adding a role that doesn't exist in the data model would be exactly the kind of architecture
change this part is told not to make. Sections 1, 2, 3, 5–9 apply directly and are covered below.

## 1. Inspection performed first

Read every authorization-relevant class and every controller before changing anything:

- `security/Role.java`, `RequireRole.java`, `RoleAuthorizationInterceptor.java`,
  `CurrentUser.java`, `CurrentUserContext.java`, `CurrentUserContextFilter.java`
- `security/AdminAuthInterceptor.java`, `AdminOnlyApiInterceptor.java`, `TeacherAuthInterceptor.java`,
  `StudentAuthInterceptor.java`, `config/WebConfig.java` (interceptor registration + path scope)
- All 38 controllers in `controller/`, checked for `@RequireRole` coverage and whether each one
  sits under a path already guarded by `WebConfig`'s interceptors
- Existing authorization tests (`RoleBasedAuthorizationTest`, `TeacherAdminSeparationTest`,
  `TeacherCrossRoleSecurityAuditTest`, `StudentTeacherApiProtectionTest`,
  `TeacherApiAuthorizationTest`, `AdminManagementSecurityTest`, `StudentLearningSecurityTest`,
  `StudentProfileSecurityTest`, `TeacherCourseSecurityTest`, `TeacherCourseLevelAuthorizationTest`)
- Every admin-panel and teacher-admin JS file, and `assets/js/api.js` / `assets/js/site.js` /
  `assets/js/student.js`, to confirm which endpoints each role's frontend actually calls and
  whether it sends the matching session headers — this determines what can be locked down without
  breaking a real, currently-working flow.

**Finding — Main Admin, Teacher, and Student areas are already solidly separated.** Every path
under `/api/v1/admin/**`, `/api/v1/admins/**`, `/api/v1/users/**`, `/api/v1/payments/**`,
`/api/v1/enrollments/**`, `/api/v1/assignment-orders/**`, `/api/v1/invoices/**`,
`/api/v1/teacher/**`, and `/api/v1/student/**` is already guarded server-side by an interceptor
that resolves the caller from a database-verified session token (never from a client-supplied
header value) and rejects the rest with 401/403. Teacher-vs-Student-vs-Main-Admin cross-role
attempts are already covered by `RoleBasedAuthorizationTest` and `TeacherAdminSeparationTest`, and
course-level IDOR (a Teacher reaching another teacher's course/students) is already covered by
`TeacherCourseSecurityTest` / `TeacherCourseLevelAuthorizationTest` via
`TeacherAuthorizationService`.

**Finding — a real gap: 12 public-content controllers had *no* backend authorization on their
write operations.** `CourseController`, `AssignmentController`, `FaqController`,
`GalleryItemController`, `PricingController`, `ReviewController`, `TestimonialController`,
`BlogPostController`, `ContactMessageController`, `InternshipApplicationController`,
`JobApplicationController`, and `FileController` sit outside every interceptor's path prefix
(they're mapped to `/api/v1/courses`, `/api/v1/faqs`, etc., not `/api/v1/admin/**`). Their
**read** endpoints are meant to be public (that's how the storefront pages get course/FAQ/pricing
data), but their **write** endpoints (`POST`/`PUT`/`PATCH`/`DELETE`) had nothing checking who was
calling — only the admin panel hiding the "Add/Edit/Delete" buttons stood between an anonymous
caller and, e.g., deleting every course, rewriting pricing plans, listing every uploaded file
(including student ID photos / résumés), or reading every contact-form submission and its
sender's email/phone. This is precisely the "access based only on frontend restrictions" failure
mode this part exists to close.

## 2. Files changed

Backend — added `@RequireRole(Role.MAIN_ADMIN)` to the write/management endpoints below, using the
same centralized annotation + `RoleAuthorizationInterceptor` mechanism PART 11B-1 already built
(no new enforcement machinery was written; this part only applies the existing one where it was
missing):

- `backend/src/main/java/com/vitc/controller/CourseController.java` — `create`, `update`, `delete`
- `backend/src/main/java/com/vitc/controller/AssignmentController.java` — `create`, `update`, `delete`
- `backend/src/main/java/com/vitc/controller/GalleryItemController.java` — `create`, `update`, `delete`
- `backend/src/main/java/com/vitc/controller/PricingController.java` — `create`, `update`,
  `updateActive`, `delete`
- `backend/src/main/java/com/vitc/controller/FaqController.java` — `create`, `update`,
  `updateActive`, `delete`
- `backend/src/main/java/com/vitc/controller/BlogPostController.java` — `getAll`, `getById`,
  `create`, `update`, `delete` (no admin UI consumes the "all posts" list today, and it can include
  unpublished drafts, so the list/detail views are locked down along with the writes)
- `backend/src/main/java/com/vitc/controller/TestimonialController.java` — `getAll`, `getById`,
  `create`, `update`, `delete`, `approval` (same reasoning — `/approved` stays public)
- `backend/src/main/java/com/vitc/controller/ReviewController.java` — `getAll` ("admin view" per
  its own `@Operation` summary), `getById`, `update`, `approval`, `delete`, `byEmail` (reviewer
  email lookup). `POST` (submit a review) and `/approved`, `/featured`, `/course/{code}`,
  `/course/{code}/average-rating`, `/page` are untouched — the public reviews page depends on them.
- `backend/src/main/java/com/vitc/controller/ContactMessageController.java` — `getAll`, `getById`,
  `update`, `delete`, `unhandled`, `markHandled`. `POST` (the public "Contact us" form) is untouched.
- `backend/src/main/java/com/vitc/controller/InternshipApplicationController.java` — `getAll`,
  `getById`, `update`, `delete`, `byStatus`, `updateStatus`. `POST` (the public application form)
  is untouched.
- `backend/src/main/java/com/vitc/controller/JobApplicationController.java` — same pattern as
  InternshipApplicationController; `POST` untouched.
- `backend/src/main/java/com/vitc/controller/FileController.java` — `getAll`, `getById`, `delete`,
  `deleteMany`. `POST` (single) and `POST /bulk` (upload) are **left public on purpose** — they are
  genuinely called without a session today by three different legitimate flows: the public
  internship/job-application résumé attachment (`assets/js/site.js`), the student avatar upload
  (`assets/js/student.js`), and the admin media picker (`admin/assets/admin.js`, which already
  attaches `X-Admin-*` headers). Locking `POST` down would break the two public-facing ones.
  Upload content-type/size safety is handled separately by the existing
  `security/upload/UploadFileValidator.java` / `FileContentInspector.java` and is out of this
  part's scope.

Frontend — one line fixed, not redesigned:

- `assets/js/student.js` (`uploadProfileImage`) — the function already computed the student's
  `X-Student-Id` / `X-Student-Token` headers (`var h = authHeaders();`) and even checked they were
  present, but never attached them to the actual `fetch()` call, so the avatar upload was silently
  going out unauthenticated. The headers are now sent (`fetch(..., { method: "POST", headers: h,
  body: form })`). This restores the behavior the code already intended; it doesn't change what
  the feature does.

## 3. New files

- `backend/src/test/java/com/vitc/PublicContentAdminWriteAuthorizationTest.java` — see §7.
- This report.

## 4. Authorization improvements

- Every mutating endpoint on the 11 public-content controllers now requires a valid `MAIN_ADMIN`
  session, enforced by the backend (`RoleAuthorizationInterceptor`), not by the admin panel hiding
  a button.
- `FileController`'s listing/detail/delete endpoints — previously fully open, meaning anyone could
  enumerate every uploaded file (student avatars, job/internship résumés, gallery/blog images) or
  delete any of them by id — now require `MAIN_ADMIN`.
- The four genuinely public submission endpoints (review, contact message, job application,
  internship application) and the file-upload endpoints stay open, matching how the live site
  actually uses them — visitors have no session to present.

## 5. Role-separation improvements

No change to the existing Main Admin / Teacher / Student separation — it was already backend-
enforced and already tested (see §1). This part only extended the *same* mechanism to the
public-content controllers that had never been wired into it.

## 6. Backend access-control improvements

- Closed 12 controllers' worth of unauthenticated write access (create/update/delete/status-patch)
  that previously depended entirely on the admin panel's UI not exposing a button.
- Closed an unauthenticated full-listing/delete surface on the shared file store.
- Fixed a real bug (not a new control) where the student avatar upload was going out with no
  session headers at all, despite the client code already having them on hand.

## 7. Endpoint compatibility verification

Traced every frontend caller of each changed endpoint before changing it (not just the admin nav):

| Controller | Frontend caller(s) found | Headers already sent? | Effect of this change |
|---|---|---|---|
| Course / Assignment / Gallery / Pricing / FAQ writes | `admin/*.html` via `Admin.crudPage` → `admin.js request()` | Yes — `X-Admin-Username`/`X-Admin-Token` attached automatically | None — still works |
| Review `POST` | `assets/js/api.js: submitReview` (public reviews form) | N/A — public | Untouched, still public |
| Review admin ops / `byEmail` | none found | — | Was already effectively unused by any UI; now backend-enforced |
| Testimonial / BlogPost writes+lists | none found (no admin UI page exists for either today) | — | Was already effectively unused by any UI; now backend-enforced |
| ContactMessage / Internship / JobApplication `POST` | `assets/js/api.js: contact / applyInternship / applyJob` (public forms) | N/A — public | Untouched, still public |
| ContactMessage / JobApplication admin ops | no admin UI page exists for either | — | Now backend-enforced |
| InternshipApplication admin ops | `admin/internships.html` via `Admin.crudPage` | Yes | None — still works |
| Files `POST` / `POST /bulk` | `assets/js/site.js` (public résumé upload), `assets/js/student.js` (avatar upload), `admin/assets/admin.js` | Public ones: none by design. Admin: yes. Student: **was missing, now fixed** | Uploads keep working for all three; student flow is now actually authenticated as intended |
| Files list/detail/delete | none found | — | Was fully open to anyone; now backend-enforced |

No endpoint was renamed, no HTTP method changed, no response shape changed.

## 8. Authorization tests performed

Manual trace (above) plus a new automated test class,
`PublicContentAdminWriteAuthorizationTest`, following the existing `RoleBasedAuthorizationTest`
pattern:

- Anonymous caller → newly-protected write/list endpoints (courses, gallery, faqs, pricing,
  assignments, blog posts, testimonials, reviews list, contact messages, internships, careers,
  files) = **Forbidden**
- Authenticated Teacher session → same endpoints = **Forbidden** (a Teacher is authenticated but
  not authorized here — proves this isn't just "no session" being rejected)
- Authenticated Main Admin session → same endpoints = **OK**, permissions unchanged
- Public read endpoints (`/courses`, `/courses/active`, `/faqs/active`, `/gallery`,
  `/pricing/active`, `/reviews/approved`, `/testimonials/approved`, `/blog-posts/published`) with
  no session at all = **OK**, unaffected
- Public submission endpoint (`POST /reviews`) with no session = reaches validation, not blocked
  by authorization

Existing suites re-read for conflicts: `AuthenticationCoreSystemTest` and `SecurityHeadersTest`
only assert `GET /api/v1/courses` (untouched) is `200` — no conflict with this change.

`mvn test` could not be executed in this environment (no network access to Maven Central to
resolve dependencies, no local `~/.m2` cache present, no `javac`/build toolchain installed here).
Every edited file was checked by hand for brace balance, import correctness, and annotation
placement; run `mvn test` (or `RUN-WINDOWS.cmd` for the Maven wrapper) on your machine before
deploying to confirm the new test class passes — see §9.

## 9. Student/Teacher/Main Admin access matrix (post-fix)

| Endpoint group | Student | Teacher | Main Admin |
|---|---|---|---|
| `/api/v1/admin/**`, `/api/v1/admins/**`, `/api/v1/users/**`, `/api/v1/payments/**`, `/api/v1/invoices/**` (list/manage) | 403 | 403 | Allowed |
| `/api/v1/teacher/**` | 403 | Allowed (own courses/students only) | 403 (not Main Admin surface) |
| `/api/v1/student/**` | Allowed (own data only) | 403 | 403 |
| Course/Assignment/Gallery/Pricing/FAQ **writes** | 403 | 403 | Allowed |
| BlogPost/Testimonial (list + writes), Review (admin ops) | 403 | 403 | Allowed |
| ContactMessage/Internship/JobApplication (admin ops) | 403 | 403 | Allowed |
| Files: list/detail/delete | 403 | 403 | Allowed |
| Files: upload, public forms (review/contact/internship/careers submit), public content reads | Public — no session required for any role |||
| Unauthenticated → any Main-Admin-only or Teacher/Student session route | 401 | | |
| Unauthenticated → newly `@RequireRole`-only content routes above | 403 (no separate session gate exists on these paths, so `RoleAuthorizationInterceptor`'s own null-caller check answers — consistent with how every other `@RequireRole` endpoint in this codebase already behaves) | | |
| Expired or invalid token on any session route | 401 (admin/teacher) / 401 (student) — unchanged, verified in PART 2B-3 | | |

## 10. Remaining authorization risks

- **`FileController` uploads are still open by design.** This is a deliberate, pre-existing
  product decision (public résumé/avatar uploads have no session to present), not something this
  authorization-only part can close without breaking those flows. If tightened later, the right
  fix is a short-lived, purpose-scoped upload token issued by the specific public flow (mirroring
  how `StudentVideoStreamController` already does signed, scope-limited tokens for video), not a
  blanket role check. File-type/size validation for this endpoint already exists separately in
  `security/upload/`.
- **`BlogPostController` / `TestimonialController` have no admin UI today.** Their write endpoints
  are now backend-authorized, but if an admin management page is built for either later, confirm
  it goes through `admin.js`'s shared `request()` helper (which already attaches admin headers) so
  it keeps working — the same pattern every other `admin/*.html` page already uses.
- **No "Student Admin" role exists to test against**, per §0 — if a fourth role is intentionally
  planned for a future part, it needs to be added to `Role.java`/`AdminRole.java` first; that is an
  architecture change and was correctly out of scope here.
- This part did not add audit logging, per the prompt's own instruction — every 403/401 above is
  enforced but not yet recorded anywhere.
