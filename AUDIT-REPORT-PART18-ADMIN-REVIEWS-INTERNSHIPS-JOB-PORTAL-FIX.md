# AUDIT REPORT — PART 18: Admin Panel Reviews / Internships / Job Portal Fix

## Reported symptoms
1. A student-submitted review never appears in the Main Admin panel (`admin/reviews.html`).
2. A student's internship application never appears in the Main Admin panel (`admin/internships.html`).
3. Adding a Job Requirement from `admin/job-portal.html` silently fails to save.

## Root cause 1 & 2 — missing authentication wiring for two admin surfaces

`ReviewController` and `InternshipApplicationController` mark their admin-only
handlers (list all, get by id, update, approve, delete, filter by status, etc.)
with `@RequireRole(Role.MAIN_ADMIN)`. That annotation is enforced by
`RoleAuthorizationInterceptor`, which only checks *who the caller is* — it
relies on an earlier interceptor to have already authenticated the request and
published the caller via `CurrentUserContext`.

In `WebConfig`, the interceptor that performs that authentication
(`AdminOnlyApiInterceptor`) was registered for `/api/v1/admins/**`,
`/api/v1/users/**`, `/api/v1/payments/**`, `/api/v1/admin/jobs/**`, etc. —
but **not** for `/api/v1/reviews/**` or `/api/v1/internships/**`. So when the
Main Admin panel called, e.g., `GET /api/v1/reviews`, no interceptor ever set
`CurrentUserContext`, and `RoleAuthorizationInterceptor` saw a `null` caller
and rejected the request with `403 Forbidden` — every time, for every admin,
regardless of login state. The public "submit a review" / "apply for an
internship" endpoints have no `@RequireRole` annotation, so those kept working
normally, which is why students could submit but admins could never see the
results.

`JobApplicationController` (careers/job applications) had the identical gap
and has been fixed the same way for consistency, even though no admin page
currently lists it.

### Fix
- `backend/src/main/java/com/vitc/config/WebConfig.java` — added
  `/api/v1/reviews/**`, `/api/v1/internships/**`, and `/api/v1/careers/**` to
  `AdminOnlyApiInterceptor`'s path patterns.
- `backend/src/main/java/com/vitc/security/AdminOnlyApiInterceptor.java` —
  added the genuinely public routes on those paths to the allow-list so they
  keep working without a login: `POST /api/v1/reviews`,
  `GET /api/v1/reviews/page`, `GET /api/v1/reviews/approved`,
  `GET /api/v1/reviews/featured`, `GET /api/v1/reviews/course/**`,
  `POST /api/v1/internships`, `POST /api/v1/careers`.

Every other route under those three controllers (list-all, get-by-id, update,
approve/reject, delete, status filters, status updates) now correctly
requires — and accepts — a valid Main Admin session, matching what the
`@RequireRole(Role.MAIN_ADMIN)` annotations already declared.

## Root cause 3 — blank "Application Deadline" breaks Job Requirement save

`admin/job-portal.html` builds the Create/Edit Job Requirement payload
straight from `FormData`. The `openings` field was already special-cased to
convert an empty string to `null`, but `applicationDeadline` (an HTML
`<input type="date">`) was not: leaving it blank sent `"applicationDeadline":
""` to the backend. `JobRequirementRequest.applicationDeadline` is a
`java.time.LocalDate`, and Jackson cannot parse an empty string into a date —
the request failed validation and the job was never created, with no
indication to the admin beyond "didn't save."

### Fix
- `admin/job-portal.html` — `applicationDeadline` is now normalized to `null`
  when left blank, the same way `openings` already was.

## Files changed
- `backend/src/main/java/com/vitc/config/WebConfig.java`
- `backend/src/main/java/com/vitc/security/AdminOnlyApiInterceptor.java`
- `admin/job-portal.html`

No other file, endpoint, or piece of functionality was touched.

## How to verify after rebuilding
1. Submit a review from `reviews.html` → log into `admin/index.html` → open
   `admin/reviews.html` → the new review appears (pending approval).
2. Submit an application from `internship.html` → open
   `admin/internships.html` → the new application appears.
3. Open `admin/job-portal.html` → "+ New Job Requirement" → fill in the
   required fields, leave Application Deadline blank → Save → the job appears
   in the list and can be published.
