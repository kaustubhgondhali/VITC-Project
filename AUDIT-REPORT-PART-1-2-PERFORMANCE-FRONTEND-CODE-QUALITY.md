# VITC Website — PART 1-2: Performance & Frontend Code-Quality Audit

Scope: authenticated Student portal (`student-*.html`, `assets/js/student.js`,
`assets/js/student-learning.js`, `assets/css/student-portal.css`, shared
`assets/css/style.css`) and Teacher portal (`teacher-admin/*.html`,
`teacher-admin/assets/teacher.js`, `teacher-admin/assets/teacher.css`).

This was a static, read-only code audit (no live server, browser, or
Spring Boot backend was run in this environment — see **Verification
Limits** at the bottom before trusting any PASS/FAIL line).

---

## Performance Issues Found

- **One real duplicate CSS declaration**: `.student-welcome` and
  `.student-stats-grid` were each declared with `margin-bottom` twice in
  `assets/css/style.css` — once in the base rule (22px/26px) and once again
  ~200 lines later (24px), with the second silently winning by cascade
  order. Harmless in practice (only the final value ever rendered) but
  wasted a parse/paint of a rule that had no effect, and was confusing to
  read.
- **9 unused reviewer avatar images + 1 unused `profile.png`** in
  `assets/img/` (≈70KB total) are not referenced by any HTML, CSS, or JS
  file, and are not present in `database/seed_data.sql` either. They are
  most likely leftovers from an earlier reviewer set. Left in place (see
  below) since removing image files the DB might reference by a URL added
  outside the seed script is not something a static audit can safely
  confirm.
- No other duplicate API calls, duplicate event listeners, duplicate
  functions, memory-leak patterns, oversized images, or unnecessary
  animations were found in the Student/Teacher code — see the detail below
  for what was actually checked.

## Performance Fixes

- Merged the two `.student-welcome`/`.student-stats-grid` declarations in
  `assets/css/style.css` into one (kept the 24px value that was actually
  winning), and removed the now-empty duplicate block. Net effect: **zero
  visual change**, 2 fewer redundant rules for the browser to parse.
- No other changes were made to CSS, JS, HTML, images, or animations —
  everything else already checked out clean (details below), and the
  brief said explicitly not to rewrite working code for style reasons.

## API Improvements

Checked how `assets/js/student.js`, `assets/js/student-learning.js`,
`assets/js/site.js`, and `teacher-admin/assets/teacher.js` initialize on
each authenticated page:

- Every `DOMContentLoaded` handler in `student.js` and
  `student-learning.js` is guarded by an element-existence check
  (`document.getElementById("studentDashboard")`,
  `"studentCoursePage"`, `"studentProfilePage"`, etc.) before it does
  anything, so a handler written for one page is a silent no-op on every
  other page — it does **not** fire a duplicate request.
- `site.js` runs one shared `boot()` on every page (including the
  dashboard, since `site.js` is also loaded there for the shared nav/
  footer). Every function it calls (`loadHomeCourses`, `loadMarketplace`,
  `loadCourseCatalogue`, `loadReviews`, etc.) starts with
  `var host = el(id); if (!host) return;` — since none of those container
  IDs exist on the Student/Teacher pages, `boot()` fires zero network
  requests there. This already prevents the exact "public-page loader
  runs on the dashboard too" bug the brief was checking for.
- No polling (`setInterval` and API calls) was found anywhere in the
  Student or Teacher JS.
- No endpoint URLs, request methods, or request/response shapes were
  changed, per the constraint in the brief.

**Result: no duplicate-request bug existed to fix.** No caching was added.

## JavaScript Improvements

- Checked for duplicate top-level function names across
  `assets/js/*.js`, `teacher-admin/assets/teacher.js`, and
  `admin/assets/admin.js` — none found. Every file is wrapped in its own
  `(function (window, document) { ... })(window, document)` IIFE, so
  there's no accidental global-scope collision between Student, Teacher,
  and Admin code.
- `node --check` was run against every JS file involved
  (`api.js`, `site.js`, `main.js`, `student.js`, `student-learning.js`,
  `upgrade.js`, `payments.js`, `java-course.js`, `content-data.js`,
  `teacher-admin/assets/teacher.js`, `admin/assets/admin.js`) — all pass
  with zero syntax errors, before and after the CSS-only change above.
