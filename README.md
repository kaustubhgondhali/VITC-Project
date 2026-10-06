# Vandana IT Course (VITC) Website

Premium educational institute website — pure HTML5, CSS3 & Vanilla JavaScript.
No frameworks. No build step. Just open `index.html`.

## Pages
- index.html (Home)
- about.html
- courses.html
- contact.html
- testimonials.html
- gallery.html
- blog.html
- career.html
- internship.html
- reviews.html
- 404.html

## Structure
- `assets/css/style.css` — full design system
- `assets/js/main.js` — animations, forms, filters, lightbox, popup
- `assets/img/logo.jpeg` — official VITC logo (used as favicon too)

## Deploy
Upload the whole folder to any static host (Netlify, Vercel, GitHub Pages, cPanel).

Contact: info@vandanaitcourse.com • +91 99678 74887

## Payment Module

Frontend pages
- `payment.html` — checkout (UPI, Card, Wallet, Net Banking) + coupons
- `payment-success.html` — order confirmation
- `order-summary.html?order=CODE` — full order summary
- `invoice.html?order=CODE` — printable GST invoice
- `assets/js/payments.js` — API client + checkout orchestration (falls back to a
  local simulation when the backend is not running)

Backend APIs (`/api/v1`)
- `POST /checkout/orders` — create an order (totals, coupon, GST computed server-side)
- `GET  /checkout/orders/{orderCode}` — order summary
- `POST /checkout/orders/{orderCode}/pay` — initiate a payment attempt
- `POST /checkout/orders/{orderCode}/confirm` — verify payment, mark order PAID, issue invoice
- `GET  /checkout/orders/{orderCode}/confirmation` — order + payment + invoice bundle
- `POST /checkout/orders/{orderCode}/cancel` — cancel an unpaid order
- `GET  /invoices`, `/invoices/{id}`, `/invoices/number/{no}`, `/invoices/order/{orderCode}`
- Existing `/payments` CRUD endpoints remain unchanged

Tables: `payment_orders`, `payments`, `invoices` (created by `database/vitc_db_fresh.sql`).

Gateway architecture: everything goes through `com.vitc.payment.gateway.PaymentGateway`.
`MockPaymentGateway` is active by default (`payment.gateway=mock`).
To switch to Razorpay later: add the Razorpay SDK, implement
`RazorpayPaymentGateway` (createOrder / verify / refund), and set
`payment.gateway=razorpay`, `payment.razorpay.key-id`, `payment.razorpay.key-secret`.
No service, controller, entity or frontend change is required.

## Part 3/10 — Audio Upload & Media Validation

The backend now supports secure MP3, WAV, AAC, M4A, OGG, FLAC, WMA and OPUS uploads from
the admin and teacher course-content APIs. Audio is validated from file signatures in addition
to the filename and browser MIME type, stored with a generated filename, and recorded in
`media_files` as `AUDIO`.

For an existing database, run `database/audio_upload_part3_migration.sql` once. The upload
endpoints are:

- `PUT /api/v1/admin/lessons/{lessonId}/audio`
- `PUT /api/v1/teacher/lessons/{lessonId}/audio`
- `DELETE` the same paths to remove lesson audio

Send the audio as a multipart field named `file`. The default audio limit is 100MB and can be
changed with `app.upload.security.audio-max-size`.

## Part 4/10 — Universal Media Compatibility

The backend probes lesson media with `ffprobe`. Browser-compatible MP4/H.264/AAC video and
MP3 or AAC/M4A audio are preserved. Other valid video/audio inputs are converted automatically
with FFmpeg to MP4/H.264/AAC or MP3. Invalid, stream-mismatched, unsupported, unavailable, or
failed conversions return a clear 400 error; they are never silently accepted.

Configure `app.media.ffmpeg-binary`, `app.media.ffprobe-binary`, and
`app.media.transcode-timeout-seconds` for deployment. FFmpeg and FFprobe are optional for
browser-compatible media: a valid MP4 with a verified MP4 signature and `video/mp4` MIME type
can be stored directly when the tools are unavailable. Set `MEDIA_FFMPEG_ENABLED=false` or
`app.media.ffmpeg-enabled=false` to disable conversion; unsupported media that needs transcoding
then returns a clear conversion-required error.

## Part 5/10 — Upload Size & Safe Storage

Upload transport limits have one source of truth in `backend/src/main/resources/application.properties`:
`app.media.max-file-size` (500MB), `app.media.max-request-size` (520MB), and
`app.media.audio-max-file-size` (100MB). Spring multipart limits and category validation reference
these values; do not raise only one layer. Reverse proxies must allow the same request size.

Physical media is stored below the configured `app.upload.dir` with generated UUID/date filenames.
Original filenames are retained only as sanitized display metadata. The database stores references
and metadata, never binary media.

## Part 6/10 — Media Lifecycle

Lesson activation is the lifecycle switch for attached video and audio. Teacher controls are
available at `PATCH /api/v1/teacher/lessons/{lessonId}/status?active=true|false`; inactive
lessons are excluded by the student learning queries and cannot be opened, while their files
remain available for later reactivation. Main admins use the existing lesson update endpoint.
Lesson deletion removes the lesson reference first and then safely cleans only its managed media.
Replacement uploads and validates the new file before switching the lesson reference.
