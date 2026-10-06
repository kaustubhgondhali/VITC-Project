/* =======================================================================
   VITC — Page hydration layer
   Renders every public page section from the Spring Boot REST API.
   Requires assets/js/api.js to be loaded first.
   ======================================================================= */
(function (window, document) {
  "use strict";

  var V = window.VITC;
  if (!V) return;
  var esc = V.esc, INR = V.INR, stars = V.stars, media = V.mediaUrl;

  /* Keep the legacy career.html route while presenting the public navigation
     consistently as Job Portal across pages that still use the shared shell. */
  Array.prototype.forEach.call(document.querySelectorAll('a[href="career.html"]'), function (link) {
    link.textContent = "Job Portal";
  });

  function $(sel, root) { return (root || document).querySelector(sel); }
  function el(id) { return document.getElementById(id); }

  /* ------------------------------ cards ------------------------------ */
  function courseCard(c) {
    return '<div class="course-card assign-card" data-item data-cat="' + esc(c.cat) + '" data-price="' + c.price +
      '" data-title="' + esc(c.title) + '">' +
      '<div class="head"><div class="ico">' + esc(c.ico) + '</div><h3>' + esc(c.title) + "</h3></div>" +
      '<p class="desc">' + esc(c.desc) + "</p>" +
      '<div class="tag-row">' + (c.level ? '<span class="pill">' + esc(c.level) + "</span>" : "") +
      (c.duration ? '<span class="pill pill-tech">' + esc(c.duration) + "</span>" : "") + "</div>" +
      '<div class="foot"><div><div class="price">' + INR(c.price) + '</div><div class="price-note">Incl. material & certificate</div></div>' +
      '<div class="btn-row"><a class="btn btn-ghost btn-sm" href="contact.html">Enquire</a>' +
      '<button class="btn btn-primary btn-sm" data-buy="' + esc(c.id) + '" data-buy-type="course">Buy Now →</button></div></div></div>';
  }

  function catalogueCourseCard(c) {
    return '<div class="course-card" data-course="' + esc(c.cat) + '" data-item data-cat="' + esc(c.cat) +
      '" data-price="' + c.price + '" data-title="' + esc(c.title) + '">' +
      '<div class="head"><div class="ico">' + esc(c.ico) + '</div><h3>' + esc(c.title) + "</h3></div>" +
      '<p style="font-size:.9rem;margin-top:8px">' + esc(c.desc) + "</p>" +
      '<div class="meta"><span><b>Duration</b>' + esc(c.duration || c.meta) + "</span>" +
      '<a href="' + (String(c.id).toUpperCase() === "JAVA" ? "course-java.html" : "buy-course.html") +
      '" class="btn btn-primary" style="padding:9px 18px;font-size:.85rem">Enrol →</a></div></div>';
  }

  function assignmentCard(a) {
    return '<div class="course-card assign-card" data-item data-cat="' + esc(a.cat) + '" data-price="' + a.price +
      '" data-title="' + esc(a.title) + '">' +
      '<div class="head"><div class="ico">' + esc(a.ico) + '</div><h3>' + esc(a.title) + "</h3></div>" +
      '<p class="desc">' + esc(a.desc) + "</p>" +
      '<div class="tag-row"><span class="diff diff-' + esc(a.diff) + '">' + esc(a.diff) + "</span>" +
      (a.tech ? '<span class="pill pill-tech">' + esc(a.tech) + "</span>" : "") +
      (a.days ? '<span class="pill">' + esc(a.days) + "</span>" : "") + "</div>" +
      '<div class="foot"><div><div class="price">' + INR(a.price) + "</div></div>" +
      '<div class="btn-row"><a class="btn btn-ghost btn-sm" href="assignment-details.html?id=' + encodeURIComponent(a.id) + '">Details</a>' +
      '<button class="btn btn-primary btn-sm" data-buy="' + esc(a.id) + '" data-buy-type="assignment">Buy →</button></div></div></div>';
  }

  function reviewCard(r) {
    var img = r.imageUrl ? '<img src="' + esc(media(r.imageUrl)) + '" alt="' + esc(r.reviewerName) +
      '" style="width:44px;height:44px;border-radius:50%;object-fit:cover" loading="lazy">'
      : '<span class="avatar-initial" style="width:44px;height:44px;border-radius:50%;background:var(--accent,#0B3D91);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700">' +
        esc((r.reviewerName || "?").charAt(0).toUpperCase()) + "</span>";
    return '<div class="card" style="border-left:4px solid var(--accent)">' +
      '<div style="display:flex;align-items:center;gap:12px;margin-bottom:10px">' + img +
      "<div><b style=\"display:block\">" + esc(r.reviewerName) + "</b>" +
      '<span style="color:var(--muted);font-size:.8rem">' + esc(V.timeAgo(r.createdAt)) +
      (r.courseTitle ? " · " + esc(r.courseTitle) : "") + "</span></div></div>" +
      '<div class="stars">' + stars(r.rating) + "</div>" +
      '<p style="font-size:.92rem">' + esc(r.comment) + "</p></div>";
  }

  /* Derive a filter key from the course/role text so the Testimonials filter
     bar works with the same records the API returns. */
  var TESTI_CATS = [
    ["fullstack", /full ?stack|web develop|react|node|mern/i],
    ["data", /data|analytic|machine learning|\bai\b|artificial/i],
    ["marketing", /marketing|seo|social media/i],
    ["cloud", /cloud|aws|azure|network|hardware|devops/i],
    ["cyber", /cyber|security|ethical hack/i],
    ["design", /design|graphic|photoshop|ui\/ux/i],
    ["account", /excel|tally|account|office|typing|mscit/i],
    ["programming", /program|python|java|\bc\+\+|coding|software/i]
  ];

  function testimonialCategory(t) {
    if (t.category) return String(t.category).toLowerCase();
    var hay = [t.course, t.courseTitle, t.role, t.message].filter(Boolean).join(" ");
    for (var i = 0; i < TESTI_CATS.length; i++) {
      if (TESTI_CATS[i][1].test(hay)) return TESTI_CATS[i][0];
    }
    return "programming";
  }

  function testimonialCard(t) {
    var course = t.course || t.courseTitle || "";
    return '<div class="testi-card" data-testi data-cat="' + esc(testimonialCategory(t)) + '">' +
      '<span class="quote">"</span><div class="stars" aria-label="Rated ' + (Number(t.rating) || 5) + ' out of 5">' + stars(t.rating) + "</div>" +
      "<p>" + esc(t.message) + "</p>" +
      (course ? '<span class="tag" style="display:inline-block;padding:4px 12px;background:rgba(11,61,145,.08);color:var(--primary);border-radius:99px;font-size:.75rem;font-weight:600;margin-bottom:14px">' + esc(course) + "</span>" : "") +
      '<div class="who">' + (t.photoUrl ? '<img src="' + esc(media(t.photoUrl)) + '" alt="' + esc(t.name) + ' — VITC student" loading="lazy" width="52" height="52">' : "") +
      "<div><b>" + esc(t.name) + "</b><span>" + esc(t.role || V.timeAgo(t.createdAt)) + "</span></div></div></div>";
  }

  function pricingCard(p) {
    var features = V.splitFeatures(p.featureList && p.featureList.length ? p.featureList : p.features);
    return '<div class="card' + (p.highlighted ? " card-highlight" : "") + '" style="text-align:center">' +
      '<div class="ico" style="margin:0 auto 18px;font-size:1.4rem">' + esc(p.currency || "₹") + "</div>" +
      "<h3>" + esc(p.name) + "</h3>" +
      '<p style="margin:10px 0 6px;font-size:.9rem">' + esc(p.tagline || "") + "</p>" +
      '<div class="price" style="font-size:1.3rem;font-weight:800">' + INR(p.price) +
      (p.oldPrice ? ' <s style="font-weight:400;font-size:.9rem;color:var(--muted)">' + INR(p.oldPrice) + "</s>" : "") + "</div>" +
      '<ul style="list-style:none;text-align:left;font-size:.9rem;color:var(--muted);line-height:2;margin-top:12px">' +
      features.map(function (f) { return "<li>✔ " + esc(f) + "</li>"; }).join("") + "</ul>" +
      '<a href="contact.html" class="btn btn-primary" style="margin-top:16px;width:100%">Enquire</a></div>';
  }

  function galleryFigure(g) {
    return '<figure class="g-item" data-cat="' + esc((g.category || "all").toLowerCase()) + '">' +
      '<img src="' + esc(media(g.imageUrl)) + '" alt="' + esc(g.title) + '" loading="lazy">' +
      '<figcaption class="cap">' + esc(g.caption || g.title) + "</figcaption></figure>";
  }

  function blogCategory(b) {
    return b.category || (b.tags ? String(b.tags).split(",")[0].trim() : "");
  }

  function blogDate(b) {
    var iso = b.publishedAt || b.createdAt;
    if (!iso) return "";
    var d = new Date(iso);
    if (isNaN(d.getTime())) return "";
    return d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
  }

  function blogCard(b) {
    var href = b.slug ? "blog.html?post=" + encodeURIComponent(b.slug) : "#";
    var cat = blogCategory(b);
    var date = blogDate(b);
    var metaLine = [date, b.author].filter(Boolean).join(" · ");
    return '<article class="blog-card" data-blog data-search="' +
      esc(((b.title || "") + " " + (b.excerpt || "") + " " + cat + " " + (b.author || "")).toLowerCase()) + '">' +
      (b.coverImageUrl || b.imageUrl ? '<a href="' + href + '"><img src="' + esc(media(b.coverImageUrl || b.imageUrl)) + '" alt="' + esc(b.title) + '" loading="lazy" width="1024" height="220"></a>' : "") +
      '<div class="body">' + (cat ? '<span class="tag">' + esc(cat) + "</span>" : "") +
      '<h3><a href="' + href + '" style="color:inherit">' + esc(b.title) + "</a></h3>" +
      (metaLine ? '<p style="color:var(--muted);font-size:.82rem;margin-top:6px">' + esc(metaLine) + "</p>" : "") +
      '<p style="margin:10px 0 14px">' + esc(b.excerpt || b.summary || "") + "</p>" +
      '<a href="' + href + '" class="btn btn-ghost" style="padding:9px 20px;font-size:.88rem">Read More →</a></div></article>';
  }

  function faqItem(f) {
    return '<div class="faq-item"><button class="faq-q" type="button">' + esc(f.question) + "</button>" +
      '<div class="faq-a"><p>' + esc(f.answer) + "</p></div></div>";
  }

  /* --------------------------- interactions --------------------------- */
  function bindFaq(host) {
    host.querySelectorAll(".faq-q").forEach(function (q) {
      q.addEventListener("click", function () {
        var item = q.closest(".faq-item");
        item.classList.toggle("open");
      });
    });
  }

  function bindGallery(host) {
    var filterHost = el("galleryFilters");
    if (filterHost) {
      var categories = [];
      host.querySelectorAll(".g-item").forEach(function (item) {
        var category = (item.dataset.cat || "").trim().toLowerCase();
        if (category && category !== "all" && categories.indexOf(category) < 0) categories.push(category);
      });
      categories.sort();
      filterHost.innerHTML = '<button data-filter="all" class="active">All</button>' + categories.map(function (category) {
        var label = category.charAt(0).toUpperCase() + category.slice(1);
        return '<button data-filter="' + esc(category) + '">' + esc(label) + '</button>';
      }).join("");
    }
    document.querySelectorAll("[data-filter]").forEach(function (b) {
      b.addEventListener("click", function () {
        document.querySelectorAll("[data-filter]").forEach(function (x) { x.classList.remove("active"); });
        b.classList.add("active");
        var f = b.dataset.filter;
        host.querySelectorAll(".g-item").forEach(function (g) {
          g.style.display = (f === "all" || g.dataset.cat === f) ? "" : "none";
        });
      });
    });
    var lb = el("lightbox");
    if (lb) {
      host.querySelectorAll("img").forEach(function (img) {
        img.addEventListener("click", function () {
          lb.querySelector("img").src = img.src;
          lb.classList.add("open");
        });
      });
    }
  }

  function bindCourseSearch(host) {
    var input = el("courseSearch");
    var cards = [].slice.call(host.querySelectorAll("[data-course]"));
    function apply() {
      var q = (input && input.value || "").toLowerCase().trim();
      var active = document.querySelector("[data-cfilter].active");
      var f = active ? active.dataset.cfilter : "all";
      cards.forEach(function (c) {
        var ok = (f === "all" || c.dataset.course === f) && c.textContent.toLowerCase().indexOf(q) > -1;
        c.style.display = ok ? "" : "none";
      });
    }
    document.querySelectorAll("[data-cfilter]").forEach(function (b) {
      b.addEventListener("click", function () {
        document.querySelectorAll("[data-cfilter]").forEach(function (x) { x.classList.remove("active"); });
        b.classList.add("active");
        apply();
      });
    });
    if (input) input.addEventListener("input", apply);
    apply();
  }

  function buildFilterBar(host, items, attr) {
    if (!host) return;
    var cats = [];
    items.forEach(function (i) { if (i.cat && cats.indexOf(i.cat) < 0) cats.push(i.cat); });
    host.innerHTML = '<button data-' + attr + '="all" class="active">All</button>' +
      cats.map(function (c) {
        return "<button data-" + attr + '="' + esc(c) + '">' + esc(c.charAt(0).toUpperCase() + c.slice(1)) + "</button>";
      }).join("");
  }

  /* ------------------------------ forms ------------------------------ */
  function formMessage(form, text, ok) {
    var msg = form.querySelector(".form-msg");
    if (!msg) { msg = document.createElement("p"); msg.className = "form-msg"; form.appendChild(msg); }
    msg.textContent = text;
    msg.style.color = ok ? "#0a8a3d" : "#c0271a";
  }

  function validate(form) {
    var ok = true;
    form.querySelectorAll("[required]").forEach(function (fld) {
      var wrap = fld.closest(".field") || fld.parentElement;
      var v = (fld.value || "").trim();
      var bad = !v;
      if (fld.type === "email") bad = bad || !/^\S+@\S+\.\S+$/.test(v);
      if (fld.type === "tel") bad = bad || !/^[0-9+\-\s()]{7,15}$/.test(v);
      if (wrap) wrap.classList.toggle("invalid", bad);
      if (bad) ok = false;
    });
    return ok;
  }

  var PAYLOADS = {
    contact: function (d) {
      return {
        endpoint: V.api.contact,
        body: {
          name: d.name, email: d.email, phone: d.phone,
          subject: d.course ? "Course enquiry: " + d.course : (d.subject || "Website enquiry"),
          message: d.message || ("Enquiry about " + (d.course || "VITC courses") + " from " + d.name + ".")
        }
      };
    },
    internship: function (d) {
      return {
        endpoint: V.api.applyInternship,
        body: {
          fullName: d.name, email: d.email, phone: d.phone, college: d.qual,
          domain: d.skills || d.domain, duration: d.duration || null,
          resumeUrl: d.resumeUrl || null,
          message: d.message || null
        }
      };
    },
    career: function (d) {
      return {
        endpoint: V.api.applyJob,
        body: {
          fullName: d.name, email: d.email, phone: d.phone, position: d.position,
          experienceYears: d.experience ? Number(d.experience) : 0,
          message: d.message || null
        }
      };
    },
    review: function (d) {
      return {
        endpoint: V.api.submitReview,
        body: {
          reviewerName: d.name, email: d.email, courseTitle: d.course || null,
          rating: Number(d.rating || 5), comment: d.comment
        }
      };
    }
  };

  /* Uploads any file input marked with data-upload="<folder>" and puts the
     resulting public URL on the payload data as <name>Url (e.g. resumeUrl).
     Silently skips when no file was chosen — attachments stay optional. */
  var MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

  function uploadAttachments(form, data) {
    var inputs = [].slice.call(form.querySelectorAll('input[type="file"][data-upload]'));
    return inputs.reduce(function (chain, input) {
      return chain.then(function () {
        var file = input.files && input.files[0];
        if (!file) return null;
        if (file.size > MAX_UPLOAD_BYTES) {
          throw new Error("\"" + file.name + "\" is larger than 5 MB. Please upload a smaller file.");
        }
        return V.api.uploadFile(file, input.dataset.upload).then(function (saved) {
          data[(input.name || "file") + "Url"] = saved && saved.url;
        });
      });
    }, Promise.resolve());
  }

  function ownerWhatsappNumber() {
    var link = document.querySelector("a.f-wa");
    var match = link && /wa\.me\/(\d{8,15})/.exec(link.getAttribute("href") || "");
    return match ? match[1] : "919967874887";
  }

  function formatContactWhatsappText(d, ref) {
    var lines = ["*New Website Enquiry* — Vandana IT Course (VITC)"];
    if (ref) lines.push("Ref: " + ref);
    lines.push("");
    if (d.name) lines.push("*Name:* " + d.name);
    if (d.phone) lines.push("*Phone:* " + d.phone);
    if (d.email) lines.push("*Email:* " + d.email);
    if (d.course) lines.push("*Course Interested:* " + d.course);
    else if (d.subject) lines.push("*Subject:* " + d.subject);
    if (d.message) {
      lines.push("");
      lines.push("*Message:*");
      lines.push(d.message);
    }
    return lines.join("\n");
  }

  function bindApiForms() {
    document.querySelectorAll("form[data-api]").forEach(function (form) {
      form.addEventListener("submit", function (e) {
        e.preventDefault();
        if (!validate(form)) { formMessage(form, "Please correct the highlighted fields.", false); return; }
        var kind = form.dataset.api;
        var data = {};
        new FormData(form).forEach(function (v, k) { data[k] = typeof v === "string" ? v.trim() : v; });
        var built = PAYLOADS[kind];
        if (!built) return;
        var btn = form.querySelector('button[type="submit"], button:not([type])');
        var label = btn ? btn.textContent : "";
        if (btn) { btn.disabled = true; btn.textContent = "Sending…"; }
        formMessage(form, "Submitting…", true);
        /* Contact enquiries also reach the owner on WhatsApp, with no WhatsApp API: open the owner's chat
           with the enquiry already typed NOW, while this click still counts as the visitor's own action.
           Opened after the save (which also emails the owner and can take seconds), browsers treat it as an
           unwanted pop-up and block it - iPhone Safari always does. The visitor then just taps Send. */
        var waUrl = null, waOpened = false;
        if (kind === "contact") {
          waUrl = "https://wa.me/" + ownerWhatsappNumber() + "?text=" + encodeURIComponent(formatContactWhatsappText(data, ""));
          try {
            var waWin = window.open(waUrl, "_blank");
            if (waWin) { waWin.opener = null; waOpened = true; }
          } catch (popupError) { /* blocked - the "Send on WhatsApp" button below still works */ }
        }
        uploadAttachments(form, data).then(function () {
          var spec = built(data);
          return spec.endpoint(spec.body);
        }).then(function (res) {
          V.toast("Submitted successfully", "ok");
          if (kind === "contact") {
            var ref = (res && res.id) ? ("EQ-" + String(res.id).padStart(5, "0")) : "";
            var msgEl = form.querySelector(".form-msg");
            if (!msgEl) { msgEl = document.createElement("p"); msgEl.className = "form-msg"; form.appendChild(msgEl); }
            msgEl.innerHTML = '<div style="margin-top:14px;padding:16px;background:#eef7ee;border-radius:8px;border:1px solid #b7e4c7;text-align:left;">' +
              '<p style="color:#0a8a3d;font-weight:700;margin:0 0 6px 0;font-size:0.95rem;">✅ Thank you! Your enquiry' + (ref ? ' (' + esc(ref) + ')' : '') + ' has been saved and emailed to our team.</p>' +
              '<p style="color:#2d6a4f;margin:0 0 12px 0;font-size:0.9rem;">' + (waOpened
                ? 'WhatsApp has opened with your enquiry already typed — just tap <b>Send</b> there for an immediate reply. If it did not open:'
                : 'For an immediate reply, send it to us on WhatsApp too — tap the button, then tap <b>Send</b>:') + '</p>' +
              '<a href="' + waUrl + '" target="_blank" rel="noopener" class="btn" style="background:#25D366;color:#ffffff;border:none;display:inline-flex;align-items:center;gap:8px;font-weight:600;padding:10px 22px;border-radius:6px;text-decoration:none;box-shadow:0 2px 6px rgba(37,211,102,0.3);">' +
              '💬 Send on WhatsApp' +
              '</a>' +
              '</div>';
          } else {
            formMessage(form, form.dataset.successMessage ||
              "✓ Thank you! Your submission has been received — our team will contact you shortly.", true);
          }
          form.reset();
          if (kind === "review") loadReviews();
        }).catch(function (err) {
          if (kind === "contact" && waUrl && (data.name || data.phone)) {
            var msgEl = form.querySelector(".form-msg");
            if (!msgEl) { msgEl = document.createElement("p"); msgEl.className = "form-msg"; form.appendChild(msgEl); }
            msgEl.innerHTML = '<div style="margin-top:14px;padding:16px;background:#fff5f5;border-radius:8px;border:1px solid #fed7d7;text-align:left;">' +
              '<p style="color:#c0271a;margin:0 0 6px 0;font-weight:600;">✕ ' + esc(err.message) + '</p>' +
              '<p style="color:#742a2a;margin:0 0 12px 0;font-size:0.9rem;">You can still send your details directly to the owner on WhatsApp:</p>' +
              '<a href="' + waUrl + '" target="_blank" rel="noopener" class="btn" style="background:#25D366;color:#ffffff;border:none;display:inline-flex;align-items:center;gap:8px;font-weight:600;padding:10px 22px;border-radius:6px;text-decoration:none;">' +
              '💬 Send on WhatsApp' +
              '</a>' +
              '</div>';
          } else {
            formMessage(form, "✕ " + err.message, false);
          }
        }).finally(function () {
          if (btn) { btn.disabled = false; btn.textContent = label; }
        });
      });
      form.querySelectorAll("input,select,textarea").forEach(function (i) {
        i.addEventListener("input", function () {
          var w = i.closest(".field"); if (w) w.classList.remove("invalid");
        });
      });
    });
  }

  /* ------------------------------ loaders ------------------------------ */
  function loadReviews() {
    var host = el("reviewsGrid");
    if (!host) return;
    V.load(host, V.api.reviews, function (list, h) {
      h.innerHTML = list.map(reviewCard).join("");
      var summary = el("reviewsSummary");
      if (summary) {
        var avg = list.reduce(function (s, r) { return s + (r.rating || 0); }, 0) / list.length;
        summary.textContent = avg.toFixed(1) + "/5 from " + list.length + " verified reviews";
      }
    }, "No reviews published yet — be the first to share your experience.");
  }

  function loadFeaturedReviews() {
    var host = el("featuredReviews");
    if (!host) return;
    V.load(host, function () {
      return V.api.featuredReviews().then(function (l) { return (l && l.length) ? l : V.api.reviews(); });
    }, function (list, h) {
      h.innerHTML = list.slice(0, 6).map(reviewCard).join("");
    }, "Reviews will appear here soon.");
  }

  function initMarketplace(host, items) {
    var search = el("mSearch"), sort = el("mSort"), count = el("mCount"), empty = el("mEmpty");
    var cards = [].slice.call(host.querySelectorAll("[data-item]"));
    function apply() {
      var q = (search && search.value || "").toLowerCase().trim();
      var active = document.querySelector("[data-mfilter].active");
      var f = active ? active.dataset.mfilter : "all";
      var shown = 0;
      cards.forEach(function (c) {
        var ok = (f === "all" || c.dataset.cat === f) && c.textContent.toLowerCase().indexOf(q) > -1;
        c.style.display = ok ? "" : "none";
        if (ok) shown++;
      });
      if (sort && sort.value !== "default") {
        cards.slice().sort(function (a, b) {
          if (sort.value === "name") return String(a.dataset.title).localeCompare(String(b.dataset.title));
          return (Number(a.dataset.price) - Number(b.dataset.price)) * (sort.value === "low" ? 1 : -1);
        }).forEach(function (c) { host.appendChild(c); });
      }
      if (count) count.textContent = shown + (shown === 1 ? " result" : " results");
      if (empty) empty.style.display = shown ? "none" : "block";
    }
    document.querySelectorAll("[data-mfilter]").forEach(function (b) {
      b.addEventListener("click", function () {
        document.querySelectorAll("[data-mfilter]").forEach(function (x) { x.classList.remove("active"); });
        b.classList.add("active"); apply();
      });
    });
    if (search) search.addEventListener("input", apply);
    if (sort) sort.addEventListener("change", apply);
    apply();
  }

  function loadMarketplace() {
    var host = el("mGrid");
    if (!host) return;
    var kind = host.dataset.catalogue === "course" ? "course" : "assignment";
    V.load(host, function () {
      return V.catalog().then(function (c) { return kind === "course" ? c.courses : c.assignments; });
    }, function (items, h) {
      h.innerHTML = items.map(kind === "course" ? courseCard : assignmentCard).join("");
      buildFilterBar(el("mFilters"), items, "mfilter");
      initMarketplace(h, items);
    }, kind === "course" ? "No courses are published yet." : "No assignments are published yet.");
  }

  function loadCourseCatalogue() {
    var host = el("coursesGrid");
    if (!host) return;
    /* PART 3/6 — public Courses page fix.
       This grid only needs course data, so it must hit GET /api/v1/courses/active
       directly through the shared V.api utility and stop there.
       It previously went through V.catalog(), which Promise.all()s courses
       together with /api/v1/assignments/active — so any failure on the
       *assignments* endpoint (auth hiccup, empty table, 500, etc.) tripped
       the shared promise and made this page show a load error even when
       /api/v1/courses/active itself returned 200 with a valid list. Courses
       and assignments are unrelated failure domains and must not be coupled
       here. window.VITC_DATA.courses is still populated (via V.mapCourse)
       for any other script on this page that expects that shape. */
    V.load(host, function () {
      return V.api.courses().then(function (list) {
        var mapped = (Array.isArray(list) ? list : []).map(V.mapCourse);
        window.VITC_DATA = window.VITC_DATA || {};
        window.VITC_DATA.courses = mapped;
        return mapped;
      });
    },
      function (items, h) {
        h.innerHTML = items.map(catalogueCourseCard).join("");
        buildFilterBar(el("courseFilters"), items, "cfilter");
        bindCourseSearch(h);
      }, "No courses are published yet.");
  }

  function loadSimple(id, fetcher, render, emptyMsg, after) {
    var host = el(id);
    if (!host) return;
    V.load(host, fetcher, function (list, h) {
      var limit = Number(h.dataset.limit || 0);
      var items = limit ? list.slice(0, limit) : list;
      h.innerHTML = items.map(render).join("");
      if (after) after(h, items);
    }, emptyMsg);
  }

  /* Home page — a short "popular courses" strip. */
  function loadHomeCourses() {
    var host = el("homeCourses");
    if (!host) return;
    V.load(host, function () { return V.catalog().then(function (c) { return c.courses; }); },
      function (items, h) { h.innerHTML = items.slice(0, 6).map(catalogueCourseCard).join(""); },
      "Courses will be published here soon.");
  }

  /* ------------------------- testimonials page ------------------------- */
  function bindTestimonialFilters(host) {
    var bar = document.querySelector(".filters");
    if (!bar) return;
    var buttons = bar.querySelectorAll("[data-cfilter]");
    if (!buttons.length) return;
    buttons.forEach(function (b) {
      b.addEventListener("click", function () {
        buttons.forEach(function (x) { x.classList.remove("active"); });
        b.classList.add("active");
        var want = b.dataset.cfilter;
        var shown = 0;
        host.querySelectorAll("[data-testi]").forEach(function (card) {
          var match = want === "all" || card.dataset.cat === want;
          card.style.display = match ? "" : "none";
          if (match) shown++;
        });
        var empty = el("testiEmpty");
        if (empty) empty.hidden = shown !== 0;
      });
    });
  }

  /* ----------------------------- blog page ----------------------------- */
  function bindBlogSearch(host) {
    var input = el("blogSearch");
    if (!input) return;
    input.addEventListener("input", function () {
      var q = input.value.trim().toLowerCase();
      var shown = 0;
      host.querySelectorAll("[data-blog]").forEach(function (card) {
        var match = !q || card.dataset.search.indexOf(q) > -1;
        card.style.display = match ? "" : "none";
        if (match) shown++;
      });
      var empty = el("blogEmpty");
      if (empty) empty.hidden = shown !== 0;
    });
  }

  function buildBlogSidebar(posts) {
    var catBox = el("blogCategories");
    if (catBox) {
      var cats = [];
      posts.forEach(function (b) {
        var c = blogCategory(b);
        if (c && cats.indexOf(c) < 0) cats.push(c);
      });
      catBox.innerHTML = cats.map(function (c) {
        return '<li><a href="blog.html?category=' + encodeURIComponent(c) + '">' + esc(c) + "</a></li>";
      }).join("");
    }
    var recent = el("blogRecent");
    if (recent) {
      recent.innerHTML = posts.slice(0, 5).map(function (b) {
        return '<li><a href="blog.html?post=' + encodeURIComponent(b.slug) + '">' + esc(b.title) + "</a></li>";
      }).join("");
    }
  }

  function setMeta(selector, value) {
    var tag = document.querySelector(selector);
    if (tag) tag.setAttribute(selector.indexOf("property") > -1 ? "content" : "content", value);
  }

  function applyPostSeo(post) {
    var title = post.title + " | VITC Blog";
    document.title = title;
    var url = "/blog.html?post=" + encodeURIComponent(post.slug);
    var pairs = [
      ['meta[name="description"]', post.excerpt || ""],
      ['meta[property="og:title"]', post.title],
      ['meta[property="og:description"]', post.excerpt || ""],
      ['meta[property="og:url"]', url],
      ['meta[property="og:type"]', "article"],
      ['meta[name="twitter:title"]', post.title],
      ['meta[name="twitter:description"]', post.excerpt || ""]
    ];
    pairs.forEach(function (p) { setMeta(p[0], p[1]); });
    if (post.coverImageUrl) {
      setMeta('meta[property="og:image"]', post.coverImageUrl);
    }
    var canonical = document.querySelector('link[rel="canonical"]');
    if (canonical) canonical.setAttribute("href", url);

    var ld = document.createElement("script");
    ld.type = "application/ld+json";
    ld.textContent = JSON.stringify({
      "@context": "https://schema.org",
      "@type": "BlogPosting",
      headline: post.title,
      description: post.excerpt || "",
      image: post.coverImageUrl || "assets/img/logo.jpeg",
      datePublished: post.publishedAt || post.createdAt || undefined,
      author: { "@type": "Person", name: post.author || "Vandana IT Course (VITC)" },
      publisher: {
        "@type": "EducationalOrganization",
        name: "Vandana IT Course (VITC)",
        logo: { "@type": "ImageObject", url: "assets/img/logo.jpeg" }
      },
      mainEntityOfPage: url
    });
    document.head.appendChild(ld);
  }

  function renderBlogPost(host, post, all) {
    var cat = blogCategory(post);
    var date = blogDate(post);
    host.innerHTML =
      '<article class="card blog-post" style="padding:0;overflow:hidden">' +
      (post.coverImageUrl ? '<img src="' + esc(media(post.coverImageUrl)) + '" alt="' + esc(post.title) + '" style="width:100%;max-height:380px;object-fit:cover" width="1024" height="380">' : "") +
      '<div style="padding:28px">' +
      (cat ? '<span class="tag" style="display:inline-block;padding:4px 12px;background:rgba(11,61,145,.08);color:var(--primary);border-radius:99px;font-size:.75rem;font-weight:600;margin-bottom:12px">' + esc(cat) + "</span>" : "") +
      "<h1 style=\"font-size:1.9rem;line-height:1.25\">" + esc(post.title) + "</h1>" +
      '<p style="color:var(--muted);font-size:.88rem;margin:10px 0 20px">' +
      esc([date, post.author].filter(Boolean).join(" · ")) + "</p>" +
      '<div class="post-body" style="line-height:1.85">' + (post.content || "<p>" + esc(post.excerpt || "") + "</p>") + "</div>" +
      '<a href="blog.html" class="btn btn-ghost" style="margin-top:26px">← Back to all articles</a>' +
      "</div></article>";

    var related = (all || []).filter(function (b) { return b.slug !== post.slug; }).slice(0, 2);
    if (related.length) {
      var wrap = document.createElement("div");
      wrap.style.marginTop = "36px";
      wrap.innerHTML = '<h2 style="font-size:1.25rem;margin-bottom:18px">You may also like</h2>' +
        '<div class="grid grid-2" style="gap:24px">' + related.map(blogCard).join("") + "</div>";
      host.appendChild(wrap);
    }
    applyPostSeo(post);
  }

  function loadBlog() {
    var grid = el("blogGrid");
    if (!grid) return;
    var params = new URLSearchParams(window.location.search);
    var slug = params.get("post");
    var category = params.get("category");
    var listWrap = el("blogListWrap");
    var detailWrap = el("blogDetail");

    V.load(grid, V.api.blogPosts, function (list) {
      buildBlogSidebar(list);
      if (slug) {
        var post = list.filter(function (b) { return b.slug === slug; })[0];
        if (post && detailWrap) {
          if (listWrap) listWrap.hidden = true;
          detailWrap.hidden = false;
          renderBlogPost(detailWrap, post, list);
          return;
        }
      }
      var items = category
        ? list.filter(function (b) { return (blogCategory(b) || "").toLowerCase() === category.toLowerCase(); })
        : list;
      var heading = el("blogListHeading");
      if (heading && category) heading.textContent = "Articles in " + category;
      grid.innerHTML = items.map(blogCard).join("");
      var empty = el("blogEmpty");
      if (empty) empty.hidden = items.length !== 0;
      bindBlogSearch(grid);
    }, "No blog posts published yet.");
  }

  function boot() {
    loadCourseCatalogue();
    loadHomeCourses();
    loadMarketplace();
    loadReviews();
    loadFeaturedReviews();
    loadSimple("testimonialsGrid", V.api.testimonials, testimonialCard, "No testimonials published yet.", bindTestimonialFilters);
    loadSimple("galleryGrid", V.api.gallery, galleryFigure, "No gallery images yet.", bindGallery);
    loadBlog();
    loadSimple("pricingGrid", V.api.pricing, pricingCard, "No pricing plans published yet.");
    document.querySelectorAll("[data-faq-list]").forEach(function (host) {
      var category = host.dataset.faqCategory;
      V.load(host, function () {
        return V.api.faqs().then(function (list) {
          return category ? list.filter(function (f) { return (f.category || "").toLowerCase() === category.toLowerCase(); }) : list;
        });
      }, function (list, h) { h.innerHTML = list.map(faqItem).join(""); bindFaq(h); }, "No FAQs published yet.");
    });
    bindApiForms();
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})(window, document);
