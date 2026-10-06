/* =======================================================================
   VITC — Public site API client
   Single place where the website talks to the Spring Boot backend.

   Base URL resolution order:
     1. window.VITC_API_BASE   (set inline in a page before this script)
     2. localStorage 'vitc_api'
     3. <meta name="vitc-api-base" content="...">
     4. same origin when the site is served by the backend, else
        http://localhost:8080
   ======================================================================= */
(function (window, document) {
  "use strict";

  function resolveBase() {
    if (window.VITC_API_BASE) return String(window.VITC_API_BASE);
    try {
      var stored = window.localStorage.getItem("vitc_api");
      /* Guard against garbage values (e.g. someone typed "admin" into an API
         base field by mistake) making it into localStorage: only trust a
         stored value if it actually looks like an absolute http(s) URL.
         Anything else is ignored and removed so it can't keep breaking every
         page on this origin. */
      if (stored) {
        if (/^https?:\/\//i.test(stored)) return stored;
        try { window.localStorage.removeItem("vitc_api"); } catch (e2) { /* ignore */ }
      }
    } catch (e) { /* storage blocked */ }
    var meta = document.querySelector('meta[name="vitc-api-base"]');
    if (meta && meta.content) return meta.content;
    if (window.location.port === "8080") return window.location.origin;
    return "http://localhost:8080";
  }

  /* Whatever source the base comes from (inline override, localStorage from
     an older build, a <meta> tag), normalize it down to a bare origin so a
     stray "/api" or "/api/v1" saved there can never get doubled up with the
     "/api/v1" this file appends below. */
  function normalizeBase(raw) {
    return String(raw).replace(/\/+$/, "").replace(/\/api(\/v1)?$/i, "");
  }

  var BASE = normalizeBase(resolveBase());
  var API = BASE + "/api/v1";

  function unwrap(json) {
    return json && Object.prototype.hasOwnProperty.call(json, "data") ? json.data : json;
  }

  function errorMessage(json, res) {
    if (json) {
      if (json.errors && typeof json.errors === "object") {
        var parts = Object.keys(json.errors).map(function (k) { return json.errors[k]; });
        if (parts.length) return parts.join(", ");
      }
      if (json.message) return json.message;
      if (json.error) return json.error;
    }
    return "Request failed (" + res.status + " " + res.statusText + ")";
  }

  async function request(path, options) {
    options = options || {};
    var init = { method: options.method || "GET", headers: {} };
    if (options.body instanceof FormData) {
      /* Let the browser set the multipart boundary itself. */
      init.body = options.body;
    } else if (options.body !== undefined) {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(options.body);
    }
    var res;
    try {
      res = await fetch(API + path, init);
    } catch (e) {
      throw new Error("Cannot reach the VITC server at " + BASE + ". Please try again later.");
    }
    var text = await res.text();
    var json = null;
    try { json = text ? JSON.parse(text) : null; } catch (e) { /* non-JSON body */ }
    if (!res.ok) throw new Error(errorMessage(json, res));
    return unwrap(json);
  }

  /* Content bundled in assets/js/content-data.js is used when the API is
     unreachable or has nothing published yet, so Testimonials and Blog never
     render an empty page on static hosting. */
  function staticList(key) {
    var s = window.VITC_STATIC;
    return (s && Array.isArray(s[key])) ? s[key] : null;
  }
  function withStatic(promise, key) {
    return promise.then(function (list) {
      if (Array.isArray(list) && list.length) return list;
      return staticList(key) || list || [];
    }).catch(function (err) {
      var fallback = staticList(key);
      if (fallback) return fallback;
      throw err;
    });
  }

  /* --------------------------- endpoints --------------------------- */
  var api = {
    base: BASE,
    request: request,
    health: function () { return request("/health"); },

    courses: function () { return request("/courses/active"); },
    course: function (code) { return request("/courses/code/" + encodeURIComponent(code)); },

    assignments: function () { return request("/assignments/active"); },
    assignment: function (code) { return request("/assignments/code/" + encodeURIComponent(code)); },

    reviews: function () { return request("/reviews/approved"); },
    featuredReviews: function () { return request("/reviews/featured"); },
    submitReview: function (body) { return request("/reviews", { method: "POST", body: body }); },

    testimonials: function () { return withStatic(request("/testimonials/approved"), "testimonials"); },
    successStories: function () { return request("/success-stories/approved"); },
    successStoriesByCategory: function (category) { return request("/success-stories/category/" + encodeURIComponent(category)); },
    gallery: function () { return request("/gallery"); },
    faqs: function () { return request("/faqs/active"); },
    pricing: function () { return request("/pricing/active"); },
    blogPosts: function () { return withStatic(request("/blog-posts/published"), "blogPosts"); },
    blogPost: function (slug) {
      return api.blogPosts().then(function (list) {
        return (list || []).filter(function (b) { return b.slug === slug; })[0] || null;
      });
    },

    contact: function (body) { return request("/contact-messages", { method: "POST", body: body }); },
    enroll: function (body) { return request("/enrollments", { method: "POST", body: body }); },
    applyJob: function (body) { return request("/careers", { method: "POST", body: body }); },
    applyInternship: function (body) { return request("/internships", { method: "POST", body: body }); },

    uploadFile: function (file, folder) {
      var fd = new FormData();
      fd.append("file", file);
      return request("/files?folder=" + encodeURIComponent(folder || "general"), { method: "POST", body: fd });
    },

    invoiceByOrder: function (code) { return request("/invoices/order/" + encodeURIComponent(code)); }
  };

  /* --------------------------- helpers --------------------------- */
  function esc(v) {
    if (v === null || v === undefined) return "";
    return String(v).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function INR(n) { return "₹" + Number(n || 0).toLocaleString("en-IN"); }
  function stars(n) {
    n = Math.max(0, Math.min(5, Number(n) || 0));
    return "★★★★★".slice(0, n) + "☆☆☆☆☆".slice(0, 5 - n);
  }
  function timeAgo(iso) {
    if (!iso) return "";
    var d = new Date(iso);
    if (isNaN(d)) return "";
    var days = Math.floor((Date.now() - d.getTime()) / 86400000);
    if (days < 1) return "today";
    if (days < 30) return days + (days === 1 ? " day ago" : " days ago");
    var m = Math.floor(days / 30);
    if (m < 12) return m + (m === 1 ? " month ago" : " months ago");
    var y = Math.floor(m / 12);
    return y + (y === 1 ? " year ago" : " years ago");
  }
  function splitFeatures(v) {
    if (!v) return [];
    if (Array.isArray(v)) return v;
    return String(v).split(/\s*(?:\||\n|;)\s*/).filter(Boolean);
  }
  function mediaUrl(u) {
    if (!u) return "";
    if (/^(https?:)?\/\//i.test(u)) return u;
    if (u.charAt(0) === "/") return BASE + u;
    return u;
  }

  /* UI state helpers — loading / empty / error, used by every page */
  function setState(host, kind, message) {
    if (!host) return;
    var body = {
      loading: '<div class="state state-loading"><span class="spinner"></span> Loading…</div>',
      empty: '<div class="state state-empty"><div class="ico">🗂️</div><p>' + esc(message || "Nothing to show yet.") + "</p></div>",
      error: '<div class="state state-error"><div class="ico">⚠️</div><p>' + esc(message || "Something went wrong.") + '</p><button type="button" class="btn btn-ghost btn-sm" data-retry>Try again</button></div>'
    }[kind];
    host.innerHTML = '<div class="state-wrap">' + body + "</div>";
  }

  /**
   * Standard load cycle for a container:
   *   loading -> fetch -> render / empty / error(with retry)
   */
  function load(host, fetcher, render, emptyMessage) {
    if (!host) return Promise.resolve();
    setState(host, "loading");
    return fetcher().then(function (data) {
      var list = Array.isArray(data) ? data : (data ? [data] : []);
      if (!list.length) { setState(host, "empty", emptyMessage); return data; }
      render(data, host);
      window.dispatchEvent(new CustomEvent("vitc:rendered", { detail: { host: host } }));
      return data;
    }).catch(function (err) {
      setState(host, "error", err.message);
      var btn = host.querySelector("[data-retry]");
      if (btn) btn.addEventListener("click", function () { load(host, fetcher, render, emptyMessage); });
    });
  }

  function toast(msg, type) {
    var t = document.getElementById("toast");
    if (!t) { t = document.createElement("div"); t.id = "toast"; document.body.appendChild(t); }
    t.textContent = msg;
    t.className = type ? "toast-" + type : "";
    t.classList.add("show");
    clearTimeout(t._h);
    t._h = setTimeout(function () { t.classList.remove("show"); }, 3600);
  }

  /* ----------------- catalogue cache (courses + assignments) -----------------
     Exposes the same shape the older static catalogue used, so the shared
     upgrade.js checkout logic keeps working — but the data now comes from
     the database through the REST API.                                     */
  var catalogPromise = null;

  function mapCourse(c) {
    return {
      id: c.code, dbId: c.id, code: c.code, title: c.title, price: Number(c.price),
      meta: c.meta || [c.durationMonths, c.level].filter(Boolean).join(" • "),
      desc: c.description || "", cat: (c.category || "").toLowerCase(),
      ico: c.icon || "🎓", level: c.level, duration: c.durationMonths, type: "course"
    };
  }
  function mapAssignment(a) {
    return {
      id: a.code, dbId: a.id, code: a.code, title: a.title, price: Number(a.price),
      desc: a.description || "", tech: a.tech || "", diff: a.difficulty || "Beginner",
      days: a.deliveryDays || "", cat: (a.category || "").toLowerCase(), ico: a.icon || "📦",
      features: splitFeatures(a.features),
      meta: [a.tech, a.deliveryDays ? "Delivery in " + a.deliveryDays : null].filter(Boolean).join(" • "),
      type: "assignment"
    };
  }

  function catalog() {
    if (!catalogPromise) {
      catalogPromise = Promise.all([api.courses(), api.assignments()])
        .then(function (r) {
          var data = { courses: (r[0] || []).map(mapCourse), assignments: (r[1] || []).map(mapAssignment) };
          window.VITC_DATA = data;
          return data;
        })
        .catch(function (err) {
          catalogPromise = null; /* allow a retry */
          throw err;
        });
    }
    return catalogPromise;
  }

  window.VITC = {
    api: api, catalog: catalog, load: load, setState: setState,
    esc: esc, INR: INR, stars: stars, timeAgo: timeAgo,
    splitFeatures: splitFeatures, mediaUrl: mediaUrl, toast: toast,
    mapCourse: mapCourse, mapAssignment: mapAssignment
  };
  window.VITC_DATA = window.VITC_DATA || { courses: [], assignments: [] };
})(window, document);
