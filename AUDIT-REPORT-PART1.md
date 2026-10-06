# VITC Project — Part 1/12 Audit Report

No code was rebuilt, redesigned, or removed. This is a read-only inspection.
Findings below, keyed to the 14 points requested.

## 1. Admin authentication
`Admin` entity + `AdminAuthInterceptor`. Custom opaque session tokens (not
Spring Security / JWT): login issues `sessionToken` + `sessionExpiresAt` on
the `Admin` row. Every admin request sends `X-Admin-Username` +
`X-Admin-Token`; the interceptor validates them against the DB and rejects
expired/mismatched sessions. Passwords hashed via `PasswordEncoderConfig`
(BCrypt, `spring-security-crypto`). Default admin auto-seeded by
`AdminSeeder` from `app.admin.default-*` properties.

## 2. Student authentication
Same pattern, separate table: `User` entity with `role=STUDENT`,
`studentLoginId`, `sessionToken`, `sessionExpiresAt`. Guarded by
`StudentAuthInterceptor` on `X-Student-Id` + `X-Student-Token`
(login endpoint excluded). Resolved student id is placed on the request as
an attribute — the client can never pass its own studentId to access data.

## 3. User/role architecture
Two separate identity tables, not one polymorphic table:
- `Admin` (+ `AdminRole` enum) — staff/back office.
- `User` (+ `UserRole`, `UserStatus` enums) — public users; `STUDENT` role
  is the only one currently issued via checkout provisioning.
Both interceptors are wired in `WebConfig` against distinct path prefixes
(`/api/v1/admin/**` vs `/api/v1/student/**`), so the two auth systems never
overlap.

## 4. Course entities
`Course` → `CourseModule` → `CourseLesson` (video URL + duration), plus
`PricingPlan`, `Faq`, `Review`, `Testimonial` as related content. Lesson
`videoUrl` is explicitly documented as protected — only ever serialized by
the student lesson endpoint after enrolment is verified.

## 5. Enrollment system
`Enrollment` entity linking `User` ↔ `Course`, with `EnrollmentStatus`
(PENDING/CONFIRMED/ACTIVE/…​). `StudentLessonProgress` tracks per-lesson
completion. `EnrollmentService`/`EnrollmentController` cover manual/admin
enrolment; `StudentAccountServiceImpl.linkEnrollment(...)` auto-creates or
activates the enrolment when a paid order settles.

## 6. Payment system
`PaymentOrder`/`Payment`/`Invoice` entities, `CheckoutService` orchestrating
create → pay → confirm → cancel, `AssignmentOrder` for assignment purchases.
Fully documented in the existing README's "Payment Module" section and
`RAZORPAY-INTEGRATION.md`.

## 7. Razorpay verification
`payment/gateway/` package: `PaymentGateway` interface, `PaymentGatewayRouter`
picks between `MockPaymentGateway` and `RazorpayPaymentGateway` based on
`payment.gateway` (mock by default). Razorpay key/secret are **not** in
properties — they're entered via Admin → Payment Settings and stored
AES‑256‑GCM encrypted (`CryptoService`, `app.security.encryption-key`) in
`payment_gateway_settings`, decrypted only server-side for signature
verification in `RazorpayClient`.

## 8. Student ID generation
`StudentAccountServiceImpl.nextStudentLoginId()` — sequential, collision
-checked: `VITCSTU10001`, `VITCSTU10002`, ... via
`userRepository.findMaxStudentLoginSequence()` + an `existsBy...` guard loop.

## 9. Password generation
`StudentAccountServiceImpl.generatePassword()` — `SecureRandom` over a
36‑char unambiguous alphabet (no `0/O/1/I`), prefixed `VITC@`, 6 random
chars, e.g. `VITC@7K3PQR`. Stored only as a BCrypt hash; the plaintext is
returned once (for the email) and never persisted or logged.

