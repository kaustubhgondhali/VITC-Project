/* VITC — Student Admin sidebar drawer + header (mobile toggle, header name/avatar).
   Self-contained and null-checked: does not touch assets/js/student.js,
   assets/js/main.js, or any public-site behaviour. Loaded only by
   student-dashboard.html / student-course.html / student-profile.html. */
(function () {
  /* Reads the same session object assets/js/student.js already writes to
     localStorage after login (SESSION_KEY = "vitc_student_session") -
     read-only, never touches auth/session logic itself. Populates the
     .sa-header student name + initials avatar with real logged-in data;
     if no session is present yet this simply leaves the header hidden. */
  function initHeader() {
    var nameEl = document.getElementById("saHeaderName");
    var avatarEl = document.getElementById("saHeaderAvatar");
    var wrapEl = document.getElementById("saHeaderUser");
    if (!nameEl || !avatarEl || !wrapEl) return;

    var session = null;
    try { session = JSON.parse(window.localStorage.getItem("vitc_student_session") || "null"); }
    catch (e) { session = null; }
    if (!session || !session.fullName) return;

    var parts = String(session.fullName).trim().split(/\s+/).filter(Boolean);
    var initials = parts.length
      ? (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase()
      : "S";

    nameEl.textContent = session.fullName;
    avatarEl.textContent = initials;
    wrapEl.hidden = false;
  }

  document.addEventListener("DOMContentLoaded", function () {
    initHeader();

    var toggle = document.getElementById("saMenuToggle");
    var sidebar = document.getElementById("saSidebar");
    var backdrop = document.getElementById("saBackdrop");
    if (!toggle || !sidebar || !backdrop) return;

    function closeSidebar() {
      sidebar.classList.remove("open");
      backdrop.classList.remove("open");
      toggle.setAttribute("aria-expanded", "false");
    }
    function openSidebar() {
      sidebar.classList.add("open");
      backdrop.classList.add("open");
      toggle.setAttribute("aria-expanded", "true");
    }

    toggle.addEventListener("click", function () {
      if (sidebar.classList.contains("open")) closeSidebar();
      else openSidebar();
    });
    backdrop.addEventListener("click", closeSidebar);
    sidebar.querySelectorAll("a").forEach(function (a) {
      a.addEventListener("click", closeSidebar);
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") closeSidebar();
    });
  });
})();
