# PART 17 — Student Admin Panel: Remove Public Navbar & Professional LMS Redesign

## Scope
Frontend-only, and only the 3 Student Admin pages:
`student-dashboard.html`, `student-course.html`, `student-profile.html`.
(`student-login.html` already had no public navbar and is unchanged.)
No backend, database, authentication, Main Admin, Teacher Admin, or public
website file was touched — verified with a full diff against the prior
project state.

## Step 1 — Analysis performed before changing anything
- The public navbar (`<header class="header">`), public footer
  (`<footer class="footer">`), marketing floats (WhatsApp/Call/Email/
  Back-to-top) and the "Free Career Counselling" promo popup are all
  **directly written, duplicated static HTML** in every page of the site —
  not a shared include, not injected by JS, not loaded dynamically. They
  existed in these 3 Student Admin files only because the pages were
  originally cloned from a public-page template.
- Student Admin's own navigation was the horizontal `.student-subnav`
  strip (Dashboard / My Courses / Continue Learning / Profile / Logout) —
  already present and already wired to real functionality. This is the
  only navigation set the redesign carries forward (per the "no fake
  functionality" instruction, no Certificates/Resources/Settings/Progress
  items were invented — those pages/APIs don't exist in this project yet).
- `assets/js/main.js` (shared, loads on every page including the public
  site) only ever does `if (el) ...` null-checks against `.header`,
  `.hamburger`, `.nav-links`, `#backTop`, `#popup`, `.newsletter`, etc. —
  confirmed before deleting anything, so removing these nodes from the 3
  Student Admin pages causes zero console errors.
- `assets/js/student.js` / `student-learning.js` (the actual student
  functionality) bind to specific element **IDs** only: `studentDashboard`,
  `studentCoursePage`, `studentProfilePage`, `dashboardSubNav`,
  `subNavLogout`, `courseHeroTitle`, `courseCrumb`, plus all the
  dashboard/course/profile content IDs. None of them care about the
  surrounding layout markup — confirmed by reading every
  `getElementById`/`querySelector` call in both files first.

## Step 2 & 3 — What changed
For all 3 pages:
- Removed entirely: public `<header>` navbar, public `<section class="page-hero">`
  banner, public `<footer>`, `.floats` (WhatsApp/Call/Email/Back-to-top),
  and the `#popup` promo modal.
- Replaced with a Student Admin **sidebar shell** (`.sa-shell` / `.sa-sidebar`
  / `.sa-main`):
  - Sidebar: VITC logo + "Student Learning Portal" label, then the same
    5 nav items as before (Dashboard, My Courses, Continue Learning,
    Profile, Logout) — same hrefs, same IDs, same active-state logic,
    just laid out vertically instead of as a horizontal pill strip.
  - A slim topbar + slide-in drawer (with backdrop) appears only on
    screens ≤960px, replacing the old public-site hamburger menu.
  - Main content column keeps every existing dashboard/course/profile
    section, ID, and class exactly as it was — only the surrounding page
    header was replaced with a simpler `.sa-page-head` (page title +
    subtitle, and for the course page a 2-level "My Courses / <course
    title>" trail instead of the old "Home / My Learning / Course"
    breadcrumb that linked out to the public site).

## Step 10 — File safety
- `assets/css/student-portal.css` is loaded **only** by these 3 pages
  (confirmed by grep across the whole project before editing) — all new
  sidebar CSS lives there, scoped under `body.student-portal` / `.sa-*`
  classes, reusing existing `:root` tokens from `style.css` (`--primary`,
  `--muted`, `--border`, `--shadow-lg`, `--container`) rather than
  introducing new ones. `style.css` (shared by the public site) was not
  touched.
- New file `assets/js/student-sidebar.js` (mobile drawer toggle only) is
  self-contained, null-checked, and included only by the 3 Student Admin
  pages — it does not modify `student.js`, `main.js`, or any public-page
  behaviour.

## Verification
- [x] Public navbar (header, nav links, logo-as-public-brand, Apply Now CTA, hamburger) completely removed from all 3 pages
- [x] Public footer (Quick Links, Popular Courses, newsletter, admin login links) removed
- [x] Public marketing floats and promo popup removed
- [x] Student Admin sidebar renders with Dashboard / My Courses / Continue Learning / Profile / Logout
- [x] All required functional IDs present exactly once: `studentDashboard`, `studentCoursePage`, `studentProfilePage`, `dashboardSubNav`, `subNavLogout` (×3), `courseHeroTitle`, `courseCrumb`
- [x] Dashboard, My Courses, Continue Learning, course video/lessons/progress, Profile edit/password — markup and IDs untouched, so existing JS/API calls are unaffected
- [x] Logout button present on every page (sidebar) — same click handler as before
- [x] Responsive: sidebar becomes a topbar + slide-in drawer ≤960px; no public hamburger menu appears
- [x] No shared file (`style.css`, `main.js`, `student.js`, `student-learning.js`, `api.js`, `site.js`, `upgrade.js`) modified
- [x] Backend, database, Main Admin, Teacher Admin, public website — byte-identical to the pre-change project (confirmed via full diff)
