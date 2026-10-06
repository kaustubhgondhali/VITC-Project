/* =======================================================================
   VITC — Payment Module (frontend)
   Talks to the backend checkout APIs:
     POST /api/v1/checkout/orders                    -> create order
     POST /api/v1/checkout/orders/{code}/pay         -> initiate payment
     POST /api/v1/checkout/orders/{code}/confirm     -> verify + invoice
     GET  /api/v1/checkout/orders/{code}/confirmation
     GET  /api/v1/invoices/order/{code}
   The gateway is chosen server-side (mock today, Razorpay later); this file
   never talks to a provider SDK directly. If the API is unreachable the
   checkout falls back to a local simulation so the static demo keeps working.
   ======================================================================= */
(function (window, document) {
  "use strict";

  /* Share the single base URL resolved by assets/js/api.js. */
  var BASE = (window.VITC && window.VITC.api && window.VITC.api.base)
    || (function () {
      try { return window.localStorage.getItem("vitc_api") || ""; } catch (e) { return ""; }
    })()
    || (window.location.port === "8080" ? window.location.origin : "http://localhost:8080");
  var API = String(BASE).replace(/\/+$/, "") + "/api/v1";
  var STORE_KEY = "vitc_order";
  var RESULT_KEY = "vitc_payment_result";

  var METHOD_MAP = {
    "UPI": "UPI",
    "Card": "CARD",
    "Credit / Debit Card": "CARD",
    "Wallet": "WALLET",
    "NetBanking": "NETBANKING",
    "Net Banking": "NETBANKING"
  };

  function INR(n) { return "₹" + Number(n || 0).toLocaleString("en-IN"); }

  function unwrap(json) {
    return json && Object.prototype.hasOwnProperty.call(json, "data") ? json.data : json;
  }

  async function request(path, options) {
    options = options || {};
    var init = { method: options.method || "GET", headers: {} };
    if (options.body !== undefined) {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(options.body);
    }
    var res = await fetch(API + path, init);
    var json = null;
    try { json = await res.json(); } catch (e) { /* empty body */ }
    if (!res.ok) {
      var msg = (json && (json.message || json.error)) || ("Request failed (" + res.status + ")");
      throw new Error(msg);
    }
    return unwrap(json);
  }

  /* ======================= API surface ======================= */
  var api = {
    createOrder: function (payload) { return request("/checkout/orders", { method: "POST", body: payload }); },
    getOrder: function (code) { return request("/checkout/orders/" + encodeURIComponent(code)); },
    initiate: function (code, payload) {
      return request("/checkout/orders/" + encodeURIComponent(code) + "/pay", { method: "POST", body: payload });
    },
    confirm: function (code, payload) {
      return request("/checkout/orders/" + encodeURIComponent(code) + "/confirm", { method: "POST", body: payload });
    },
    confirmation: function (code) {
      return request("/checkout/orders/" + encodeURIComponent(code) + "/confirmation");
    },
    invoiceByOrder: function (code) { return request("/invoices/order/" + encodeURIComponent(code)); }
  };

  /* ======================= Local fallback ======================= */
  var COUPONS = { VITC10: 10, STUDENT15: 15, NEW20: 20 };

  function localCheckout(order, ctx) {
    var base = Number(order.price) || 0;
    var pct = COUPONS[order.coupon] || 0;
    var disc = Math.round(base * pct / 100);
    var taxable = base - disc;
    var gst = Math.round(taxable * 0.18);
    var code = "VITC-" + Date.now().toString().slice(-8);
    var stamp = Date.now().toString(36).toUpperCase();
    return {
      offline: true,
      order: {
        orderCode: code, itemType: (order.type || "course").toUpperCase(), itemTitle: order.title,
        itemMeta: order.meta || "", customerName: ctx.name, email: ctx.email, phone: ctx.phone,
        city: ctx.city, couponCode: order.coupon || null, subtotal: base, discountAmount: disc,
        taxRate: 18, taxAmount: gst, totalAmount: taxable + gst, currency: "INR", status: "PAID"
      },
      payment: {
        transactionId: "TXN" + stamp, method: ctx.method, methodDetail: ctx.methodDetail,
        status: "SUCCESS", provider: "MOCK", providerPaymentId: "mock_pay_" + stamp,
        amount: taxable + gst, currency: "INR", paidAt: new Date().toISOString()
      },
      invoice: {
        invoiceNumber: "INV-LOCAL-" + stamp, orderCode: code, billingName: ctx.name,
        billingEmail: ctx.email, billingPhone: ctx.phone, billingAddress: ctx.city,
        subtotal: base, discountAmount: disc, taxAmount: gst, totalAmount: taxable + gst,
        gstNumber: "27ABCDE1234F1Z5", status: "PAID", issuedAt: new Date().toISOString()
      }
    };
  }

  /* ======================= Razorpay checkout ======================= */
  var RZP_SDK = "https://checkout.razorpay.com/v1/checkout.js";

  function loadRazorpaySdk() {
    if (window.Razorpay) return Promise.resolve();
    return new Promise(function (resolve, reject) {
      var existing = document.querySelector('script[data-rzp]');
      if (existing) {
        existing.addEventListener("load", function () { resolve(); });
        existing.addEventListener("error", function () { reject(new Error("Could not load Razorpay checkout")); });
        return;
      }
      var s = document.createElement("script");
      s.src = RZP_SDK;
      s.setAttribute("data-rzp", "1");
      s.onload = function () { resolve(); };
      s.onerror = function () { reject(new Error("Could not load Razorpay checkout")); };
      document.head.appendChild(s);
    });
  }

  /**
   * Opens the Razorpay widget and resolves with the ids the backend needs to
   * verify the signature. No key secret is ever present in the browser — only
   * the publishable key id returned by the server.
   */
  function openRazorpay(initiation, order, ctx) {
    return loadRazorpaySdk().then(function () {
      return new Promise(function (resolve, reject) {
        var rzp = new window.Razorpay({
          key: initiation.providerKey,
          order_id: initiation.providerOrderId,
          amount: Math.round(Number(initiation.amount) * 100),
          currency: initiation.currency || "INR",
          name: "VIT Chennai",
          description: order.title || "VITC Purchase",
          prefill: { name: ctx.name || "", email: ctx.email || "", contact: ctx.phone || "" },
          notes: { orderCode: initiation.orderCode },
          theme: { color: "#0b3c8c" },
          modal: {
            ondismiss: function () { reject(new Error("Payment cancelled")); }
          },
          handler: function (response) { resolve(response); }
        });
        rzp.on("payment.failed", function (resp) {
          reject(new Error((resp && resp.error && resp.error.description) || "Payment failed"));
        });
        rzp.open();
      });
    });
  }

  /* ======================= Checkout orchestration ======================= */
  async function processCheckout(order, ctx) {
    var result;
    var created = null;
    try {
      created = await api.createOrder({
        itemType: (order.type || "course").toUpperCase() === "ASSIGNMENT" ? "ASSIGNMENT" : "COURSE",
        /* The catalogue id of the course/assignment bought, so the backend can
           fulfil the right item instead of guessing from its title. */
        itemRefId: order.refId || order.dbId || null,
        itemTitle: order.title || "VITC Purchase",
        itemMeta: order.meta || null,
        customerName: ctx.name,
        email: ctx.email,
        phone: ctx.phone || null,
        city: ctx.city || null,
        couponCode: order.coupon || null,
        subtotal: Number(order.price) || 0
      });

      var initiation = await api.initiate(created.orderCode, {
        method: METHOD_MAP[ctx.method] || "UPI",
        methodDetail: ctx.methodDetail || null
      });

      var confirmPayload;
      if (initiation.provider === "RAZORPAY") {
        /* Real gateway: the buyer pays in the Razorpay widget, then the
           backend verifies the signature before the order is marked paid. */
        var rzpResult = await openRazorpay(initiation, order, ctx);
        confirmPayload = {
          providerOrderId: rzpResult.razorpay_order_id || initiation.providerOrderId,
          providerPaymentId: rzpResult.razorpay_payment_id,
          providerSignature: rzpResult.razorpay_signature,
          simulateFailure: false
        };
      } else {
        confirmPayload = {
          providerOrderId: initiation.providerOrderId,
          providerPaymentId: initiation.providerOrderId.replace("order", "pay"),
          providerSignature: null,
          simulateFailure: false
        };
      }

      var confirmation = await api.confirm(created.orderCode, confirmPayload);

      result = {
        offline: false,
        order: confirmation.order || created,
        payment: confirmation.payment || initiation.payment,
        invoice: confirmation.invoice || null
      };

      /* Student portal hook: the backend returns the one-time credentials of
         the account it provisioned for this paid order. Stash them so
         payment-success.html can show them exactly once. */
      if (confirmation.studentAccount && window.VITCStudent) {
        window.VITCStudent.stashCredentials(confirmation.studentAccount);
      }
    } catch (err) {
      /* A real gateway failure must surface to the buyer — never silently
         "succeed" with a simulated order. The local simulation is only for the
         static demo where the backend itself is unreachable. */
      if (created) {
        throw err;
      }
      console.warn("[VITC payments] falling back to local simulation:", err.message);
      result = localCheckout(order, ctx);
    }
    persist(order, result);
    return result;
  }


  function persist(order, result) {
    var o = result.order, p = result.payment;
    var stored = Object.assign({}, order, {
      orderId: o.orderCode,
      orderCode: o.orderCode,
      transactionId: p ? p.transactionId : null,
      invoiceNumber: result.invoice ? result.invoice.invoiceNumber : null,
      method: p ? p.method : order.method,
      methodDetail: p ? p.methodDetail : null,
      provider: p ? p.provider : "MOCK",
      offline: !!result.offline,
      date: new Date(((p && p.paidAt) || Date.now())).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }),
      buyer: { name: o.customerName, email: o.email, phone: o.phone },
      totals: {
        base: Number(o.subtotal),
        disc: Number(o.discountAmount || 0),
        gst: Number(o.taxAmount || 0),
        total: Number(o.totalAmount)
      }
    });
    window.localStorage.setItem(STORE_KEY, JSON.stringify(stored));
    window.localStorage.setItem(RESULT_KEY, JSON.stringify(result));
  }

  function lastResult() {
    try { return JSON.parse(window.localStorage.getItem(RESULT_KEY) || "null"); } catch (e) { return null; }
  }

  function orderCodeFromUrl() {
    var m = /[?&]order=([^&]+)/.exec(window.location.search);
    return m ? decodeURIComponent(m[1]) : null;
  }

  /** Resolves the confirmation bundle: URL order code -> API, else last local result. */
  async function resolveBundle() {
    var code = orderCodeFromUrl();
    if (code) {
      try {
        var bundle = await api.confirmation(code);
        if (bundle && bundle.order) return { offline: false, order: bundle.order, payment: bundle.payment, invoice: bundle.invoice };
      } catch (e) {
        console.warn("[VITC payments] could not load order " + code + ":", e.message);
      }
    }
    return lastResult();
  }

  /* ======================= Shared renderer ======================= */
  function fill(root, bundle) {
    if (!bundle || !bundle.order) return false;
    var o = bundle.order, p = bundle.payment || {}, inv = bundle.invoice || {};
    var map = {
      orderId: o.orderCode,
      orderCode: o.orderCode,
      status: o.status,
      item: o.itemTitle,
      meta: o.itemMeta || (o.itemType === "COURSE" ? "Course enrolment" : "Assignment package"),
      name: o.customerName,
      email: o.email,
      phone: o.phone || "—",
      city: o.city || "—",
      coupon: o.couponCode || "—",
      method: p.method || "—",
      methodDetail: p.methodDetail || "—",
      provider: p.provider || "MOCK",
      txn: p.transactionId || "—",
      invoiceNo: inv.invoiceNumber || "—",
      date: new Date(p.paidAt || inv.issuedAt || o.createdAt || Date.now())
        .toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }),
      base: INR(o.subtotal),
      disc: "– " + INR(o.discountAmount),
      gst: INR(o.taxAmount),
      total: INR(o.totalAmount),
      gstin: inv.gstNumber || "27ABCDE1234F1Z5"
    };
    Object.keys(map).forEach(function (k) {
      (root || document).querySelectorAll("[data-i=" + k + "]").forEach(function (el) {
        el.textContent = map[k];
      });
    });
    /* Blocks that only apply to one kind of purchase, e.g. data-show-type="ASSIGNMENT". */
    (root || document).querySelectorAll("[data-show-type]").forEach(function (el) {
      el.style.display = el.getAttribute("data-show-type") === o.itemType ? "" : "none";
    });
    var discRows = (root || document).querySelectorAll("[data-disc-row]");
    discRows.forEach(function (r) { r.style.display = Number(o.discountAmount) > 0 ? "flex" : "none"; });
    (root || document).querySelectorAll("[data-link=invoice]").forEach(function (a) {
      a.href = "invoice.html?order=" + encodeURIComponent(o.orderCode);
    });
    (root || document).querySelectorAll("[data-link=summary]").forEach(function (a) {
      a.href = "order-summary.html?order=" + encodeURIComponent(o.orderCode);
    });
    return true;
  }

  /* ======================= Page bootstraps ======================= */
  document.addEventListener("DOMContentLoaded", function () {
    var page = document.getElementById("invoicePage") || document.getElementById("summaryPage")
      || document.getElementById("successPage");
    if (!page) return;
    var empty = document.getElementById("payEmpty");
    resolveBundle().then(function (bundle) {
      var ok = fill(page, bundle);
      if (!ok) {
        page.style.display = "none";
        if (empty) empty.style.display = "block";
      } else if (empty) {
        empty.style.display = "none";
      }
    });
    var printBtn = document.getElementById("printDoc");
    if (printBtn) printBtn.addEventListener("click", function () { window.print(); });
  });

  window.VITCPay = {
    api: api,
    processCheckout: processCheckout,
    resolveBundle: resolveBundle,
    lastResult: lastResult,
    fill: fill,
    INR: INR
  };
})(window, document);
