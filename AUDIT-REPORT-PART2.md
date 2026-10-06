# VITC Project — Part 2/12 Fix Report

## Root cause of "customer does not receive email"

`backend/.env.example` documents copying itself to `backend/.env` and
filling in `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, etc.
**Plain Spring Boot never reads a `.env` file** — only real OS/shell
environment variables. So unless someone had also manually exported those
values in their shell or hosting panel, `spring.mail.host=${MAIL_HOST:}`
always resolved to an empty string.

Tracing the flow confirmed this was the only broken link:

```
Buy Course → Checkout → Razorpay → Backend verification (gateway.verify) → order=PAID
  → confirmFulfilment → invoice issued → studentAccountService.provisionForPaidOrder
    (Student ID + temp password generated, enrolment linked/activated — all correct)
  → studentCredentialEmailService.sendForPaidOrder(order, credentials)
    → EmailServiceImpl.isConfigured() == false  (MAIL_HOST never actually loaded)
    → sendHtml(...) throws BadRequestException("Email is not configured...")
    → caught inside sendForPaidOrder's try/catch (by design, so a mail
      outage can never break a verified payment) → logged, nothing sent,
      nothing visible to the customer or the checkout response.
```

Every step up to and including "Credential Email Sent" in the required
diagram was already implemented and already wired in the right order in
both payment-confirmation paths (`confirmPayment` for the browser return,
`settleVerifiedWebhookPayment` for the Razorpay webhook). The email step
was simply never actually reachable in practice because its configuration
never loaded.

## Fix 1 — make `.env` actually load (the real fix)

Added `com.vitc.config.DotenvEnvironmentPostProcessor`, registered via
`src/main/resources/META-INF/spring.factories`. It reads `backend/.env`
(checked in a couple of likely working directories) before
`application.properties` placeholders resolve, and only fills values that
aren't already set by a real OS environment variable (so production
deployments that already export `MAIL_HOST` etc. are unaffected — `.env` is
purely a local-development convenience, exactly as `.env.example` implied).

No new library dependency was added; no existing service, entity, or API
was touched by this fix.

**Action needed on your machine:** `cd backend && cp .env.example .env`
and fill in real SMTP values (e.g. a Gmail address + App Password), then
start the backend as usual. Nothing else changes.

## Fix 2 — incorrect id used in `confirmFulfilment` (course branch)

`CheckoutServiceImpl.confirmFulfilment(order)` was calling
`enrollmentRepository.findById(order.getItemRefId())` for `COURSE` orders.
But `order.getItemRefId()` is the **Course id**, not an Enrollment id (see
`java-course.js`: `refId: course.dbId` → `StudentAccountServiceImpl
.resolveCourse()`, which correctly looks it up as a Course). So this branch
was silently doing nothing useful — either finding no enrollment (safe,
inert) or, in an unlucky case, finding an unrelated enrollment whose
numeric id happened to match the course id and wrongly marking it
`CONFIRMED`.

The real enrolment activation for a course purchase already happens
correctly right after, in `StudentAccountServiceImpl.linkEnrollment()`
(finds-or-creates by `user + course`, sets `ACTIVE`), so the incorrect
branch was also redundant. Removed it; the assignment-order branch
(`ASSIGNMENT` orders, which *do* store their own order id in `itemRefId`)
is untouched.

## What was verified as already correct (no change needed)

- **No duplicate student accounts**: `provisionForPaidOrder` looks up the
  user by email first; only creates a new `User` row if none exists.
- **No duplicate enrollments**: `linkEnrollment` looks for an existing
  enrolment by `(user, course)` then by `(email, course)` before creating
  one.
- **No unnecessary password overwrite**: a temporary password is only
  generated for a brand-new account. An existing student buying another
  course keeps their current password — they get a "course added" email
  with no password in it.
- **Backend-only trust**: the email is triggered only after
  `gateway.verify(...)` succeeds server-side and the order is persisted as
  `PAID` — never from anything the frontend claims. Confirmed for both the
  browser-return path and the Razorpay webhook path.
- **Correct recipient**: `order.getEmail()` (the email captured at checkout)
  is what's used — not a frontend-supplied value at confirmation time.
- **Duplicate-send protection**: `email_delivery_logs.dedupe_key` (unique)
  already prevents a webhook replay or double confirmation from sending the
  same email twice.
- **Razorpay implementation**: untouched — the bug was never there.

## Reaching the email-sending method — now observable

Added one log line in each confirmation path, right where the flow reaches
the email step, e.g.:
```
Order VITC-AB12CD34 verified and PAID - reached credential email step (studentId=VITCSTU10007)
```
followed by `StudentCredentialEmailServiceImpl`'s own `SENT`/`FAILED` log
line. Combined with the existing Admin → Emails screen (backed by
`AdminEmailController` / `email_delivery_logs`), you can now confirm from
both the server log and the admin UI that the flow reaches this step and
see exactly why a send failed if it ever does again (e.g. wrong SMTP
password) — and retry it from there without a new purchase.

## Files touched this part

- `backend/src/main/java/com/vitc/config/DotenvEnvironmentPostProcessor.java` (new)
- `backend/src/main/resources/META-INF/spring.factories` (new)
- `backend/src/main/java/com/vitc/service/impl/CheckoutServiceImpl.java` (edited: 2 log lines, `confirmFulfilment` course-branch fix, 1 unused import removed)
- `EMAIL-CREDENTIALS.md` (documentation updated)

Nothing else in the project was modified.

## Same build-verification limitation as Part 1

This sandbox cannot reach Maven Central, so `mvn clean install` could not be
run here. The two edited/added Java files were checked for balanced
braces/parens and reviewed line-by-line against the existing surrounding
code and imports. Please run `mvn clean install` locally; if anything
surfaces, send me the error in the next part.
