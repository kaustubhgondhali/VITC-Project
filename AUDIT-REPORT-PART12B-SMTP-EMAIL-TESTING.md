# AUDIT REPORT — PART 12B/12: SMTP & COURSE-PURCHASE EMAIL TESTING

Scope: TEST 5 (SMTP test email + SMTP failure handling) and TEST 6 (course-purchase
credential email), verified without adding features, redesigning anything, or removing
existing functionality.

## 0. Environment disclosure (read this first)

This sandbox has **no internet access** and **no cached Maven dependencies / no `mvn`
binary**. That means, from here, I could not and did not:

- connect to any real SMTP server (Gmail, Mailtrap, SendGrid, your production mailbox, etc.),
- connect to Razorpay,
- compile or run the backend or its existing test suite (`SmtpSettingsIntegrationTest`,
  `PaymentToEmailFlowTest`),
- open any actual inbox.

Anything below marked **Verified (static)** was confirmed by reading the exact code path
line by line, tracing it from controller → service → JavaMailSender / Razorpay client.
Anything marked **Needs your live run** is something only you can confirm, because it
requires a real SMTP account, a real inbox, and/or a real Razorpay test key — I've given
you the exact steps in §4 so it takes a few minutes.

I will not claim "email received" or "payment verified" without that live confirmation —
doing so would be exactly the false-positive this PART explicitly forbids.

## 1. Files reviewed (no changes were needed)

`EmailServiceImpl`, `SmtpSettingsServiceImpl`, `SmtpSettingsController`,
`StudentCredentialEmailServiceImpl`, `StudentEmailTemplates`, `AdminEmailController`,
`CheckoutServiceImpl` (`confirmPayment` / `settleVerifiedWebhookPayment`),
`StudentAccountServiceImpl`, `admin/smtp-settings.html`, `admin/emails.html`, plus the
existing `SmtpSettingsIntegrationTest.java` and `PaymentToEmailFlowTest.java`.

**Finding: no bugs found.** This part of the codebase already does the right thing end to
end — see §2/§3. Nothing was changed.

## 2. TEST 5 — SMTP test email

- `POST /api/v1/admin/smtp-settings/test` → `SmtpSettingsServiceImpl.sendTestEmail()` →
  `EmailServiceImpl.sendHtml()`, which calls `JavaMailSenderImpl.send(message)`. This is a
  **synchronous, blocking call that performs the real SMTP handshake** (connect, STARTTLS/SSL,
  AUTH, `MAIL FROM`/`RCPT TO`/`DATA`) against whatever host/port/credentials are stored — it
  is not a fire-and-forget queue and does not return early. **Verified (static).**
- Success is only reported if that call returns without throwing — i.e. the destination mail
  server accepted the message for delivery. The controller only returns `200 OK` after
  `service.sendTestEmail(...)` completes without exception. **Verified (static).**
- The credentials/API-accepted/frontend-displayed pitfalls named in the brief don't apply
  here because there's no separate "accept" step — accepting the HTTP request and sending
  the mail are the same call. **Verified (static).**
- Frontend (`admin/smtp-settings.html`): the "Send Test Email" button awaits the API call;
  on the promise resolving it shows "Email sent successfully ✅", on rejection it shows
  "Email sending failed ❌ &lt;reason&gt;". Since the backend only resolves after a real
  accepted send, the UI cannot show success for a send that didn't happen. **Verified (static).**

### SMTP failure handling

- `EmailServiceImpl.sendHtml` catches `MailAuthenticationException` (bad username/password) and
  `MailSendException` (host unreachable / connection refused / TLS failure / rejected recipient)
  separately and converts each into a distinct, safe `BadRequestException` message — no SMTP
  password, host secrets, or raw stack trace in either message. Any other exception falls back to
  a generic "could not be sent" message, logged only as the exception's class name. **Verified
  (static) — see §5 for the credential-safety check.**
- `BadRequestException` → `GlobalExceptionHandler.handleBadRequest` → HTTP `400`, so the
  frontend's `catch` branch fires and displays the ❌ failure state. There is no code path where
  an SMTP rejection is swallowed and reported as success. **Verified (static).**

## 3. TEST 6 — Course purchase → credential email

Traced `CheckoutServiceImpl.confirmPayment` (browser confirm) and
`settleVerifiedWebhookPayment` (Razorpay webhook) — both follow the exact sequence the brief
requires:

1. `gateway.verify(...)` — Razorpay HMAC signature is checked against the real `keySecret`
   before anything else happens; a failed/forged signature stops here with the order marked
   `FAILED` and nothing further runs.
2. Only on verified success: `order.setStatus(PAID)`.
3. `studentAccountService.provisionForPaidOrder(order)` — creates the `User` (role `STUDENT`,
   `studentLoginId` like `VITCSTU10047`, a random temporary password hashed with BCrypt,
   `mustChangePassword=true`) or, if the email already has an account, adds the course to it
   without touching the existing password.
