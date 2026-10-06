# VITC Backend — Spring Boot 3 REST API

Backend for the VITC Institute website (courses, assignments, enrollments, orders,
payments, contact, testimonials, blog, gallery, internships, careers).

## Tech Stack

- Java 21
- Spring Boot 3.3 (Web, Data JPA, Validation)
- Maven
- MySQL 8 (`vitc_db`)
- Lombok

## Package Structure

```
com.vitc
├── VitcApplication.java
├── common/            ApiResponse, PageResponse wrappers
├── config/            CorsConfig, WebConfig, JpaAuditingConfig
├── controller/        REST controllers (/api/v1/**)
├── dto/
│   ├── request/       Validated inbound payloads
│   └── response/      Outbound payloads
├── entity/            JPA entities + BaseEntity (auditing)
│   └── enums/         Status / method enums
├── exception/         Custom exceptions + GlobalExceptionHandler
├── mapper/            Entity <-> DTO mappers
├── repository/        Spring Data JPA repositories
└── service/           Service interfaces
    └── impl/          Service implementations
```

## Database Configuration

Configured in `src/main/resources/application.properties`, overridable via
`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (see `.env.example`):

| Property | Local default |
| --- | --- |
| Host | localhost |
| Port | 3306 |
| Database | vitc_db (auto-created) |
| Username | root |
| Password | root |

These defaults are for local development only — set the three environment
variables above (and everything else in `.env.example`) before running with
`SPRING_PROFILES_ACTIVE=prod`, which requires them explicitly and refuses to
start if any are missing.

## Configuration & Secrets

- Copy `backend/.env.example` to `backend/.env` and fill in real values —
  never commit `.env` (already covered by `.gitignore`).
- No real credential ever needs to live in a `.java`, `.properties`, or
  frontend file: database, SMTP, Razorpay, encryption, and video-token
  secrets are all read from environment variables.
- Login, forgot-password, reset/change-password, the contact form, review
  submission, and checkout endpoints are protected by a centralized rate
  limiter (`RateLimitingFilter`), configurable via `app.rate-limit.*` /
  `RATE_LIMIT_*` — see `application.properties`.

## Run

### Windows (recommended)

1. Extract the ZIP to a new folder (do not copy an old `target` directory into it).
2. Make sure Java 21, Maven and MySQL 8 are installed, and MySQL is running.
3. Double-click `BUILD-WINDOWS.cmd`, then `RUN-WINDOWS.cmd`.

The build script intentionally runs `mvn package` instead of `mvn clean package`. A fresh ZIP has
no generated `target` folder, and this avoids the Windows `Failed to delete target/classes` error
caused when an IDE, antivirus scanner, or previously running Java process temporarily locks a
compiled folder. To force a completely clean build, stop the backend first, delete `target`
manually, and run the build script again.

### Command line

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

API base URL: `http://localhost:8080/api/v1`

## Endpoints

| Module | Base path |
| --- | --- |
| Health | `GET /api/v1/health` |
| Courses | `/api/v1/courses` (+ `/active`, `/code/{code}`, `/category/{category}`) |
| Assignments | `/api/v1/assignments` (+ `/active`, `/code/{code}`, `/category/{category}`) |
| Enrollments | `/api/v1/enrollments` (+ `/email/{email}`, `/course/{id}`, `PATCH /{id}/status`) |
| Assignment orders | `/api/v1/assignment-orders` (+ `/code/{orderCode}`, `/email/{email}`, `PATCH /{id}/status`) |
| Payments | `/api/v1/payments` (+ `/transaction/{txnId}`, `/email/{email}`, `PATCH /{id}/status`) |
| Contact | `/api/v1/contact-messages` (+ `/unhandled`, `PATCH /{id}/handled`) |
| Testimonials | `/api/v1/testimonials` (+ `/approved`, `PATCH /{id}/approval?approved=true`) |
| Blog | `/api/v1/blog-posts` (+ `/published`, `/slug/{slug}`) |
| Gallery | `/api/v1/gallery` (+ `/category/{category}`) |
| Internships | `/api/v1/internships` (+ `/status/{status}`, `PATCH /{id}/status?status=...`) |
| Careers | `/api/v1/careers` (+ `/status/{status}`, `PATCH /{id}/status?status=...`) |

Each module supports `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `DELETE /{id}` unless noted.

## Response Envelope

```json
{
  "success": true,
  "message": "Success",
  "data": { },
  "timestamp": "2026-01-01T10:00:00"
}
```

Errors:

```json
{
  "success": false,
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/courses",
  "fieldErrors": { "title": "Title is required" },
  "timestamp": "2026-01-01T10:00:00"
}
```

## CORS

Allowed origins are configurable via `app.cors.allowed-origins` in
`application.properties` (defaults cover Live Server, Vite and CRA dev ports).

## Frontend Integration

```js
const API = "http://localhost:8080/api/v1";

const res = await fetch(`${API}/courses/active`);
const { data } = await res.json();
```

## API Documentation (Swagger / OpenAPI 3)

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`

## Modules

Every module ships Controller → Service (interface) → ServiceImpl → Repository → Mapper → DTOs (request/response) → Entity,
with Bean Validation on all request DTOs and errors handled by `GlobalExceptionHandler`.

| Module | Base path |
| --- | --- |
| Courses | `/api/v1/courses` |
| Assignments | `/api/v1/assignments` |
| Reviews | `/api/v1/reviews` |
| Testimonials | `/api/v1/testimonials` |
| Orders (assignment orders) | `/api/v1/assignment-orders` |
| Enrollments | `/api/v1/enrollments` |
| Payments | `/api/v1/payments` |
| Gallery | `/api/v1/gallery` |
| FAQ | `/api/v1/faqs` |
| Users | `/api/v1/users` |
| Admin | `/api/v1/admins` |
| Blog / Contact / Internships / Careers | `/api/v1/blog-posts`, `/api/v1/contact-messages`, `/api/v1/internship-applications`, `/api/v1/job-applications` |

Standard CRUD per module: `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `DELETE /{id}`,
plus module-specific filters (`/active`, `/approved`, `/category/{category}`, `/email/{email}`, `/search`, `PATCH /{id}/status`).

### Security notes
- User and admin passwords are stored as BCrypt hashes (`PasswordEncoderConfig`) and are never returned by any endpoint.
- `POST /api/v1/admins/login` only verifies credentials and stamps `lastLoginAt`; wire it to a real token/session layer before production.
