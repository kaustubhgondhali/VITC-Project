# AUDIT REPORT — PART 10C/12: MAIN ADMIN TEACHER MANAGEMENT

Scope: add a **Teachers** section inside the **existing** Main Admin Panel. No separate admin
system was created — the new screen is one more page in `admin/` and every endpoint lives under
the already-guarded `/api/v1/admin/**` prefix.

## 1. What was added

### Backend (`backend/src/main/java/com/vitc`)
| File | Purpose |
|---|---|
| `controller/AdminTeacherController.java` | `/api/v1/admin/teachers` — list, detail, create, edit, status, assign/remove courses, password reset |
| `service/AdminTeacherService.java` | Contract for Main Admin teacher management |
| `service/impl/AdminTeacherServiceImpl.java` | Implementation (BCrypt only, assignment persistence, no content deletion) |
| `dto/request/AdminTeacherRequest.java` | Add Teacher payload (password optional → generated) |
| `dto/request/AdminTeacherUpdateRequest.java` | Edit Teacher profile |
| `dto/request/TeacherCourseAssignmentRequest.java` | Complete set of assigned course ids |
| `dto/request/TeacherPasswordResetRequest.java` | Optional explicit new password |
| `dto/response/AdminTeacherResponse.java` | Teacher row — no hash, no session token |
| `dto/response/AdminTeacherCourseResponse.java` | Course row with its current assignment |
| `dto/response/TeacherCredentialsResponse.java` | One-time credentials handed to Main Admin |
| `src/test/java/com/vitc/AdminTeacherManagementTest.java` | 8 new tests covering the full flow |

### Frontend
- `admin/teachers.html` — new Teachers screen (list, add, edit, view, assign courses, remove
  assignment, reset password, activate/deactivate).
- `admin/assets/admin.js` — new **Teachers** group in the existing sidebar navigation.

Nothing else was modified except two stale assertions in older tests (see §6).

## 2. API surface (Main Admin only)

| Method | Path | Action |
|---|---|---|
| GET | `/api/v1/admin/teachers` | View Teachers (with assigned courses) |
| GET | `/api/v1/admin/teachers/{id}` | Teacher details |
| POST | `/api/v1/admin/teachers` | Add Teacher |
| PUT | `/api/v1/admin/teachers/{id}` | Edit Teacher |
| PATCH | `/api/v1/admin/teachers/{id}/status?status=ACTIVE\|INACTIVE` | Activate / Deactivate |
| GET | `/api/v1/admin/teachers/courses` | All courses + current assignment |
| GET | `/api/v1/admin/teachers/{id}/courses` | View assigned courses |
| PUT | `/api/v1/admin/teachers/{id}/courses` | Assign / update assignments |
| DELETE | `/api/v1/admin/teachers/{id}/courses/{courseId}` | Remove one assignment |
| POST | `/api/v1/admin/teachers/{id}/password-reset` | Reset Teacher password |

All of these are under `/api/v1/admin/**`, which `AdminAuthInterceptor` guards. A Teacher or
Student session token is rejected with **403 Forbidden** before any handler runs (PART 10B rule).

## 3. Teacher status enforcement (backend, not UI)

- Status is the existing `users.status` column, constrained here to `ACTIVE` / `INACTIVE`
  (a request for any other value is rejected with 400).
- Deactivation **clears the session token and expiry**, so an already signed-in teacher loses
  access on the very next request.
- `TeacherAccountServiceImpl.login` refuses a non-ACTIVE teacher →
  *"This teacher account is not active. Please contact the administrator."*
- `TeacherAuthInterceptor` re-checks role **and** `status == ACTIVE` on every
  `/api/v1/teacher/**` call → 403 Forbidden. Frontend hiding is never relied upon.

## 4. Course assignment = real authorisation

Assignments are persisted on `courses.teacher_id` — the exact column
`TeacherAuthorizationService` reads on every Teacher API call (course, module, lesson, video).
So the Main Admin screen changes actual backend authorisation:

```
Teacher A: Java assigned, Python not assigned
GET /api/v1/teacher/courses/{javaId}    -> 200 OK
GET /api/v1/teacher/courses/{pythonId}  -> 403 Forbidden
```

A course already assigned to another teacher cannot be silently stolen: the request is rejected
with a clear message and that course is shown disabled in the assign dialog.

## 5. Passwords & historical content

- Only BCrypt hashes are stored. Plain text is never persisted or logged; the generated
  temporary password is returned exactly once in the API response for the Main Admin to hand over.
- Reset forces `mustChangePassword = true` and kills the live session.
- No response from any endpoint contains `passwordHash` or `sessionToken` (asserted by test).
- Deactivation and assignment removal **delete nothing**: modules, lessons, videos, course
  content, enrolments, progress and every historical record are preserved. Only authorisation is
  withdrawn.

## 6. Verification

`mvn test` — **61 tests, 0 failures** (was 53 before this part).

New `AdminTeacherManagementTest`:
1. Main Admin creates a Teacher → hash starts with `$2`, role TEACHER, status ACTIVE.
2. Teachers list carries no `passwordHash` / `sessionToken`.
3. Teacher session on `/api/v1/admin/teachers` → **403**.
4. Assign Java only → Java 200, Python **403**.
5. Remove assignment → course row and content still present, access now **403**.
6. Deactivate → stale token rejected, login refused; reactivate → login works again.
7. Password reset → new hash, session cleared, `mustChangePassword` true.
8. Edit profile → credentials untouched.

Two assertions in older tests were updated to match the PART 10B behaviour they were written
before (`AdminManagementSecurityTest`: authenticated non-admin now gets 403 rather than 401;
`TeacherAdminSeparationTest`: public enrollment returns 201 Created). No production behaviour
changed for these.

## 7. How to run

Backend:
```bash
cd backend
mvn spring-boot:run        # http://localhost:8080  (Swagger: /swagger-ui.html)
```
Frontend (static): serve the project root with any web server, e.g.
```bash
python3 -m http.server 5500
```
Then open `http://localhost:5500/admin/index.html`, sign in as Main Admin and use the new
**Teachers** entry in the sidebar. Teacher panel: `teacher-login.html`.

Default seeded teacher: `VITCteacher` / `VITC@123` (must change at first login).

Nothing from PARTS 1–9 was removed or altered in behaviour.
