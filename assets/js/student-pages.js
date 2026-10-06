/* VITC — Student Admin: Assignments, Certificates and Resources tabs.
   Self-contained: reuses the existing authenticated client in
   assets/js/student.js (window.VITCStudent) and the public catalogue client
   in assets/js/api.js (window.VITC.api). It never touches auth/session logic,
   public-site behaviour, Main Admin or Teacher Admin code, and every value
   rendered here comes from the backend — no hard-coded student data. */
(function (window, document) {
  "use strict";

  var S = window.VITCStudent;

  function esc(v) {
    return String(v == null ? "" : v).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }
  function fmtDate(v) {
    if (!v) return "—";
    var d = new Date(v);
    if (isNaN(d.getTime())) return "—";
    return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
  }
  function requireSession() {
    var raw = null;
    try { raw = JSON.parse(window.localStorage.getItem("vitc_student_session") || "null"); }
    catch (e) { raw = null; }
    if (!raw) { window.location.replace("student-login.html"); return null; }
    return raw;
  }
  /* loading / error / body switch shared by all three pages */
  function view(prefix) {
    return {
      loading: document.getElementById(prefix + "Loading"),
      error: document.getElementById(prefix + "Error"),
      errorMsg: document.getElementById(prefix + "ErrorMsg"),
      retry: document.getElementById(prefix + "Retry"),
      body: document.getElementById(prefix + "Body"),
      show: function (which, msg) {
        if (this.loading) this.loading.style.display = which === "loading" ? "" : "none";
        if (this.error) this.error.style.display = which === "error" ? "" : "none";
        if (this.body) this.body.style.display = which === "body" ? "" : "none";
        if (which === "error" && this.errorMsg && msg) this.errorMsg.textContent = msg;
      }
    };
  }
  function bindLogout() {
    var btn = document.getElementById("subNavLogout");
    if (!btn || !S) return;
    btn.addEventListener("click", async function () {
      try { await S.logout(); } catch (e) { /* session cleared anyway */ }
      window.location.href = "student-login.html";
    });
  }
  function emptyBox(title, body, cta) {
    return '<div class="student-state-box"><h3 style="margin:0 0 6px">' + esc(title) + "</h3>"
      + '<p style="margin:0;color:var(--muted)">' + esc(body) + "</p>"
      + (cta || "") + "</div>";
  }
  function boot(prefix, loader) {
    var v = view(prefix);
    async function run() {
      v.show("loading");
      try {
        await loader();
        v.show("body");
      } catch (err) {
        if (err && /401|unauthor/i.test(err.message || "")) {
          window.location.replace("student-login.html");
          return;
        }
        v.show("error", (err && err.message) || "Something went wrong.");
      }
    }
    if (v.retry) v.retry.addEventListener("click", run);
    run();
  }

  /* ------------------------------ Assignments ------------------------------ */
  document.addEventListener("DOMContentLoaded", function () {
    if (!document.getElementById("studentAssignmentsPage")) return;
    if (!requireSession()) return;
    bindLogout();

    var grid = document.getElementById("assignmentsGrid");

    function card(a) {
      var meta = [a.tech, a.difficulty, a.deliveryDays ? "Delivery in " + a.deliveryDays : null]
        .filter(Boolean).join(" • ");
      var price = (window.VITC && window.VITC.INR) ? window.VITC.INR(a.price) : a.price;
      return '<div class="student-course-card">'
        + '<div class="thumb" aria-hidden="true"><span class="thumb-ico">' + esc(a.icon || "📝") + "</span>"
        + (a.category ? '<span class="pill">' + esc(a.category) + "</span>" : "") + "</div>"
        + '<div class="body">'
        + '<h3 class="course-card-title">' + esc(a.title) + "</h3>"
        + (a.description ? '<p class="desc">' + esc(a.description) + "</p>" : "")
        + (meta ? '<p class="course-card-meta">' + esc(meta) + "</p>" : "")
        + '<p class="progress-label">' + esc(price) + "</p>"
        + '<div class="card-foot"><a class="btn btn-primary student-course-cta" href="assignment-details.html?id='
        + encodeURIComponent(a.code || a.id) + '">View details</a></div>'
        + "</div></div>";
    }

    boot("assignments", async function () {
      var list = await window.VITC.api.assignments();
      list = Array.isArray(list) ? list : [];
      grid.innerHTML = list.length
        ? list.map(card).join("")
        : emptyBox("No assignments published yet.", "Your VITC assignment projects will appear here once published.");
    });
  });

  /* ------------------------------ Certificates ----------------------------- */
  document.addEventListener("DOMContentLoaded", function () {
    if (!document.getElementById("studentCertificatesPage")) return;
    if (!requireSession()) return;
    bindLogout();

    var grid = document.getElementById("certificatesGrid");
    var pending = document.getElementById("certificatesPending");

    function certCard(c, studentName) {
      return '<div class="student-course-card is-complete">'
        + '<div class="thumb" aria-hidden="true"><span class="thumb-ico">🏅</span>'
        + '<span class="pill pill-complete">Completed</span></div>'
        + '<div class="body">'
        + '<h3 class="course-card-title">' + esc(c.courseTitle) + "</h3>"
        + '<p class="instructor">Awarded to ' + esc(studentName) + "</p>"
        + '<p class="course-card-meta">Course code: ' + esc(c.courseCode || "—")
        + " • Completed " + esc(fmtDate(c.lastWatchedAt)) + "</p>"
        + '<p class="progress-label">' + (c.completedLessons || 0) + "/" + (c.totalLessons || 0) + " lessons completed</p>"
        + '<div class="card-foot"><button class="btn btn-primary student-course-cta" type="button" data-print="'
        + esc(c.courseId) + '">Print certificate</button></div>'
        + "</div></div>";
    }

    function printCertificate(course, profile) {
      var w = window.open("", "_blank", "width=1000,height=720");
      if (!w) return;
      w.document.write(
        "<!DOCTYPE html><html><head><title>VITC Certificate — " + esc(course.courseTitle) + "</title>"
        + "<style>body{font-family:Georgia,serif;text-align:center;padding:60px;color:#0B3D91}"
        + ".frame{border:10px double #0B3D91;padding:60px 40px;border-radius:8px}"
        + "h1{font-size:34px;margin:0 0 8px;letter-spacing:.06em}h2{font-size:28px;margin:22px 0 6px}"
        + "p{font-size:16px;color:#333}small{color:#666}</style></head><body onload=\"window.print()\">"
        + '<div class="frame"><h1>CERTIFICATE OF COMPLETION</h1>'
        + "<p>Vandana IT Course (VITC)</p><p>This is to certify that</p>"
        + "<h2>" + esc(profile.fullName) + "</h2>"
        + "<p>Student ID: " + esc(profile.studentLoginId) + "</p>"
        + "<p>has successfully completed the course</p>"
        + "<h2>" + esc(course.courseTitle) + "</h2>"
        + "<p>on " + esc(fmtDate(course.lastWatchedAt)) + "</p>"
        + "<p><small>" + (course.completedLessons || 0) + " of " + (course.totalLessons || 0)
        + " lessons completed</small></p></div></body></html>"
      );
      w.document.close();
    }

    boot("certificates", async function () {
      var results = await Promise.all([S.me(), S.courses()]);
      var profile = results[0] || {};
      var courses = Array.isArray(results[1]) ? results[1] : [];
      var done = courses.filter(function (c) { return (c.progressPercentage || 0) >= 100; });
      var rest = courses.filter(function (c) { return (c.progressPercentage || 0) < 100; });

      grid.innerHTML = done.length
        ? done.map(function (c) { return certCard(c, profile.fullName || "Student"); }).join("")
        : emptyBox("No certificates yet.",
            "Complete all lessons of an enrolled course and its certificate appears here automatically.",
            '<div style="margin-top:14px"><a class="btn btn-ghost" href="student-dashboard.html#studentCourses">Go to My Courses</a></div>');

      pending.innerHTML = rest.length
        ? rest.map(function (c) {
            var pct = c.progressPercentage || 0;
            return '<div style="margin-bottom:16px"><div style="display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap">'
              + "<b>" + esc(c.courseTitle) + "</b><span>" + pct + "%</span></div>"
              + '<div class="progress-track" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="'
              + pct + '"><span style="width:' + pct + '%"></span></div></div>';
          }).join("")
        : '<p style="color:var(--muted);margin:0">Nothing in progress right now.</p>';

      grid.querySelectorAll("[data-print]").forEach(function (btn) {
        btn.addEventListener("click", function () {
          var course = done.filter(function (c) { return String(c.courseId) === btn.getAttribute("data-print"); })[0];
          if (course) printCertificate(course, profile);
        });
      });
    });
  });

  /* -------------------------------- Resources ------------------------------ */
  document.addEventListener("DOMContentLoaded", function () {
    if (!document.getElementById("studentResourcesPage")) return;
    if (!requireSession()) return;
    bindLogout();

    var list = document.getElementById("resourcesList");

    function lessonRow(courseId, l) {
      return '<li style="display:flex;justify-content:space-between;gap:10px;align-items:center;padding:8px 0;border-bottom:1px solid var(--line)">'
        + '<span>' + (l.completed ? "✅ " : "▫️ ") + esc(l.title)
        + (l.duration ? ' <small style="color:var(--muted)">(' + esc(l.duration) + ")</small>" : "") + "</span>"
        + (l.locked
            ? '<small style="color:var(--muted)">🔒 Locked</small>'
            : '<a class="btn btn-ghost btn-sm" href="student-course.html?courseId=' + encodeURIComponent(courseId)
              + "&lessonId=" + encodeURIComponent(l.id) + '">Open</a>')
        + "</li>";
    }

    boot("resources", async function () {
      var courses = await S.courses();
      courses = Array.isArray(courses) ? courses : [];
      if (!courses.length) {
        list.innerHTML = emptyBox("No resources yet.",
          "Course materials appear here once you are enrolled in a course.",
          '<div style="margin-top:14px"><a class="btn btn-ghost" href="courses.html">Browse courses</a></div>');
        return;
      }
      var modulesPerCourse = await Promise.all(courses.map(function (c) {
        return S.modules(c.courseId).catch(function () { return []; });
      }));

      list.innerHTML = courses.map(function (c, i) {
        var mods = Array.isArray(modulesPerCourse[i]) ? modulesPerCourse[i] : [];
        var inner = mods.length
          ? mods.map(function (m) {
              var lessons = Array.isArray(m.lessons) ? m.lessons : [];
              return '<div style="margin-bottom:18px"><h3 style="margin:0 0 6px;font-size:1rem">'
                + esc(m.title) + ' <small style="color:var(--muted);font-weight:500">('
                + (m.completedLessons || 0) + "/" + (m.totalLessons || lessons.length) + " lessons)</small></h3>"
                + (m.description ? '<p style="color:var(--muted);margin:0 0 8px;font-size:.9rem">' + esc(m.description) + "</p>" : "")
                + '<ul style="list-style:none;margin:0;padding:0">'
                + lessons.map(function (l) { return lessonRow(c.courseId, l); }).join("")
                + "</ul></div>";
            }).join("")
          : '<p style="color:var(--muted);margin:0">No materials published for this course yet.</p>';

        return '<div class="student-auth-card" style="margin-bottom:22px">'
          + '<div class="profile-section-head"><h2>' + esc(c.courseTitle) + "</h2>"
          + '<a class="btn btn-ghost" href="student-course.html?courseId=' + encodeURIComponent(c.courseId) + '">Open course</a></div>'
          + inner + "</div>";
      }).join("");
    });
  });
})(window, document);
