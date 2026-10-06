# VITC Website — PART 7B/8: Public Website, Payment, Database & Build Regression

Same constraint as Part 7A: no Maven Central access in this sandbox (confirmed
again this pass — Maven isn't even installed here), no database server, no
browser. Everything below is either (a) a concrete static check I actually
ran and can show you the command/output for, or (b) explicitly marked NOT
TESTED. Nothing is claimed as "everything works" without the check that
backs it up, per the brief's rule.

---

## PUBLIC WEBSITE

**Static checks actually run:**

- Confirmed every public page (`index.html`, `courses.html`, `assignments.html`,
  `testimonials.html`, `blog.html`, `gallery.html`, `about.html`, `contact.html`,
  `payment.html`) loads only `assets/css/style.css` — none of them link
  `student-portal.css`, `teacher.css`, or `admin.css`.
- Scanned `style.css` for all Student/Teacher-related selectors (113 blocks)
  and confirmed none of them are unscoped generic selectors — they're all
  either prefixed `.student-*`/`.footer-student-link`/`.footer-teacher-link`,
  or scoped under `#studentDashboard`/`#studentCoursePage`/`#studentProfilePage`.
  Then checked whether any public page's HTML actually contains a class name
  that collides with one of those selectors (e.g. `.student-course-card`,
  `.course-progress-box`, `.profile-main`) — zero matches found across all 9
  public pages.
- Scanned every `.html` file in the project for `<script src="...">` and
  `<link href="...">` pointing at local `.js`/`.css` files, and resolved
  each path against the actual filesystem — **0 broken references** out of
  every script/stylesheet include in the whole site.
- Same check for local `<img src="...">` references — **0 missing images**.

**Result: PASSED** for what a static check can verify (no dashboard CSS/JS
leak path exists, no broken local asset references anywhere in the site).

**NOT TESTED**: actually loading each public page in a browser and visually
confirming it's pixel-identical to before, and confirming Contact/Payment
forms submit correctly — that needs a live server + browser, which isn't
available here.

---

## PAYMENT

**Static trace of the actual code path** (not assumed — read line by line):

`CheckoutServiceImpl`:
1. `createOrder()` — creates the `PaymentOrder` row.
2. `initiatePayment()` — creates a `Payment` row and calls the configured
   gateway (`PaymentGatewayRouter` → `RazorpayPaymentGateway` or
   `MockPaymentGateway` depending on settings) to get a provider order id.
3. `confirmPayment()` — this is the one that matters most:
   - Looks up the `Payment` by `providerOrderId` and confirms it belongs to
     the order in the URL (rejects mismatches).
   - **Idempotency**: if the payment is already `SUCCESS`, returns the
     existing confirmation instead of re-running anything — a double-submit
     or page refresh can't double-fulfil an order.
   - Calls `gateway.verify(...)` — this is where the Razorpay signature is
     actually checked server-side, not trusted from the client.
   - Only if verification succeeds: marks payment `SUCCESS`, marks order
     `PAID`, **then and only then** calls `confirmFulfilment()` (enrollment),
     issues an invoice, and provisions the student account.
   - Email is sent last, and the code comment states explicitly it's
     wrapped so "email problems cannot affect the payment" — confirmed
     `sendForPaidOrder` is called after the transactional payment/enrollment
     work, not interleaved with it.
4. `settleVerifiedWebhookPayment()` — a second, independent path for
   Razorpay's server-to-server webhook, following the identical
   verify-then-fulfil-then-email order. This is what protects against a
   user closing their browser tab right after paying but before the
   confirm-page call fires.

**Result: PASSED** as a static trace — the ordering (verify → mark paid →
fulfil → email, with an idempotency guard) is the correct pattern for a
payment flow, no shortcuts found. Nothing was modified.

**NOT TESTED**: an actual Razorpay test-mode transaction, actual email
delivery, actual enrollment row appearing in a live database — none of
these can be exercised without a running backend + real/sandbox Razorpay
keys + SMTP config, none of which exist in this sandbox.

---

## DATABASE

This is where a real, concrete issue turned up — not a duplication problem
(the brief's main concern), but the opposite: **a gap**.

**What was checked**: compared every `@Entity` class in
`backend/src/main/java/com/vitc/entity/` against every `CREATE TABLE` in
`database/vitc_db_fresh.sql` and all 4 migration files
(`student_portal_upgrade.sql`, `student_profile_upgrade_part6.sql`,
`smtp_settings_migration.sql`, `email_delivery_logs_migration.sql`).

**Finding**: `vitc_db_fresh.sql` and `DATABASE_DESIGN.md` both only
document the original 19 tables from before the Student/Teacher portal work
(`users`, `courses`, `enrollments`, `payments`, etc.). Three entities that
the Teacher/Student portal code actively uses in production — `CourseModule`
(`course_modules`), `CourseLesson` (`course_lessons`), and
`StudentLessonProgress` (`student_lesson_progress`) — have **no
corresponding `CREATE TABLE` anywhere in `database/`**. Same for
`PaymentGatewaySetting` (`payment_gateway_settings`) — referenced by
comment in `smtp_settings_migration.sql` as an existing pattern to mirror,
but its own migration script isn't in this folder either.

**Why this hasn't broken anything so far**: `application.properties`
(default/dev profile) sets `spring.jpa.hibernate.ddl-auto=update`, so
Hibernate silently creates these tables from the entity annotations the
first time the backend starts against a fresh dev database. That's why
local development has been working.

**Why it matters anyway**: `application-prod.properties` sets
`spring.jpa.hibernate.ddl-auto=validate` — in that profile, Hibernate
expects the schema to already exist and match exactly; it does **not**
create tables. If `vitc_db_fresh.sql` + the 4 migration files are the only
thing ever run against a production database, **the backend will fail to
start in prod** with a schema validation error the first time it hits
`course_modules`/`course_lessons`/`student_lesson_progress`/
`payment_gateway_settings`.

I did not write a new migration file to fix this myself — generating the
exact `CREATE TABLE` DDL (column types, indexes, foreign keys) that matches
Hibernate's expectations for 4 entities is real schema-authoring work I'd
rather hand you a clearly-scoped task for, rather than guess at column
definitions that could be subtly wrong in a way that's hard to catch
without a real database to test the migration against. Happy to draft it
in the next message if you want it — just say so.

**Result: FAILED** (as a discovered issue, not as "the database has
unnecessary duplication" — that part checked out fine: no duplicate course
system, no duplicate video system, no duplicate dashboard data tables were
found; the problem is a missing migration, not an extra one).

**NOT TESTED**: whether existing seed/demo data in a real database is
actually still readable — that needs a live DB, which isn't available here.

---

## BACKEND REGRESSION

**NOT TESTED.** Maven isn't installed in this sandbox (confirmed by
attempting to run it), and even if it were, this sandbox's network
allow-list doesn't include Maven Central, so dependencies couldn't be
downloaded. The backend cannot start here, so "backend starts successfully",
every API category, and runtime authentication/authorization behavior
are all NOT TESTED in the execution sense.

What *was* verified (code-level, covered in detail in
`AUDIT-REPORT-PART-7A-8-STUDENT-TEACHER-SECURITY-CODE-AUDIT.md`): the
authorization chain for Student and Teacher APIs is structurally sound
when read as code. That is not the same as confirming it at runtime.

---

## BUILD TEST

**Backend compilation: NOT TESTED** — see above, `mvn` unavailable.

**Frontend "build"**: this project has no bundler/build step (static
HTML/CSS/JS), so "build" here means: does every file parse and does every
reference resolve. Concretely ran:
- `node --check` against all 11 JS files (`assets/js/*.js`,
  `teacher-admin/assets/teacher.js`, `admin/assets/admin.js`) — **all pass,
  0 syntax errors**.
- Full site-wide scan for broken local script/CSS/image references (see
  Public Website section above) — **0 broken references**.

**Result: PASSED** for the frontend (no bundler exists, so this is the
complete "build" check available; syntax + reference integrity both clean).
**NOT TESTED** for the backend.

---

## FINAL REPORT

### STUDENT TESTING
**NOT TESTED** — requires a live backend + browser. Code-level tracing was
done in Part 7A (see the linked audit) but that is explicitly not a
substitute for execution.

### TEACHER TESTING
**NOT TESTED** — same reason.

### AUTHORIZATION TESTING
**NOT TESTED** (execution) — the authorization *design* was traced through
the actual controller/service code in Part 7A and found consistent
everywhere it was checked, but per the brief's own rule that isn't a test
result. Use `TEST-SCRIPT-PART-7A-8-STUDENT-TEACHER-SECURITY.md` to get real
PASS/FAIL here.

### PUBLIC WEBSITE
**PASSED** (static: no CSS/JS leak path, zero broken asset references).
Visual/functional confirmation in a real browser is NOT TESTED.

### PAYMENT
**PASSED** (static trace: correct verify-then-fulfil-then-email order,
idempotency guard present, webhook path mirrors the same order). Actual
transaction execution is NOT TESTED.

### DATABASE
**FAILED** — `course_modules`, `course_lessons`, `student_lesson_progress`,
and `payment_gateway_settings` tables are used by entity code but have no
migration script in `database/`. Will break backend startup under
`ddl-auto=validate` (production profile). See details above.

### BACKEND BUILD
**NOT TESTED** — Maven unavailable in this sandbox, no Maven Central
network access.

### FRONTEND BUILD
**PASSED** — all JS syntax-checked clean, zero broken local asset
references anywhere in the site (no bundler exists for this static
frontend, so this is the full applicable check).

### REMAINING ISSUES
1. **Missing production migration** for `course_modules`, `course_lessons`,
   `student_lesson_progress`, `payment_gateway_settings` — real risk if
   deployed with `ddl-auto=validate`. I can draft this migration file next
   if you want it.
2. Backend build/startup, live API behavior, and all Student/Teacher/
   Authorization/Payment execution results are still unverified — they
   need to be run against a real instance. Use the test script from Part
   7A for the click-through/authorization checks; for the backend build,
   run `mvn clean install` locally and let me know what comes back if
   anything fails.
3. Carried over from Part 1-2: 10 unused reviewer/profile images
   (~70KB) still present, not deleted.
