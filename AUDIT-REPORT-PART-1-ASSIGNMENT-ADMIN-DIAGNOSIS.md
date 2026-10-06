# PART 1 — Inspect and Diagnose the Assignment Admin Problem (Diagnosis only, no code changed)

## 1. Exact root cause

`/api/v1/assignments` and `/api/v1/assignments/**` are **not registered on any authentication interceptor** in `WebConfig.addInterceptors()`.

Because no interceptor authenticates the caller for that path:

- `AdminAuthInterceptor` never runs for assignment requests
- therefore `CurrentUserContext.set(new CurrentUser(Role.MAIN_ADMIN, ...))` (AdminAuthInterceptor line 53) never executes
- `RoleAuthorizationInterceptor` (registered globally on `/**`) then reads `CurrentUserContext.get()` and gets `null`
- for every handler annotated `@RequireRole(Role.MAIN_ADMIN)` it short-circuits with **403 Forbidden**

The admin UI *does* send the correct headers (`X-Admin-Username`, `X-Admin-Token` — `admin/assets/admin.js` lines 48–49); nobody consumes them on this path, so a perfectly valid Main Admin session is treated as an anonymous caller.

This is the identical defect that was previously fixed for `/api/v1/reviews`, `/api/v1/internships`, `/api/v1/careers` (see the FIX comment inside `WebConfig`) — assignments were simply missed.

## 2. Files responsible

| File | Role in the failure |
|---|---|
| `backend/src/main/java/com/vitc/config/WebConfig.java` | **Primary.** Missing `"/api/v1/assignments", "/api/v1/assignments/**"` in the `adminOnlyApiInterceptor` path patterns |
| `backend/src/main/java/com/vitc/security/AdminOnlyApiInterceptor.java` | Never invoked for this path; its `PUBLIC_ALLOWLIST` has no assignment entries (needed for public GETs once the path is registered) |
| `backend/src/main/java/com/vitc/security/AdminAuthInterceptor.java` | Correct, but never reached — it is the only place that populates `CurrentUserContext` for Main Admin |
| `backend/src/main/java/com/vitc/security/RoleAuthorizationInterceptor.java` | Correct — it is the component emitting the 403 (null caller) |
| `backend/src/main/java/com/vitc/controller/AssignmentController.java` | Correct — `@RequireRole(Role.MAIN_ADMIN)` on `create` (POST), `update` (PUT `/{id}`), `delete` (DELETE `/{id}`) |
| `admin/assignments.html`, `admin/assets/admin.js` | Correct — send admin session headers; Hide/Publish is a `PUT /assignments/{id}` toggle (`assignments.html` line 34) |

`AssignmentService` / `AssignmentServiceImpl` / `AssignmentRepository` are **not** implicated — the request never reaches them.

## 3. Why each action fails

| Action | Request | Result |
|---|---|---|
| Add | `POST /api/v1/assignments` | `@RequireRole(MAIN_ADMIN)` + null context → 403 |
| Edit | `PUT /api/v1/assignments/{id}` | same → 403 |
| Delete | `DELETE /api/v1/assignments/{id}` | same → 403 |
| Hide | `PUT /api/v1/assignments/{id}` (isActive=false) | same → 403 |
| Publish / Unhide | `PUT /api/v1/assignments/{id}` (isActive=true) | same → 403 |

Listing works because `GET /api/v1/assignments` has no `@RequireRole`, so the role interceptor skips it — which is exactly why the page renders data yet every write button fails.

## 4. HTTP status / error produced

`403 Forbidden` with body:

```json
{"success":false,"message":"Forbidden"}
```

emitted by `RoleAuthorizationInterceptor.preHandle`. The admin UI surfaces it as a generic "Forbidden" / failed-save toast. It is **not** 401, not 404, not a validation or DB error.

## 5. Minimal fix required (PART 2 — not applied)

1. In `WebConfig.addInterceptors()`, add to the existing `adminOnlyApiInterceptor.addPathPatterns(...)` list:
   ```java
   "/api/v1/assignments", "/api/v1/assignments/**",
   ```
2. In `AdminOnlyApiInterceptor.PUBLIC_ALLOWLIST`, keep the intentionally public storefront reads open (they are used by the public `assignments.html` / `assignment-details.html` pages):
   ```java
   new String[] {"GET", "/api/v1/assignments"},
   new String[] {"GET", "/api/v1/assignments/active"},
   new String[] {"GET", "/api/v1/assignments/code/**"},
   new String[] {"GET", "/api/v1/assignments/category/**"},
   new String[] {"GET", "/api/v1/assignments/*"}  // detail by id (exact-segment match)
   ```
   POST / PUT / DELETE stay closed and continue to require a Main Admin session plus `@RequireRole(MAIN_ADMIN)`.

No annotation is removed, no security disabled, no write endpoint made public, no other module touched.

## Public GET check

Yes — public GETs are intentional. `assignments.html` and `assignment-details.html` (site root, no auth) call the list/active/detail/code/category endpoints, and those handlers deliberately carry no `@RequireRole`. The fix must preserve that via the method+path allow-list, otherwise registering the path would break the public catalogue.
