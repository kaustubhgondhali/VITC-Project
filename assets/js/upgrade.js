/* ============ VITC — Interaction layer (checkout, tabs, detail page) ============
   Catalogue data comes from the REST API through assets/js/api.js
   (window.VITC.catalog() -> window.VITC_DATA). Nothing here is hardcoded. */
(function () {
  var KEY = 'vitc_order';
  var INR = function (n) { return '₹' + Number(n).toLocaleString('en-IN'); };

  function catalogNow() { return window.VITC_DATA || { courses: [], assignments: [] }; }

  /* Runs cb once courses + assignments are loaded from the backend. */
  function withCatalog(cb) {
    if (window.VITC && window.VITC.catalog) {
      window.VITC.catalog().then(cb).catch(function () { cb(catalogNow()); });
    } else {
      cb(catalogNow());
    }
  }

  /* ---------- Toast ---------- */
  function toast(msg) {
    var t = document.getElementById('toast');
    if (!t) { t = document.createElement('div'); t.id = 'toast'; document.body.appendChild(t); }
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(t._h);
    t._h = setTimeout(function () { t.classList.remove('show'); }, 2800);
  }

  /* ---------- Order store ---------- */
  function findItem(type, id, CATALOG) {
    var list = (CATALOG || catalogNow())[type === 'course' ? 'courses' : 'assignments'] || [];
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i];
    return null;
  }
  function setOrder(o) { try { localStorage.setItem(KEY, JSON.stringify(o)); } catch (e) {} }
  function getOrder() { try { return JSON.parse(localStorage.getItem(KEY) || 'null'); } catch (e) { return null; } }

  function startCheckout(type, id) {
    withCatalog(function (CATALOG) {
      var item = findItem(type, id, CATALOG);
      if (!item) { toast('This item is no longer available.'); return; }
      setOrder({
        type: type, id: item.id, dbId: item.dbId, code: item.code, title: item.title,
        price: item.price, meta: item.meta || '', coupon: null
      });
      toast('Added to checkout — redirecting…');
      setTimeout(function () { location.href = 'payment.html'; }, 500);
    });
  }

  /* Courses that have their own details page: "Buy" opens the details page
     instead of jumping straight to checkout. Only these codes are affected —
     every other course/assignment keeps its existing behaviour. */
  var DETAIL_PAGES = { JAVA: 'course-java.html' };

  document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-buy]');
    if (!b) return;
    e.preventDefault();
    var type = b.dataset.buyType || 'assignment';
    var id = b.dataset.buy;
    if (type === 'course' && DETAIL_PAGES[String(id).toUpperCase()]) {
      location.href = DETAIL_PAGES[String(id).toUpperCase()];
      return;
    }
    startCheckout(type, id);
  });


  /* ---------- Marketplace search / filter / sort ----------
     Moved to assets/js/site.js: the grid is now rendered from the API, so the
     filter must be wired up after the cards exist in the DOM. ---------- */


  /* ---------- Tabs ---------- */
  document.querySelectorAll('.tabs').forEach(function (bar) {
    bar.querySelectorAll('button').forEach(function (btn) {
      btn.addEventListener('click', function () {
        bar.querySelectorAll('button').forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        var scope = bar.parentElement;
        scope.querySelectorAll('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
        var target = scope.querySelector('#' + btn.dataset.tab);
        if (target) target.classList.add('active');
      });
    });
  });

  /* ---------- Assignment details page (reads ?id= from the API catalogue) ---------- */
  if (document.getElementById('assignmentDetail')) withCatalog(function (CATALOG) {
    var detail = document.getElementById('assignmentDetail');
    if (detail) {
    var id = new URLSearchParams(location.search).get('id');
    var a = id ? findItem('assignment', id, CATALOG) : null;
    if (!a) a = CATALOG.assignments[0];
    if (a) {
      var set = function (sel, val) { document.querySelectorAll(sel).forEach(function (el) { el.textContent = val; }); };
      document.title = a.title + ' — Assignment | Vandana IT Course (VITC)';
      set('[data-f=title]', a.title);
      set('[data-f=desc]', a.desc);
      set('[data-f=tech]', a.tech);
      set('[data-f=days]', a.days);
      set('[data-f=id]', a.id);
      set('[data-f=price]', INR(a.price));
      set('[data-f=ico]', a.ico);
      document.querySelectorAll('[data-f=diff]').forEach(function (d) {
        d.textContent = a.diff;
        if (d.classList.contains('diff') || d.className.indexOf('diff') === 0) d.className = 'diff diff-' + a.diff;
      });
      var fl = document.getElementById('featureList');
      if (fl) fl.innerHTML = a.features.map(function (f) { return '<li>' + f + '</li>'; }).join('');
      var shots = document.getElementById('shotGrid');
      if (shots) shots.innerHTML = ['Dashboard View', 'Module Screen', 'Data / Reports Output']
        .map(function (s, i) { return '<div class="shot' + (i % 2 ? ' shot-alt' : '') + '"><span>' + a.title + '<br>' + s + '</span></div>'; }).join('');
      document.querySelectorAll('[data-buy-dynamic]').forEach(function (b) {
        b.setAttribute('data-buy', a.id); b.setAttribute('data-buy-type', 'assignment');
      });
      var rel = document.getElementById('relatedGrid');
      if (rel) {
        rel.innerHTML = CATALOG.assignments.filter(function (x) { return x.id !== a.id && x.cat === a.cat; })
          .concat(CATALOG.assignments.filter(function (x) { return x.id !== a.id && x.cat !== a.cat; }))
          .slice(0, 3).map(function (x) {
            return '<div class="course-card assign-card"><div class="head"><div class="ico">' + x.ico + '</div><h3>' + x.title + '</h3></div>'
              + '<p class="desc">' + x.desc + '</p><div class="tag-row"><span class="diff diff-' + x.diff + '">' + x.diff + '</span><span class="pill pill-tech">' + x.tech + '</span></div>'
              + '<div class="foot"><div><div class="price">' + INR(x.price) + '</div></div>'
              + '<a class="btn btn-primary btn-sm" href="assignment-details.html?id=' + x.id + '">View →</a></div></div>';
          }).join('');
      }
      var bc = document.getElementById('crumbTitle');
      if (bc) bc.textContent = a.title;
    }
    }
  });


  /* ---------- Payment page ---------- */
  var payPage = document.getElementById('payPage');
  if (payPage && !getOrder()) {
    /* Nothing in the cart — send the visitor back to pick a course. */
    toast('Please choose a course or assignment first.');
    setTimeout(function () { location.href = 'buy-course.html'; }, 900);
  }
  if (payPage && getOrder()) {
    var order = getOrder();
    var COUPONS = { VITC10: 10, STUDENT15: 15, NEW20: 20 };
    var discountPct = order.coupon ? (COUPONS[order.coupon] || 0) : 0;

    function render() {
      var base = Number(order.price);
      var disc = Math.round(base * discountPct / 100);
      var taxable = base - disc;
      var gst = Math.round(taxable * 0.18);
      var total = taxable + gst;
      order.totals = { base: base, disc: disc, gst: gst, total: total, pct: discountPct };
      document.querySelectorAll('[data-s=item]').forEach(function (e) { e.textContent = order.title; });
      document.querySelectorAll('[data-s=meta]').forEach(function (e) { e.textContent = order.meta || (order.type === 'course' ? 'Course enrolment' : 'Assignment package'); });
      document.querySelectorAll('[data-s=base]').forEach(function (e) { e.textContent = INR(base); });
      document.querySelectorAll('[data-s=disc]').forEach(function (e) { e.textContent = '– ' + INR(disc); });
      document.querySelectorAll('[data-s=gst]').forEach(function (e) { e.textContent = INR(gst); });
      document.querySelectorAll('[data-s=total]').forEach(function (e) { e.textContent = INR(total); });
      var dr = document.getElementById('discRow');
      if (dr) dr.style.display = disc ? 'flex' : 'none';
      var pb = document.getElementById('payBtn');
      if (pb) pb.textContent = 'Pay ' + INR(total) + ' securely';
    }
    render();

    document.querySelectorAll('.pay-method').forEach(function (pm) {
      pm.addEventListener('click', function () {
        document.querySelectorAll('.pay-method').forEach(function (x) { x.classList.remove('selected'); });
        document.querySelectorAll('.pay-method-wrap').forEach(function (x) { x.classList.remove('open'); });
        pm.classList.add('selected');
        pm.closest('.pay-method-wrap').classList.add('open');
        var r = pm.querySelector('input'); if (r) r.checked = true;
      });
    });
    var first = document.querySelector('.pay-method');
    if (first) first.click();

    document.querySelectorAll('.upi-app').forEach(function (u) {
      u.addEventListener('click', function () {
        document.querySelectorAll('.upi-app').forEach(function (x) { x.classList.remove('active'); });
        u.classList.add('active');
      });
    });

    var cnum = document.getElementById('cardNumber');
    if (cnum) cnum.addEventListener('input', function () {
      var v = cnum.value.replace(/\D/g, '').slice(0, 16).replace(/(.{4})/g, '$1 ').trim();
      cnum.value = v;
    });
    var cexp = document.getElementById('cardExp');
    if (cexp) cexp.addEventListener('input', function () {
      var v = cexp.value.replace(/\D/g, '').slice(0, 4);
      cexp.value = v.length > 2 ? v.slice(0, 2) + '/' + v.slice(2) : v;
    });

    var capply = document.getElementById('couponBtn');
    if (capply) capply.addEventListener('click', function () {
      var inp = document.getElementById('couponInput');
      var msg = document.getElementById('couponMsg');
      var code = (inp.value || '').trim().toUpperCase();
      msg.style.display = 'block';
      if (COUPONS[code]) {
        discountPct = COUPONS[code]; order.coupon = code;
        msg.textContent = '✓ Coupon ' + code + ' applied — ' + discountPct + '% off';
        msg.style.color = '#0a8a3d';
      } else {
        discountPct = 0; order.coupon = null;
        msg.textContent = '✕ Invalid coupon code. Try VITC10, STUDENT15 or NEW20.';
        msg.style.color = '#d43a3a';
      }
      render();
    });

    var form = document.getElementById('payForm');
    if (form) form.addEventListener('submit', function (e) {
      e.preventDefault();
      var ok = true;
      var wrap = document.querySelector('.pay-method-wrap.open');
      var scopes = [form.querySelector('#billing')];
      if (wrap) scopes.push(wrap);
      scopes.forEach(function (sc) {
        if (!sc) return;
        sc.querySelectorAll('[required]').forEach(function (fld) {
          if (fld.offsetParent === null) return;
          var v = fld.value.trim(), bad = !v;
          if (fld.type === 'email') bad = bad || !/^\S+@\S+\.\S+$/.test(v);
          if (fld.type === 'tel') bad = bad || !/^[0-9+\-\s()]{7,15}$/.test(v);
          fld.closest('.field').classList.toggle('invalid', bad);
          if (bad) ok = false;
        });
      });
      if (!ok) { toast('Please fix the highlighted fields'); return; }
      var method = document.querySelector('.pay-method.selected');
      order.method = method ? method.dataset.method : 'UPI';
      order.orderId = 'VITC-' + Date.now().toString().slice(-8);
      order.date = new Date().toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
      order.buyer = {
        name: (document.getElementById('bName') || {}).value || 'VITC Student',
        email: (document.getElementById('bEmail') || {}).value || '',
        phone: (document.getElementById('bPhone') || {}).value || ''
      };
      order.city = (document.getElementById('bCity') || {}).value || '';
      setOrder(order);
      var btn = document.getElementById('payBtn');
      btn.disabled = true; btn.textContent = 'Processing securely…';

      /* Method specific detail sent to the payment API */
      var detail = '';
      if (order.method === 'UPI') {
        detail = (document.getElementById('upiId') || {}).value || '';
      } else if (/Card/.test(order.method)) {
        var num = ((document.getElementById('cardNumber') || {}).value || '').replace(/\s/g, '');
        detail = num ? 'XXXX XXXX XXXX ' + num.slice(-4) : 'Card';
      } else if (order.method === 'Wallet') {
        detail = (document.getElementById('walletSel') || {}).value || 'Wallet';
      } else {
        detail = (document.getElementById('bankSel') || {}).value || 'Net Banking';
      }

      var ctx = {
        name: order.buyer.name, email: order.buyer.email, phone: order.buyer.phone,
        city: order.city, method: order.method, methodDetail: detail
      };

      if (window.VITCPay) {
        window.VITCPay.processCheckout(order, ctx).then(function (result) {
          location.href = 'payment-success.html?order=' + encodeURIComponent(result.order.orderCode);
        }).catch(function (err) {
          btn.disabled = false; btn.textContent = 'Pay securely';
          toast(err.message || 'Payment could not be completed. Please try again.');
        });
      } else {
        setTimeout(function () { location.href = 'payment-success.html'; }, 1400);
      }
    });

    form && form.querySelectorAll('input,select').forEach(function (i) {
      i.addEventListener('input', function () { i.closest('.field') && i.closest('.field').classList.remove('invalid'); });
    });
  }

  /* ---------- Success page ---------- */
  var succ = document.getElementById('successPage');
  if (succ) {
    var o = getOrder();
    if (o && o.totals) {
      var put = function (k, v) { document.querySelectorAll('[data-i=' + k + ']').forEach(function (e) { e.textContent = v; }); };
      put('orderId', o.orderId || 'VITC-DEMO01');
      put('date', o.date || new Date().toLocaleString('en-IN'));
      put('method', o.method || 'UPI');
      put('item', o.title);
      put('meta', o.meta || (o.type === 'course' ? 'Course enrolment' : 'Assignment package'));
      put('base', INR(o.totals.base));
      put('disc', '– ' + INR(o.totals.disc));
      put('gst', INR(o.totals.gst));
      put('total', INR(o.totals.total));
      put('name', (o.buyer && o.buyer.name) || 'VITC Student');
      put('email', (o.buyer && o.buyer.email) || '—');
      put('phone', (o.buyer && o.buyer.phone) || '—');
      var dr2 = document.getElementById('iDiscRow');
      if (dr2) dr2.style.display = o.totals.disc ? 'flex' : 'none';
    }
    var pr = document.getElementById('printInvoice');
    if (pr) pr.addEventListener('click', function () { window.print(); });
    var cp = document.getElementById('copyId');
    if (cp) cp.addEventListener('click', function () {
      var v = document.querySelector('[data-i=orderId]').textContent;
      navigator.clipboard && navigator.clipboard.writeText(v);
      toast('Order ID copied: ' + v);
    });
  }

  /* ---------- Buy-course page: plan toggle ---------- */
  document.querySelectorAll('[data-plan]').forEach(function (b) {
    b.addEventListener('click', function () {
      document.querySelectorAll('[data-plan]').forEach(function (x) { x.classList.remove('active'); });
      b.classList.add('active');
      var one = b.dataset.plan === 'full';
      document.querySelectorAll('[data-emi]').forEach(function (e) { e.style.display = one ? 'none' : ''; });
      document.querySelectorAll('[data-fullpay]').forEach(function (e) { e.style.display = one ? '' : 'none'; });
    });
  });
})();
