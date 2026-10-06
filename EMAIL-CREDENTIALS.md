# Automatic Student Credential Email (after verified Razorpay payment)

Added on top of the existing project. Nothing in the checkout, Razorpay
verification, student portal or admin panel behaviour was changed — only a new
email step was appended after a payment is verified.

## When the email is sent

`CheckoutServiceImpl` sends it **only after**:

1. Razorpay signature / payment status verification succeeded, and
2. the order is marked `PAID`, the invoice is issued, and
3. `StudentAccountService.provisionForPaidOrder(order)` created (or reused) the
   student account.

Both verified paths are covered:

* `confirmPayment(...)` — the normal browser return flow
* `settleVerifiedWebhookPayment(...)` — the Razorpay webhook flow

Failed, pending or unverified payments never send an email.

## Two templates

| Situation | Email |
|---|---|
| Brand-new student account | Student Login ID + temporary password + course + login steps |
| Existing student buys another course | "Course added" email, **no password is sent** |

Both include the purchased course, order code, amount, and a button to the
**Student Admin Login** page.

## Security

* No SMTP credentials in code — everything comes from environment variables.
* The temporary password is **never stored** anywhere (only its BCrypt hash on
  the user row) and is never written to logs or to the delivery log table.
* Sending is wrapped so that a mail failure can never fail or roll back a
  verified payment.
* Duplicate protection: `email_delivery_logs.dedupe_key` is unique, so webhook
  replays or a double confirmation cannot send the same email twice.

## Configuration

Copy `backend/.env.example` and set:

```
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-address@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM=your-address@gmail.com
MAIL_FROM_NAME=VITC - Vertex IT Career
MAIL_SUPPORT_EMAIL=support@vitc.in
STUDENT_LOGIN_URL=https://your-domain.com/student-login.html
```

Leaving `MAIL_HOST` empty disables sending — the rest of the app keeps working
and each skipped attempt is recorded as `FAILED` with a clear reason.

## Fixed in Part 2/12: `.env` was never actually loaded

Plain Spring Boot does **not** read a `.env` file by itself — only real OS
environment variables. Copying `.env.example` to `backend/.env` and filling
in `MAIL_HOST`/`MAIL_USERNAME`/etc. therefore had no effect unless those
values were also exported in the shell (or set in the hosting provider's
environment panel). `MAIL_HOST` stayed empty, `EmailService.isConfigured()`
returned `false`, and the credential email was silently skipped — the
payment, student account, and enrolment all still succeeded, so nothing
looked broken except the missing email.

`com.vitc.config.DotenvEnvironmentPostProcessor` now loads `backend/.env`
(if present) into Spring's environment before `application.properties`
placeholders are resolved, so `.env.example` → `.env` works exactly as
documented. Real OS/server environment variables still always win over
`.env` file values — this only fills in what isn't already set.

**To receive the email:** `cd backend && cp .env.example .env`, fill in a
real `MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD`/`MAIL_FROM` (e.g. a Gmail
App Password), then start the backend. Admin → Emails shows every
attempt (`SENT`/`FAILED` + reason) and lets you retry a failed one without
a new purchase.

## Admin panel

New page **Students → Credential Emails** (`admin/emails.html`):

* delivery status per student (`PENDING` / `SENT` / `FAILED`), failure reason,
  retry count, timestamps
* **Retry** on any failed email
* **Send test email** to verify SMTP settings

Retrying a credentials email issues a **new** temporary password (the original
is not stored), sets "must change password" and mails the new one.

API (all guarded by the existing admin session interceptor):

```
GET  /api/v1/admin/emails/status
GET  /api/v1/admin/emails/logs
GET  /api/v1/admin/emails/logs/student/{userId}
POST /api/v1/admin/emails/logs/{id}/retry
POST /api/v1/admin/emails/test        { "to": "you@example.com" }
```

## Database

One new table, created automatically by Hibernate (`ddl-auto=update`):
`email_delivery_logs`. No existing table was modified.

## Run

```
cd backend
mvn spring-boot:run          # backend on :8080
# serve the site root with any static server, e.g.
python -m http.server 5500
```