4. `linkEnrollment` — creates/activates the `Enrollment` row for the purchased course.
5. `studentCredentialEmailService.sendForPaidOrder(order, studentAccount)` — builds and sends
   the HTML email via the same synchronous `EmailServiceImpl.sendHtml` verified in §2, then
   records the outcome (`SENT`/`FAILED`) in `email_delivery_logs`.

**Verified (static), all of the following:**

- **Email content** — `StudentEmailTemplates.credentials(...)` includes the Student Login ID,
  the plaintext temporary password (this is the one and only place it's ever shown — it is
  never persisted anywhere in plaintext and never returned by any API response), the purchased
  course title, the order code/amount, and a "Go to Student Admin Login" button linking to
  `student-login.html`.
- **Credentials actually work** — the temporary password is BCrypt-encoded with
  `passwordEncoder.encode(...)` at creation and checked with the same
  `passwordEncoder.matches(...)` in `StudentAccountServiceImpl.login(...)` — same encoder, same
  value, so the emailed password is guaranteed to log the student in (and
  `mustChangePassword=true` forces them to set a new one on first login, matching the pattern
  already verified for Teacher accounts in PART 12A).
- **Order matches "Razorpay SUCCESS → Student Account → Enrollment → Credentials → SMTP →
  customer receives"** exactly, including the sequencing (account/enrolment only ever happen
  after verified success, never before).
- **A mail failure cannot roll back the payment** — `sendForPaidOrder` catches every exception
  internally and only logs it; the payment, account and enrolment are already committed by the
  time it runs, and the existing `PaymentToEmailFlowTest.paidOrder_activatesStudentAndEnrollment_
  andRecordsEmailOutcome_evenWhenSmtpFails` test proves this by running the whole flow with SMTP
  unconfigured and asserting the order/account/enrolment still succeed while the email log is
  correctly recorded `FAILED` (not `PENDING`, not silently dropped).
- **No duplicate emails** — a `dedupeKey` unique constraint means a page refresh re-posting the
  same confirm, or Razorpay's webhook arriving after the browser already confirmed, produces
  exactly one email attempt, proven by the same test's second half and by
  `duplicateWebhookAfterBrowserConfirm_doesNotDuplicateAnything`.

## 4. What you still need to do live (I can't do this from here)

Because of §0, please run these yourself against your running backend — each takes a minute:

**5a — SMTP test email**
1. Main Admin → Email/SMTP Settings → enter a real SMTP account (Gmail app password, or a
   free Mailtrap/Brevo sandbox if you don't want to use a live inbox yet) → Save.
2. Send Test Email to an address whose inbox you can open.
3. Confirm the email actually lands in that inbox (check spam too) and that the UI shows ✅.

**5b — SMTP failure**
1. Change the password field to something wrong → Save → Send Test Email → confirm the UI
   shows "Email sending failed ❌" with a message about authentication, not a false success.
2. Put back the correct password and re-send once to confirm it recovers.

**6 — Course purchase**
1. Use a real (or Razorpay test-mode) course purchase through `buy-course.html` →
   `payment.html` with a Razorpay test card.
2. After payment, check the buyer's inbox for the credentials email; confirm Student ID,
   temporary password, course name, and the login button are all correct.
3. Log in at `student-login.html` with those exact credentials and confirm it works and
   forces a password change.
4. Optionally check Main Admin → Emails to see the delivery logged as `SENT`.

## 5. Email/credential safety

- Neither the SMTP password nor the Razorpay Key Secret is ever returned by any admin API
  response (`SmtpSettingsResponse`/`PaymentSettingsResponse` only expose a masked placeholder
  and a `configured`/`passwordConfigured` boolean) — both are AES-GCM encrypted at rest and
  decrypted only in memory for the duration of one send/one API call.
- Searched every `log.info/warn/error/debug` call touching password/secret variables in the
  backend: the SMTP and Razorpay code paths never log the raw value, only safe fixed strings
  ("SMTP authentication failed...") or, where a value must be shown at all, the *masked*
  placeholder. **Verified (static).**
- One unrelated line, `AdminSeeder.java:59`, logs the auto-generated **default Main Admin**
  password once on first application boot (a common bootstrap pattern so you have something to
  log in with at all) — this is outside PART 12B's scope (it isn't SMTP or Razorpay) and was
  already present before this part; flagging it here only for completeness. Let me know if
  you'd like it removed/changed and I'll do that as its own small fix.

## 6. Status

TEST 5 and TEST 6 are correctly implemented at the code level — no changes were required.
Live inbox/live-payment confirmation (§4) is the one thing only you can do, since this
sandbox has no network access.

Waiting for PART 12C before finalizing the project zip.