## 10. Existing email service
Already implemented (this is the "part3-email" drop). `EmailServiceImpl`
wraps `JavaMailSender`, no-ops gracefully when `MAIL_HOST` is unset so
payments never fail because of email. `StudentCredentialEmailServiceImpl` +
`StudentEmailTemplates` send two templates (new account w/ credentials vs.
"course added" for existing students) after a **verified** payment only,
from both the browser-confirm and webhook paths, with `email_delivery_logs`
(`dedupe_key` unique) preventing duplicate sends. See
`EMAIL-CREDENTIALS.md` for full behaviour.

## 11. SMTP configuration
`application.properties` reads `spring.mail.*` and `app.mail.*` entirely
from environment variables (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`,
`MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_FROM_NAME`, `MAIL_SUPPORT_EMAIL`,
`STUDENT_LOGIN_URL`) — none hard-coded. Documented in `.env.example`.

## 12. Course modules/lessons/videos
Confirmed 3-level structure `Course → CourseModule → CourseLesson`, admin
CRUD via `AdminCourseContentController`/`AdminCourseContentService`, student
read access via `StudentLearningController`/`StudentLearningService` which
checks active enrolment before exposing `videoUrl` or lesson detail.

## 13. Footer
Shared across all public HTML pages (present in `index.html`, `about.html`,
`courses.html`, etc.) with contact info and nav links; not templated via a
backend include (static multi-page site), so any footer change is a
find-and-replace across pages — noted for later parts, not touched now.

## 14. `/api/v1` architecture
Single consistent base path. 28 controllers, all mapped under `/api/v1/**`
(see list below). No duplicate or conflicting mappings found.

```
/api/v1/health              /api/v1/admins                /api/v1/admin (course content)
/api/v1/admin/emails        /api/v1/admin/students         /api/v1/admin/payment-settings
/api/v1/assignments         /api/v1/assignment-orders      /api/v1/blog-posts
/api/v1/checkout            /api/v1/contact-messages       /api/v1/courses
/api/v1/enrollments         /api/v1/faqs                   /api/v1/files
/api/v1/gallery             /api/v1/internships            /api/v1/invoices
/api/v1/careers             /api/v1/payments               /api/v1/payment (gateway)
/api/v1/pricing             /api/v1/reviews                /api/v1/student (auth + learning)
/api/v1/testimonials        /api/v1/users
```

## Frontend ↔ backend consistency check
`assets/js/api.js`, `payments.js`, `student.js` all default to
`http://localhost:8080` (overridable via `window.VITC_API_BASE`) and send
the exact same `X-Student-Id` / `X-Student-Token` headers the backend
interceptor expects — confirmed wired correctly, not just parallel-built.

## Structural changes made in this part
**None.** The existing architecture (dual auth, `/api/v1` namespace, course/
enrollment/payment/email entities and services) is already suitable for
building on. No entities, services, repositories, or APIs were duplicated
or altered.

## One limitation to flag
This sandbox's outbound network is restricted to package registries
(Maven Central / `repo.maven.apache.org` is not reachable here), so I could
not run `mvn clean install` inside this environment to prove a green
compile. Everything was verified by static inspection instead (interceptor
wiring, entity relationships, controller mappings, frontend↔backend header
contracts, property references). Please run `mvn clean install` /
`mvn spring-boot:run` on your own machine (see Run instructions below) —
if anything fails there, send me the error and I'll fix it in the next part.

## How to run what's in this zip
1. **Database**: create `vitc_db` in MySQL 8 (or let
   `createDatabaseIfNotExist=true` do it), then optionally load
   `database/vitc_db_fresh.sql` / `database/seed_data.sql`.
2. **Backend**: `cd backend && cp .env.example .env` (fill in DB/mail/
   Razorpay values as needed), then `mvn clean install && mvn spring-boot:run`.
   Runs on `http://localhost:8080`.
3. **Frontend**: open `index.html` directly, or serve the repo root with any
   static server (e.g. `npx serve .` or VS Code Live Server on port 5500 —
   the frontend's default API base and CORS allow-list already expect 5500).
4. **Admin login**: `admin` / `Admin@123` (change immediately — seeded from
   `app.admin.default-*`).
