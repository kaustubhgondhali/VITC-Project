# PART 9C — Teacher Video Management & Student Portal Integration

## Architecture decision
This project has **no separate video table**. `course_lessons` IS the video record:
`title`, `video_url`, `duration`, `display_order`, `active`, `module_id`.
So video management reuses the existing entity/repository/service/DTOs.
**No `teacher_videos` table, no duplicate course-content system.**

Hierarchy: Course -> course_modules -> course_lessons (lesson + its video).

## Files changed
- backend `dto/request/TeacherVideoRequest.java` (new)
- backend `service/TeacherCourseContentService.java`
- backend `service/impl/TeacherCourseContentServiceImpl.java`
- backend `controller/TeacherCourseContentController.java`
- backend `src/test/java/com/vitc/TeacherVideoManagementTest.java` (new)
- `teacher-admin/course-content.html`
- `teacher-admin/assets/teacher.js`

## Database changes
None. Existing `course_lessons.video_url` / `duration` / `display_order` / `active` are reused.

## Backend changes
- Teacher lesson create/update now accept the video fields (previously stripped).
  Omitting them in a lesson edit keeps the stored video (no accidental wipe).
- New service ops: `video()`, `setLessonVideo()`, `clearLessonVideo()` — all delegate to the
  existing `AdminCourseContentService` write path.

## APIs added
- `GET    /api/v1/teacher/lessons/{lessonId}/video`
- `PUT    /api/v1/teacher/lessons/{lessonId}/video`  body: `{ "videoUrl": "...", "duration": "12:40" }`
- `DELETE /api/v1/teacher/lessons/{lessonId}/video`
Modified: `POST /api/v1/teacher/modules/{moduleId}/lessons` and `PUT /api/v1/teacher/lessons/{id}`
now honour `videoUrl` / `duration`.

## Frontend changes
- Lesson form has Video URL + Duration fields.
- Each lesson row shows its video (url + duration) or "No video yet", with an Add/Edit video button.
- Dedicated video dialog with Save / Remove.

## Security / authorization
Every video call re-walks **teacher (session) -> assigned course -> module -> lesson -> video**
server-side (`assertOwnsLesson`). Ids from the URL/body are untrusted; a foreign lesson id returns
403 even if it exists. Unauthenticated calls return 401 via the existing `TeacherAuthInterceptor`.

## Student Portal
Unchanged. `StudentLearningServiceImpl` reads the same `course_lessons` row, so teacher edits
(module/lesson/description/order/active/video/duration) appear immediately under the existing
enrolment + active-content rules. Main Admin course-content management is untouched.

## Tests
`mvn test` → 39 tests, 0 failures (4 new in `TeacherVideoManagementTest`).
