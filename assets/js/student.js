/* =======================================================================
   VITC — Student portal client
   Talks to the existing Spring Boot backend:
     POST /api/v1/student/auth/login
     POST /api/v1/student/auth/logout
     POST /api/v1/student/auth/change-password
     GET  /api/v1/student/me
   The session token is issued by the backend; the dashboard is always
   re-validated server side, never from localStorage alone.
   ======================================================================= */
(function (window, document) {
  "use strict";

  var SESSION_KEY = "vitc_student_session";
  var CRED_KEY = "vitc_student_credentials";

  function apiBase() {
    if (window.VITC && window.VITC.api && window.VITC.api.base) return window.VITC.api.base;
    if (window.VITCPay && window.VITCPay.api && window.VITCPay.api.base) return window.VITCPay.api.base;
    if (window.VITC_API_BASE) return String(window.VITC_API_BASE).replace(/\/+$/, "");
    if (window.location.port === "8080") return window.location.origin;
    return "http://localhost:8080";
  }

  function session() {
    try { return JSON.parse(window.localStorage.getItem(SESSION_KEY) || "null"); } catch (e) { return null; }
  }
  function saveSession(s) {
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(s));
  }
  function clearSession() {
    window.localStorage.removeItem(SESSION_KEY);
  }

  function authHeaders() {
    var s = session();
    if (!s) return null;
    return { "X-Student-Id": s.studentLoginId, "X-Student-Token": s.sessionToken };
  }

  /* Small fetch wrapper that reuses the API base resolved by api.js. */
  async function call(path, method, body, withAuth) {
    var base = apiBase();
    var init = { method: method || "GET", headers: { "Content-Type": "application/json" } };
    if (withAuth) {
      var h = authHeaders();
      if (!h) throw new Error("Session expired, please sign in again");
      init.headers["X-Student-Id"] = h["X-Student-Id"];
      init.headers["X-Student-Token"] = h["X-Student-Token"];
    }
    if (body !== undefined) init.body = JSON.stringify(body);
    var res;
    try {
      res = await fetch(base + "/api/v1" + path, init);
    } catch (e) {
      var netErr = new Error("Cannot reach the VITC server. Please try again later.");
      netErr.status = 0;
      throw netErr;
    }
    var text = await res.text();
    var json = null;
    try { json = text ? JSON.parse(text) : null; } catch (e) { /* ignore */ }
    if (!res.ok) {
      var httpErr = new Error((json && json.message) || ("Request failed (" + res.status + ")"));
      httpErr.status = res.status;
      throw httpErr;
    }
    return json && Object.prototype.hasOwnProperty.call(json, "data") ? json.data : json;
  }

  var StudentAuth = {
    session: session,
    clearSession: clearSession,

    login: async function (studentLoginId, password) {
      var data = await call("/student/auth/login", "POST", {
        studentLoginId: studentLoginId,
        password: password
      });
      saveSession({
        studentLoginId: data.student.studentLoginId,
        sessionToken: data.sessionToken,
        fullName: data.student.fullName,
        mustChangePassword: data.mustChangePassword
      });
      return data;
    },

    me: function () { return call("/student/me", "GET", undefined, true); },

    /* ---- Profile (Part 6) ---- */
    updateProfile: function (fullName, phone, city) {
      return call("/student/profile", "PUT", { fullName: fullName, phone: phone, city: city }, true);
    },
    updateProfileImage: function (imageUrl) {
      return call("/student/profile/image", "PUT", { imageUrl: imageUrl }, true);
    },
    /* Reuses the existing generic upload endpoint (POST /api/v1/files) - no
       second upload pipeline. Returns the MediaFileResponse (has .url). */
    uploadProfileImage: async function (file) {
      var h = authHeaders();
      if (!h) throw new Error("Session expired, please sign in again");
      var form = new FormData();
      form.append("file", file);
      form.append("folder", "student-avatars");
      form.append("uploadedBy", h["X-Student-Id"]);
      var res;
      try {
        res = await fetch(apiBase() + "/api/v1/files", { method: "POST", headers: h, body: form });
      } catch (e) {
        throw new Error("Cannot reach the VITC server. Please try again later.");
      }
      var text = await res.text();
      var json = null;
      try { json = text ? JSON.parse(text) : null; } catch (e) { /* ignore */ }
      if (!res.ok) throw new Error((json && json.message) || ("Upload failed (" + res.status + ")"));
      return json && json.data ? json.data : json;
    },

    /* ---- Learning portal (Part 2) ---- */
    courses: function () { return call("/student/courses", "GET", undefined, true); },
    exams: function () { return call("/student/exams", "GET", undefined, true); },
    applyExam: function (enrollmentId) {
      return call("/student/exams/" + encodeURIComponent(enrollmentId) + "/apply", "POST", {}, true);
    },
    course: function (courseId) { return call("/student/courses/" + encodeURIComponent(courseId), "GET", undefined, true); },
    modules: function (courseId) { return call("/student/courses/" + encodeURIComponent(courseId) + "/modules", "GET", undefined, true); },
    lesson: function (lessonId) { return call("/student/lessons/" + encodeURIComponent(lessonId), "GET", undefined, true); },
    saveProgress: function (lessonId, payload) {
      return call("/student/lessons/" + encodeURIComponent(lessonId) + "/progress", "POST", payload || {}, true);
    },

    /* ---- Forgot password (OTP) ---- */
    forgotPassword: function (studentLoginIdOrEmail) {
      return call("/student/auth/forgot-password", "POST", {
        studentLoginIdOrEmail: studentLoginIdOrEmail
      });
    },
    verifyOtp: function (recoveryToken, otp) {
      return call("/student/auth/verify-otp", "POST", {
        recoveryToken: recoveryToken,
        otp: otp
      });
    },
    resendOtp: function (recoveryToken) {
      return call("/student/auth/resend-otp", "POST", {
        recoveryToken: recoveryToken
      });
    },
    resetPassword: function (resetToken, newPassword, confirmPassword) {
      return call("/student/auth/reset-password", "POST", {
        resetToken: resetToken,
        newPassword: newPassword,
        confirmPassword: confirmPassword
      });
    },

    logout: async function () {
      try { await call("/student/auth/logout", "POST", {}, true); } catch (e) { /* token already gone */ }
      clearSession();
    },

    changePassword: async function (currentPassword, newPassword, confirmPassword) {
      var data = await call("/student/auth/change-password", "POST", {
        currentPassword: currentPassword,
        newPassword: newPassword,
        confirmPassword: confirmPassword
      }, true);
      saveSession({
        studentLoginId: data.student.studentLoginId,
        sessionToken: data.sessionToken,
        fullName: data.student.fullName,
        mustChangePassword: false
      });
      return data;
    },

    /* One-time account info handed over by the payment confirmation.
       The temporary password is never included here - the backend no
       longer returns it from any API. It only ever goes to the
       student's inbox. */
    stashCredentials: function (creds) {
      try {
        var safe = creds ? {
          studentLoginId: creds.studentLoginId,
          newAccount: creds.newAccount,
          message: creds.message
        } : null;
        window.sessionStorage.setItem(CRED_KEY, JSON.stringify(safe));
      } catch (e) { /* ignore */ }
    },
    takeCredentials: function () {
      try {
        var raw = window.sessionStorage.getItem(CRED_KEY);
        return raw ? JSON.parse(raw) : null;
      } catch (e) { return null; }
    },
    forgetCredentials: function () {
      try { window.sessionStorage.removeItem(CRED_KEY); } catch (e) { /* ignore */ }
    }
  };

  window.VITCStudent = StudentAuth;

  /* ---------------- payment-success.html: show account info once ---------------- */
  document.addEventListener("DOMContentLoaded", function () {
    var card = document.getElementById("studentAccountCard");
    if (!card) return;
    var creds = StudentAuth.takeCredentials();
    if (!creds || !creds.studentLoginId) return;
    card.style.display = "block";
    var idEl = document.getElementById("credStudentId");
    var noteEl = document.getElementById("credNote");
    if (idEl) idEl.textContent = creds.studentLoginId;
    if (noteEl) {
      noteEl.textContent = creds.newAccount
        ? "Your Student ID is above. Your temporary password has been emailed to you " +
          "(check spam if it doesn't arrive in a few minutes) - it is never shown on screen."
        : (creds.message || "This course has been added to your existing student account.");
    }
  });

  /* ---------------- student-login.html ---------------- */
  document.addEventListener("DOMContentLoaded", function () {
    var form = document.getElementById("studentLoginForm");
    if (!form) return;

    /* PART 4/6 FIX: student-login.html must always render the sign-in form
       when opened directly - it must never silently auto-redirect to
       student-dashboard.html just because a session happens to exist in
       localStorage (stale, shared-computer, or otherwise). The only path
       onto the dashboard is: submit this form -> backend authenticates ->
       new session saved -> then navigate to student-dashboard.html (see the
       submit handler below). Protection in the other direction is untouched:
       student-dashboard.html / student-profile.html / student-course.html
       each independently re-check the session on load and re-validate it
       against the backend (GET /student/me), so this change cannot be used
       to reach a protected page without a real, server-issued session.

       Any leftover session is cleared here too, so simply loading the login
       page always forces a fresh, explicit sign-in - there is no way to
       land on the dashboard by opening this page alone. */
    clearSession();
    var msg = document.getElementById("studentLoginMsg");
    form.addEventListener("submit", async function (e) {
      e.preventDefault();
      msg.className = "student-msg";
      var studentId = document.getElementById("studentId").value.trim();
      var password = document.getElementById("studentPassword").value;
      if (!studentId && !password) {
        msg.className = "student-msg error";
        msg.textContent = "Enter your Student ID and password.";
        return;
      }
      if (!studentId) {
        msg.className = "student-msg error";
        msg.textContent = "Please enter your Student ID.";
        return;
      }
      if (!password) {
        msg.className = "student-msg error";
        msg.textContent = "Please enter your password.";
        return;
      }
      var btn = document.getElementById("studentLoginBtn");
      btn.disabled = true;
      btn.textContent = "Signing in…";
      try {
        await StudentAuth.login(studentId, password);
        window.location.href = "student-dashboard.html";
      } catch (err) {
        msg.className = "student-msg error";
        msg.textContent = err.message;
        btn.disabled = false;
        btn.textContent = "Sign in";
      }
    });

    /* ---- Forgot password wizard (4 steps) ---- */
    var forgotForm = document.getElementById("studentForgotForm");
    var verifyForm = document.getElementById("studentVerifyOtpForm");
    var resetForm = document.getElementById("studentResetForm");
    var successCard = document.getElementById("studentSuccessCard");

    if (forgotForm && verifyForm && resetForm) {
      var cards = {
        login: form,
        forgot: forgotForm,
        verify: verifyForm,
        reset: resetForm,
        success: successCard
      };
      function showCard(name) {
        Object.keys(cards).forEach(function (k) {
          if (cards[k]) cards[k].style.display = k === name ? "" : "none";
        });
      }

      var currentRecoveryToken = null;
      var currentResetToken = null;
      var resendInterval = null;

      function stopResendTimer() {
        if (resendInterval) {
          clearInterval(resendInterval);
          resendInterval = null;
        }
      }

      function startResendTimer(seconds) {
        stopResendTimer();
        var remaining = typeof seconds === "number" && seconds > 0 ? seconds : 60;
        var btn = document.getElementById("studentResendBtn");
        var timerSpan = document.getElementById("studentResendTimer");
        if (btn) btn.disabled = true;
        if (timerSpan) timerSpan.textContent = " (" + remaining + "s)";
        resendInterval = setInterval(function () {
          remaining--;
          if (remaining <= 0) {
            stopResendTimer();
            if (btn) btn.disabled = false;
            if (timerSpan) timerSpan.textContent = "";
          } else {
            if (timerSpan) timerSpan.textContent = " (" + remaining + "s)";
          }
        }, 1000);
      }

      function resetRecoveryState() {
        stopResendTimer();
        currentRecoveryToken = null;
        currentResetToken = null;
        forgotForm.reset();
        verifyForm.reset();
        resetForm.reset();
        var msgs = document.querySelectorAll(".student-msg");
        msgs.forEach(function (m) {
          if (m.id !== "studentLoginMsg") {
            m.className = "student-msg";
            m.textContent = "";
          }
        });
      }

      var showForgotLink = document.getElementById("showStudentForgot");
      if (showForgotLink) {
        showForgotLink.addEventListener("click", function (e) {
          e.preventDefault();
          resetRecoveryState();
          showCard("forgot");
        });
      }
      Array.prototype.forEach.call(document.querySelectorAll(".backToStudentLogin"), function (a) {
        a.addEventListener("click", function (e) {
          e.preventDefault();
          resetRecoveryState();
          showCard("login");
        });
      });

      var forgotMsg = document.getElementById("studentForgotMsg");
      forgotForm.addEventListener("submit", async function (e) {
        e.preventDefault();
        forgotMsg.className = "student-msg";
        var account = document.getElementById("studentForgotAccount").value.trim();
        if (!account) {
          forgotMsg.className = "student-msg error";
          forgotMsg.textContent = "Please enter your Student ID, registered email, or phone.";
          return;
        }
        var btn = document.getElementById("studentForgotBtn");
        btn.disabled = true;
        btn.textContent = "Sending…";
        try {
          var res = await StudentAuth.forgotPassword(account);
          currentRecoveryToken = res.recoveryToken;
          showCard("verify");
          document.getElementById("studentOtpCode").value = "";
          var verifyMsg = document.getElementById("studentVerifyMsg");
          if (verifyMsg) {
            verifyMsg.className = "student-msg";
            verifyMsg.textContent = "";
          }
          document.getElementById("studentVerifySub").textContent =
            "A 6-digit verification code has been sent to " + (res.targetMasked || "your registered contact") +
            ". It expires in " + (res.expiresInMinutes || 5) + " minutes.";
          startResendTimer(res.cooldownSeconds || 60);
          forgotForm.reset();
        } catch (err) {
          forgotMsg.className = "student-msg error";
          forgotMsg.textContent = err.message;
        } finally {
          btn.disabled = false;
          btn.textContent = "Send OTP";
        }
      });

      var verifyMsg = document.getElementById("studentVerifyMsg");
      verifyForm.addEventListener("submit", async function (e) {
        e.preventDefault();
        verifyMsg.className = "student-msg";
        var otp = document.getElementById("studentOtpCode").value.trim();
        if (!/^\d{6}$/.test(otp)) {
          verifyMsg.className = "student-msg error";
          verifyMsg.textContent = "Please enter a valid 6-digit numeric OTP.";
          return;
        }
        if (!currentRecoveryToken) {
          verifyMsg.className = "student-msg error";
          verifyMsg.textContent = "Recovery session expired. Please request a new OTP.";
          showCard("forgot");
          return;
        }
        var btn = document.getElementById("studentVerifyBtn");
        btn.disabled = true;
        btn.textContent = "Verifying…";
        try {
          var res = await StudentAuth.verifyOtp(currentRecoveryToken, otp);
          currentResetToken = res.resetToken;
          stopResendTimer();
          showCard("reset");
          var resetMsg = document.getElementById("studentResetMsg");
          if (resetMsg) {
            resetMsg.className = "student-msg";
            resetMsg.textContent = "";
          }
        } catch (err) {
          verifyMsg.className = "student-msg error";
          verifyMsg.textContent = err.message;
        } finally {
          btn.disabled = false;
          btn.textContent = "Verify OTP";
        }
      });

      var resendBtn = document.getElementById("studentResendBtn");
      if (resendBtn) {
        resendBtn.addEventListener("click", async function (e) {
          e.preventDefault();
          if (!currentRecoveryToken) {
            showCard("forgot");
            return;
          }
          resendBtn.disabled = true;
          try {
            var res = await StudentAuth.resendOtp(currentRecoveryToken);
            startResendTimer(res.cooldownSeconds || 60);
            if (res.targetMasked) {
              document.getElementById("studentVerifySub").textContent =
                "A 6-digit verification code has been sent to " + res.targetMasked +
                ". It expires in " + (res.expiresInMinutes || 5) + " minutes.";
            }
            verifyMsg.className = "student-msg ok";
            verifyMsg.textContent = "A new verification OTP has been sent.";
          } catch (err) {
            verifyMsg.className = "student-msg error";
            verifyMsg.textContent = err.message;
            resendBtn.disabled = false;
          }
        });
      }

      var resetMsg = document.getElementById("studentResetMsg");
      resetForm.addEventListener("submit", async function (e) {
        e.preventDefault();
        resetMsg.className = "student-msg";
        if (!currentResetToken) {
          resetMsg.className = "student-msg error";
          resetMsg.textContent = "Please verify your OTP first.";
          showCard("forgot");
          return;
        }
        var pw = document.getElementById("studentNewPassword").value;
        var confirm = document.getElementById("studentConfirmPassword").value;
        if (!pw || pw.length < 8) {
          resetMsg.className = "student-msg error";
          resetMsg.textContent = "Password must be at least 8 characters.";
          return;
        }
        if (pw !== confirm) {
          resetMsg.className = "student-msg error";
          resetMsg.textContent = "Passwords do not match.";
          return;
        }
        var btn = document.getElementById("studentResetBtn");
        btn.disabled = true;
        btn.textContent = "Saving…";
        try {
          await StudentAuth.resetPassword(currentResetToken, pw, confirm);
          resetForm.reset();
          if (successCard) {
            showCard("success");
          } else {
            msg.className = "student-msg ok";
            msg.textContent = "Your password has been reset successfully.";
            showCard("login");
          }
        } catch (err) {
          resetMsg.className = "student-msg error";
          resetMsg.textContent = err.message;
        } finally {
          btn.disabled = false;
          btn.textContent = "Reset password";
        }
      });
    }
  });

  /* ---------------- student-dashboard.html ---------------- */
  document.addEventListener("DOMContentLoaded", async function () {
    var page = document.getElementById("studentDashboard");
    if (!page) return;

    if (!session()) {
      window.location.replace("student-login.html");
      return;
    }

    var dashCard = document.getElementById("dashboardCard");
    var tempPwNotice = document.getElementById("tempPasswordNotice");


    /* Renders "My Courses" from GET /api/v1/student/courses (progress included).
       Falls back to the enrolments already present on the profile if that call
       fails, so the dashboard never ends up empty because of one request. */
    /* Single source of truth for where "Continue Learning" should go: the
       backend-computed resume lesson (next incomplete lesson) while a course
       is in progress, or the last-accessed lesson for review once complete.
       Falls back to the course page alone when no lesson id is known yet
       (brand-new enrolment / lessons not published) - the course page then
       opens the first available lesson itself via its own resumeLessonId. */
    function continueHref(c) {
      var pct = typeof c.progressPercentage === "number" ? c.progressPercentage : 0;
      var lessonId = pct >= 100 ? (c.lastAccessedLessonId || c.resumeLessonId) : c.resumeLessonId;
      var href = "student-course.html?courseId=" + encodeURIComponent(c.courseId);
      if (lessonId) href += "&lessonId=" + encodeURIComponent(lessonId);
      return href;
    }

    function continueLabel(c) {
      var pct = typeof c.progressPercentage === "number" ? c.progressPercentage : 0;
      if (pct >= 100) return "Review Course";
      if (pct > 0) return "Continue Learning";
      return "Start Learning";
    }

    function courseCard(c) {
      var pct = typeof c.progressPercentage === "number" ? c.progressPercentage : 0;
      var allowed = c.accessAllowed !== false;
      var meta = [c.level, c.category, c.durationMonths].filter(Boolean).join(" \u2022 ");
      var href = continueHref(c);
      var complete = pct >= 100;
      var cta = allowed
        ? '<a class="btn btn-primary student-course-cta" href="' + href + '">'
            + continueLabel(c) + "</a>"
        : '<span class="student-locked-note">Access opens once your enrolment is active</span>';
      var lastLessonTitle = c.lastAccessedLessonTitle || (pct >= 100 ? null : c.resumeLessonTitle);
      return '<div class="student-course-card' + (complete ? " is-complete" : "") + '">'
        + '<div class="thumb" aria-hidden="true">'
        + '<span class="thumb-ico">' + esc(c.icon || "\uD83C\uDF93") + "</span>"
        + '<span class="pill">' + esc(c.status || "ACTIVE") + "</span>"
        + (complete ? '<span class="pill pill-complete">Completed</span>' : "")
        + "</div>"
        + '<div class="body">'
        + '<h3 class="course-card-title">' + esc(c.courseTitle) + "</h3>"
        + '<p class="instructor">' + esc(c.instructorName || "VITC Faculty") + "</p>"
        + (c.courseDescription ? '<p class="desc">' + esc(c.courseDescription) + "</p>" : "")
        + (meta ? '<p class="course-card-meta">' + esc(meta) + "</p>" : "")
        + '<div class="progress-track" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="'
            + pct + '" aria-label="' + esc(c.courseTitle) + ' progress: ' + pct + '% complete">'
            + '<span style="width:' + pct + '%"></span></div>'
        + '<p class="progress-label">' + pct + "% Complete"
        + (c.totalLessons ? ' <span>(' + c.completedLessons + "/" + c.totalLessons + " Lessons)</span>" : "")
        + "</p>"
        + (lastLessonTitle
            ? '<p class="last-lesson">Last lesson: <b>' + esc(lastLessonTitle) + "</b></p>"
            : "")
        + '<div class="card-foot">' + cta + "</div>"
        + "</div></div>";
    }

    function esc(v) {
      return String(v == null ? "" : v).replace(/[&<>"']/g, function (ch) {
        return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
      });
    }

    /* Simple, backend-driven filtering: operates only on the course list the
       authenticated student's own API call already returned - filtering
       never asks the server for a different student's data and never
       changes what is "owned". */
    var FILTER_EMPTY_TEXT = {
      all: { title: "You haven\u2019t enrolled in any courses yet.", body: "Browse our course catalog to get started.", showBrowse: true },
      "in-progress": { title: "No courses in progress yet.", body: "Courses you\u2019ve started but not finished will show up here.", showBrowse: false },
      completed: { title: "No completed courses yet.", body: "Finish all lessons in a course to see it here.", showBrowse: false }
    };

    function matchesFilter(c, filter) {
      var pct = typeof c.progressPercentage === "number" ? c.progressPercentage : 0;
      if (filter === "in-progress") return pct > 0 && pct < 100;
      if (filter === "completed") return pct >= 100;
      return true;
    }

    var allCourses = [];
    var activeFilter = "all";

    function renderCourses(courses) {
      var list = document.getElementById("studentCourses");
      if (!list) return;
      if (!allCourses.length) {
        var t = FILTER_EMPTY_TEXT.all;
        list.innerHTML = '<div class="student-empty-state">'
          + '<span class="ico" aria-hidden="true">\uD83D\uDCDA</span>'
          + '<h3 style="margin:0 0 8px">' + esc(t.title) + '</h3>'
          + '<p style="margin:0">' + esc(t.body) + '</p>'
          + '<a class="btn btn-primary" href="courses.html">Browse Courses</a>'
          + '</div>';
        return;
      }
      var filtered = courses.filter(function (c) { return matchesFilter(c, activeFilter); });
      if (!filtered.length) {
        var e = FILTER_EMPTY_TEXT[activeFilter] || FILTER_EMPTY_TEXT.all;
        var filterIco = activeFilter === "completed" ? "\u2705" : (activeFilter === "in-progress" ? "\u23F3" : "\uD83D\uDCDA");
        list.innerHTML = '<div class="student-empty-state">'
          + '<span class="ico" aria-hidden="true">' + filterIco + '</span>'
          + '<h3 style="margin:0 0 8px">' + esc(e.title) + '</h3>'
          + '<p style="margin:0">' + esc(e.body) + '</p>'
          + '</div>';
        return;
      }
      list.innerHTML = filtered.map(courseCard).join("");
    }

    function setActiveFilter(filter) {
      activeFilter = filter;
      var tabs = document.querySelectorAll(".student-filter-tab");
      for (var i = 0; i < tabs.length; i++) {
        var on = tabs[i].getAttribute("data-filter") === filter;
        tabs[i].classList.toggle("is-active", on);
        tabs[i].setAttribute("aria-selected", on ? "true" : "false");
      }
      renderCourses(allCourses);
    }

    (function bindFilterTabs() {
      var wrap = document.getElementById("studentCourseFilters");
      if (!wrap) return;
      wrap.addEventListener("click", function (ev) {
        var btn = ev.target.closest ? ev.target.closest(".student-filter-tab") : null;
        if (!btn) return;
        setActiveFilter(btn.getAttribute("data-filter") || "all");
      });
    })();

    /* Summary cards computed only from real enrolment/progress data - no fake numbers. */
    function renderStats(courses) {
      var box = document.getElementById("studentStats");
      if (!box) return;
      var list = courses || [];
      var enrolled = list.length;
      var completed = list.filter(function (c) { return (c.progressPercentage || 0) >= 100; }).length;
      var inProgress = list.filter(function (c) {
        var p = c.progressPercentage || 0;
        return p > 0 && p < 100;
      }).length;
      var overall = enrolled
        ? Math.round(list.reduce(function (sum, c) { return sum + (c.progressPercentage || 0); }, 0) / enrolled)
        : 0;
      function card(num, label) {
        return '<div class="student-stat-card"><div class="num">' + num + '</div><div class="label">' + label + '</div></div>';
      }
      box.innerHTML = card(enrolled, "Enrolled Courses")
        + card(inProgress, "Courses In Progress")
        + card(completed, "Completed Courses")
        + card(overall + "%", "Overall Learning Progress");
    }

    /* "Continue Learning" - the enrolled, not-yet-complete course the student
       most recently watched something in. Falls back to any in-progress
       course when lastWatchedAt isn't available yet. Nothing here is
       fabricated - hidden entirely when there is nothing to resume. */
    function renderContinue(courses) {
      var wrap = document.getElementById("continueLearning");
      if (!wrap) return;
      var candidates = (courses || []).filter(function (c) {
        return c.accessAllowed !== false && (c.progressPercentage || 0) < 100;
      });
      if (!candidates.length) { wrap.style.display = "none"; wrap.innerHTML = ""; return; }
      candidates.sort(function (a, b) {
        var ta = a.lastWatchedAt ? new Date(a.lastWatchedAt).getTime() : 0;
        var tb = b.lastWatchedAt ? new Date(b.lastWatchedAt).getTime() : 0;
        return tb - ta;
      });
      var c = candidates[0];
      var pct = c.progressPercentage || 0;
      var href = continueHref(c);
      wrap.style.display = "block";
      wrap.innerHTML = '<div class="student-continue-card">'
        + '<div><span class="eyebrow">Continue Learning</span>'
        + '<h3>' + esc(c.courseTitle) + '</h3>'
        + '<p>' + (c.resumeLessonTitle ? "Next up: " + esc(c.resumeLessonTitle) : "Pick up where you left off") + '</p>'
        + '<div class="progress-track" style="max-width:280px" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="'
            + pct + '" aria-label="' + esc(c.courseTitle) + ' progress: ' + pct + '% complete"><span style="width:' + pct + '%"></span></div>'
        + '<p class="progress-label" style="color:rgba(255,255,255,.88)">Progress: <b>' + pct + '%</b></p>'
        + '</div>'
        + '<a class="btn btn-primary" href="' + href + '">Continue Learning</a>'
        + '</div>';
    }

    function initials(name) {
      var parts = String(name || "").trim().split(/\s+/).filter(Boolean);
      if (!parts.length) return "S";
      return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
    }

    async function render(profile) {
      document.getElementById("studentName").textContent = profile.fullName;
      document.getElementById("studentIdOut").textContent = profile.studentLoginId;
      document.getElementById("studentEmailOut").textContent = profile.email;
      document.getElementById("studentAvatar").textContent = initials(profile.fullName);
      var courses;
      try {
        courses = await StudentAuth.courses();
      } catch (err) {
        console.error("GET /student/courses failed:", err);
        if (err.status === 401 || err.status === 403) throw err;
        courses = profile.courses;
      }
      allCourses = courses || [];
      renderStats(allCourses);
      renderContinue(allCourses);
      setActiveFilter("all");
      renderExams();
    }

    function fmtDate(v) {
      if (!v) return "—";
      var d = new Date(v);
      if (isNaN(d.getTime())) return String(v);
      return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
    }

    async function renderExams() {
      var host = document.getElementById("studentExamNotice");
      if (!host) return;
      try {
        var exams = await StudentAuth.exams();
        if (!exams || !exams.length) { host.hidden = true; host.innerHTML = ""; return; }
        host.hidden = false;
        host.innerHTML = exams.map(function (e) {
          if (e.status === "SCHEDULED") {
            var formattedDate = e.examDate ? fmtDate(e.examDate) : "";
            var formattedTime = e.examTime ? esc(e.examTime) : "";
            var locOrLink = "";
            if (e.locationOrLink) {
              if (/^https?:\/\//i.test(e.locationOrLink)) {
                locOrLink = '<a href="' + esc(e.locationOrLink) + '" target="_blank" rel="noopener noreferrer" style="color:var(--primary,#0B3D91);text-decoration:underline;font-weight:600">' + esc(e.locationOrLink) + ' ↗</a>';
              } else {
                locOrLink = '<b>' + esc(e.locationOrLink) + '</b>';
              }
            }
            return '<div class="student-msg ok" style="margin-bottom:18px;padding:16px;background:#e8f7ee;border:1px solid #c2ebd0;border-radius:12px">' +
              '<div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;margin-bottom:8px">' +
                '<strong style="font-size:15px;color:#0a6b32">🎓 Your exam has been scheduled.</strong>' +
                '<span class="pill" style="font-size:11px;font-weight:700;background:#c2ebd0;color:#0a6b32;padding:2px 8px;border-radius:999px">SCHEDULED</span>' +
              '</div>' +
              '<h4 style="margin:4px 0 10px;font-size:15px;color:#111">' + esc(e.courseName) + (e.courseCode ? ' <span style="font-size:13px;color:var(--muted)">(' + esc(e.courseCode) + ')</span>' : '') + '</h4>' +
              '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:10px;background:#fff;padding:12px;border-radius:8px;border:1px solid #d0e7d7;font-size:13px">' +
                '<div><span style="color:var(--muted);font-size:11px;display:block;text-transform:uppercase">Exam Date & Time</span><b>' + formattedDate + (formattedTime ? ' at ' + formattedTime : '') + '</b></div>' +
                '<div><span style="color:var(--muted);font-size:11px;display:block;text-transform:uppercase">Exam Mode</span><b>' + esc(e.mode || "Online") + '</b></div>' +
                (locOrLink ? '<div style="grid-column:1/-1"><span style="color:var(--muted);font-size:11px;display:block;text-transform:uppercase">Location / Meeting Link</span>' + locOrLink + '</div>' : '') +
                (e.notes ? '<div style="grid-column:1/-1"><span style="color:var(--muted);font-size:11px;display:block;text-transform:uppercase">Instructions / Notes</span><span>' + esc(e.notes) + '</span></div>' : '') +
              '</div>' +
            '</div>';
          }
          if (e.status === "APPLIED") {
            return '<div class="student-msg info" style="display:block;background:#eef3ff;color:#0B3D91;margin-bottom:18px;padding:14px;border:1px solid #d4e2fb;border-radius:12px">' +
              '<strong style="font-size:14px">🎓 Exam Application Submitted</strong>' +
              '<p style="margin:4px 0 6px">Your exam application has been submitted. Your exam will be scheduled soon.</p>' +
              '<b>' + esc(e.courseName) + '</b></div>';
          }
          return '<div class="student-msg info" style="display:block;background:#fff8ee;color:#a15c00;margin-bottom:18px;padding:14px;border:1px solid #fae1c3;border-radius:12px">' +
            '<strong style="font-size:14px">🎓 You are eligible for your exam.</strong>' +
            '<p style="margin:4px 0 6px">You are eligible for your exam.</p>' +
            '<b>' + esc(e.courseName) + '</b><br>' +
            '<button type="button" class="btn btn-primary btn-sm" data-apply-exam="' + esc(e.enrollmentId) +
            '" data-course="' + esc(e.courseName) + '" style="margin-top:10px">Apply for Exam</button></div>';
        }).join("");

        host.querySelectorAll("[data-apply-exam]").forEach(function (btn) {
          btn.addEventListener("click", async function (ev) {
            ev.preventDefault();
            var enrollmentId = btn.getAttribute("data-apply-exam");
            var courseName = btn.getAttribute("data-course") || "this course";
            if (!window.confirm("Apply for Exam?\n\nAre you sure you want to submit your exam application for " + courseName + "?")) {
              return;
            }
            btn.disabled = true;
            btn.textContent = "Submitting…";
            try {
              await StudentAuth.applyExam(enrollmentId);
              await renderExams();
            } catch (err) {
              alert(err.message || "Failed to submit exam application.");
              btn.disabled = false;
              btn.textContent = "Apply for Exam";
            }
          });
        });
      } catch (err) { host.hidden = true; host.innerHTML = ""; }
    }

    var loadingBox = document.getElementById("dashboardLoading");
    var errorBox = document.getElementById("dashboardError");

    async function boot() {
      if (tempPwNotice) tempPwNotice.style.display = "none";
      dashCard.style.display = "none";
      errorBox.style.display = "none";
      loadingBox.style.display = "block";

      var profile;
      try {
        profile = await StudentAuth.me();
      } catch (err) {
        console.error("GET /student/me failed:", err);
        loadingBox.style.display = "none";
        /* 401/403 = no (or no longer valid) session - back to login.
           Anything else (network failure, 404, 500) is a transient problem,
           not a reason to sign the student out - show the retry state instead. */
        if (err.status === 401 || err.status === 403) {
          StudentAuth.clearSession();
          window.location.replace("student-login.html");
          return;
        }
        document.getElementById("dashboardErrorMsg").textContent =
          "Unable to load your courses. Please try again.";
        errorBox.style.display = "block";
        return;
      }

      /* PART 3/6: the change-password form used to live inline here (a
         "dead end" style card - see AUDIT-REPORT-PART15/16). It now lives on
         its own dedicated page (student-change-password.html), so a student
         who must still change their temporary password just gets a small
         non-blocking notice with a link - My Courses still loads and is
         usable right away, never gated behind the password change. */
      if (profile.mustChangePassword && tempPwNotice) {
        tempPwNotice.style.display = "block";
      }

      try {
        await render(profile);
        loadingBox.style.display = "none";
        dashCard.style.display = "block";
      } catch (err) {
        console.error("Dashboard render failed:", err);
        loadingBox.style.display = "none";
        if (err.status === 401 || err.status === 403) {
          StudentAuth.clearSession();
          window.location.replace("student-login.html");
          return;
        }
        document.getElementById("dashboardErrorMsg").textContent =
          "Unable to load your courses. Please try again.";
        errorBox.style.display = "block";
      }
    }

    document.getElementById("dashboardRetry").addEventListener("click", boot);

    /* PART 3 - the subnav "My Courses" / "Continue Learning" links must do
       real work, not just move the URL hash: clicking either one resets the
       course filter to "All" and re-renders from the data already loaded by
       boot() (or refetches if the dashboard failed to load), then lets the
       native #hash navigation scroll to it. This also makes "My Courses"
       work correctly whether the page was opened plain or as
       student-dashboard.html#studentCourses. */
    (function bindSubNavJumpLinks() {
      var nav = document.getElementById("dashboardSubNav");
      if (!nav) return;
      nav.addEventListener("click", function (ev) {
        var link = ev.target.closest ? ev.target.closest('a[href="#studentCourses"], a[href="#continueLearning"]') : null;
        if (!link) return;
        if (dashCard.style.display === "none") {
          // Dashboard hasn't rendered yet (still loading, or the error/password
          // screen is showing) - nothing to jump to, so don't fight the hash.
          return;
        }
        if (link.getAttribute("href") === "#studentCourses") {
          setActiveFilter("all");
        }
      });
    })();

    await boot();

    // Supports opening the page directly as student-dashboard.html#studentCourses:
    // once the dashboard has finished its initial load, make sure the hash
    // target is actually scrolled into view (the browser's own initial-load
    // scroll can fire before the section has real content in it).
    if (window.location.hash === "#studentCourses" || window.location.hash === "#continueLearning") {
      var jumpTarget = document.getElementById(window.location.hash.slice(1));
      if (jumpTarget && dashCard.style.display !== "none") {
        jumpTarget.scrollIntoView({ block: "start" });
      }
    }

    document.getElementById("studentLogout").addEventListener("click", async function () {
      await StudentAuth.logout();
      window.location.href = "student-login.html";
    });
  });

  /* ---------------- shared: sub-nav "Logout" button (dashboard/course/profile) ---------------- */
  document.addEventListener("DOMContentLoaded", function () {
    var btn = document.getElementById("subNavLogout");
    if (!btn) return;
    btn.addEventListener("click", async function () {
      await StudentAuth.logout();
      window.location.href = "student-login.html";
    });
  });

  /* ---------------- student-profile.html ---------------- */
  document.addEventListener("DOMContentLoaded", async function () {
    var page = document.getElementById("studentProfilePage");
    if (!page) return;

    if (!session()) {
      window.location.replace("student-login.html");
      return;
    }

    function esc(v) {
      return String(v == null ? "" : v).replace(/[&<>"']/g, function (ch) {
        return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
      });
    }
    function initials(name) {
      var parts = String(name || "").trim().split(/\s+/).filter(Boolean);
      if (!parts.length) return "S";
      return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
    }
    function fmtDate(v) {
      if (!v) return "\u2014";
      var d = new Date(v);
      if (isNaN(d.getTime())) return "\u2014";
      return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
    }

    var loadingBox = document.getElementById("profileLoading");
    var errorBox = document.getElementById("profileError");
    var card = document.getElementById("profileCard");

    var avatarWrap = document.getElementById("profileAvatarLg");
    var avatarInput = document.getElementById("profileAvatarInput");
    var avatarMsg = document.getElementById("profileAvatarMsg");

    var editForm = document.getElementById("editProfileForm");
    var editMsg = document.getElementById("editProfileMsg");
    var loadedProfile = null;

    function renderAvatar(profile) {
      avatarWrap.innerHTML = profile.profileImageUrl
        ? '<img src="' + esc(profile.profileImageUrl) + '" alt="Profile photo">'
        : esc(initials(profile.fullName));
    }

    /* Every value below comes from GET /api/v1/student/me, which the backend
       resolves from the session token of the *currently logged-in* student.
       No student id is ever taken from the URL, localStorage or a constant. */
    function titleCase(v) {
      var t = String(v || "").replace(/_/g, " ").toLowerCase();
      return t ? t.charAt(0).toUpperCase() + t.slice(1) : "\u2014";
    }
    function dash(v) {
      return v == null || String(v).trim() === "" ? "\u2014" : String(v);
    }

    function render(profile) {
      loadedProfile = profile;
      renderAvatar(profile);
      document.getElementById("profileSideName").textContent = dash(profile.fullName);
      document.getElementById("profileSideRole").textContent = titleCase(profile.role || "STUDENT");
      document.getElementById("profileSideId").textContent = dash(profile.studentLoginId);
      document.getElementById("profileMemberSince").textContent =
        "Member since " + fmtDate(profile.memberSince);

      document.getElementById("profileNameOut").textContent = dash(profile.fullName);
      document.getElementById("profileEmailOut").textContent = dash(profile.email);
      document.getElementById("profilePhoneOut").textContent = dash(profile.phone);
      document.getElementById("profileCityOut").textContent = dash(profile.city);
      document.getElementById("profileRoleOut").textContent = titleCase(profile.role || "STUDENT");
      document.getElementById("profileMemberSinceOut").textContent = fmtDate(profile.memberSince);
      document.getElementById("profileStudentIdOut").textContent = dash(profile.studentLoginId);
      document.getElementById("profileLastLoginOut").textContent = fmtDate(profile.lastLoginAt);

      document.getElementById("editFullName").value = profile.fullName || "";
      document.getElementById("editPhone").value = profile.phone || "";
      document.getElementById("editCity").value = profile.city || "";
    }

    /* Course statistics: computed from the same authenticated endpoint the
       dashboard uses (GET /api/v1/student/courses -> backend-computed
       progressPercentage per enrolment). Falls back to the enrolments on the
       profile itself if that call fails, so the block is never blank. */
    function renderStats(courses) {
      var list = courses || [];
      var enrolled = list.length;
      var completed = 0;
      var inProgress = 0;
      var sum = 0;
      var hasPct = false;
      list.forEach(function (c) {
        var pct = typeof c.progressPercentage === "number" ? c.progressPercentage : null;
        if (pct !== null) { hasPct = true; sum += pct; }
        var done = pct !== null ? pct >= 100 : String(c.status || "") === "COMPLETED";
        if (done) completed++; else inProgress++;
      });
      var overall = enrolled && hasPct ? Math.round(sum / enrolled) : 0;

      document.getElementById("statEnrolled").textContent = String(enrolled);
      document.getElementById("statCompleted").textContent = String(completed);
      document.getElementById("statInProgress").textContent = String(inProgress);
      document.getElementById("statOverall").textContent = hasPct ? overall + "%" : "\u2014";
      var bar = document.getElementById("statOverallBar");
      var wrap = document.getElementById("statOverallBarWrap");
      if (bar) bar.style.width = (hasPct ? overall : 0) + "%";
      if (wrap) wrap.setAttribute("aria-valuenow", String(hasPct ? overall : 0));
    }

    async function loadStats(profile) {
      var statsMsg = document.getElementById("profileStatsMsg");
      try {
        var courses = await StudentAuth.courses();
        renderStats(courses);
        if (statsMsg) { statsMsg.className = "student-msg"; statsMsg.textContent = ""; }
      } catch (err) {
        renderStats(profile.courses || []);
        if (statsMsg) {
          statsMsg.className = "student-msg error";
          statsMsg.textContent = "Live progress is unavailable right now \u2014 showing your enrolments only.";
        }
      }
    }

    async function boot() {
      card.style.display = "none";
      errorBox.style.display = "none";
      loadingBox.style.display = "block";
      var profile;
      try {
        profile = await StudentAuth.me();
      } catch (err) {
        loadingBox.style.display = "none";
        StudentAuth.clearSession();
        window.location.replace("student-login.html");
        return;
      }
      render(profile);
      loadingBox.style.display = "none";
      card.style.display = "block";
      loadStats(profile);
    }

    document.getElementById("profileRetry").addEventListener("click", boot);
    await boot();

    /* ---- Avatar upload: reuses the existing generic file-upload endpoint,
       then links the returned URL to this student's account. ---- */
    avatarInput.addEventListener("change", async function () {
      var file = avatarInput.files && avatarInput.files[0];
      avatarInput.value = "";
      if (!file) return;
      if (!/^image\//.test(file.type)) {
        avatarMsg.className = "student-msg error";
        avatarMsg.textContent = "Please choose an image file (JPG, PNG, GIF or WEBP).";
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        avatarMsg.className = "student-msg error";
        avatarMsg.textContent = "Image must be smaller than 5 MB.";
        return;
      }
      /* Uploading state: disable the label (without touching the <input> node itself,
         so its change listener is never lost) and reuse the existing status line. */
      var avatarLabel = document.querySelector(".profile-avatar-edit");
      if (avatarLabel) avatarLabel.classList.add("is-disabled");
      avatarMsg.className = "student-msg ok";
      avatarMsg.innerHTML = '<span class="spinner" aria-hidden="true"></span>Uploading\u2026';
      try {
        var uploaded = await StudentAuth.uploadProfileImage(file);
        var updated = await StudentAuth.updateProfileImage(uploaded.url);
        renderAvatar(updated);
        avatarMsg.className = "student-msg ok";
        avatarMsg.textContent = "Profile photo updated.";
      } catch (err) {
        avatarMsg.className = "student-msg error";
        avatarMsg.textContent = err.message || "Could not update your photo. Please try again.";
      } finally {
        if (avatarLabel) avatarLabel.classList.remove("is-disabled");
      }
    });

    /* ---- Edit mode toggle (view <-> edit, with cancel) ---- */
    var editWrap = document.getElementById("profileEditWrap");
    var editToggle = document.getElementById("profileEditToggle");
    var editCancel = document.getElementById("editProfileCancel");

    function setEditMode(on) {
      if (!editWrap || !editToggle) return;
      editWrap.hidden = !on;
      editToggle.textContent = on ? "Close editor" : "\u270E Edit Profile";
      editToggle.setAttribute("aria-expanded", on ? "true" : "false");
      if (on) {
        var first = document.getElementById("editFullName");
        if (first) first.focus();
      }
    }
    if (editToggle) {
      editToggle.setAttribute("aria-expanded", "false");
      editToggle.addEventListener("click", function () { setEditMode(editWrap.hidden); });
    }
    if (editCancel) {
      editCancel.addEventListener("click", function () {
        if (loadedProfile) {
          document.getElementById("editFullName").value = loadedProfile.fullName || "";
          document.getElementById("editPhone").value = loadedProfile.phone || "";
          document.getElementById("editCity").value = loadedProfile.city || "";
        }
        if (editMsg) { editMsg.className = "student-msg"; editMsg.textContent = ""; }
        setEditMode(false);
      });
    }

    /* ---- Edit profile ---- */
    var fullNameField = document.getElementById("editFullNameField");
    var fullNameErr = document.getElementById("editFullNameError");
    editForm.addEventListener("submit", async function (e) {
      e.preventDefault();
      editMsg.className = "student-msg";
      var fullName = document.getElementById("editFullName").value.trim();
      var phone = document.getElementById("editPhone").value.trim();
      var city = document.getElementById("editCity").value.trim();

      if (!fullName) {
        if (fullNameField) fullNameField.classList.add("has-error");
        if (fullNameErr) { fullNameErr.textContent = "Full name is required."; fullNameErr.classList.add("show"); }
        editMsg.className = "student-msg error";
        editMsg.textContent = "Full name is required.";
        return;
      }
      if (fullNameField) fullNameField.classList.remove("has-error");
      if (fullNameErr) fullNameErr.classList.remove("show");
      if (phone && !/^[0-9+\-\s]{7,20}$/.test(phone)) {
        editMsg.className = "student-msg error";
        editMsg.textContent = "Please enter a valid phone number.";
        return;
      }

      var btn = document.getElementById("editProfileSubmit");
      btn.disabled = true;
      btn.innerHTML = '<span class="spinner" aria-hidden="true"></span>Saving\u2026';
      try {
        var updated = await StudentAuth.updateProfile(fullName, phone, city);
        render(updated);
        editMsg.className = "student-msg ok";
        editMsg.textContent = "Profile updated successfully.";
        setEditMode(false);
      } catch (err) {
        editMsg.className = "student-msg error";
        editMsg.textContent = err.message || "Could not update your profile. Please try again.";
      } finally {
        btn.disabled = false;
        btn.textContent = "Save changes";
      }
    });
  });

  /* ---------------- student-change-password.html (PART 3/6) ----------------
     Dedicated Change Password tab. Same backend endpoint/flow that used to
     be embedded on the Dashboard (forced first-login) and on the Profile
     page (optional) - both now point here instead, so there is exactly one
     place a student's password is ever changed from. */
  document.addEventListener("DOMContentLoaded", function () {
    var page = document.getElementById("studentChangePasswordPage");
    if (!page) return;

    if (!session()) {
      window.location.replace("student-login.html");
      return;
    }

    var forced = window.location.search.indexOf("forced=1") !== -1;
    var noticeBox = document.getElementById("scpForcedNotice");
    if (forced && noticeBox) noticeBox.style.display = "block";

    var form = document.getElementById("scpForm");
    var msg = document.getElementById("scpMsg");
    var confirmField = document.getElementById("scpConfirmField");
    var confirmErr = document.getElementById("scpConfirmError");
    var btn = document.getElementById("scpSubmit");

    form.addEventListener("submit", async function (e) {
      e.preventDefault();
      msg.className = "student-msg";
      if (confirmField) confirmField.classList.remove("has-error");
      if (confirmErr) confirmErr.classList.remove("show");

      var current = document.getElementById("scpCurrent").value;
      var next = document.getElementById("scpNew").value;
      var confirm = document.getElementById("scpConfirm").value;

      if (!current) {
        msg.className = "student-msg error";
        msg.textContent = "Current password is required.";
        return;
      }
      if (!next) {
        msg.className = "student-msg error";
        msg.textContent = "New password is required.";
        return;
      }
      if (next.length < 8) {
        msg.className = "student-msg error";
        msg.textContent = "New password must be at least 8 characters.";
        return;
      }
      if (!confirm) {
        if (confirmField) confirmField.classList.add("has-error");
        if (confirmErr) { confirmErr.textContent = "Please confirm your new password."; confirmErr.classList.add("show"); }
        msg.className = "student-msg error";
        msg.textContent = "Please confirm your new password.";
        return;
      }
      if (next !== confirm) {
        if (confirmField) confirmField.classList.add("has-error");
        if (confirmErr) { confirmErr.textContent = "New password and confirmation do not match."; confirmErr.classList.add("show"); }
        msg.className = "student-msg error";
        msg.textContent = "New password and confirmation do not match.";
        return;
      }

      var originalLabel = btn ? btn.textContent : "";
      if (btn) { btn.disabled = true; btn.innerHTML = '<span class="spinner" aria-hidden="true"></span>Updating\u2026'; }
      try {
        /* Real backend call - POST /api/v1/student/auth/change-password.
           A wrong current password comes back as a normal API error (401/400
           with a message) and is shown exactly as the server phrases it, so
           "Incorrect current password" etc. are true backend responses, not
           client-guessed text. */
        await StudentAuth.changePassword(current, next, confirm);
        form.reset();
        if (forced) {
          msg.className = "student-msg ok";
          msg.textContent = "Password changed successfully. Redirecting to your dashboard\u2026";
          setTimeout(function () { window.location.href = "student-dashboard.html"; }, 900);
        } else {
          msg.className = "student-msg ok";
          msg.textContent = "Password changed successfully.";
        }
      } catch (err) {
        msg.className = "student-msg error";
        msg.textContent = err.message || "Could not change your password. Please try again.";
      } finally {
        if (btn) { btn.disabled = false; btn.textContent = originalLabel; }
      }
    });
  });
  /* ---------------- student-settings.html (PART 5/6) ----------------
     Read-only account settings for the currently logged-in student. All
     values come from GET /api/v1/student/me (session-resolved server side);
     nothing is hard-coded and no student id is read from the URL. */
  document.addEventListener("DOMContentLoaded", async function () {
    var page = document.getElementById("studentSettingsPage");
    if (!page) return;

    if (!session()) {
      window.location.replace("student-login.html");
      return;
    }

    function fmtDate(v) {
      if (!v) return "\u2014";
      var d = new Date(v);
      if (isNaN(d.getTime())) return "\u2014";
      return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
    }
    function dash(v) {
      return v == null || String(v).trim() === "" ? "\u2014" : String(v);
    }
    function titleCase(v) {
      var t = String(v || "").replace(/_/g, " ").toLowerCase();
      return t ? t.charAt(0).toUpperCase() + t.slice(1) : "\u2014";
    }

    var loadingBox = document.getElementById("settingsLoading");
    var errorBox = document.getElementById("settingsError");
    var card = document.getElementById("settingsCard");

    async function boot() {
      card.style.display = "none";
      errorBox.style.display = "none";
      loadingBox.style.display = "block";
      var profile;
      try {
        profile = await StudentAuth.me();
      } catch (err) {
        loadingBox.style.display = "none";
        if (err && err.status === 401) {
          StudentAuth.clearSession();
          window.location.replace("student-login.html");
          return;
        }
        errorBox.style.display = "block";
        return;
      }
      document.getElementById("setName").textContent = dash(profile.fullName);
      document.getElementById("setEmail").textContent = dash(profile.email);
      document.getElementById("setStudentId").textContent = dash(profile.studentLoginId);
      document.getElementById("setRole").textContent = titleCase(profile.role || "STUDENT");
      document.getElementById("setMemberSince").textContent = fmtDate(profile.memberSince);
      document.getElementById("setLastLogin").textContent = fmtDate(profile.lastLoginAt);
      loadingBox.style.display = "none";
      card.style.display = "block";
    }

    var retry = document.getElementById("settingsRetry");
    if (retry) retry.addEventListener("click", boot);
    var out = document.getElementById("settingsLogout");
    if (out) out.addEventListener("click", async function () {
      await StudentAuth.logout();
      window.location.href = "student-login.html";
    });
    await boot();
  });
})(window, document);
