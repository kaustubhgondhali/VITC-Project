# PART 2 — Fix Assignment API Authentication and Authorization

## Files changed (2)

### 1. `backend/src/main/java/com/vitc/config/WebConfig.java`
Added `"/api/v1/assignments", "/api/v1/assignments/**"` to the existing
`adminOnlyApiInterceptor.addPathPatterns(...)` list.

**Reason:** this path was on no auth interceptor, so `AdminAuthInterceptor` never ran,
`CurrentUserContext` stayed null, and `RoleAuthorizationInterceptor` rejected every
`@RequireRole(MAIN_ADMIN)` handler with 403. Registering it reuses the existing Main Admin
session flow — a valid `X-Admin-Username` / `X-Admin-Token` now populates
`CurrentUserContext` with `Role.MAIN_ADMIN` before the role check runs.

### 2. `backend/src/main/java/com/vitc/security/AdminOnlyApiInterceptor.java`
Added two GET-only entries to `PUBLIC_ALLOWLIST`:
`GET /api/v1/assignments` and `GET /api/v1/assignments/**`.

**Reason:** keeps the public catalogue (`assignments.html`, `assignment-details.html`,
`/active`, `/code/{code}`, `/category/{category}`, `/{id}`) anonymous. The allow-list matches
method + path, so POST / PUT / DELETE on the same paths remain closed.

## Resulting security model

| Endpoint | Access |
|---|---|
| `GET /api/v1/assignments...` | Public |
| `POST /api/v1/assignments` | Main Admin session + `@RequireRole(MAIN_ADMIN)` |
| `PUT /api/v1/assignments/{id}` | Main Admin session + `@RequireRole(MAIN_ADMIN)` |
| `DELETE /api/v1/assignments/{id}` | Main Admin session + `@RequireRole(MAIN_ADMIN)` |

Teacher / Student tokens hitting a write endpoint are still rejected with 403 by
`AdminOnlyApiInterceptor` before the admin store is touched.

## Not changed
No new auth system, no hard-coded credentials, no `@RequireRole` removed, no controller /
service / repository / frontend edits, no other API's security configuration touched.

## Build
`mvn -DskipTests compile` — BUILD SUCCESS, no errors or new warnings.
