# AUDIT REPORT — PART 12C/12: PAYMENT, ENROLLMENT & STUDENT TESTING

Scope: TEST 6 (student login → dashboard → course → videos), TEST 7 (existing student buys
another course), TEST 12 (duplicate payment/callback idempotency), TEST 13 (email failure
must not affect payment). Same environment limits as PART 12B apply — see §0 there; this
part is again a **static, line-by-line trace**, not a live run, for the same reason (no
network, no Maven in this sandbox).

**Finding: no bugs found. No files changed.** Every behaviour asked for in this part was
already implemented correctly by the existing code.

## TEST 6 — Student login after purchase → dashboard → course → videos

Traced the full chain, backend and frontend:

- `student-login.html` → `POST /api/v1/student/auth/login` (`StudentAccountServiceImpl.login`)
  — looks the account up **by Student Login ID**, checks `role == STUDENT`,
  `status == ACTIVE`, and `passwordEncoder.matches(...)` against the same BCrypt hash the
  purchase flow created. This is the exact ID + temporary password the credentials email
  contains (PART 12B, §3). **Verified (static).**
- `student.js` stores the returned session token and `student-dashboard.html` calls
  `GET /api/v1/student/courses` → `StudentLearningService.myCourses(studentId)`, which reads
  from the student's own `Enrollment` rows only (`studentId` comes from the server-verified
  session attribute, never a browser-supplied id) — so the dashboard can only ever show
  courses this student actually owns.
- Clicking a course opens `student-course.html?courseId=...` → `GET
  /api/v1/student/courses/{courseId}` and `.../modules`, both of which **re-check enrolment
  server-side and return 403 if the course id isn't one the student owns** — the course id in
  the URL is just a hint, not a trust boundary.
- Opening a lesson → `GET /api/v1/student/lessons/{lessonId}` returns the video URL only if
  the lesson's course is one of that student's active enrolments (same 403-if-not-enrolled
  rule); `student-learning.js` then renders it as a native `<video>` or an `<iframe>`.

Net result: Student ID + temp password from the actual email → dashboard → the specific
purchased course → its videos, with server-side authorization at every hop, not just a
UI redirect. **Verified (static).**

## TEST 7 — Existing student purchases a second course

`StudentAccountServiceImpl.provisionForPaidOrder`:

- Looks the buyer up by **email** first. If a `User` already exists, it is reused —
  `newAccount` stays `false`, no new `User` row, and (crucially) **the existing password is
  left untouched**, so the student keeps using the same credentials for every course they buy.
- `linkEnrollment` looks for an enrolment for *this specific course*
  (`findFirstByUserIdAndCourseId`). Not found → creates a **new** `Enrollment` row for the new
  course only; it never touches, replaces, or removes the student's prior enrolments.
- `StudentCredentialEmailServiceImpl.sendForPaidOrder`: when `newAccount` is `false`, it sends
  the `COURSE_ADDED` template ("your new course is unlocked... sign in with your existing
  Student Login ID and your current password — it has not changed") instead of the
  `STUDENT_CREDENTIALS` one, so no second temporary password is ever issued to an existing
  student.
- `StudentAccountServiceImpl.toProfile` builds the dashboard course list from
  `enrollmentRepository.findByUserIdOrderByIdDesc(user.getId())` — **all** enrolments for that
  user, so both the old and the newly purchased course appear, and the old course's access
  (§ TEST 6 authorization check) is untouched by the new purchase.

No duplicate account, new enrolment only for the new course, old course access unaffected.
**Verified (static).**

## TEST 12 — Duplicate payment / callback replay

`CheckoutServiceImpl.confirmPayment`:
```
if (payment.getStatus() == PaymentStatus.SUCCESS) {
    return buildConfirmation(order, payment);   // <-- short-circuits here
}
```
A second `confirm` call for a payment that's already `SUCCESS` (browser retry, page refresh,
double-tap, or the Razorpay webhook arriving after the browser already confirmed) returns the
existing confirmation **without** re-verifying, re-provisioning, or re-emailing — the same
`Payment`/`PaymentOrder` rows are reused, nothing new is inserted.

`settleVerifiedWebhookPayment` has the equivalent guard (`if (payment.getStatus() ==
PaymentStatus.SUCCESS) return; // already settled - replay safe`) for the webhook path, and
calls into the same `provisionForPaidOrder` / `sendForPaidOrder`, which are themselves
idempotent (§ TEST 7's find-or-create enrolment, and PART 12B's `dedupeKey` unique constraint
on the email log).

`GET /api/v1/checkout/orders/{orderCode}/confirm` (the page-refresh / "get confirmation" path
used by `payment-success.html` / `order-summary.html`) is a **pure read** — it does not touch
the database at all, so refreshing that page can never create anything.

This exact scenario (verified confirm → duplicate confirm → assert exactly one user, one
enrolment, one email-log row; separately, webhook-after-browser-confirm → assert no new rows
of any kind) is precisely what the existing `PaymentToEmailFlowTest` (`PART 6`, referenced in
PART 12B) already asserts. **Verified (static).**

## TEST 13 — Email failure must not affect payment

Already exercised end-to-end by `PaymentToEmailFlowTest.
paidOrder_activatesStudentAndEnrollment_andRecordsEmailOutcome_evenWhenSmtpFails`, which runs
with SMTP unconfigured and asserts, in one flow:

| State | Verified value |
|---|---|
| Payment | `SUCCESS` |
| Order | `PAID` |
| Student (`User.status`) | `ACTIVE` |
| Enrollment | `ACTIVE` |
| Email delivery log | `FAILED` (never left `PENDING`, never silently dropped) |

This falls directly out of `CheckoutServiceImpl.confirmPayment`'s ordering: payment/order are
committed to `PAID` **before** `studentCredentialEmailService.sendForPaidOrder(...)` is even
called, and that call is wrapped in a try/catch that only logs — it cannot throw back up into
the transaction, so a mail exception has no way to roll back the payment, account or
enrolment. **Verified (static)** — matches the code already reviewed line-by-line in PART 12B
§3 and confirmed again here in the transaction-ordering sense TEST 13 specifically asks about.

**Live confirmation still recommended:** the same 5b steps from the PART 12B report (break the
SMTP password, do one real/test-mode purchase, confirm the payment/enrolment/login all still
work while Main Admin → Emails shows that one row as `FAILED`) exercise this exact scenario
against your running instance.

## Final Student Flow — Payment → Order → Student → Enrollment → Login → Course → Content

Every link in this chain was traced individually above (TEST 6/7/12/13) and none of them was
found to depend on, or be broken by, any other part — payment success is determined solely by
Razorpay signature verification, student/enrolment state is determined solely by that verified
payment, and course-content access is determined solely by the student's own enrolment rows,
re-checked server-side on every request. Nothing was changed to make this true; it was already
built this way.

## Status

TEST 6, 7, 12 and 13 all check out at the code level. Nothing needed fixing in this part.

Waiting for PART 12D before finalizing the project zip.
