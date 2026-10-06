# PART 7A/8 — Manual Test Script: Student, Teacher & Security Testing

**Why this exists:** I don't have a way to actually run this app in my
sandbox (no Maven Central access to build the Spring Boot backend, no
database, no browser). This script is the exact set of steps you (or a
QA person) can run in ~30–45 minutes against a real local instance, with
a place to record PASS / FAIL / NOT TESTED for each item, exactly as the
brief asked for.

## Setup (once)

1. `cd backend && mvn spring-boot:run` (or your IDE's run config) with a
   real MySQL/Postgres instance and `.env` filled in per
   `backend/.env.example`.
2. Serve the frontend (`VITC-Website/`) with any static server, e.g.
   `npx serve .` or your existing setup, pointed at the running backend.
3. Have two student accounts and two teacher accounts available (or seed
   them from `database/seed_data.sql`) so the authorization tests in
   Section 3 have a second party to test against.
4. Open browser DevTools → Network tab and Console tab before you start,
   and leave them open the whole time — most of the "verify" bullets below
   are things you read off there, not just the screen.

---

## 1. Student End-to-End

Login (`student-login.html`) → Dashboard → My Courses → Continue Learning
→ Course → Lesson → Video → Progress → Assignments → Resources → Course
Completion → Certificate → Statistics → Profile → Logout.

| # | Step | What to check | Result |
|---|------|----------------|--------|
| 1 | Login | Wrong password → error notification, not a silent fail. Correct login → redirected to dashboard. | ☐ PASS ☐ FAIL ☐ NOT TESTED |
| 2 | Dashboard load | Loading state briefly visible on slow network (DevTools → Network → Slow 3G), then real data. | ☐ |
| 3 | Empty state | If this student has 0 courses, confirm an empty-state message appears (not a blank grid). | ☐ |
| 4 | My Courses → Continue Learning | Click a course card and "Continue Learning" — lands on the correct lesson, not always lesson 1. | ☐ |
| 5 | Lesson navigation | Switching lessons updates content without a full page flash/error. | ☐ |
| 6 | Video playback | Plays, pauses, shows correct duration. Seek forward/back works smoothly. | ☐ |
| 7 | Progress tracking | Mark a lesson complete → percentage on the course card updates on next dashboard visit. | ☐ |
| 8 | Assignments | List loads; opening one shows details; empty state if none assigned. | ☐ |
| 9 | Resources | Files/links open or download correctly. | ☐ |
| 10 | Course completion | Completing all lessons flips the course to "Completed" state. | ☐ |
| 11 | Certificate | Only appears/generates after completion; not obtainable earlier by URL-guessing (see Section 3). | ☐ |
| 12 | Statistics | Numbers shown match what you'd expect from the courses/progress above. | ☐ |
| 13 | Profile | Edit + save works; a validation error (e.g. bad email) shows an error notification, not a crash. | ☐ |
| 14 | Logout | Session actually invalidated — pressing Back button after logout does not show dashboard data. | ☐ |

Also check throughout: buttons have visible hover/focus/disabled states,
cards don't overlap on mobile width (DevTools device toolbar, 375px and
768px), modals trap focus and close on Esc/backdrop click, and forms show
inline validation errors rather than only a top-level toast.

---

## 2. Teacher End-to-End

Login (`teacher-login.html`) → Dashboard → My Courses → Course Content →
Module → Lesson → Video → Assignments → Resources → Students → Student
Progress → Profile → Logout.

| # | Step | What to check | Result |
|---|------|----------------|--------|
| 1 | Login | Same as student: wrong creds → error, correct → dashboard. | ☐ |
| 2 | Dashboard | Only shows this teacher's assigned courses, not every course in the system. | ☐ |
| 3 | Course Content → Module → Lesson | Add/edit/reorder a module and a lesson; changes persist after refresh. | ☐ |
| 4 | Video management | Upload a video, replace it, then remove it (see Section 4 for the detailed pass). | ☐ |
| 5 | Assignments | Create/edit an assignment tied to a course this teacher owns. | ☐ |
| 6 | Resources | Add/remove a resource; broken/empty state if none. | ☐ |
| 7 | Students | Roster only shows students enrolled in this teacher's own courses. | ☐ |
| 8 | Student Progress | Per-student progress numbers match what that student's own dashboard shows. | ☐ |
| 9 | Profile | Edit + save; validation errors surface properly. | ☐ |
| 10 | Logout | Session invalidated; Back button doesn't restore the dashboard. | ☐ |

---

## 3. Authorization Regression — do these with two browser profiles/incognito windows open side by side

| # | Test | How | Result |
|---|------|-----|--------|
| 1 | Student can't reach Teacher UI | While logged in as a student, manually navigate to `teacher-admin/index.html`. Should bounce to teacher login, not show any teacher data. | ☐ |
| 2 | Student can't reach Teacher API | In DevTools console while logged in as student: `fetch('<api-base>/api/v1/teacher/courses', {headers:{'X-Teacher-Username':'x','X-Teacher-Token':'y'}})` — should be 401/403, never 200. | ☐ |
| 3 | Student A can't see Student B's data | Log in as Student A, note their course/progress URLs or ids from Network tab. Log in as Student B in the other window, try hitting the same lesson/progress endpoint with Student A's ids swapped into the request body if the UI lets you construct one (e.g. via DevTools "Copy as fetch") — expect 403/404, not Student A's data. | ☐ |
| 4 | Teacher A can't modify Teacher B's course | Log in as Teacher A. Grab a `courseId` you know belongs to Teacher B (from an admin view or DB). Try `PUT /api/v1/teacher/courses/{thatId}` (or edit a module/lesson under it) as Teacher A — expect 403 "not authorised", never a successful save. | ☐ |
| 5 | Teacher only sees assigned courses | Confirm the "My Courses" list and Students roster for Teacher A never contain a course/student that belongs only to Teacher B. | ☐ |
| 6 | Unauthorized API access | Log fully out (or use a fresh incognito tab with no session). Try hitting any `/api/v1/student/**` or `/api/v1/teacher/**` endpoint directly with curl/DevTools and no auth headers — expect 401, never data. | ☐ |
| 7 | Certificate not obtainable early | As a student who hasn't completed a course, try navigating directly to that course's certificate URL/endpoint — expect it blocked, not just hidden from the UI. | ☐ |

If any of #1–#7 return real data instead of a 401/403, that's a genuine
security bug — stop and report it rather than continuing down the list.

---

## 4. Video Regression

| # | Test | Result |
|---|------|--------|
| 1 | Upload a new video to a lesson that has none | ☐ |
| 2 | Replace an existing video | ☐ |
| 3 | Remove a video | ☐ |
| 4 | Local video plays for the teacher who owns it | ☐ |
| 5 | Local video plays for an enrolled student | ☐ |
| 6 | A non-enrolled student (or logged-out user) hitting the same lesson's video URL directly gets blocked, not the video | ☐ |
| 7 | Seeking forward/back works without re-downloading the whole file (watch Network tab for 206 Partial Content responses) | ☐ |
| 8 | An external URL (YouTube/Vimeo link) still plays via the embed, not the local-file endpoint | ☐ |

---

## Result summary (fill in after running)

Student E2E: ___ / 14 passed
Teacher E2E: ___ / 10 passed
Authorization: ___ / 7 passed
Video: ___ / 8 passed

Anything marked FAIL — paste the failing step, what you expected, and
what actually happened, and I can dig into the relevant code with you.
