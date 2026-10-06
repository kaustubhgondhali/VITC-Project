# AUDIT REPORT — PART 10B/12: TEACHER SECURITY RESTRICTIONS

Strict, backend-enforced separation between the **Main Admin** role and the **Teacher** role.
Nothing in this part relies on hidden buttons, hidden menu items or frontend route guards.

## 1. Role model (unchanged, now fully enforced)

```
MAIN_ADMIN  (Admin table, X-Admin-Username + X-Admin-Token)   -> full administration
TEACHER     (User.role = TEACHER, X-Teacher-* session)        -> assigned courses only
STUDENT     (User.role = STUDENT, X-Student-* session)         -> own enrolled courses
```

The three session stores are independent: a Teacher token is not valid for any admin API, and an
admin token is not valid for the Teacher APIs.

## 2. What a Teacher can never reach (verified, backend)

| Surface | Endpoints | Result for a valid Teacher session |
|---|---|---|
| Razorpay keys / secrets / payment settings | `/api/v1/admin/payment-settings/**` | 403 Forbidden |
| SMTP settings + passwords, email logs | `/api/v1/admin/smtp-settings/**`, `/api/v1/admin/emails/**` | 403 Forbidden |
| Main Admin student administration | `/api/v1/admin/students/**` | 403 Forbidden |
| Main Admin course-content administration | `/api/v1/admin/courses/**`, `/api/v1/admin/modules/**`, `/api/v1/admin/lessons/**` | 403 Forbidden |
| Admin management + admin credentials | `/api/v1/admins/**` | 403 Forbidden |
| User / account administration | `/api/v1/users/**` | 403 Forbidden |
| Order management | `/api/v1/assignment-orders/**` | 403 Forbidden |
| Payments, refunds, payment status changes | `/api/v1/payments/**` | 403 Forbidden |
| Enrollment administration | `GET/PUT/PATCH/DELETE /api/v1/enrollments/**` | 403 Forbidden |
| Invoice administration / listing | `/api/v1/invoices`, `/api/v1/invoices/{id}` | 403 Forbidden |
| Main Admin dashboard statistics | `/api/v1/admins/dashboard/stats` | 403 Forbidden |

## 3. How it is enforced

**`security/AdminOnlyApiInterceptor.java` (new)**
Guards the Main Admin surfaces that do not live under the `/api/v1/admin/**` prefix
(`/api/v1/admins`, `/api/v1/users`, `/api/v1/payments`, `/api/v1/assignment-orders`,
`/api/v1/enrollments`, `/api/v1/invoices`).

* If the caller presents a Teacher or Student session header, the request is rejected immediately
  with **403 Forbidden** — authenticated, but not authorised.
* Otherwise a valid Main Admin session (`X-Admin-Username` + `X-Admin-Token`, unexpired, active
  admin) is required.
* A method + path allow-list keeps the public flows open and unchanged:
  `POST /api/v1/admins/login|session|logout|forgot-password|reset-password`,
  `POST /api/v1/enrollments` (visitor enrolment),
  `GET /api/v1/invoices/order/**` and `GET /api/v1/invoices/number/**` (customer invoice lookup).
  Note the allow-list is method aware: `POST /api/v1/enrollments` is public while
  `GET /api/v1/enrollments` (the admin list) is admin-only.

**`security/AdminAuthInterceptor.java` (hardened)**
`/api/v1/admin/**` now distinguishes *unauthenticated* from *unauthorised*: a request carrying a
Teacher or Student session token receives **403 Forbidden** instead of a 401 prompt. Anonymous
requests still receive 401 and all previous checks (active admin, matching token, unexpired
session) are unchanged.

**`config/WebConfig.java`**
Registers the new interceptor next to the existing admin, student and teacher interceptors. No
existing rule was removed or relaxed.

**`security/TeacherAuthInterceptor.java` (PART 10A, unchanged)**
Still requires an ACTIVE `TEACHER` account and resolves the teacher id server-side; per-course
ownership is checked in `TeacherAuthorizationService` on every request.

## 4. Sensitive data protection

No response reachable by a Teacher (or anyone else) contains a secret:

* `PaymentSettingsResponse` — exposes `keyId`, `maskedKeySecret`, `webhookSecretConfigured`;
  the raw Razorpay key secret and webhook secret are never serialised.
* `SmtpSettingsResponse` — exposes `passwordConfigured` only; the SMTP password / app password is
  never serialised.
* `GatewayStatusResponse` (public storefront) — gateway name, mode, live flag only.
* `UserResponse`, `AdminResponse`, `TeacherProfileResponse` — no password hashes.
* Database credentials and other server-side secrets live in environment / `.env` values read by
  `DotenvEnvironmentPostProcessor` and are never returned by any API.

## 5. Tests

`backend/src/test/java/com/vitc/TeacherAdminSeparationTest.java` (new, 7 tests):

1. Teacher -> payment settings / SMTP settings / email logs = 403.
2. Teacher -> orders, payments, refund status changes, invoices, enrollments = 403.
3. Teacher -> admin management (list, create, password change, delete, stats) and user
   administration = 403.
4. Teacher -> Main Admin student and course-content APIs = 403.
5. Teacher-readable responses contain no `keySecret`, `webhookSecret`, `smtpPassword`,
   `passwordHash`.
6. Even for the Main Admin, payment settings and SMTP settings never return raw secrets.
7. Regression: Main Admin APIs still return 200, the public gateway status and visitor enrolment
   still work, and the Teacher can still manage the course assigned to them.

Existing suites (`AdminManagementSecurityTest`, `TeacherApiAuthorizationTest`,
`TeacherCourseSecurityTest`, `PaymentSettingsIntegrationTest`, `SmtpSettingsIntegrationTest`,
`PaymentToEmailFlowTest`, `StudentLearningSecurityTest`, teacher module/lesson/video tests) are
untouched.

## 6. How to run

```bash
cd backend
mvn clean spring-boot:run       # backend on http://localhost:8080
mvn test                        # full test suite
```

Serve the static site from the project root (any static server), e.g.:

```bash
python3 -m http.server 5500
```

* Public site: `http://localhost:5500/index.html`
* Main Admin: `http://localhost:5500/admin/index.html`
* Teacher Admin: `http://localhost:5500/teacher-login.html`
* Student portal: `http://localhost:5500/student-login.html`

Nothing changed in the database schema, so the existing `database/vitc_db_fresh.sql` +
`database/seed_data.sql` still apply.