- No unused functions, unsafe implicit globals, or obvious leak patterns
  (e.g. listeners added without ever being removed on a page with a long
  SPA-like lifetime) were found. Since Student/Teacher pages are classic
  multi-page navigations (full reload per page), listener cleanup on
  unload isn't a real leak risk here the way it would be in an SPA.
- No changes were made to any `.js` file — nothing unsafe or duplicated
  was found to fix, and the brief was explicit about not rewriting working
  logic.

## CSS Improvements

- One real duplicate resolved (above).
- The other repeated selectors the scan flagged
  (`.modal`, `.toast`, `.spinner`, `@media (max-width:480px)`, etc. in
  `teacher.css`, and various breakpoint blocks in `style.css`) are all
  legitimate — either the same breakpoint used for several unrelated
  rule groups (normal, not a bug) or a base rule plus a narrower
  media-query override (responsive design working as intended). None of
  these were touched.
- No conflicting media queries, runaway global selectors, or
  Student/Teacher rules leaking into public-page selectors were found.

## Asset Improvements

- Every image under `assets/img/` is already small (largest is the 60KB
  logo; everything else is 4–40KB) — nothing was worth compressing further.
- Logo `<img>` tags in the Student/Teacher header are correctly **not**
  lazy-loaded (they're above the fold on every page load), so no
  `loading="lazy"` was added there.
- 10 unused image files were identified (listed above) but left in place
  — flagging for your own removal decision rather than deleting files a
  static audit can't fully prove are safe to delete.
- No image paths were changed; nothing was renamed or moved.

## Animations

- `prefers-reduced-motion` is already respected in `student-portal.css`,
  `teacher.css`, and `style.css`.
- `teacher.css`'s spinner deliberately *slows* (rather than removes) its
  animation under reduced-motion, which is a reasonable accommodation for
  a loading indicator (it still needs to convey "in progress") — left
  as-is.
- No CPU-heavy or usability-interfering animations were found in the
  authenticated portals.

---

## Accessibility (from Part 1)

Not re-run in this pass — Part 1 covered accessibility and responsive
testing separately; nothing done here touched markup, ARIA attributes, or
focus order, so those results should be unaffected. If you want this
confirmed rather than assumed, say so and it can be re-checked here too.

## Responsive Testing

No new responsive testing was performed in this pass. The CSS edit only
changed which of two already-identical-in-effect margin values is now
declared once instead of twice — it does not change layout at any
breakpoint, so previously-verified responsive results should still hold.
No live browser was used to re-confirm this visually (see limits below).

## Build Result

**NOT RUN.** This sandbox has no access to Maven Central (network is
restricted to a fixed allow-list of domains that doesn't include Maven's
repositories), so `mvn clean install` / `mvn spring-boot:run` on
`backend/` cannot be executed here. `node --check` was run on every JS
file as a syntax-only substitute for a frontend build (there is no
bundler/build step in this project — it's static HTML/CSS/JS) and all
files pass.

## Regression Result

**NOT RUN.** There's no live backend or browser in this environment, so
the Student and Teacher click-through flows listed in the brief
(Login → Dashboard → … → Logout) were not actually executed — only
statically reasoned about from the code. Please treat this as "nothing
found that should break those flows," not as "flows were tested and
passed."

## Remaining Issues

- 10 unused image files (listed above) — safe to delete if you confirm
  the DB doesn't reference them by a path outside `seed_data.sql`.
- Backend build/regression and live-browser verification still need to be
  run on your machine — this audit could not execute either in this
  sandbox.

---

## Verification Limits (please read before trusting PASS/FAIL above)

This environment can read and statically analyze code, but it cannot run
your Spring Boot backend (no Maven Central access) or a real browser
against your dashboard pages. Everything above is based on:
`node --check` for JS syntax, a CSS brace-balance/duplicate-selector scan,
`grep`-based tracing of event-listener guards and fetch call sites, and
manual reading of the relevant files. That's a real audit, but it is not
the same as running the app — please run the actual login → logout flows
locally before shipping.
