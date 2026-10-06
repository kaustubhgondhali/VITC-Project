/* =======================================================================
   VITC — Java Programming course details page (course-java.html)
   • Course data comes from the SAME source as every other course:
     window.VITC.catalog() -> REST /api/v1/courses/active (code "JAVA").
   • Payment reuses the SAME pipeline as payment.html:
     window.VITCPay.processCheckout() -> /checkout/orders (create),
     /pay (initiate) and /confirm (verify + invoice).
     Only the LOCATION of the payment UI changed: it is revealed inline
     below the course details instead of redirecting to payment.html.
   ======================================================================= */
(function (window, document) {
  "use strict";

  var page = document.getElementById("javaCoursePage");
  if (!page) return;

  var COURSE_CODE = "JAVA";
  var ORDER_KEY = "vitc_order";
  var COUPONS = { VITC10: 10, STUDENT15: 15, NEW20: 20 };

  function INR(n) { return "₹" + Number(n || 0).toLocaleString("en-IN"); }
  function esc(s) {
    return String(s === undefined || s === null ? "" : s)
      .replace(/[&<>"']/g, function (c) {
        return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
      });
  }
  function toast(msg) {
    if (window.VITC && window.VITC.toast) return window.VITC.toast(msg);
    var t = document.getElementById("toast");
    if (!t) { t = document.createElement("div"); t.id = "toast"; document.body.appendChild(t); }
    t.textContent = msg; t.classList.add("show");
    clearTimeout(t._h); t._h = setTimeout(function () { t.classList.remove("show"); }, 2800);
  }

  /* ------------------------------------------------------------------
     Curriculum — all 59 topics of the VITC Java Programming syllabus,
     grouped into 8 modules. No topic is removed.
     ------------------------------------------------------------------ */
  var MODULES = [
    { name: "Java Fundamentals", topics: [
      "Introduction to Programming", "What is Java", "History of Java", "Features of Java",
      "C++ vs Java", "Hello Java Program", "Program Internal", "How to set path?",
      "JDK, JRE and JVM", "JVM: Java Virtual Machine"] },
    { name: "Java Basics", topics: [
      "Java Variables", "Java Data Types", "Java Operators", "Java Comments"] },
    { name: "Control Statements", topics: [
      "Java Control Statements", "Java if-Simple", "Java if-else", "Java if-else-if Ladder",
      "Java Nested if", "Java Switch", "Java For Loop", "Java While Loop", "Java Do While Loop",
      "Java Break", "Java Continue", "Java Programs"] },
    { name: "OOP Concepts", topics: [
      "Java OOPs Concepts", "Naming Convention", "Object and Class", "Method Constructor",
      "static keyword", "this keyword", "Java Inheritance & its 4 Types", "Java Polymorphism",
      "Method Overloading", "Method Overriding", "Super Keyword", "Abstraction Class",
      "Interface", "Java Encapsulation"] },
    { name: "Core Java Essentials", topics: [
      "Java Array", "Java String", "Java Exception Handling"] },
    { name: "Advanced Java & JDBC", topics: [
      "Java Swing", "Java Applet", "Java JDBC"] },
    { name: "Servlet", topics: [
      "Java Servlet", "HTTP Servlet, Generic Servlet", "Java Servlet Life Cycle",
      "Servlet Request Response", "User Input in Servlet",
      "Use Design making System in Servlet & etc.", "Java Session", "Java Cookies"] },
    { name: "JSP", topics: [
      "Java Introduction JSP", "Java JSP API", "Java JSP Code User Input",
      "JSP if, Switch, Loop & Etc.", "JSP Use With HTML, CSS Design"] }
  ];

  function renderCurriculum() {
    var host = document.getElementById("javaCurriculum");
    if (!host) return;
    var n = 0;
    host.innerHTML = MODULES.map(function (m, i) {
      var items = m.topics.map(function (t) {
        n++;
        return '<li><span class="tick">✓</span><span>' + esc(n + ". " + t) + "</span></li>";
      }).join("");
      return '<div class="faq-item mod-item' + (i === 0 ? " open" : "") + '">' +
        '<button class="faq-q" type="button"><b>Module ' + (i + 1) + "</b> — " + esc(m.name) +
        ' <small style="color:var(--muted);font-weight:500">(' + m.topics.length + " topics)</small></button>" +
        '<div class="faq-a"><ul class="mod-list">' + items + "</ul></div></div>";
    }).join("");
    host.querySelectorAll(".faq-q").forEach(function (q) {
      q.addEventListener("click", function () { q.closest(".faq-item").classList.toggle("open"); });
    });
    var count = document.getElementById("javaTopicCount");
    if (count) count.textContent = n + " Topics";
  }

  /* ------------------------------------------------------------------
     Course data (single source of truth = existing course catalogue)
     ------------------------------------------------------------------ */
  var FALLBACK = {
    id: COURSE_CODE, code: COURSE_CODE, dbId: null, title: "Java Programming",
    price: 8999, level: "Beginner to Advanced", duration: "3 Months",
    meta: "3 Months • Beginner to Advanced",
    desc: "Master Java Programming from fundamentals to Advanced Java, JDBC, Servlet and JSP with practical learning.",
    type: "course"
  };

  var course = FALLBACK;

  function applyCourse(c) {
    course = c;
    var set = function (key, val) {
      page.querySelectorAll("[data-j=" + key + "]").forEach(function (el) { el.textContent = val; });
    };
    set("title", c.title);
    set("desc", c.desc || FALLBACK.desc);
    set("level", c.level || "—");
    set("duration", c.duration || c.durationMonths || "—");
    set("meta", c.meta || "");
    set("price", INR(c.price));
    set("item", c.title);
    document.querySelectorAll("[data-j=emi]").forEach(function (el) {
      el.textContent = INR(Math.round(Number(c.price) / 3)) + " × 3 months";
    });
    render();
  }

  function loadCourse() {
    if (!(window.VITC && window.VITC.catalog)) { applyCourse(FALLBACK); return; }
    window.VITC.catalog().then(function (cat) {
      var list = (cat && cat.courses) || [];
      var found = null;
      for (var i = 0; i < list.length; i++) {
        if (String(list[i].id).toUpperCase() === COURSE_CODE) { found = list[i]; break; }
      }
      applyCourse(found || FALLBACK);
    }).catch(function () { applyCourse(FALLBACK); });
  }

  /* ------------------------------------------------------------------
     Buy Now -> reveal the inline payment section (no page redirect)
     ------------------------------------------------------------------ */
  var paySection = document.getElementById("javaPay");

  function revealPayment() {
    if (!paySection) return;
    paySection.hidden = false;
    paySection.classList.add("in");
    setTimeout(function () {
      paySection.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 60);
  }

  page.querySelectorAll("[data-java-buy]").forEach(function (b) {
    b.addEventListener("click", function (e) {
      e.preventDefault();
      revealPayment();
    });
  });

  /* ------------------------------------------------------------------
     Inline checkout — same fields, validation and API calls as payment.html
     ------------------------------------------------------------------ */
  var discountPct = 0;
  var form = document.getElementById("javaPayForm");

  function totals() {
    var base = Number(course.price) || 0;
    var disc = Math.round(base * discountPct / 100);
    var taxable = base - disc;
    var gst = Math.round(taxable * 0.18);
    return { base: base, disc: disc, gst: gst, total: taxable + gst };
  }

  function render() {
    if (!paySection) return;
    var t = totals();
    var set = function (key, val) {
      paySection.querySelectorAll("[data-s=" + key + "]").forEach(function (el) { el.textContent = val; });
    };
    set("item", course.title);
    set("meta", course.meta || "Course enrolment");
    set("base", INR(t.base));
    set("disc", "– " + INR(t.disc));
    set("gst", INR(t.gst));
    set("total", INR(t.total));
    var dr = document.getElementById("javaDiscRow");
    if (dr) dr.style.display = t.disc ? "flex" : "none";
    var pb = document.getElementById("javaPayBtn");
    if (pb && !pb.disabled) pb.textContent = "Pay " + INR(t.total) + " securely";
  }

  if (paySection) {
    paySection.querySelectorAll(".pay-method").forEach(function (pm) {
      pm.addEventListener("click", function () {
        paySection.querySelectorAll(".pay-method").forEach(function (x) { x.classList.remove("selected"); });
        paySection.querySelectorAll(".pay-method-wrap").forEach(function (x) { x.classList.remove("open"); });
        pm.classList.add("selected");
        pm.closest(".pay-method-wrap").classList.add("open");
        var r = pm.querySelector("input"); if (r) r.checked = true;
      });
    });
    var firstMethod = paySection.querySelector(".pay-method");
    if (firstMethod) firstMethod.click();

    paySection.querySelectorAll(".upi-app").forEach(function (u) {
      u.addEventListener("click", function () {
        paySection.querySelectorAll(".upi-app").forEach(function (x) { x.classList.remove("active"); });
        u.classList.add("active");
      });
    });

    var cnum = document.getElementById("jCardNumber");
    if (cnum) cnum.addEventListener("input", function () {
      cnum.value = cnum.value.replace(/\D/g, "").slice(0, 16).replace(/(.{4})/g, "$1 ").trim();
    });
    var cexp = document.getElementById("jCardExp");
    if (cexp) cexp.addEventListener("input", function () {
      var v = cexp.value.replace(/\D/g, "").slice(0, 4);
      cexp.value = v.length > 2 ? v.slice(0, 2) + "/" + v.slice(2) : v;
    });

    var capply = document.getElementById("jCouponBtn");
    if (capply) capply.addEventListener("click", function () {
      var inp = document.getElementById("jCouponInput");
      var msg = document.getElementById("jCouponMsg");
      var code = ((inp && inp.value) || "").trim().toUpperCase();
      msg.style.display = "block";
      if (COUPONS[code]) {
        discountPct = COUPONS[code];
        msg.textContent = "✓ Coupon " + code + " applied — " + discountPct + "% off";
        msg.style.color = "#0a8a3d";
      } else {
        discountPct = 0;
        msg.textContent = "✕ Invalid coupon code. Try VITC10, STUDENT15 or NEW20.";
        msg.style.color = "#d43a3a";
      }
      render();
    });
  }

  if (form) {
    form.querySelectorAll("input,select").forEach(function (i) {
      i.addEventListener("input", function () {
        var f = i.closest(".field"); if (f) f.classList.remove("invalid");
      });
    });

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var ok = true;
      var wrap = paySection.querySelector(".pay-method-wrap.open");
      var scopes = [document.getElementById("jBilling")];
      if (wrap) scopes.push(wrap);
      scopes.forEach(function (sc) {
        if (!sc) return;
        sc.querySelectorAll("[required]").forEach(function (fld) {
          if (fld.offsetParent === null) return;
          var v = fld.value.trim(), bad = !v;
          if (fld.type === "email") bad = bad || !/^\S+@\S+\.\S+$/.test(v);
          if (fld.type === "tel") bad = bad || !/^[0-9+\-\s()]{7,15}$/.test(v);
          fld.closest(".field").classList.toggle("invalid", bad);
          if (bad) ok = false;
        });
      });
      if (!ok) { toast("Please fix the highlighted fields"); return; }

      var methodEl = paySection.querySelector(".pay-method.selected");
      var method = methodEl ? methodEl.dataset.method : "UPI";
      var detail = "";
      if (method === "UPI") {
        detail = (document.getElementById("jUpiId") || {}).value || "";
      } else if (/Card/.test(method)) {
        var num = ((document.getElementById("jCardNumber") || {}).value || "").replace(/\s/g, "");
        detail = num ? "XXXX XXXX XXXX " + num.slice(-4) : "Card";
      } else if (method === "Wallet") {
        detail = (document.getElementById("jWalletSel") || {}).value || "Wallet";
      } else {
        detail = (document.getElementById("jBankSel") || {}).value || "Net Banking";
      }

      var couponInput = document.getElementById("jCouponInput");
      var coupon = discountPct ? ((couponInput && couponInput.value) || "").trim().toUpperCase() : null;

      var order = {
        type: "course", id: course.id, refId: course.dbId, dbId: course.dbId,
        code: course.code, title: course.title, price: Number(course.price),
        meta: course.meta || "Course enrolment", coupon: coupon
      };
      try { localStorage.setItem(ORDER_KEY, JSON.stringify(order)); } catch (err) { /* ignore */ }

      var ctx = {
        name: (document.getElementById("jName") || {}).value || "VITC Student",
        email: (document.getElementById("jEmail") || {}).value || "",
        phone: (document.getElementById("jPhone") || {}).value || "",
        city: (document.getElementById("jCity") || {}).value || "",
        method: method, methodDetail: detail
      };

      var btn = document.getElementById("javaPayBtn");
      btn.disabled = true; btn.textContent = "Processing securely…";

      if (window.VITCPay) {
        window.VITCPay.processCheckout(order, ctx).then(function (result) {
          location.href = "payment-success.html?order=" + encodeURIComponent(result.order.orderCode);
        }).catch(function (err) {
          btn.disabled = false; render();
          toast(err.message || "Payment could not be completed. Please try again.");
        });
      } else {
        setTimeout(function () { location.href = "payment-success.html"; }, 1200);
      }
    });
  }

  renderCurriculum();
  loadCourse();
})(window, document);
