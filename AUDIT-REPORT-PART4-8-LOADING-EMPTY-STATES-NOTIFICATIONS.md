# PART 4/8 — Loading, Empty States & Notification System — Audit & Change Report

## 1. Audit findings (before any changes)

**Student portal** already had a mature feedback system, built in earlier parts:
- `assets/js/api.js` exposes `VITC.setState()` / `VITC.load()` (loading → fetch → render/empty/error,
  with a retry button on error) and `VITC.toast()`.
- `assets/css/style.css` already defines `.skeleton`, `.state`, `.spinner`, `.student-empty-state`,
  and `aria-live`/`aria-busy` wiring.
- `student-dashboard.html`, `student-course.html`, and `student-profile.html` already use skeleton
  placeholders on first load and icon-ready empty states.

**Teacher Admin portal** (`teacher-admin/*.html` + `assets/teacher.js`) was the real gap:
- Every list ("My Courses", "Students", "Modules", "Progress", "Profile fields") showed a bare
  `Loading…` text string instead of a skeleton.
- Empty and error states shared one plain `.empty` div with no icon, no distinct styling, and
  (except for `course-content.html`'s `load()`) no retry action on failure.
- Reorder/activate-deactivate buttons (module & lesson "↑ / ↓ / Activate / Deactivate") fired a
  request without disabling themselves first, so a fast double-click could double-submit.
- Toasts already existed (`Teacher.toast`) and were reused as-is — no new notification library
  was added.

**What does *not* exist in this codebase** (so nothing was built for it, to avoid promising a
feature with no backend behind it):
- A separate authenticated "Assignments" or "Resources" section for students/teachers.
  `assignments.html` / `assignment-details.html` are the **public marketplace**, not part of the
  logged-in student or teacher portal.
- "Certificates" and "Statistics" as dedicated pages or API endpoints.
If any of these are added in a later part, they should reuse the same `VITC.load` /
`Teacher.load` + skeleton + icon-empty-state pattern documented below.

## 2. What was added

### Teacher Admin (`teacher-admin/assets/teacher.css`, `teacher-admin/assets/teacher.js`)
Additive only — nothing in `admin/assets/admin.css` (shared with the Main Admin panel) was touched.
- CSS: `.skeleton` shimmer, `.t-skel-row/-card/-stat/-grid/-line/-select`, an icon-capable `.empty`
  (icon + title + message + optional action/retry button), a `.spinner` for inline/button use, and
  a pulsing `.value.is-loading` for dashboard stat tiles.
- JS, exposed on the existing `Teacher` object:
  - `Teacher.skeleton(host, opts)` — renders N skeleton rows/cards into a container.
  - `Teacher.emptyState(host, opts)` — icon + title + message, optional `retry` (wires a
    `[data-retry]` button) or `actionHtml` (only when the user actually has an action available).
  - `Teacher.load(host, fetcher, render, emptyOpts, errorOpts)` — the full
    skeleton → fetch → render/empty/retryable-error cycle, mirroring the student side's
    `VITC.load`.
  - `Teacher.withButtonLoading(btn, fn, label)` — disables a button, swaps in a spinner + label,
    runs the async action, and always restores the button, so Save/Upload/Submit/Update actions
    can never be double-submitted.

### Wired into every Teacher Admin page
- `index.html` (Dashboard): stat tiles pulse while loading and show a retryable error state;
  "My Courses" list uses `Teacher.load` with an icon empty state.
- `my-courses.html`: course grid uses `Teacher.load` with skeleton cards.
- `course-content.html`: course card, modules tree, and video panel all get skeletons; "no modules
  yet" gets an icon + an inline "+ Add Module" action; module/lesson Save buttons use
  `withButtonLoading`; every reorder/activate button disables its whole action group immediately
  on click and re-enables itself only if the request fails.
- `students.html`: student table uses a skeleton on first load and distinguishes "no students in
  any of your courses" from "no students match this course/search".
- `student-progress.html`: course/student selects and the lesson list all get skeleton + retryable
  error states.
- `profile.html`: profile fields get skeleton lines and a retryable error state.

### Student portal (light polish — infrastructure already existed)
- `assets/js/student.js`: added icons to the two "My Courses" empty states (enrolled / filtered),
  and a proper "Uploading…" status + disabled label while a profile photo is uploading.
- `assets/js/student-learning.js`: "no lessons yet" now uses the same icon empty-state markup as
  the rest of the student portal (was plain text); "Mark Complete" now shows a spinner + "Saving…"
  while the request is in flight, consistent with the Save/Update buttons elsewhere.
- `assets/css/style.css`: one small addition, `.profile-avatar-edit.is-disabled`, for the avatar
  upload's disabled-while-uploading state.

## 3. Notifications
No new notification/toast library was installed. Both existing systems (`VITC.toast` for the
public/student site, `Teacher.toast` for Teacher Admin) were reused as-is for every success/error
case listed in the brief — they already cover profile updates, password changes, course/module/
lesson save, video upload/replace/remove, and API/validation errors, each as a single dismissible
toast (never stacked with a banner+alert for the same event).

## 4. Not changed
- Main Admin panel (`admin/*.html`, `admin/assets/admin.css`, `admin/assets/admin.js`) — out of
  scope for this part and not touched.
- Backend (`backend/`) — this part is front-end feedback/UX only; no API contracts changed.
