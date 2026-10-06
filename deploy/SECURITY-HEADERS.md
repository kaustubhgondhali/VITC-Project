# PART 2B-1/7 — Security Headers & Content Security Policy

Scope: HTTP security headers + CSP only. No feature changes, no redesign, no auth changes.

## 1. Frontend inspection (what the policy had to support)

| Resource type | Findings |
|---|---|
| JavaScript | All first-party: `assets/js/*.js`, `assets/admin.js`, `assets/teacher.js`, `teacher-admin/assets/teacher.js`. No JS CDN. |
| Inline scripts | Present on ~30 pages (page bootstrap `<script>` blocks in public, admin, teacher-admin, student pages) + Swagger UI. |
| CSS | First-party `assets/css/*` + Google Fonts stylesheet (`https://fonts.googleapis.com/css2`). |
| Inline styles | 14 pages with `<style>` blocks, 47 files with `style="..."` attributes, plus dynamically generated markup with inline styles. |
| Fonts | `https://fonts.gstatic.com`, plus `data:` fonts. |
| Images | Same-origin, `/uploads/**` (same origin), `data:`/`blob:` previews, `https://images.unsplash.com`, `https://www.gstatic.com` (Google "G" logo), YouTube thumbnails. |
| Video / media | Backend-streamed lesson videos (`/api/v1/student/lessons/{id}/video`, same origin) + `blob:`. |
| API endpoints | `/api/v1/**` on the backend origin (base URL resolved by `assets/js/api.js`). |
| Payments | Razorpay Checkout loaded at runtime from `https://checkout.razorpay.com/v1/checkout.js`; widget renders in a Razorpay iframe and talks to `api.razorpay.com` / `lumberjack.razorpay.com`. |
| iframes | Same-origin video preview iframes (teacher-admin success stories, course content) and YouTube embeds. |
| Other external | Social profile links, WhatsApp, Google Maps links (navigation only — no embedded frames), `schema.org` JSON-LD (no network use). |
| Dynamic code | No `eval()` / `new Function()` anywhere → `'unsafe-eval'` is **not** required. |

## 2. Headers added (backend, every response)

Implemented in `SecurityHeadersFilter` (runs before the Part 2A rate-limit filter, so even
429 responses carry the headers):

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: SAMEORIGIN` (keeps the existing same-origin preview iframes working)
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: geolocation=(), microphone=(), camera=(), payment=(self), usb=(), magnetometer=(), gyroscope=(), accelerometer=(), fullscreen=(self)`
- `Cross-Origin-Resource-Policy: cross-origin` (the site and API may sit on different origins)
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` — **HTTPS requests only**
- `Content-Security-Policy` (below)

## 3. Final CSP

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
object-src 'none';
base-uri 'self';
form-action 'self';
frame-ancestors 'self';
```

External domains allowed: `checkout.razorpay.com`, `api.razorpay.com`, `lumberjack.razorpay.com`,
`*.razorpay.com`, `fonts.googleapis.com`, `fonts.gstatic.com`, `images.unsplash.com`,
`www.gstatic.com`, `i.ytimg.com`, `www.youtube.com`, `www.youtube-nocookie.com`. Nothing else.

## 4. Unavoidable exceptions (documented)

- `'unsafe-inline'` in `script-src`: ~30 existing HTML pages bootstrap themselves with inline
  `<script>` blocks, and Swagger UI ships inline scripts. Nonces/hashes would require rewriting
  every page and generating documents server-side — explicitly out of scope for this part. The
  switch `app.security.headers.allow-inline-scripts=false` is ready for a later part that
  externalises those blocks.
- `'unsafe-inline'` in `style-src`: inline `style="..."` attributes and `<style>` blocks are used
  throughout, including in dynamically generated admin/teacher markup. CSS cannot execute JS, so
  the residual risk is low.
- `'unsafe-eval'` is **not** enabled (no dependency needs it, Razorpay included).

## 5. HSTS and local development

HSTS is emitted only when the request arrived over HTTPS — `request.isSecure()` or
`X-Forwarded-Proto: https` from a TLS-terminating proxy. Plain `http://localhost:8080` /
`http://127.0.0.1:5500` development never receives the header, so browsers cannot pin the
local host to HTTPS. Everything is overridable via `app.security.headers.*` /
`SECURITY_HEADERS_*` environment variables.

## 6. Static frontend (pages served by a web server, not the backend)

The HTML pages are served by a static web server (locally VS Code Live Server on `:5500`).
Headers on those documents must come from that server — copy one of the ready-made snippets:

- Nginx: `deploy/nginx-security-headers.conf`
- Apache: `deploy/apache-security-headers.htaccess`

Both carry the same header set and the same CSP, with a placeholder for the API origin so
`connect-src` covers the backend when it lives on a different host.

## 7. Rollout tip

Set `SECURITY_HEADERS_CSP_REPORT_ONLY=true` on a new environment first, watch the browser
console for violations, then flip back to enforcing.
