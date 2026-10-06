# PART 16 — Remove Public Website Navigation from Student Admin (Surgical UI Change)

## Scope
Frontend-only. No backend, database, authentication, Main Admin, Teacher Admin,
or public-website changes.

## Investigation
- Public navbar markup (`<header class="header">` with `.nav-links` and
  `.nav-cta`) is duplicated per static HTML page (not a shared
  include/component) across the whole site.
- Only 3 files under Student Admin actually contain this public navbar:
  - `student-dashboard.html`
  - `student-course.html`
  - `student-profile.html`
- `student-login.html` never had the public navbar (it already uses its own
  standalone login card layout) — no change needed there.
- `.header` is `position:fixed`; `.page-hero` has `padding-top:150px`
  specifically to clear the fixed header's height. Deleting the header
  element outright would leave a large blank gap above "My Learning" and
  violate the "no blank space" requirement — so the header bar itself
  (logo/brand) is kept, and only the public links + CTA are hidden.
- `assets/js/main.js` reads `.header`, `.hamburger`, `.nav-links` with
  null-safe checks (`if (burger && links)`), so hiding these nodes with CSS
  (rather than deleting them) causes zero console errors.

## Change made
1. Added a `student-portal` class to `<body>` on the 3 files above
   (`class="inner student-portal"`).
2. Added one scoped CSS rule to `assets/css/student-portal.css` (already
   loaded ONLY by these 3 pages — never by the public site):

   ```css
   body.student-portal .header .nav-links,
   body.student-portal .header .nav-cta{ display:none !important; }
   body.student-portal .header .container.nav{ justify-content:flex-start; }
   ```

No HTML nodes were deleted, no other file was touched, `style.css` (shared
by the public website) was not modified.

## Resulting Student Admin layout
```
Header bar (logo / "Vandana IT Course" brand only)
        ↓
My Learning / page-hero
        ↓
Student sub-nav: Dashboard | My Courses | Continue Learning | Profile | Logout
        ↓
Existing Student Admin content
```

## Verification checklist
- [x] Public navbar links/CTA hidden on Student Admin pages
- [x] Student Admin sub-navigation unchanged and functional
- [x] Dashboard / My Courses / Continue Learning / Profile / Logout untouched
- [x] Full Stack Development course, lessons, videos, progress — markup/JS untouched
- [x] Student authentication — untouched
- [x] Backend / database — untouched
- [x] Teacher Admin / Main Admin — untouched
- [x] Public website navbar & links — unchanged (style.css untouched, no shared file edited)
- [x] Header height preserved → no blank gap above page-hero, no overlap
- [x] Mobile hamburger also hidden (it lived inside `.nav-cta`, which is now hidden) — no orphaned floating button
- [x] No DOM nodes removed → `assets/js/main.js` null-checks still pass, no console errors
