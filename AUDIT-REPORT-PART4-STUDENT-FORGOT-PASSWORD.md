# AUDIT REPORT — PART 4: STUDENT LOGIN FORGOT PASSWORD

## 0. Scope

Adds a self-service "Forgot Password" flow to the Student Login only, per
`PART 4/6 — STUDENT LOGIN FORGOT PASSWORD`. Continues from PART 3
(`VITC-Website-PART3-6-STUDENT-CHANGE-PASSWORD-TAB.zip`).

## 1. What already existed (inspected before writing anything)

| Portal | Forgot-password UI | API | OTP/token store | Email delivery |
|---|---|---|---|---|
| Main Admin | Yes (`admin/index.html`, card-switching) | `POST /api/v1/admins/forgot-password` / `reset-password` | `admins.reset_token` / `reset_token_expires_at` | **No** — code is returned directly in the API response |
| Teacher Admin | **No self-service flow exists** | — | — | — |
| Student (before this part) | Fake — a phone number / email line, not a real flow | — | — | — |

The project also already has a real, working SMTP layer (`EmailService` /
`EmailServiceImpl`, admin-configurable in Main Admin → Email/SMTP Settings)
that is already used to send student credential emails
(`StudentCredentialEmailService`). Its own `student.js` comments explicitly
say the temporary password "is never shown on screen... it only ever goes to
the student's inbox."

**Decision:** reuse the Main Admin *pattern* (single-use, time-limited,
server-issued code; card-switching UI) but reuse the project's *own, more
secure, already-established convention* of emailing the code via the
existing `EmailService` rather than returning it in the API response like
the older Admin flow does. This satisfies "follow the existing secure
implementation" without copying the one part of the Admin flow that the
project itself has since moved away from.

## 2. What was added (Student portal only)

**Backend**
- `entity/User.java` — added `resetToken` / `resetTokenExpiresAt` (same
  shape as `Admin.resetToken` / `resetTokenExpiresAt`, just on the shared
  `users` table).
- `repository/UserRepository.java` — added
  `findByResetTokenAndRole(String, UserRole)`, scoped to a role so a code
  issued for a student can never resolve to a teacher account sharing the
  same `users` table (or vice versa).
- `dto/request/StudentForgotPasswordRequest.java`,
  `dto/request/StudentResetPasswordRequest.java`,
  `dto/response/StudentForgotPasswordResponse.java` — new, student-specific
  (never reuses the Admin DTOs, so validation messages and Swagger docs stay
  correct per role). The response never carries the code.
- `service/StudentAccountService.java` /
  `service/impl/StudentAccountServiceImpl.java` — `forgotPassword(...)` and
  `resetPassword(...)`:
  - looks the account up by Student ID **or** email, filtered to
    `role == STUDENT` — a match on a Teacher/Admin row is never returned;
  - issues an 8-character unambiguous alphanumeric code (no `0/O/1/I`),
    20-minute expiry, single use;
  - emails it via the existing `EmailService` (fails loudly to the caller if
    SMTP isn't configured — this is a self-service flow, so, unlike the
    fire-and-forget credentials email, it must not silently pretend to
    succeed);
  - on reset: validates the code, checks expiry, requires
    `newPassword == confirmPassword`, BCrypt-encodes the new password,
    clears the reset code, and clears the student's active session token so
    every device is forced to sign in again — mirrors the Main Admin reset
    exactly.
- `service/impl/StudentEmailTemplates.java` — added `resetCode(...)`, same
  inline-styled HTML template family as the existing credentials email.
- `controller/StudentController.java` — added
  `POST /api/v1/student/auth/forgot-password` and
  `POST /api/v1/student/auth/reset-password`.
- `config/WebConfig.java` — excluded both new endpoints from
  `StudentAuthInterceptor` (same reasoning as the existing `/auth/login`
  exclusion: a student who lost their password cannot present a session).
- `database/student_forgot_password_upgrade_part4.sql` — new, additive-only
  migration (`ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token ...`),
  following the exact style of `student_portal_upgrade.sql` and
  `student_profile_upgrade_part6.sql`. **Run this once** against an existing
  database (see §4) — `spring.jpa.hibernate.ddl-auto=validate` in prod means
  the schema must be migrated by hand, same as every earlier student-portal
  upgrade.

**Frontend**
- `student-login.html` — replaced the fake phone-number line with a real
  `Forgot password?` link and two additional cards (request code / set new
  password), reusing the exact same `login-wrap` / `login-card` / `field` /
  `pw-wrap` / `pw-toggle` / `btn` classes already shared by Admin, Teacher
  and Student login pages.
- `assets/js/student.js` — added `StudentAuth.forgotPassword(...)` /
  `StudentAuth.resetPassword(...)`, and the card-switching + form-submit
  wiring for the two new cards (same pattern as `admin/index.html`'s inline
  script, but placed in `student.js` alongside the rest of the student auth
  logic, consistent with the file's own existing comment that UI toggles —
  not auth logic — belong inline on the page).

## 3. Role safety

- Lookup is filtered to `role == STUDENT` on both the "request code" and
  "use code" steps (`findByResetTokenAndRole(code, UserRole.STUDENT)`), so a
  code can never be generated for, or redeemed against, a Main Admin or
  Teacher account, even though Teacher and Student share the same `users`
  table.
- Main Admin (`admins` table) is a completely separate entity/table/service
  — untouched.
- The two new endpoints are the only ones excluded from
  `StudentAuthInterceptor`; every other student endpoint keeps requiring a
  valid session exactly as before.

## 4. How to run this

1. Apply the new migration once (after the earlier student-portal scripts):
   ```
   mysql -u root -proot vitc_db < database/student_forgot_password_upgrade_part4.sql
   ```
2. Make sure SMTP is configured (Main Admin → Email / SMTP Settings, or the
   `MAIL_*` environment variables) — the forgot-password email will not send
   without it, and the API will return a clear error saying so rather than
   failing silently.
3. Start the backend as usual: `cd backend && mvn spring-boot:run`.
4. Open `student-login.html`, click **Forgot password?**, enter a Student ID
   or email, check the inbox for the code, then set a new password.

## 5. Regression / isolation check

Only the 12 files listed in §2 were touched (3 new backend files, 1 new SQL
migration, 6 edited backend files, 2 edited frontend files). Nothing under
`admin/`, `teacher-login.html`, `AdminController`/`AdminServiceImpl`,
`TeacherController`, any `*AuthInterceptor` other than the path list in
`WebConfig`, or any public marketing page was modified. Main Admin and
Teacher Admin login/reset behavior is unchanged.

## 6. Environment disclosure

Same constraint as prior PART audits in this project: this sandbox has no
network access to Maven Central and no cached `~/.m2` repository, so
`mvn compile` / `mvn test` could not be run here. Every new/edited Java file
was checked for brace/paren balance and manually reviewed line-by-line
against the existing, already-compiling code it mirrors (imports, method
signatures, DTO shapes, `ApiResponse` usage). `assets/js/student.js` passed
`node --check`. Please run `mvn -o compile` (or a full `mvn spring-boot:run`)
in your own environment before deploying, same as recommended for every
earlier part of this project.
