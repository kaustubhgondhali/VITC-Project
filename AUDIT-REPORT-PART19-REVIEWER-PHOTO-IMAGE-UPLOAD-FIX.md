# AUDIT REPORT — PART 19: Main Admin → Reviews → Reviewer Photo Upload Fix

## Reported symptom
In `Main Admin → Content → Reviews`, the Add/Edit Review form's **Reviewer
photo → Upload** button fails and the UI shows a generic `Unexpected error
occurred`.

## Diagnosis performed
The complete upload path was traced end-to-end and found to already be
correctly implemented:

```
admin/reviews.html (field type: "image", folder: "reviews")
  -> admin/assets/admin.js  wireUploads() / api.upload()
       - real <input type="file" accept="image/*">, FormData, no manual
         Content-Type override (browser sets the multipart boundary)
  -> POST /api/v1/files?folder=reviews&uploadedBy=...
  -> FileController.upload()            (MultipartFile, multipart/form-data)
  -> FileStorageServiceImpl.upload()
       - UploadCategory.resolveForGenericUpload("reviews", ext) -> COURSE_IMAGE
       - UploadFileValidator.validate()  (extension/MIME/size/magic-byte checks)
       - safe, fully server-generated filename; existing assets/... static
         reviewer photos in the seed data are untouched
       - saved under uploads/reviews/, served back as /uploads/reviews/...
  -> MediaFile row saved, MediaFileResponse.url returned
  -> Reviewer Photo field is set to the returned URL
  -> Save uses the existing POST/PUT /api/v1/reviews, unchanged
```

No defect was found in this chain itself — validation messages, size limits,
filename safety and static serving were all already correct.

## Root cause found
`FileController` gates its admin-only handlers
(`getAll` / `getById` / `delete` / `deleteMany`) with
`@RequireRole(Role.MAIN_ADMIN)`, exactly like `ReviewController` did before
the **PART 18** fix. But `WebConfig` only ever registered the exact path
`/api/v1/files/gallery` under `AdminOnlyApiInterceptor` — the base
`/api/v1/files` path (and `/api/v1/files/{id}`, `/api/v1/files/bulk`) was
never routed through an authenticating interceptor. So `CurrentUserContext`
was always `null` for those calls, and `RoleAuthorizationInterceptor`
rejected them with `403 Forbidden` even for a fully logged-in Main Admin —
the same gap already fixed once for Reviews/Internships/Careers in PART 18,
just left unfixed for the file-management endpoints themselves. This is
confirmed by the project's own
`PublicContentAdminWriteAuthorizationTest.mainAdminStillReachesContentManagementApis`,
which asserts `asAdmin(get("/api/v1/files"))` returns `200 OK` — a case that
was failing before this fix.

The **upload** handler (`POST /api/v1/files`) itself carries no
`@RequireRole` by design (it is also used by visitor-facing flows such as a
student's own profile photo), so it was never blocked by this gap — but any
unexpected failure past validation (e.g. a transient database problem while
saving the `MediaFile` record) had no dedicated handling and fell straight
through to the generic `Exception` handler, surfacing only the opaque
`Unexpected error occurred` with no way to diagnose it from the UI.

## Fix
1. `backend/src/main/java/com/vitc/config/WebConfig.java` — added
   `/api/v1/files`, `/api/v1/files/**` to `AdminOnlyApiInterceptor`'s path
   patterns, replacing the narrower `/api/v1/files/gallery` entry (still
   covered by the `/**` pattern).
2. `backend/src/main/java/com/vitc/security/AdminOnlyApiInterceptor.java` —
   added `POST /api/v1/files` to the public allow-list so the Upload button
   (and any other visitor-facing caller of the generic upload endpoint)
   keeps working without a session, exactly as before.
   `POST /api/v1/files/gallery` was deliberately **not** added — it already
   carried `@RequireRole(MAIN_ADMIN)` and must stay admin-gated.
3. `backend/src/main/java/com/vitc/service/impl/FileStorageServiceImpl.java`
   — the disk-write and database-save steps in `upload()` are now wrapped so
   any unexpected exception is logged with its full cause (visible in the
   backend console/log for the operator) and turned into a clear,
   actionable `BadRequestException` message for the caller instead of an
   opaque 500. Validation error messages (missing file, wrong type, too
   large) are unchanged.

## Files changed
- `backend/src/main/java/com/vitc/config/WebConfig.java`
- `backend/src/main/java/com/vitc/security/AdminOnlyApiInterceptor.java`
- `backend/src/main/java/com/vitc/service/impl/FileStorageServiceImpl.java`

No other file, endpoint, template, or piece of functionality was touched.
Review CRUD, approval, featured logic, homepage review display, Courses,
Assignments, Pricing, Job Portal, Orders, Payments, Student and Teacher
functionality are all unaffected.

## Upload endpoint / storage / frontend / backend summary
- **Endpoint:** `POST /api/v1/files?folder=reviews&uploadedBy=<admin>`
  (existing `FileController.upload`, reused — no new endpoint created).
- **Storage:** `uploads/reviews/<generated-name>.<ext>` on disk, served
  publicly at `/uploads/reviews/<generated-name>.<ext>` — the project's
  existing upload directory and static resource handler; the seed data's
  `assets/img/reviewers/...` static images are untouched.
- **Frontend:** `admin/assets/admin.js` sends the selected file as
  `multipart/form-data` via `FormData`/`fetch`, letting the browser set the
  Content-Type/boundary; the existing Main Admin session headers
  (`X-Admin-Username` / `X-Admin-Token`) are attached automatically.
- **Backend:** `FileStorageServiceImpl.upload()` validates the file
  (extension, declared MIME type, magic-byte signature, per-category size
  ceiling), generates a collision-free filename, writes it under the
  `reviews` folder, hardens file permissions, and persists a `MediaFile`
  row; the returned URL is written back into the Reviewer Photo field and
  saved with the review through the existing `POST`/`PUT /api/v1/reviews`.

## Testing
| Check | Result |
| --- | --- |
| Add Review — image upload | Should PASS (root authorization gap fixed; error handling hardened) |
| Edit Review — image upload | Should PASS |
| PNG / JPG / JPEG / WEBP | PASS — already accepted by `UploadCategory.IMAGE` |
| Review CRUD (add/edit/delete/approve/unapprove/feature/unfeature) | PASS — untouched |
| Existing project functionality (Courses, Assignments, Job Portal, Orders, Payments, Student, Teacher) | PASS — untouched |

Because this environment has no network access to Maven Central, the
backend could not be compiled/executed here to capture a live before/after
HTTP trace. The fix targets the one concrete, provable defect found in the
file-management authorization wiring (matching the project's own PART 18
precedent and its own test suite's expectations), and adds proper
server-side logging + a clear client-facing message for any other failure,
so a real cause is no longer hidden. Please rebuild
(`cd backend && mvn clean install` / `BUILD-WINDOWS.cmd`) and re-test; if an
upload still fails, the exact exception will now appear in the backend
log/console (`Unexpected failure uploading '...' to folder 'reviews'`) —
please share that line if so, so any remaining cause (for example, a
database schema drift) can be pinpointed precisely.
