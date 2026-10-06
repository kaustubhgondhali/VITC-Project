# AUDIT REPORT — PART 2B-1/7: VITC SECURITY HARDENING — SECURITY HEADERS & CSP

Scope implemented: HTTP security headers + Content Security Policy only.
No feature changes, no public-site redesign, no authentication changes, no file-upload
security, no audit logging. All existing functionality preserved (headers are additive only).

## 1. Frontend inspection performed first

Inspected every HTML page, `assets/js/*`, `assets/admin.js`, `assets/teacher.js`,
`teacher-admin/assets/teacher.js`, and `assets/css/*`:

- JS: 100% first-party, no JS CDN. Razorpay Checkout is injected at runtime by
  `assets/js/payments.js` from `https://checkout.razorpay.com/v1/checkout.js`.
- Inline `<script>` bootstrap blocks: ~30 pages (public + admin + teacher-admin + student).
- CSS: first-party + Google Fonts (`fonts.googleapis.com` / `fonts.gstatic.com`).
- Inline styles: 14 `<style>` blocks, 47 files using `style="..."`, plus generated markup.
- Images: same-origin, `/uploads/**`, `data:`/`blob:`, `images.unsplash.com`,
  `www.gstatic.com` (Google logo), YouTube thumbnails.
- Media: lesson videos streamed from the backend (`/api/v1/student/lessons/{id}/video`) + `blob:`.
- API: `/api/v1/**` on the base URL resolved by `assets/js/api.js`.
- iframes: same-origin video previews (teacher-admin) + YouTube embeds + the Razorpay frame.
- No `eval()` / `new Function()` anywhere → `'unsafe-eval'` intentionally NOT enabled.

## 2. Files changed

- `backend/src/main/resources/application.properties` — new `app.security.headers.*` block.
- `backend/src/main/resources/application-prod.properties` — enforcing CSP + full HSTS.
- `backend/src/main/resources/application-dev.properties` — headers on, HSTS auto-skipped on HTTP.

## 3. New files

- `backend/src/main/java/com/vitc/security/headers/SecurityHeadersProperties.java`
- `backend/src/main/java/com/vitc/security/headers/ContentSecurityPolicyBuilder.java`
- `backend/src/main/java/com/vitc/security/headers/SecurityHeadersFilter.java`
- `backend/src/test/java/com/vitc/SecurityHeadersTest.java`
- `deploy/SECURITY-HEADERS.md` (full documentation)
- `deploy/nginx-security-headers.conf`, `deploy/apache-security-headers.htaccess`
  (same header set for the statically served HTML pages)

## 4. Security headers added (every backend response)

`X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`,
`Referrer-Policy: strict-origin-when-cross-origin`,
`Permissions-Policy: geolocation=(), microphone=(), camera=(), payment=(self), usb=(), magnetometer=(), gyroscope=(), accelerometer=(), fullscreen=(self)`,
`Cross-Origin-Resource-Policy: cross-origin`,
`Strict-Transport-Security: max-age=31536000; includeSubDomains` (HTTPS requests only),
`Content-Security-Policy` (below).

The filter runs at `HIGHEST_PRECEDENCE + 5`, i.e. before the PART 2A rate-limit filter, so
even 429/error responses carry the headers. It only ever adds headers — no blocking,
no body rewriting.

## 5. Final CSP

```
default-src 'self';
script-src 'self' https://checkout.razorpay.com 'unsafe-inline';
script-src-elem 'self' https://checkout.razorpay.com 'unsafe-inline';
style-src 'self' https://fonts.googleapis.com 'unsafe-inline';
style-src-elem 'self' https://fonts.googleapis.com 'unsafe-inline';
img-src 'self' data: blob: https://images.unsplash.com https://www.gstatic.com https://*.razorpay.com https://i.ytimg.com;
font-src 'self' data: https://fonts.gstatic.com;
connect-src 'self' https://api.razorpay.com https://lumberjack.razorpay.com https://*.razorpay.com;
media-src 'self' blob: data:;
frame-src 'self' https://api.razorpay.com https://checkout.razorpay.com https://www.youtube.com https://www.youtube-nocookie.com;
worker-src 'self' blob:;
object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self';
```

External domains allowed (nothing else): checkout.razorpay.com, api.razorpay.com,
lumberjack.razorpay.com, *.razorpay.com, fonts.googleapis.com, fonts.gstatic.com,
images.unsplash.com, www.gstatic.com, i.ytimg.com, www.youtube.com, www.youtube-nocookie.com.

## 6. Unavoidable CSP exceptions

- `script-src 'unsafe-inline'` — required by the ~30 existing pages' inline bootstrap scripts
  and by Swagger UI. Nonces/hashes would require rewriting the frontend (out of scope).
  Switch `app.security.headers.allow-inline-scripts=false` is already wired for a later part.
- `style-src 'unsafe-inline'` — required by existing inline `style="..."`/`<style>` usage.
- `'unsafe-eval'` — NOT enabled anywhere.

## 7. HSTS behaviour

Emitted only when `request.isSecure()` or `X-Forwarded-Proto: https` (proxy) — so
`http://localhost:8080` and `http://127.0.0.1:5500` development never gets pinned to HTTPS.
Max-age 1 year + includeSubDomains in production; `preload` off by default (irreversible).

## 8. Tests performed

- `mvn compile` — BUILD SUCCESS, all 360 sources compile with the new package.
- `mvn -Dtest=SecurityHeadersTest test` — 4/4 passing:
  baseline headers present; CSP contains exactly the required domains and the hardened
  `object-src/base-uri/form-action/frame-ancestors` directives with no `'unsafe-eval'`;
  HSTS absent on HTTP and present on HTTPS; master switch disables everything.
- Static review of every allowed source against the actual frontend references (fonts,
  Razorpay SDK/API, YouTube frames, Unsplash images, backend video streaming, `blob:` previews),
  so public pages, login pages, admin, teacher-admin and student pages keep loading their
  scripts, styles, images, fonts, videos and API calls.

## 9. Remaining risks

- `'unsafe-inline'` for scripts materially weakens XSS containment; removing it needs the
  inline blocks externalised (future part).
- The HTML pages are served by a static web server, not the backend, so their document-level
  headers depend on deploying `deploy/nginx-security-headers.conf` or the `.htaccess` snippet
  (backend responses are covered automatically).
- Live browser-console CSP verification requires a running MySQL + the static site host; the
  policy was validated by unit tests and full dependency inspection. Use
  `SECURITY_HEADERS_CSP_REPORT_ONLY=true` for a first pass on a new environment.
- `img-src`/`connect-src` use `https://*.razorpay.com`, slightly broader than exact hosts, to
  cover Razorpay's rotating CDN/telemetry subdomains.
