/* =======================================================================
   VITC Teacher Admin — shared runtime (authentication foundation only)
   Mirrors the session model used by admin.js, but completely separate:
   a Teacher session can never be used against admin/student endpoints
   and vice versa (enforced server-side by TeacherAuthInterceptor).
   ======================================================================= */
(function (window, document) {
  "use strict";

  var storedApi = window.localStorage.getItem("vitc_api");
  if (storedApi && !/^https?:\/\//i.test(storedApi)) {
    window.localStorage.removeItem("vitc_api");
    storedApi = null;
  }
  var API_BASE = (storedApi || "http://localhost:8080")
    .replace(/\/+$/, "").replace(/\/api(\/v1)?$/i, "");
  var API = API_BASE + "/api/v1";
  var SESSION_KEY = "vitc_teacher";
  var SESSION_TTL_MS = 8 * 60 * 60 * 1000; /* must not exceed the backend session window */

  /* ============================ HTTP ============================ */
  function unwrap(json) {
    if (json && Object.prototype.hasOwnProperty.call(json, "data")) return json.data;
    return json;
  }

  async function request(path, options) {
    options = options || {};
    var init = { method: options.method || "GET", headers: {} };
    if (options.formData !== undefined) {
      /* PART 4/8: multipart/form-data (e.g. lesson video upload). The browser sets the
         Content-Type header itself (including the multipart boundary) - setting it manually
         here would break the upload, so it is deliberately left out. */
      init.body = options.formData;
    } else if (options.body !== undefined) {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(options.body);
    }
    var s = getSession();
    if (s) {
      init.headers["X-Teacher-Username"] = s.username;
      init.headers["X-Teacher-Token"] = s.sessionToken;
    }
    var res;
    try {
      res = await fetch(API + path, init);
    } catch (e) {
      throw new Error("Cannot reach the API at " + API_BASE + ". Is the backend running?");
    }
    var text = await res.text();
    var json = null;
    try { json = text ? JSON.parse(text) : null; } catch (e) { /* non json */ }
    if (!res.ok) {
      var msg = (json && (json.message || json.error)) || res.status + " " + res.statusText;
      throw new Error(msg);
    }
    return unwrap(json);
  }

  var api = {
    base: API_BASE,
    get: function (p) { return request(p); },
    post: function (p, b) { return request(p, { method: "POST", body: b }); },
    put: function (p, b) { return request(p, { method: "PUT", body: b }); },
    patch: function (p, b) { return request(p, { method: "PATCH", body: b }); },
    del: function (p) { return request(p, { method: "DELETE" }); },
    /* PART 4/8: multipart upload helper (lesson video). `method` defaults to PUT to match the
       existing /teacher/lessons/{id}/video contract; formData is a browser FormData instance. */
    upload: function (p, formData, method) {
      return request(p, { method: method || "PUT", formData: formData });
    },
    /* PART 5/8: same multipart contract as upload(), but over XMLHttpRequest instead of fetch so
       we can report real upload progress (fetch has no cross-browser upload-progress event).
       onProgress(pct) is called with an integer 0-100 whenever the browser can compute it, or
       with null when the length isn't computable (caller should then show an indeterminate
       state rather than inventing a percentage). Resolves/rejects with the same shape as the
       rest of the api object, so callers can share one catch block. */
    uploadWithProgress: function (p, formData, onProgress, method) {
      return new Promise(function (resolve, reject) {
        var xhr = new XMLHttpRequest();
        xhr.open(method || "PUT", API + p, true);
        var s = getSession();
        if (s) {
          xhr.setRequestHeader("X-Teacher-Username", s.username);
          xhr.setRequestHeader("X-Teacher-Token", s.sessionToken);
        }
        if (xhr.upload && onProgress) {
          xhr.upload.onprogress = function (e) {
            onProgress(e.lengthComputable ? Math.round((e.loaded / e.total) * 100) : null);
          };
        }
        xhr.onload = function () {
          var json = null;
          try { json = xhr.responseText ? JSON.parse(xhr.responseText) : null; } catch (e) { /* non json */ }
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(unwrap(json));
          } else {
            var msg = (json && (json.message || json.error)) || xhr.status + " " + xhr.statusText;
            reject(new Error(msg));
          }
        };
        xhr.onerror = function () {
          reject(new Error("Cannot reach the API at " + API_BASE + ". Is the backend running?"));
        };
        xhr.send(formData);
      });
    }
  };

  /** Resolves a possibly-relative media URL (e.g. "/uploads/videos/xyz.mp4" as returned by the
   *  backend) into an absolute one the browser can load from this separate Teacher Admin origin.
   *  Mirrors admin.js's mediaUrl() so uploaded files behave identically in both admin surfaces. */
  function mediaUrl(u) {
    if (!u) return "";
    if (/^https?:\/\//i.test(u)) return u;
    if (u.charAt(0) === "/") return API_BASE + u;
    return u;
  }

  /* ============================ Auth ============================ */
  function getSession() {
    var raw;
    try { raw = JSON.parse(window.localStorage.getItem(SESSION_KEY)); }
    catch (e) { raw = null; }
    if (!raw || typeof raw !== "object") return null;
    if (!raw.username || !raw.sessionToken || !raw.expiresAt) return null;
    if (new Date(raw.expiresAt).getTime() <= Date.now()) {
      window.localStorage.removeItem(SESSION_KEY);
      return null;
    }
    return raw;
  }

  function setSession(loginResponse) {
    var t = loginResponse && loginResponse.teacher;
    if (!loginResponse || !loginResponse.sessionToken || !t || !t.username) {
      throw new Error("Login response did not include a session token.");
    }
    window.localStorage.setItem(SESSION_KEY, JSON.stringify({
      id: t.id,
      username: t.username,
      email: t.email,
      fullName: t.fullName,
      mustChangePassword: !!loginResponse.mustChangePassword,
      sessionToken: loginResponse.sessionToken,
      expiresAt: new Date(Date.now() + SESSION_TTL_MS).toISOString()
    }));
  }

  function clearSession() { window.localStorage.removeItem(SESSION_KEY); }

  function logout() {
    var s = getSession();
    clearSession();
    if (s) {
      request("/teacher/auth/logout", { method: "POST" }).catch(function () { /* best effort */ });
    }
    window.location.href = "../teacher-login.html";
  }

  function requireAuth() {
    if (!getSession()) { window.location.href = "../teacher-login.html"; return false; }
    return true;
  }

  /** Server-side check - kicks the user out if the token is unknown, revoked or expired. */
  async function verifySession() {
    var s = getSession();
    if (!s) return null;
    try {
      var profile = await request("/teacher/auth/session", {
        method: "POST", body: { username: s.username, token: s.sessionToken }
      });
      return profile;
    } catch (e) {
      if (/Cannot reach the API/.test(e.message)) return null; // backend down: keep local state
      clearSession();
      window.location.href = "../teacher-login.html";
      return null;
    }
  }

  /* ============================ UI utils ============================ */
  function esc(v) {
    if (v === null || v === undefined) return "";
    return String(v).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function toast(message, type) {
    var host = document.getElementById("toasts");
    if (!host) {
      host = document.createElement("div");
      host.id = "toasts";
      document.body.appendChild(host);
    }
    var t = document.createElement("div");
    t.className = "toast " + (type || "");
    t.setAttribute("role", type === "err" ? "alert" : "status");
    t.setAttribute("aria-live", type === "err" ? "assertive" : "polite");
    /* PART 1/2 accessibility: the left border colour alone used to carry the success/error
       meaning. Add an icon plus a screen-reader-only word so status is never colour-only. */
    var word = type === "err" ? "Error" : (type === "ok" ? "Success" : "Notice");
    var glyph = type === "err" ? "\u26A0\uFE0F" : (type === "ok" ? "\u2714\uFE0F" : "\u2139\uFE0F");
    var ico = document.createElement("span");
    ico.className = "toast-ico";
    ico.setAttribute("aria-hidden", "true");
    ico.textContent = glyph;
    var sr = document.createElement("span");
    sr.className = "t-sr-only";
    sr.textContent = word + ": ";
    var txt = document.createElement("span");
    txt.className = "toast-text";
    txt.textContent = message;
    t.appendChild(ico); t.appendChild(sr); t.appendChild(txt);
    host.appendChild(t);
    setTimeout(function () { t.remove(); }, 4200);
  }

  /* ============================ PART 4/8 — Feedback helpers ============================
     Shared loading / empty / error rendering + a generic "disable while a request is in
     flight" wrapper for buttons, so every Teacher Admin page shows the same skeletons,
     the same icon+message empty states, and never allows a double submission. */

  /** N skeleton rows/cards inside `host` while data is loading. `host` should be the same
   *  element the real content will be rendered into once the request resolves. */
  function skeleton(host, opts) {
    if (!host) return;
    opts = opts || {};
    var count = opts.count || 3;
    var cls = "skeleton " + (opts.rowClass || "t-skel-row");
    var rows = "";
    for (var i = 0; i < count; i++) rows += '<div class="' + cls + '" aria-hidden="true"></div>';
    host.setAttribute("aria-busy", "true");
    host.innerHTML = opts.grid
      ? '<div class="t-skel-grid">' + rows + '</div>'
      : rows;
  }

  /** Renders an icon + title + message empty/error state into `host`. Pass `retry:true`
   *  (error only) to get a "Try again" button the caller wires up via
   *  host.querySelector('[data-retry]'). Never render an action the user cannot use. */
  function emptyState(host, opts) {
    if (!host) return;
    opts = opts || {};
    host.removeAttribute("aria-busy");
    host.innerHTML =
      '<div class="empty' + (opts.error ? " empty-error" : "") + '">' +
        (opts.icon ? '<span class="ico" aria-hidden="true">' + opts.icon + "</span>" : "") +
        (opts.title ? "<h4>" + esc(opts.title) + "</h4>" : "") +
        "<p>" + esc(opts.message || "") + "</p>" +
        (opts.retry ? '<button type="button" class="btn btn-secondary btn-sm" data-retry>Try again</button>' : "") +
        (opts.actionHtml || "") +
      "</div>";
  }

  /** Standard load cycle for a list container: skeleton -> fetch -> render, or an icon
   *  empty state when the result is empty, or a retryable error state on failure.
   *  `emptyOpts`/`errorOpts` accept {icon, title, message}. */
  function load(host, fetcher, render, emptyOpts, errorOpts) {
    if (!host) return Promise.resolve();
    skeleton(host, { grid: !!(emptyOpts && emptyOpts.grid), count: (emptyOpts && emptyOpts.count) || 3 });
    return fetcher().then(function (data) {
      var list = Array.isArray(data) ? data : (data ? [data] : []);
      if (!list.length) {
        emptyState(host, Object.assign({ icon: "\uD83D\uDCC1", title: "Nothing here yet" }, emptyOpts || {}));
        return data;
      }
      render(data, host);
      host.removeAttribute("aria-busy");
      return data;
    }).catch(function (err) {
      emptyState(host, Object.assign(
        { icon: "\u26A0\uFE0F", title: "Something went wrong", error: true, retry: true },
        errorOpts || {}, { message: err.message }
      ));
      var btn = host.querySelector("[data-retry]");
      if (btn) btn.addEventListener("click", function () { load(host, fetcher, render, emptyOpts, errorOpts); });
    });
  }

  /** Wraps an async action with the standard button-loading cycle: disable -> label swap
   *  with a spinner -> run -> restore, so a slow request can never be double-submitted. */
  async function withButtonLoading(btn, fn, loadingLabel) {
    if (!btn) return fn();
    var original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner" aria-hidden="true"></span>' + esc(loadingLabel || "Working…");
    try {
      return await fn();
    } finally {
      btn.disabled = false;
      btn.innerHTML = original;
    }
  }

  /* ============================ PART 5/8 — Modal + form-field helpers ============================
     One accessible open/close cycle shared by every Teacher Admin modal (currently the two
     "Change password" dialogs): Escape closes it, clicking the dimmed backdrop closes it,
     focus moves into the dialog on open and returns to whatever triggered it on close, and the
     dialog carries role="dialog"/aria-modal so assistive tech announces it correctly. Does not
     introduce a new modal framework - it only wires up the existing .modal/.modal-card markup
     that already comes from admin.css. */
  function bindModal(modalId, opts) {
    var modal = document.getElementById(modalId);
    if (!modal) return { open: function () {}, close: function () {} };
    opts = opts || {};
    var card = modal.querySelector(".modal-card");
    if (card) {
      card.setAttribute("role", "dialog");
      card.setAttribute("aria-modal", "true");
      var heading = card.querySelector(".modal-head h3");
      if (heading) {
        if (!heading.id) heading.id = modalId + "Title";
        card.setAttribute("aria-labelledby", heading.id);
      }
    }
    var lastFocused = null;

    function focusables() {
      return Array.prototype.slice.call(
        modal.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')
      ).filter(function (el) { return !el.disabled && el.offsetParent !== null; });
    }

    function onKeydown(e) {
      if (e.key === "Escape" || e.keyCode === 27) { close(); return; }
      if (e.key !== "Tab") return;
      var items = focusables();
      if (!items.length) return;
      var first = items[0], last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }

    function onBackdropClick(e) { if (e.target === modal) close(); }

    function open() {
      lastFocused = document.activeElement;
      modal.classList.add("open");
      document.addEventListener("keydown", onKeydown);
      modal.addEventListener("mousedown", onBackdropClick);
      var items = focusables();
      if (items.length) items[0].focus();
      if (opts.onOpen) opts.onOpen();
    }
    function close() {
      modal.classList.remove("open");
      document.removeEventListener("keydown", onKeydown);
      modal.removeEventListener("mousedown", onBackdropClick);
      if (opts.onClose) opts.onClose();
      if (lastFocused && typeof lastFocused.focus === "function") lastFocused.focus();
    }

    var closeBtn = modal.querySelector("[data-modal-close]");
    if (closeBtn) closeBtn.addEventListener("click", close);

    return { open: open, close: close };
  }

  /** Shows/clears an inline validation message under a field. `fieldWrap` is the .f/.field
   *  element (gets .has-error); `errEl` is the .field-error node that holds the text. */
  function fieldError(fieldWrap, errEl, message) {
    if (errEl) { errEl.textContent = message || ""; errEl.classList.toggle("show", !!message); }
    if (fieldWrap) fieldWrap.classList.toggle("has-error", !!message);
  }

  /* ============================ Navigation shell (PART 1/8, Phase 2) ============================
     Adds a persistent sidebar across Teacher Admin pages, mirroring the Main Admin's
     sidebar (same admin.css classes: .sidebar/.nav-group/.nav-item/.burger) so no new
     visual design system is introduced. Purely additive: each page keeps its own
     existing markup/logic and only needs an empty <aside class="sidebar" id="teacherSidebar">
     placeholder plus a call to Teacher.mountSidebar('<key>'). */
  var NAV = [
    { key: "dashboard", label: "Dashboard", icon: "\u25A3", href: "index.html" },
    { key: "courses", label: "My Courses", icon: "\u25A4", href: "my-courses.html" },
    { key: "content", label: "Content Management", icon: "\u25A5", href: "my-courses.html" },
    { key: "students", label: "Students", icon: "\u25CD", href: "students.html" },
    { key: "progress", label: "Student Progress", icon: "\u25B6", href: "student-progress.html" },
    { key: "exams", label: "Exam Management", icon: "\u270E", href: "exams.html" },
    { key: "success-stories", label: "Success Stories", icon: "\u2605", href: "success-stories.html" },
    { key: "profile", label: "Profile", icon: "\u263A", href: "profile.html" }
  ];

  function mountSidebar(activeKey) {
    var host = document.getElementById("teacherSidebar");
    if (!host) return;
    host.setAttribute("role", "navigation");
    host.setAttribute("aria-label", "Teacher portal navigation");
    var session = getSession();
    var displayName = (session && (session.fullName || session.username)) || "Teacher";
    host.innerHTML =
      '<div class="brand-badge"><span class="dot">V</span> VITC Teacher</div>' +
      '<div class="nav-group">' + NAV.map(function (item) {
        return '<a class="nav-item' + (item.key === activeKey ? " active" : "") + '" href="' + item.href + '"' +
          (item.key === activeKey ? ' aria-current="page"' : "") + '>' +
          "<i>" + item.icon + "</i>" + esc(item.label) + "</a>";
      }).join("") + "</div>" +
      '<div class="nav-group">' +
        '<a class="nav-item" href="../index.html"><i>\u2197</i>Back to Website</a>' +
        '<a class="nav-item" href="#" id="sidebarLogoutBtn"><i>\u23FB</i>Logout</a>' +
      "</div>";
    var logoutLink = document.getElementById("sidebarLogoutBtn");
    if (logoutLink) logoutLink.addEventListener("click", function (e) { e.preventDefault(); logout(); });

    /* PART 3/8: dimmed backdrop behind the mobile drawer so it can be dismissed by tapping
       outside it, not just by re-tapping the burger. Created once and reused across pages. */
    var backdrop = document.getElementById("sidebarBackdrop");
    if (!backdrop) {
      backdrop = document.createElement("div");
      backdrop.id = "sidebarBackdrop";
      backdrop.className = "sidebar-backdrop";
      document.body.appendChild(backdrop);
    }
    var burger = document.getElementById("burger");

    /* PART 1/2 accessibility: the drawer now reports its state to assistive tech
       (aria-expanded), closes on Escape and returns focus to the menu button, and moves
       focus to the first nav link when opened by keyboard. */
    function syncBurger(open) {
      if (!burger) return;
      burger.setAttribute("aria-expanded", open ? "true" : "false");
      burger.setAttribute("aria-label", open ? "Close navigation menu" : "Open navigation menu");
    }
    function closeMobileNav(restoreFocus) {
      host.classList.remove("open");
      backdrop.classList.remove("open");
      syncBurger(false);
      if (restoreFocus && burger) burger.focus();
    }
    backdrop.addEventListener("click", function () { closeMobileNav(false); });

    if (burger) {
      syncBurger(false);
      burger.addEventListener("click", function () {
        var open = !host.classList.contains("open");
        host.classList.toggle("open", open);
        backdrop.classList.toggle("open", open);
        syncBurger(open);
        if (open) {
          var first = host.querySelector(".nav-item");
          if (first) first.focus();
        }
      });
    }
    document.addEventListener("keydown", function (e) {
      if ((e.key === "Escape" || e.keyCode === 27) && host.classList.contains("open")) {
        closeMobileNav(true);
      }
    });
    /* Following a link inside the drawer leaves it open behind the next page load on
       browsers that restore the DOM from cache - close it on navigation instead. */
    Array.prototype.forEach.call(host.querySelectorAll(".nav-item"), function (link) {
      link.addEventListener("click", function () { closeMobileNav(false); });
    });
    if (displayName) {
      var whoName = document.getElementById("whoName");
      if (whoName && !whoName.textContent.trim()) whoName.textContent = displayName;
    }
  }

  window.Teacher = {
    api: api,
    getSession: getSession,
    setSession: setSession,
    clearSession: clearSession,
    logout: logout,
    requireAuth: requireAuth,
    verifySession: verifySession,
    esc: esc,
    toast: toast,
    mountSidebar: mountSidebar,
    mediaUrl: mediaUrl,
    skeleton: skeleton,
    emptyState: emptyState,
    load: load,
    withButtonLoading: withButtonLoading,
    bindModal: bindModal,
    fieldError: fieldError
  };
})(window, document);
