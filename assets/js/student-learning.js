/* =======================================================================
   VITC — Student learning portal (student-course.html)
   All lessons, modules and video urls come from the backend:
     GET  /api/v1/student/courses/{courseId}
     GET  /api/v1/student/lessons/{lessonId}
     POST /api/v1/student/lessons/{lessonId}/progress
   Nothing about the curriculum is hard-coded here, and a video url is only
   ever received after the backend verified the enrolment (403 otherwise).
   ======================================================================= */
(function (window, document) {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    var page = document.getElementById("studentCoursePage");
    if (!page || !window.VITCStudent) return;

    var S = window.VITCStudent;
    var params = new URLSearchParams(window.location.search);
    var courseId = params.get("courseId");
    var requestedLessonId = params.get("lessonId");
    var loading = document.getElementById("courseLoading");
    var errorBox = document.getElementById("courseError");
    var errorText = document.getElementById("courseErrorText");
    var errorActions = document.getElementById("courseErrorActions");
    var bodyBox = document.getElementById("courseBody");
    var outline = document.getElementById("courseOutline");
    var outlineToggle = document.getElementById("courseOutlineToggle");
    var currentLesson = null;

    if (!S.session()) {
      window.location.replace("student-login.html");
      return;
    }

    function esc(v) {
      return String(v == null ? "" : v).replace(/[&<>"']/g, function (ch) {
        return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
      });
    }

    /* Error state, PART 5: distinguishes "nothing to load" (no courseId in
       the URL) from a real API/authorisation failure so the recovery action
       offered actually makes sense - Retry re-runs the exact same request,
       Back to My Courses is offered whenever the course itself is the
       problem (invalid id, not enrolled, course removed). */
    function fail(message, opts) {
      opts = opts || {};
      loading.style.display = "none";
      bodyBox.style.display = "none";
      errorBox.style.display = "block";
      errorText.textContent = message;
      var actions = [];
      if (opts.retry) {
        actions.push('<button type="button" class="btn btn-primary" id="courseErrorRetry">Retry</button>');
      }
      actions.push('<a class="btn btn-ghost" href="student-dashboard.html">← Back to My Courses</a>');
      errorActions.innerHTML = actions.join("");
      var retryBtn = document.getElementById("courseErrorRetry");
      if (retryBtn) retryBtn.addEventListener("click", function () { window.location.reload(); });
    }

    function lessonMsg(text, isError) {
      var el = document.getElementById("lessonMsg");
      if (!el) return;
      if (!text) { el.style.display = "none"; return; }
      el.style.display = "block";
      el.className = "student-msg" + (isError ? " error" : " ok");
      el.textContent = text;
    }

    /* ------------------------- outline ------------------------- */
    function renderCourse(course) {
      var heroTitle = document.getElementById("courseHeroTitle");
      if (heroTitle) {
        heroTitle.textContent = course.courseTitle;
      }
      var title = document.getElementById("courseTitle");
      if (title) title.textContent = course.courseTitle;
      document.title = course.courseTitle + " | Vandana IT Course (VITC)";
      var meta = document.getElementById("courseMeta");
      if (meta) {
        meta.textContent = [course.level, course.category, course.durationMonths].filter(Boolean).join(" \u2022 ");
      }
      setProgress(course.progressPercentage, course.completedLessons, course.totalLessons);
      renderModules(course.modules);
    }

    function setProgress(pct, done, total) {
      pct = typeof pct === "number" ? pct : 0;
      var bar = document.getElementById("courseProgressBar");
      if (bar) {
        bar.style.width = pct + "%";
        var track = bar.parentElement;
        if (track) {
          track.setAttribute("role", "progressbar");
          track.setAttribute("aria-valuemin", "0");
          track.setAttribute("aria-valuemax", "100");
          track.setAttribute("aria-valuenow", String(pct));
          track.setAttribute("aria-label", "Course progress: " + pct + "%");
        }
        var box = bar.closest(".course-progress-box");
        if (box) box.classList.toggle("is-complete", pct >= 100);
      }
      var progressText = document.getElementById("courseProgressText");
      if (progressText) {
        progressText.textContent = pct + "%" + (total ? " • " + done + "/" + total + " lessons" : "");
      }
      var completion = document.getElementById("courseCompletion");
      if (completion) completion.hidden = !(total > 0 && done === total);
    }

    function renderModules(modules) {
      var host = document.getElementById("courseModules");
      if (!host) return;
      if (!modules || !modules.length) {
        host.innerHTML = '<div class="student-empty-state">'
          + '<span class="ico" aria-hidden="true">\uD83C\uDF9E\uFE0F</span>'
          + '<h3 style="margin:0 0 8px">No lessons yet</h3>'
          + '<p style="margin:0">Lessons for this course are being published. Please check back soon.</p>'
          + '</div>';
        return;
      }
      host.innerHTML = modules.map(function (m, i) {
        var panelId = "moduleLessons" + (m.id != null ? m.id : i);
        var lessons = (m.lessons || []).map(function (l) {
          var icon = l.completed ? "\u2713" : (l.locked ? "\uD83D\uDD12" : "\u25B6");
          var cls = "lesson-row" + (l.completed ? " done" : "") + (l.locked ? " locked" : "");
          var label = esc(l.title) + (l.completed ? " (completed)" : (l.locked ? " (locked)" : ""));
          return '<button type="button" class="' + cls + '" data-lesson="' + l.id + '"'
            + (l.locked ? ' data-locked="1" aria-disabled="true"' : "") + ' aria-label="' + label + '">'
            + '<span class="lesson-icon" aria-hidden="true">' + icon + "</span>"
            + '<span class="lesson-name">' + esc(l.title) + "</span>"
            + (l.duration ? '<span class="lesson-time">' + esc(l.duration) + "</span>" : "")
            + "</button>";
        }).join("");
        return '<div class="module-block' + (i === 0 ? " open" : "") + '">'
          + '<button type="button" class="module-head" aria-expanded="' + (i === 0 ? "true" : "false") + '"'
          + ' aria-controls="' + panelId + '"><span class="caret" aria-hidden="true">\u25BC</span> '
          + esc(m.title) + ' <em><span class="sr-only">Lessons completed: </span>'
          + m.completedLessons + "/" + m.totalLessons + "</em></button>"
          + '<div class="module-lessons" id="' + panelId + '" role="group" aria-label="' + esc(m.title) + ' lessons">'
          + lessons + "</div></div>";
      }).join("");

      host.querySelectorAll(".module-head").forEach(function (btn) {
        btn.addEventListener("click", function () {
          var open = btn.parentElement.classList.toggle("open");
          btn.setAttribute("aria-expanded", open ? "true" : "false");
        });
      });
      host.querySelectorAll(".lesson-row").forEach(function (btn) {
        btn.addEventListener("click", function () {
          if (btn.getAttribute("data-locked")) {
            lessonMsg("Finish the earlier lessons to unlock this one.", true);
            return;
          }
          openLesson(btn.getAttribute("data-lesson"));
        });
      });
    }

    /* Mobile: the module list opens as an accordion over the layout; once a
       lesson is picked, collapse it back so the video is the focus instead
       of requiring an extra manual scroll/tap. Desktop is unaffected - the
       toggle button and this class only do anything under the 900px
       breakpoint (see style.css). */
    function collapseOutlineOnMobile() {
      if (outline && window.innerWidth <= 900) {
        outline.classList.remove("nav-open");
        if (outlineToggle) outlineToggle.setAttribute("aria-expanded", "false");
      }
    }

    if (outlineToggle && outline) {
      outlineToggle.addEventListener("click", function () {
        var open = outline.classList.toggle("nav-open");
        outlineToggle.setAttribute("aria-expanded", open ? "true" : "false");
      });
    }

    /* ------------------------- player ------------------------- */
    /* PART 6C-1/8: lesson.videoUrl can be a backend-relative upload path
       (e.g. "/uploads/videos/example.mp4") or a full external URL
       (e.g. "https://example.com/video.mp4"). A relative path must be
       resolved against the backend origin - not the page's own origin,
       since the student portal HTML is often served separately from the
       Spring Boot backend (e.g. a static file server on another port).
       window.VITC.mediaUrl() already does exactly this same-project-wide;
       it passes absolute http(s) URLs straight through unchanged, so
       existing external video lessons are unaffected. */
    function resolveVideoSrc(url) {
      if (window.VITC && typeof window.VITC.mediaUrl === "function") {
        return window.VITC.mediaUrl(url);
      }
      return url;
    }

    function mediaMarkup(lesson) {
      var markup = "";
      if (lesson.videoUrl) {
        if (lesson.videoType === "FILE") {
          markup += '<video controls playsinline preload="metadata" title="' + esc(lesson.title) + '"'
            + ' aria-label="Video lesson: ' + esc(lesson.title) + '"'
            + ' src="' + esc(resolveVideoSrc(lesson.videoUrl)) + '"></video>';
        } else {
          markup += '<iframe src="' + esc(lesson.videoUrl) + '" title="' + esc(lesson.title)
            + '" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"'
            + ' referrerpolicy="strict-origin-when-cross-origin" allowfullscreen loading="lazy"></iframe>';
        }
      } else if (!lesson.audioUrl) {
        return '<div class="video-missing" role="status">'
          + '<span aria-hidden="true">\u26A0\uFE0F</span> Media coming soon</div>';
      }
      if (lesson.audioUrl) {
        markup += '<audio controls preload="metadata" title="' + esc(lesson.title) + '"'
          + ' aria-label="Audio lesson: ' + esc(lesson.title) + '"'
          + ' src="' + esc(resolveVideoSrc(lesson.audioUrl)) + '"></audio>';
      }
      return markup;
    }

    /* PART 5/7 - maps a failed stream request's real HTTP status to the exact
       user-facing copy this part specifies. The <video> element's own "error"
       event never exposes the HTTP status code (only a generic MediaError),
       so a lightweight fetch() probe against the same protected URL is used
       purely to read the status - never to fetch/display the video bytes
       themselves (the <video> element still owns actual playback). */
    function mediaErrorMessageFor(status, kind) {
      if (status === 401) return "Authentication required.";
      if (status === 403) return "You do not have access to this course.";
      if (status === 404) return (kind === "audio" ? "Audio not found." : "Video not found.");
      if (status === 415) return "This media format is not supported.";
      return "Unable to load " + (kind || "media") + ". Please try again.";
    }

    /* PART 5/7 - a lesson's signed video token has a limited lifetime
       (app.video.stream-token-ttl-minutes). If a still-open lesson's token
       expires mid-session, re-request the SAME lesson from the existing
       /api/v1/student/lessons/{id} endpoint - it mints a fresh token the
       same way it did on first load - and swap the <video> src in place.
       No second auth system, no new endpoint; one silent retry only, so a
       genuinely revoked enrolment (still 401/403 after refresh) still ends
       in the correct error message rather than a retry loop. */
    function bindMediaErrorHandling(lesson, mediaEl, kind) {
      var retried = false;
      function probeAndReport() {
        var src = mediaEl.currentSrc || mediaEl.src;
        if (!src) return;
        fetch(src, { method: "GET", headers: { Range: "bytes=0-1" } })
          .then(function (res) {
            if (res.ok || res.status === 206) return; // a transient/network blip, not an auth failure
            if (!retried && (res.status === 401 || res.status === 403)) {
              retried = true;
              S.lesson(lesson.id).then(function (fresh) {
                var freshUrl = kind === "audio" ? fresh.audioUrl : fresh.videoUrl;
                if (currentLesson && currentLesson.id === lesson.id && freshUrl) {
                  mediaEl.src = resolveVideoSrc(freshUrl);
                  mediaEl.load();
                }
              }).catch(function () {
                lessonMsg(mediaErrorMessageFor(res.status, kind), true);
              });
              return;
            }
            lessonMsg(mediaErrorMessageFor(res.status, kind), true);
          })
          .catch(function () {
            // Network-level failure (offline, CORS, server down) - same generic
            // copy this part specifies for a 500/unreachable backend.
            lessonMsg("Unable to load " + kind + ". Please try again.", true);
          });
      }
      mediaEl.addEventListener("error", probeAndReport);
    }

    function renderLesson(lesson) {
      currentLesson = lesson;
      var playerEmpty = document.getElementById("playerEmpty");
      if (playerEmpty) playerEmpty.style.display = "none";
      var playerBox = document.getElementById("playerBox");
      if (playerBox) playerBox.style.display = "block";
      var videoFrame = document.getElementById("videoFrame");
      if (videoFrame) videoFrame.innerHTML = mediaMarkup(lesson);
      var lessonTitle = document.getElementById("lessonTitle");
      if (lessonTitle) lessonTitle.textContent = "Lesson: " + lesson.title;
      var lessonMeta = document.getElementById("lessonMeta");
      if (lessonMeta) lessonMeta.textContent =
        "Lesson " + lesson.lessonNumber + " of " + lesson.totalLessons
        + (lesson.duration ? " • " + lesson.duration : "")
        + " • " + lesson.moduleTitle;
      var lessonDesc = document.getElementById("lessonDesc");
      if (lessonDesc) lessonDesc.textContent = lesson.description || "";
      var prevLesson = document.getElementById("prevLesson");
      if (prevLesson) prevLesson.disabled = !lesson.previousLessonId;
      var nextLesson = document.getElementById("nextLesson");
      if (nextLesson) nextLesson.disabled = !lesson.nextLessonId;
      var mark = document.getElementById("markComplete");
      var status = document.getElementById("lessonCompletionStatus");
      if (mark) {
        mark.disabled = false;
        mark.textContent = lesson.completed ? "Mark as Incomplete" : "Mark Complete";
      }
      if (status) status.hidden = !lesson.completed;
      lessonMsg("");
      var videoEl = videoFrame ? videoFrame.querySelector("video") : null;
      var audioEl = videoFrame ? videoFrame.querySelector("audio") : null;
      if (videoEl) bindMediaErrorHandling(lesson, videoEl, "video");
      if (audioEl) bindMediaErrorHandling(lesson, audioEl, "audio");
    }

    async function openLesson(lessonId, keepMessage) {
      if (!lessonId) return;
      try {
        var lesson = await S.lesson(lessonId);
        renderLesson(lesson);
        document.querySelectorAll(".lesson-row").forEach(function (row) {
          var current = row.getAttribute("data-lesson") === String(lessonId);
          row.classList.toggle("is-current", current);
          if (current) row.setAttribute("aria-current", "true"); else row.removeAttribute("aria-current");
        });
        if (!keepMessage) lessonMsg("");
        try {
          await S.saveProgress(lessonId, { progressPercentage: lesson.completed ? 100 : Math.max(5, lesson.progressPercentage || 0) });
        } catch (e) { /* resume marker is best-effort */ }
        var player = document.querySelector(".course-player");
        if (player) player.scrollIntoView({ behavior: "smooth", block: "start" });
      } catch (err) {
        lessonMsg(err.message, true);
      }
    }

    async function refreshOutline() {
      var course = await S.course(courseId);
      if (!Array.isArray(course.modules) || !course.modules.length) {
        course.modules = await S.modules(courseId);
      }
      renderCourse(course);
      return course;
    }

    async function loadCourse() {
      var course = await S.course(courseId);
      /* Keep the existing dedicated modules endpoint as a compatibility
         fallback for deployments whose course-detail response predates the
         embedded modules field. */
      if (!Array.isArray(course.modules) || !course.modules.length) {
        course.modules = await S.modules(courseId);
      }
      return course;
    }

    /* ------------------------- boot ------------------------- */
    (async function boot() {
      if (!courseId) { fail("No course selected. Open a course from My Courses."); return; }
      var course;
      try {
        course = await loadCourse();
      } catch (err) {
        if (/sign in|session/i.test(err.message)) {
          S.clearSession();
          window.location.replace("student-login.html");
          return;
        }
        /* "Cannot reach the VITC server..." (api.js network failure) is the
           one case worth a one-click Retry; a 403/404 message from the
           backend (invalid course, not enrolled, course removed) means
           retrying the same request will fail again the same way - the
           useful action there is going back to a course the student does
           have. */
        fail(err.message, { retry: /cannot reach/i.test(err.message) });
        return;
      }
      loading.style.display = "none";
      bodyBox.style.display = "block";
      renderCourse(course);

      /* Prefer an explicit lessonId from the "Continue Learning" link (only
         when it genuinely belongs to this course's own lesson list - cheap
         consistency check on top of the server-side ownership check that
         S.lesson() always performs), otherwise fall back to the backend's
         own resume pointer (next incomplete lesson, or the last lesson for
         review once the course is complete). */
      var validLessonIds = {};
      (course.modules || []).forEach(function (m) {
        (m.lessons || []).forEach(function (l) { validLessonIds[String(l.id)] = true; });
      });
      var target = (requestedLessonId && validLessonIds[String(requestedLessonId)])
        ? requestedLessonId
        : course.resumeLessonId;

      if (target) {
        await openLesson(target);
      } else if (!course.totalLessons) {
        var playerEmpty = document.getElementById("playerEmpty");
        if (playerEmpty) playerEmpty.textContent =
          "Lessons for this course haven\u2019t been published yet. Please check back soon.";
      }

      var markComplete = document.getElementById("markComplete");
      if (markComplete) markComplete.addEventListener("click", async function () {
        if (!currentLesson) return;
        var btn = this;
        var original = btn.textContent;
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner" aria-hidden="true"></span>Saving…';
        try {
          var completed = !currentLesson.completed;
          var updated = await S.saveProgress(currentLesson.id, { completed: completed });
          renderLesson(updated);
          await refreshOutline();
          lessonMsg(completed ? "Lesson marked complete." : "Lesson marked as incomplete.", false);
        } catch (err) {
          lessonMsg(err.message, true);
          btn.disabled = false;
          btn.textContent = original;
        }
      });

      var prevLesson = document.getElementById("prevLesson");
      if (prevLesson) prevLesson.addEventListener("click", function () {
        if (currentLesson) openLesson(currentLesson.previousLessonId);
      });
      var nextLesson = document.getElementById("nextLesson");
      if (nextLesson) nextLesson.addEventListener("click", async function () {
        if (!currentLesson) return;
        var next = currentLesson.nextLessonId;
        await openLesson(next);
        await refreshOutline();
      });
      var courseLogout = document.getElementById("courseLogout");
      if (courseLogout) courseLogout.addEventListener("click", async function () {
        await S.logout();
        window.location.replace("student-login.html");
      });
    })();
  });
})(window, document);
