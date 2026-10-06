# PART 2/6 — VITC Success Stories Database & Backend

Continues from PART 1/6 (public "Success Stories" page + foundation entity).
No redesign, no unrelated functionality touched — backend/database only.

## What changed

**Category** — kept as the same lowercase string convention already used by
`GalleryItem.category` / `Course.category` (so PART 1/6's public URLs
`/success-stories/category/student|teacher|parent` keep working unchanged),
but is now strongly validated through a new `SuccessStoryCategory` enum
(`STUDENT`/`TEACHER`/`PARENT`) at the request/service boundary, so an invalid
category can never reach the database.

**Status (DRAFT/PUBLISHED)** — new `SuccessStoryStatus` enum replaces the
PART 1/6 `approved` boolean. A story is always created as `DRAFT` and only
becomes visible on the public page once explicitly published. `approved` is
no longer read/written by the app (see migration note below).

**Course link** — optional `course_id` column, validated against the real
`courses` table (`CourseRepository.existsById`). Plain nullable column, not a
JPA relationship — same pattern already used by `Course.teacherId` — so no
existing course row, table, or query is touched. No duplicate course table.

**Audit trail** — `created_by` / `created_by_role` are populated server-side
from the authenticated Teacher Admin session (`CurrentUserContext`), never
from the request body.

## Files changed / added

- `entity/enums/SuccessStoryCategory.java` (new)
- `entity/enums/SuccessStoryStatus.java` (new)
- `entity/SuccessStory.java` — added `status`, `courseId`, `createdBy`, `createdByRole`; removed `approved`
- `repository/SuccessStoryRepository.java` — status-based queries added
- `service/SuccessStoryService.java` + `service/impl/SuccessStoryServiceImpl.java` — published vs admin views, create/update/delete/publish/unpublish, course-id validation, course-title resolution
- `mapper/SuccessStoryMapper.java` — `courseId`/`status` mapping
- `dto/request/SuccessStoryRequest.java` — added optional `courseId`
- `dto/response/SuccessStoryResponse.java` — `status` replaces `approved`; added `courseId`, `courseTitle`, `createdBy`, `updatedAt`
- `controller/SuccessStoryController.java` — **narrowed to public, read-only, published-only** (`/approved`, `/category/{cat}`, `/{id}`) — same URLs the existing `assets/js/success-stories.js` already calls, so the PART 1/6 frontend is untouched
- `controller/TeacherSuccessStoryController.java` (new) — `/api/v1/teacher/success-stories/**`, full management (create/update/delete/publish/unpublish/list/filter)
- `config/SuccessStorySeeder.java` — seeds with `status(PUBLISHED)` instead of `approved(true)`
- `database/vitc_db_fresh.sql` — added the `success_stories` table (missing from the PART 1/6 fresh-install script)
- `database/success_stories_upgrade_part2.sql` (new) — additive, non-destructive upgrade for a DB that already has the PART 1/6 shape of `success_stories`

## Security

- Public: `GET /api/v1/success-stories/approved`, `/category/{category}`, `/{id}` — unauthenticated, published-only. A DRAFT story is treated as "not found" (never leaks existence).
- Teacher Admin only: everything under `/api/v1/teacher/success-stories/**` — protected by the project's existing `TeacherAuthInterceptor` (session token) **and** the centralized `@RequireRole(Role.TEACHER)` check, so a valid Main Admin or Student session is rejected with 403, not just an unauthenticated request with 401.
- No credentials or internal identifiers are ever exposed in a response; `createdBy` stores only the teacher's login identifier, resolved server-side.

## Database migration

- **Fresh installs**: `vitc_db_fresh.sql` already creates `success_stories` in the final PART 2/6 shape — nothing else to run.
- **Existing installs** that already ran PART 1/6 against a real MySQL database (so `success_stories` exists with the old `approved` boolean and no `status`/`course_id`/`created_by` columns): run `database/success_stories_upgrade_part2.sql` once. It is additive only — no column is dropped, no row is deleted; `approved` is left in place (unused) for safety.
- Local dev (`spring.jpa.hibernate.ddl-auto=update`, the default profile) will also auto-add the new columns on next start, but the SQL script is the reliable path for a real deployment (`ddl-auto=validate` in prod).

## Manual test flow (per the PART 2/6 spec)

Create → Save → Retrieve → Filter → Update → Publish → Unpublish → Delete, via
`/api/v1/teacher/success-stories` (Teacher Admin session required):

```
POST   /api/v1/teacher/success-stories                 -> 201, status=DRAFT
GET    /api/v1/teacher/success-stories/{id}             -> 200
GET    /api/v1/teacher/success-stories/category/student -> 200 (includes the new draft)
PUT    /api/v1/teacher/success-stories/{id}             -> 200
PATCH  /api/v1/teacher/success-stories/{id}/publish     -> 200, status=PUBLISHED
GET    /api/v1/success-stories/approved                 -> 200 (now includes it, public/unauthenticated)
PATCH  /api/v1/teacher/success-stories/{id}/unpublish    -> 200, status=DRAFT
GET    /api/v1/success-stories/approved                 -> 200 (no longer includes it)
DELETE /api/v1/teacher/success-stories/{id}              -> 200
```

## Build verification note

This sandbox has no access to Maven Central (only a small allow-list of
domains: npm, PyPI, crates.io, GitHub, Ubuntu archives), so `mvn compile`
could not be run here to produce a green build log. Every changed file was
reviewed by hand and cross-checked with `grep` across the whole `src/` tree
for stale references to the removed `SuccessStory.approved` field, broken
imports, and mismatched method signatures — none were found. Please run
`mvn -q compile` (or `BUILD-WINDOWS.cmd`) locally as the final check before
deploying.
