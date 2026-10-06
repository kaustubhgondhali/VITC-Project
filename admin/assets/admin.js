/* =======================================================================
   VITC Admin Panel — shared runtime
   Handles: API access, authentication, layout, CRUD tables, forms, uploads
   ======================================================================= */
(function (window, document) {
  "use strict";

  /* ---------------------------------------------------------------
     Configuration — change API_BASE if the backend runs elsewhere.
     Can also be overridden with: localStorage.setItem('vitc_api','http://host:port')
  ---------------------------------------------------------------- */
  var DEFAULT_API = "http://localhost:8080";
  /* Only trust a stored base if it actually looks like an absolute http(s)
     URL (e.g. someone typed "admin" into the API base field by mistake) —
     otherwise fall back to the default and drop the bad value so it can't
     keep breaking every admin page on this origin. */
  var storedApi = window.localStorage.getItem("vitc_api");
  if (storedApi && !/^https?:\/\//i.test(storedApi)) {
    window.localStorage.removeItem("vitc_api");
    storedApi = null;
  }
  /* Normalize away any stray "/api" or "/api/v1" a stored value might carry
     (e.g. pasted from an old build) so it never doubles up with the
     "/api/v1" appended below. */
  var API_BASE = (storedApi || DEFAULT_API)
    .replace(/\/+$/, "").replace(/\/api(\/v1)?$/i, "");
  var API = API_BASE + "/api/v1";
  var SESSION_KEY = "vitc_admin";
  var SESSION_TTL_MS = 8 * 60 * 60 * 1000; /* must not exceed the backend session window */

  /* ============================ HTTP ============================ */
  function unwrap(json) {
    if (json && Object.prototype.hasOwnProperty.call(json, "data")) return json.data;
    return json;
  }

  async function request(path, options) {
    options = options || {};
    var init = { method: options.method || "GET", headers: {} };
    if (options.body !== undefined && !(options.body instanceof FormData)) {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(options.body);
    } else if (options.body instanceof FormData) {
      init.body = options.body;
    }
    var admin = getAdmin();
    if (admin) {
      init.headers["X-Admin-Username"] = admin.username;
      init.headers["X-Admin-Token"] = admin.sessionToken;
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
      if (json && json.errors) {
        msg += ": " + Object.keys(json.errors).map(function (k) { return json.errors[k]; }).join(", ");
      }
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
    del: function (p, b) { return request(p, { method: "DELETE", body: b }); },
    upload: function (file, folder) {
      var fd = new FormData();
      fd.append("file", file);
      var admin = getAdmin();
      if (folder === "gallery") {
        return request("/files/gallery" + (admin ? "?uploadedBy=" + encodeURIComponent(admin.username) : ""),
          { method: "POST", body: fd });
      }
      var qs = "?folder=" + encodeURIComponent(folder || "general") +
        (admin ? "&uploadedBy=" + encodeURIComponent(admin.username) : "");
      return request("/files" + qs, { method: "POST", body: fd });
    }
  };

  /* ============================ Auth ============================ */
  /* A session is only trusted when it carries a username AND a server issued
     token that has not expired locally. Every protected page additionally
     re-validates the token against the backend (see requireSession), so a
     hand-crafted localStorage entry can never open the panel. */
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
  function getAdmin() { return getSession(); }
  function setAdmin(a) {
    if (!a || !a.username || !a.sessionToken) {
      throw new Error("Login response did not include a session token.");
    }
    window.localStorage.setItem(SESSION_KEY, JSON.stringify({
      id: a.id,
      username: a.username,
      email: a.email,
      fullName: a.fullName,
      role: a.role,
      sessionToken: a.sessionToken,
      expiresAt: new Date(Date.now() + SESSION_TTL_MS).toISOString()
    }));
  }
  function clearSession() { window.localStorage.removeItem(SESSION_KEY); }
  function logout() {
    var s = getSession();
    clearSession();
    if (s) {
      request("/admins/logout", { method: "POST", body: { username: s.username, token: s.sessionToken } })
        .catch(function () { /* best effort */ });
    }
    window.location.href = "index.html";
  }
  function requireAuth() {
    if (!getSession()) { window.location.href = "index.html"; return false; }
    return true;
  }
  /* Server side check - kicks the user out if the token is unknown, revoked
     or expired on the backend. */
  async function verifySession() {
    var s = getSession();
    if (!s) return null;
    try {
      return await request("/admins/session", {
        method: "POST", body: { username: s.username, token: s.sessionToken }
      });
    } catch (e) {
      if (/Cannot reach the API/.test(e.message)) return null; // backend down: keep local state
      clearSession();
      window.location.href = "index.html";
      return null;
    }
  }

  /* ============================ UI utils ============================ */
  function el(html) {
    var d = document.createElement("div");
    d.innerHTML = html.trim();
    return d.firstElementChild;
  }
  function esc(v) {
    if (v === null || v === undefined) return "";
    return String(v).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function toast(message, type) {
    var host = document.getElementById("toasts");
    if (!host) { host = el('<div id="toasts"></div>'); document.body.appendChild(host); }
    var t = el('<div class="toast ' + (type || "") + '">' + esc(message) + "</div>");
    host.appendChild(t);
    setTimeout(function () { t.remove(); }, 4200);
  }
  function money(v, currency) {
    if (v === null || v === undefined || v === "") return "—";
    var n = Number(v);
    return (currency || "₹") + n.toLocaleString("en-IN", { maximumFractionDigits: 2 });
  }
  function date(v) {
    if (!v) return "—";
    var d = new Date(v);
    return isNaN(d) ? "—" : d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
  }
  function bytes(n) {
    if (!n) return "0 B";
    var u = ["B", "KB", "MB", "GB"], i = 0;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return n.toFixed(i ? 1 : 0) + " " + u[i];
  }
  function boolBadge(v, yes, no) {
    return v
      ? '<span class="badge badge-ok">' + (yes || "Yes") + "</span>"
      : '<span class="badge badge-muted">' + (no || "No") + "</span>";
  }
  function statusBadge(s) {
    var map = {
      SUCCESS: "ok", COMPLETED: "ok", DELIVERED: "ok", APPROVED: "ok", ACTIVE: "ok",
      PENDING: "warn", IN_PROGRESS: "warn", PROCESSING: "warn", NEW: "info",
      FAILED: "danger", CANCELLED: "danger", REJECTED: "danger", REFUNDED: "info", BLOCKED: "danger", SUSPENDED: "danger", INACTIVE: "muted"
    };
    return '<span class="badge badge-' + (map[s] || "muted") + '">' + esc(s || "—") + "</span>";
  }
  function mediaUrl(u) {
    if (!u) return "";
    if (/^https?:\/\//i.test(u)) return u;
    if (u.charAt(0) === "/") return API_BASE + u;
    return "../" + u;
  }

  /* ============================ Layout ============================ */
  var NAV = [
    { group: "Overview", items: [{ key: "dashboard", label: "Dashboard", icon: "▤", href: "dashboard.html" }] },
    {
      group: "Catalogue", items: [
        { key: "courses", label: "Courses", icon: "▣", href: "courses.html" },
        { key: "assignments", label: "Assignments", icon: "✎", href: "assignments.html" },
        { key: "assignment-upload", label: "Assignment Upload", icon: "⇪", href: "assignment-upload.html" }
      ]
    },
    {
      group: "Sales", items: [
        { key: "orders", label: "Orders", icon: "☰", href: "orders.html" },
        { key: "payments", label: "Payments", icon: "▦", href: "payments.html" },
        { key: "payment-settings", label: "Payment Settings", icon: "🔒", href: "payment-settings.html" }
      ]
    },

    {
      group: "Applications", items: [
        { key: "internships", label: "Internship Applications", icon: "\u2691", href: "internships.html" },
        { key: "job-portal", label: "Job Portal", icon: "\uD83D\uDCBC", href: "job-portal.html" },
        { key: "job-applications", label: "Job Applications", icon: "\uD83D\uDCC4", href: "job-applications.html" }
      ]
    },
    {
      group: "Content", items: [
        { key: "reviews", label: "Reviews", icon: "★", href: "reviews.html" },
        { key: "gallery", label: "Gallery", icon: "▩", href: "gallery.html" },
        { key: "faq", label: "FAQ", icon: "?", href: "faq.html" }
      ]
    },
    {
      group: "Teachers", items: [
        { key: "teachers", label: "Teachers", icon: "\u{1F469}\u200D\u{1F3EB}", href: "teachers.html" }
      ]
    },
    {
      group: "Students", items: [
        { key: "students", label: "Students", icon: "\u{1F393}", href: "students.html" },
        { key: "course-content", label: "Course Content", icon: "\u25A4", href: "course-content.html" },
        { key: "emails", label: "Credential Emails", icon: "\u2709", href: "emails.html" }
      ]
    },
    {
      group: "System", items: [
        { key: "users", label: "Users", icon: "◍", href: "users.html" },
        { key: "admins", label: "Admins", icon: "⚙", href: "admins.html" },
        { key: "smtp-settings", label: "Email / SMTP Settings", icon: "\u2709", href: "smtp-settings.html" }
      ]
    }
  ];

  function renderShell(activeKey, title) {
    if (!requireAuth()) return;
    var admin = getAdmin();
    verifySession();
    var nav = NAV.map(function (g) {
      return '<div class="nav-group"><h6>' + g.group + "</h6>" + g.items.map(function (i) {
        return '<a class="nav-item' + (i.key === activeKey ? " active" : "") + '" href="' + i.href + '">' +
          '<i>' + i.icon + "</i>" + i.label + "</a>";
      }).join("") + "</div>";
    }).join("");

    var shell = el(
      '<div class="layout">' +
        '<aside class="sidebar" id="sidebar">' +
          '<div class="brand-badge"><span class="dot">V</span> VITC Admin</div>' + nav +
          '<div class="nav-group"><a class="nav-item" href="../index.html"><i>↗</i>Back to Website</a>' +
          '<a class="nav-item" href="#" id="logoutBtn"><i>⏻</i>Logout</a></div>' +
        "</aside>" +
        '<div class="main">' +
          '<header class="topbar">' +
            '<div style="display:flex;align-items:center;gap:12px">' +
              '<button class="burger" id="burger">☰</button><h1>' + esc(title) + "</h1></div>" +
            '<div class="who"><span>' + esc(admin.username) + " · " + esc(admin.role || "ADMIN") + "</span>" +
            '<span class="avatar">' + esc((admin.username || "A").charAt(0).toUpperCase()) + "</span></div>" +
          "</header>" +
          '<main class="content" id="content"></main>' +
        "</div>" +
      "</div>"
    );
    document.body.prepend(shell);
    document.getElementById("logoutBtn").addEventListener("click", function (e) { e.preventDefault(); logout(); });
    document.getElementById("burger").addEventListener("click", function () {
      document.getElementById("sidebar").classList.toggle("open");
    });
    if (!document.getElementById("toasts")) document.body.appendChild(el('<div id="toasts"></div>'));
    return document.getElementById("content");
  }

  /* ============================ Modal ============================ */
  function openModal(title, bodyHtml, onSubmit, submitLabel) {
    var modal = el(
      '<div class="modal open"><div class="modal-card">' +
        '<div class="modal-head"><h3>' + esc(title) + '</h3><button class="x">&times;</button></div>' +
        '<form><div class="modal-body">' + bodyHtml + "</div>" +
        '<div class="modal-foot"><button type="button" class="btn cancel">Cancel</button>' +
        '<button type="submit" class="btn btn-primary">' + esc(submitLabel || "Save") + "</button></div></form>" +
      "</div></div>"
    );
    document.body.appendChild(modal);
    function close() { modal.remove(); }
    modal.querySelector(".x").addEventListener("click", close);
    modal.querySelector(".cancel").addEventListener("click", close);
    modal.addEventListener("click", function (e) { if (e.target === modal) close(); });
    modal.querySelector("form").addEventListener("submit", async function (e) {
      e.preventDefault();
      var btn = modal.querySelector('button[type="submit"]');
      btn.disabled = true;
      try {
        await onSubmit(modal, close);
      } catch (err) {
        toast(err.message, "err");
      } finally {
        btn.disabled = false;
      }
    });
    return modal;
  }

  function confirmDelete(what, onYes) {
    openModal("Delete " + what, '<p>This action cannot be undone. Delete <strong>' + esc(what) + "</strong>?</p>",
      async function (modal, close) { await onYes(); close(); }, "Delete");
  }

  /* ============================ Form fields ============================ */
  function fieldHtml(f, value) {
    var v = value === undefined || value === null ? (f.default !== undefined ? f.default : "") : value;
    var id = "f_" + f.name;
    var input;
    if (f.type === "textarea") {
      input = '<textarea id="' + id + '" name="' + f.name + '"' + (f.required ? " required" : "") +
        (f.placeholder ? ' placeholder="' + esc(f.placeholder) + '"' : "") + ">" + esc(v) + "</textarea>";
    } else if (f.type === "select") {
      input = '<select id="' + id + '" name="' + f.name + '"' + (f.required ? " required" : "") + ">" +
        (f.allowEmpty ? '<option value="">— none —</option>' : "") +
        (f.options || []).map(function (o) {
          var val = typeof o === "string" ? o : o.value;
          var lbl = typeof o === "string" ? o : o.label;
          return '<option value="' + esc(val) + '"' + (String(v) === String(val) ? " selected" : "") + ">" + esc(lbl) + "</option>";
        }).join("") + "</select>";
    } else if (f.type === "checkbox") {
      return '<div class="field checkline"><input type="checkbox" id="' + id + '" name="' + f.name + '"' +
        (v === true || v === "true" ? " checked" : "") + '><label for="' + id + '" style="margin:0">' + esc(f.label) + "</label></div>";
    } else if (f.type === "image") {
      input =
        '<div style="display:flex;gap:8px">' +
          '<input type="text" id="' + id + '" name="' + f.name + '" value="' + esc(v) + '"' +
            (f.required ? " required" : "") + ' placeholder="assets/img/... or upload">' +
          '<button type="button" class="btn btn-sm" data-upload="' + id + '" data-folder="' + esc(f.folder || "general") + '">Upload</button>' +
        "</div>" +
        '<div class="muted" style="font-size:12px;margin-top:6px" data-preview="' + id + '"></div>';
    } else {
      input = '<input type="' + (f.type || "text") + '" id="' + id + '" name="' + f.name + '" value="' + esc(v) + '"' +
        (f.required ? " required" : "") + (f.step ? ' step="' + f.step + '"' : "") +
        (f.min !== undefined ? ' min="' + f.min + '"' : "") +
        (f.placeholder ? ' placeholder="' + esc(f.placeholder) + '"' : "") + ">";
    }
    return '<div class="field"><label for="' + id + '">' + esc(f.label) + (f.required ? " *" : "") + "</label>" + input +
      (f.hint ? '<div class="muted" style="font-size:12px;margin-top:4px">' + esc(f.hint) + "</div>" : "") + "</div>";
  }

  function buildForm(fields, record) {
    var html = "";
    for (var i = 0; i < fields.length; i++) {
      var f = fields[i];
      if (f.half && fields[i + 1] && fields[i + 1].half) {
        html += '<div class="field-row"><div>' + fieldHtml(f, record ? record[f.name] : undefined) + "</div>" +
          "<div>" + fieldHtml(fields[i + 1], record ? record[fields[i + 1].name] : undefined) + "</div></div>";
        i++;
      } else {
        html += fieldHtml(f, record ? record[f.name] : undefined);
      }
    }
    return html;
  }

  function wireUploads(modal) {
    modal.querySelectorAll("[data-upload]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var target = modal.querySelector("#" + btn.getAttribute("data-upload"));
        var picker = el('<input type="file" accept="image/*" style="display:none">');
        document.body.appendChild(picker);
        picker.addEventListener("change", async function () {
          if (!picker.files[0]) return;
          btn.disabled = true; btn.textContent = "…";
          try {
            var saved = await api.upload(picker.files[0], btn.getAttribute("data-folder"));
            target.value = saved.url;
            var pv = modal.querySelector('[data-preview="' + target.id + '"]');
            if (pv) pv.innerHTML = '<img class="thumb" src="' + esc(mediaUrl(saved.url)) + '" alt="">';
            toast("File uploaded", "ok");
          } catch (e) { toast(e.message, "err"); }
          btn.disabled = false; btn.textContent = "Upload";
          picker.remove();
        });
        picker.click();
      });
    });
  }

  function readForm(modal, fields) {
    var payload = {};
    fields.forEach(function (f) {
      var input = modal.querySelector('[name="' + f.name + '"]');
      if (!input) return;
      var val;
      if (f.type === "checkbox") val = input.checked;
      else if (f.type === "number") val = input.value === "" ? null : Number(input.value);
      else val = input.value === "" ? null : input.value;
      payload[f.name] = val;
    });
    return payload;
  }

  /* ============================ CRUD page ============================ */
  /**
   * config = {
   *   key, title, endpoint, fields, columns,
   *   listPath (default endpoint), idKey='id',
   *   searchKeys: [], rowActions: [ {label, className, onClick(row, reload)} ],
   *   toPayload(record), canCreate, createLabel, emptyText
   * }
   */
  function crudPage(config) {
    var mount = renderShell(config.key, config.title);
    if (!mount) return;
    var rows = [];
    var filter = "";

    mount.innerHTML =
      '<div class="toolbar"><div class="left">' +
        '<input class="search" id="search" type="search" placeholder="Search…">' +
        '<span class="muted" id="count"></span></div>' +
        (config.canCreate === false ? "" :
          '<button class="btn btn-primary" id="addBtn">+ ' + esc(config.createLabel || "New") + "</button>") +
      "</div>" +
      '<div class="table-wrap"><table><thead><tr>' +
        config.columns.map(function (c) { return "<th>" + esc(c.label) + "</th>"; }).join("") +
        '<th class="right">Actions</th></tr></thead><tbody id="tbody"></tbody></table>' +
        '<div class="empty" id="empty" style="display:none">Loading…</div></div>';

    var tbody = mount.querySelector("#tbody");
    var empty = mount.querySelector("#empty");

    function matches(r) {
      if (!filter) return true;
      var keys = config.searchKeys || Object.keys(r);
      return keys.some(function (k) {
        return String(r[k] === undefined || r[k] === null ? "" : r[k]).toLowerCase().indexOf(filter) >= 0;
      });
    }

    function draw() {
      var view = rows.filter(matches);
      mount.querySelector("#count").textContent = view.length + " of " + rows.length + " records";
      tbody.innerHTML = "";
      if (!view.length) {
        empty.style.display = "block";
        empty.textContent = config.emptyText || "No records yet.";
        return;
      }
      empty.style.display = "none";
      view.forEach(function (r) {
        var tr = document.createElement("tr");
        var cells = config.columns.map(function (c) {
          return "<td>" + (c.render ? c.render(r) : esc(r[c.key])) + "</td>";
        }).join("");
        var actions = (config.rowActions || []).map(function (a, i) {
          if (a.visible && !a.visible(r)) return "";
          return '<button class="btn btn-sm ' + (a.className || "") + '" data-act="' + i + '">' + esc(typeof a.label === "function" ? a.label(r) : a.label) + "</button>";
        }).join(" ");
        var std = "";
        if (config.fields && config.canEdit !== false) std += ' <button class="btn btn-sm" data-edit="1">Edit</button>';
        if (config.canDelete !== false) std += ' <button class="btn btn-sm btn-danger" data-del="1">Delete</button>';
        tr.innerHTML = cells + '<td class="actions">' + actions + std + "</td>";

        tr.querySelectorAll("[data-act]").forEach(function (b) {
          b.addEventListener("click", async function () {
            b.disabled = true;
            try { await config.rowActions[Number(b.getAttribute("data-act"))].onClick(r, load); }
            catch (e) { toast(e.message, "err"); }
            b.disabled = false;
          });
        });
        var editBtn = tr.querySelector("[data-edit]");
        if (editBtn) editBtn.addEventListener("click", function () { form(r); });
        var delBtn = tr.querySelector("[data-del]");
        if (delBtn) delBtn.addEventListener("click", function () {
          confirmDelete(config.labelOf ? config.labelOf(r) : ("record #" + r.id), async function () {
            await api.del((config.endpoint) + "/" + r[config.idKey || "id"]);
            toast("Deleted", "ok");
            load();
          });
        });
        tbody.appendChild(tr);
      });
    }

    async function form(record) {
      var fields = config.fields;
      try {
        if (config.prepareFields) fields = await config.prepareFields(record);
      } catch (e) {
        toast(e.message, "err");
        return;
      }
      var modal = openModal(
        (record ? "Edit " : "New ") + (config.singular || config.title),
        buildForm(fields, record),
        async function (m, close) {
          var payload = readForm(m, fields);
          if (config.toPayload) payload = config.toPayload(payload, record);
          if (record) await api.put(config.endpoint + "/" + record[config.idKey || "id"], payload);
          else await api.post(config.endpoint, payload);
          toast(record ? "Updated successfully" : "Created successfully", "ok");
          close();
          load();
        }
      );
      wireUploads(modal);
      if (config.onFormReady) config.onFormReady(modal, record, fields);
      // Show existing image previews
      modal.querySelectorAll("[data-preview]").forEach(function (pv) {
        var input = modal.querySelector("#" + pv.getAttribute("data-preview"));
        if (input && input.value) pv.innerHTML = '<img class="thumb" src="' + esc(mediaUrl(input.value)) + '" alt="">';
      });
    }

    async function load() {
      empty.style.display = "block";
      empty.textContent = "Loading…";
      tbody.innerHTML = "";
      try {
        var data = await api.get(config.listPath || config.endpoint);
        rows = Array.isArray(data) ? data : (data && data.content) || [];
        draw();
      } catch (e) {
        empty.textContent = e.message;
        toast(e.message, "err");
      }
    }

    var addBtn = mount.querySelector("#addBtn");
    if (addBtn) addBtn.addEventListener("click", function () { form(null); });
    mount.querySelector("#search").addEventListener("input", function (e) {
      filter = e.target.value.trim().toLowerCase();
      draw();
    });
    load();
    return { reload: load, mount: mount };
  }

  /* ============================ Exports ============================ */
  window.Admin = {
    api: api, API_BASE: API_BASE,
    getAdmin: getAdmin, setAdmin: setAdmin, logout: logout, requireAuth: requireAuth,
    clearSession: clearSession, verifySession: verifySession,
    renderShell: renderShell, crudPage: crudPage, openModal: openModal, confirmDelete: confirmDelete,
    toast: toast, esc: esc, el: el, money: money, date: date, bytes: bytes,
    boolBadge: boolBadge, statusBadge: statusBadge, mediaUrl: mediaUrl
  };
})(window, document);
